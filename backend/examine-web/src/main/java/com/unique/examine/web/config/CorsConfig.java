package com.unique.examine.web.config;

import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 前后端分离部署的跨域配置。
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 前端允许访问后端接口的来源列表，多个来源用英文逗号分隔。
     */
    private final String allowedOrigins;

    public CorsConfig(@Value("${unexamine.security.cors-allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * 注册 API 跨域规则，支持前端生产预览、独立部署和本地开发服务访问。
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
        if (origins.length == 0) {
            return;
        }
        registry.addMapping("/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(RequestContextFilter.REQUEST_ID_HEADER, RequestContextFilter.TRACE_ID_HEADER)
                .maxAge(3600);
    }
}
