package com.unique.examine.plat.manage.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SecurityProperties.class,
        BootstrapProperties.class,
        PasswordRecoveryProperties.class
})
public class PlatConfiguration {
}
