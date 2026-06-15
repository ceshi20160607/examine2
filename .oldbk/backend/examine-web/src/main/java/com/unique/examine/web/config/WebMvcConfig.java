package com.unique.examine.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 装配入口，用于集中维护跨域、拦截器和格式化配置。
 */
@Configuration(proxyBeanMethods = false)
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 放行本机和局域网前端预览地址，支持前后端分离部署验收。
     *
     * @param registry CORS 注册器
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://192.168.*.*:*"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Request-Id", "X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
