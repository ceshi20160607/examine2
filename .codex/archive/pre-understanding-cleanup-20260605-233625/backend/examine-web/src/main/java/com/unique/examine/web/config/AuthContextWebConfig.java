package com.unique.examine.web.config;

import cn.hutool.core.util.StrUtil;
import com.unique.examine.core.security.AuthContext;
import com.unique.examine.core.security.AuthContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 请求头认证上下文装配。
 */
@Configuration
public class AuthContextWebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthContextInterceptor()).addPathPatterns("/api/**");
    }

    /**
     * 从请求头装配上下文。
     */
    private static class AuthContextInterceptor implements HandlerInterceptor {

        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            AuthContextHolder.set(AuthContext.builder()
                    .accountId(parseLong(request.getHeader("X-Account-Id")))
                    .tenantId(parseLong(request.getHeader("X-Tenant-Id")))
                    .systemId(parseLong(request.getHeader("X-System-Id")))
                    .build());
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            AuthContextHolder.clear();
        }

        /**
         * 解析请求头中的 Long 值。
         *
         * @param value 请求头值
         * @return Long 值
         */
        private Long parseLong(String value) {
            return StrUtil.isBlank(value) ? null : Long.valueOf(value);
        }
    }
}
