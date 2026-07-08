package com.unique.examine.web.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@Configuration
public class TomcatProtocolConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatProtocolCustomizer(Environment environment) {
        return factory -> {
            String protocol = environment.getProperty("unexamine.tomcat.protocol");
            if (StringUtils.hasText(protocol)) {
                factory.setProtocol(protocol.trim());
            }
        };
    }
}
