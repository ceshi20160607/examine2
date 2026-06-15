package com.unique.examine.web.config;

import com.unique.examine.web.flyway.FlywayManualMark;
import com.unique.examine.web.flyway.SchemaCompatibilityRepair;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 手工已执行版本（默认 V14）：先写 flyway_schema_history.success=1，再 migrate 后续脚本。
 */
@Configuration
public class FlywayStartupConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayStartupConfig.class);

    @Bean
    public FlywayConfigurationCustomizer flywayConfigurationCustomizer() {
        return configuration -> configuration.validateOnMigrate(false);
    }

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            DataSource dataSource,
            @Value("${examine.flyway.mark-applied:14}") String markApplied) {
        return (Flyway flyway) -> {
            SchemaCompatibilityRepair.repair(dataSource);
            log.info("Flyway startup: mark manual versions [{}] as success, then migrate rest", markApplied);
            FlywayManualMark.markAppliedVersions(dataSource, markApplied);
            flyway.repair();
            flyway.migrate();
        };
    }
}
