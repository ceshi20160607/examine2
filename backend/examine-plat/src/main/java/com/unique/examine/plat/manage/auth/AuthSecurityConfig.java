package com.unique.examine.plat.manage.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Authentication security component configuration.
 */
@Configuration(proxyBeanMethods = false)
public class AuthSecurityConfig {

    /**
     * Password encoder for persisted platform accounts.
     *
     * @return BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
