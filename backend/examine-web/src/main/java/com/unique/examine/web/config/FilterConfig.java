package com.unique.examine.web.config;

import com.unique.examine.web.security.TokenAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public com.unique.examine.web.security.RequestContextFilter requestContextFilter() {
        return new com.unique.examine.web.security.RequestContextFilter();
    }

    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter(com.unique.examine.core.service.SessionService sessionService) {
        return new TokenAuthenticationFilter(sessionService);
    }

    @Bean
    public FilterRegistrationBean<com.unique.examine.web.security.RequestContextFilter> requestContextFilterRegistration(
            com.unique.examine.web.security.RequestContextFilter filter) {
        FilterRegistrationBean<com.unique.examine.web.security.RequestContextFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TokenAuthenticationFilter> tokenFilterRegistration(TokenAuthenticationFilter filter) {
        FilterRegistrationBean<TokenAuthenticationFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns("/api/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }
}
