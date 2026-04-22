package com.unique.examine.web.config;

import com.unique.examine.web.logging.PlatOperLogInterceptor;
import com.unique.examine.web.logging.RequestSummaryInterceptor;
import com.unique.examine.web.logging.TraceInterceptor;
import com.unique.examine.web.security.ModuleApiPathPermissionInterceptor;
import com.unique.examine.web.security.ModuleAuthContextInterceptor;
import com.unique.examine.web.security.SystemContextInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private PlatOperLogInterceptor platOperLogInterceptor;
    @Autowired
    private SystemContextInterceptor systemContextInterceptor;
    @Autowired
    private ModuleAuthContextInterceptor moduleAuthContextInterceptor;
    @Autowired
    private ModuleApiPathPermissionInterceptor moduleApiPathPermissionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TraceInterceptor())
                .addPathPatterns("/v1/**")
                .order(0);

        registry.addInterceptor(systemContextInterceptor)
                .addPathPatterns("/v1/system/**")
                .order(10);

        registry.addInterceptor(moduleAuthContextInterceptor)
                .addPathPatterns("/v1/system/**")
                .order(20);

        registry.addInterceptor(moduleApiPathPermissionInterceptor)
                .addPathPatterns("/v1/system/**")
                .order(25);

        registry.addInterceptor(new RequestSummaryInterceptor())
                .addPathPatterns("/v1/**")
                .order(30);

        registry.addInterceptor(platOperLogInterceptor)
                .addPathPatterns("/v1/**")
                .order(40)
                .excludePathPatterns(
                        "/v1/platform/auth/login",
                        "/v1/platform/auth/register",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/webjars/**",
                        "/doc.html"
                );
    }
}

