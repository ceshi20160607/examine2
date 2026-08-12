package com.unique.examine.ai;

import com.unique.examine.core.api.DefaultPlatformSecretResolverFacade;
import com.unique.examine.core.api.PlatformSecretResolverFacade;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

@Configuration(proxyBeanMethods = false)
public class AiConfiguration {
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock aiClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean(PlatformSecretResolverFacade.class)
    PlatformSecretResolverFacade platformAiSecrets(
            @Value("${examine.ai.platform.secret-file-roots:}") String roots) {
        List<Path> values = roots == null || roots.isBlank()
                ? List.of()
                : Arrays.stream(roots.split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(Path::of)
                .map(path -> {
                    if (!path.isAbsolute()) {
                        throw new IllegalArgumentException(
                                "Platform AI secret file roots must be absolute");
                    }
                    return path.normalize();
                }).toList();
        return new DefaultPlatformSecretResolverFacade(values);
    }
}
