/*
 * Copyright 2020 Netflix, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.netflix.conductor.postgres.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.sql.DataSource;
import java.nio.file.Paths;
import java.util.concurrent.ThreadFactory;

public class PostgresDataSourceProvider implements Provider<DataSource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresDataSourceProvider.class);

    private final PostgresProperties properties;

    public PostgresDataSourceProvider(PostgresProperties properties) {
        this.properties = properties;
    }

    @Override
    public DataSource get() {
        HikariDataSource dataSource = null;
        try {
            dataSource = new HikariDataSource(createConfiguration());
            flywayMigrate(dataSource);
            return dataSource;
        } catch (final Throwable t) {
            if (null != dataSource && !dataSource.isClosed()) {
                dataSource.close();
            }
            LOGGER.error("error migration DB", t);
            throw t;
        }
    }

    private HikariConfig createConfiguration() {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(properties.getJdbcUrl());
        hikariConfig.setUsername(properties.getJdbcUserName());
        hikariConfig.setPassword(properties.getJdbcPassword());
        hikariConfig.setAutoCommit(false);
        hikariConfig.setMaximumPoolSize(properties.getConnectionPoolMaxSize());
        hikariConfig.setMinimumIdle(properties.getConnectionPoolMinIdle());
        hikariConfig.setMaxLifetime(properties.getConnectionMaxLifetime());
        hikariConfig.setIdleTimeout(properties.getConnectionIdleTimeout());
        hikariConfig.setConnectionTimeout(properties.getConnectionTimeout());
        hikariConfig.setTransactionIsolation(properties.getTransactionIsolationLevel());
        hikariConfig.setAutoCommit(properties.isAutoCommit());

        ThreadFactory tf = new ThreadFactoryBuilder()
            .setDaemon(true)
            .setNameFormat("hikari-postgres-%d")
            .build();

        hikariConfig.setThreadFactory(tf);
        return hikariConfig;
    }

    // TODO Move this into a class that has complete lifecycle for the connection, i.e. startup and shutdown.
    private void flywayMigrate(DataSource dataSource) {
        boolean enabled = properties.isFlywayEnabled();
        if (!enabled) {
            LOGGER.debug("Flyway migrations are disabled");
            return;
        }

        Flyway flyway = new Flyway();
        properties.getFlywayTable().ifPresent(tableName -> {
            LOGGER.debug("Using Flyway migration table '{}'", tableName);
            flyway.setTable(tableName);
        });

        flyway.setLocations(Paths.get("db", "migration_postgres").toString());
        flyway.setDataSource(dataSource);
        flyway.setPlaceholderReplacement(false);
        flyway.migrate();
    }
}