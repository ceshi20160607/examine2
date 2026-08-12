package com.unique.examine.web;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "examine.foundation.vnext.enabled", havingValue = "true")
class FoundationDependencyStartupCheck implements ApplicationRunner {
    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final Flyway flyway;

    FoundationDependencyStartupCheck(
            DataSource dataSource,
            RedisConnectionFactory redisConnectionFactory,
            Flyway flyway
    ) {
        this.dataSource = dataSource;
        this.redisConnectionFactory = redisConnectionFactory;
        this.flyway = flyway;
    }

    @Override
    public void run(ApplicationArguments args) {
        checkDatabase();
        checkFlyway();
        checkRedis();
    }

    private void checkDatabase() {
        try (var connection = dataSource.getConnection()) {
            if (!connection.isValid(5)) {
                throw new IllegalStateException("database connection validation returned false");
            }
        } catch (Exception unavailable) {
            throw dependencyFailure("FOUNDATION_DEPENDENCY_DATABASE_UNAVAILABLE", "MySQL", unavailable);
        }
    }

    private void checkFlyway() {
        try {
            var info = flyway.info();
            if (info.current() == null) {
                throw new IllegalStateException("no applied foundation migration");
            }
            if (info.pending().length > 0) {
                throw new IllegalStateException("foundation migrations remain pending");
            }
        } catch (Exception unavailable) {
            throw dependencyFailure("FOUNDATION_DEPENDENCY_FLYWAY_UNAVAILABLE", "Flyway", unavailable);
        }
    }

    private void checkRedis() {
        try (var connection = redisConnectionFactory.getConnection()) {
            var response = connection.ping();
            if (!"PONG".equalsIgnoreCase(response)) {
                throw new IllegalStateException("Redis ping did not return PONG");
            }
        } catch (Exception unavailable) {
            throw dependencyFailure("FOUNDATION_DEPENDENCY_REDIS_UNAVAILABLE", "Redis", unavailable);
        }
    }

    static IllegalStateException dependencyFailure(String code, String dependency, Throwable cause) {
        return new IllegalStateException(
                code + ": " + dependency + " is required by the VNext foundation; check connection configuration and availability",
                cause
        );
    }
}
