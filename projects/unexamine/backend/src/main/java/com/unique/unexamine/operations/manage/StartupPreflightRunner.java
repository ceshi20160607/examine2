package com.unique.unexamine.operations.manage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.preflight.startup-enabled", havingValue = "true", matchIfMissing = true)
public class StartupPreflightRunner implements ApplicationRunner {
    private static final Logger LOGGER = LoggerFactory.getLogger(StartupPreflightRunner.class);
    private final StartupPreflightChecks checks;

    public StartupPreflightRunner(StartupPreflightChecks checks) {
        this.checks = checks;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<StartupPreflightChecks.Check> results = checks.inspect();
        results.forEach(item -> LOGGER.info(
                "startup_preflight checkCode={} checkName={} status={} reason={}",
                item.code(), item.name(), item.status(), item.message()));
        List<StartupPreflightChecks.Check> failed = results.stream()
                .filter(item -> item.critical() && !item.passed()).toList();
        if (!failed.isEmpty()) {
            String reason = failed.stream().map(item -> item.code() + ": " + item.message())
                    .reduce((left, right) -> left + "; " + right).orElse("未知关键依赖错误");
            throw new IllegalStateException("STARTUP_PREFLIGHT_FAILED: " + reason);
        }
        LOGGER.info("startup_preflight status=READY checks={}", results.size());
    }
}
