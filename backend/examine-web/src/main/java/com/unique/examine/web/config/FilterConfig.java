package com.unique.examine.web.config;

import com.unique.examine.web.security.TokenAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class FilterConfig {

    @Bean
    public com.unique.examine.web.security.RequestContextFilter examineRequestContextFilter() {
        return new com.unique.examine.web.security.RequestContextFilter();
    }

    @Bean
    public TokenAuthenticationFilter tokenAuthenticationFilter() {
        return new TokenAuthenticationFilter();
    }

    @Bean
    public FilterRegistrationBean<com.unique.examine.web.security.RequestContextFilter> requestContextFilterRegistration(
            com.unique.examine.web.security.RequestContextFilter examineRequestContextFilter) {
        FilterRegistrationBean<com.unique.examine.web.security.RequestContextFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(examineRequestContextFilter);
        bean.addUrlPatterns("/v1/*", "/ping");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }

    @Bean
    public FilterRegistrationBean<TokenAuthenticationFilter> tokenFilterRegistration(TokenAuthenticationFilter filter) {
        FilterRegistrationBean<TokenAuthenticationFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(filter);
        bean.addUrlPatterns("/v1/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }
}
