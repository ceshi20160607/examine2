package com.unique.examine.web.config;

import com.unique.examine.plat.service.PlatOperLogService;
import com.unique.examine.web.logging.PlatOperLogInterceptor;
import com.unique.examine.web.logging.RequestSummaryInterceptor;
import com.unique.examine.web.logging.TraceInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final PlatOperLogService operLogService;

    public WebMvcConfig(PlatOperLogService operLogService) {
        this.operLogService = operLogService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceInterceptor())
                .addPathPatterns("/api/**");

        registry.addInterceptor(new RequestSummaryInterceptor())
                .addPathPatterns("/api/**");

        registry.addInterceptor(new PlatOperLogInterceptor(operLogService))
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/ping",
                        "/api/v1/platform/auth/login",
                        "/api/v1/platform/auth/register",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/doc.html"
                );
    }
}

