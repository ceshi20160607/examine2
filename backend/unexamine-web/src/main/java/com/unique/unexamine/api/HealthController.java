package com.unique.unexamine.api;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    @GetMapping("/api/v1/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "service", "unexamine-clean",
                "phase", "clean-rebuild-scaffold",
                "time", OffsetDateTime.now().toString()
        );
    }
}
