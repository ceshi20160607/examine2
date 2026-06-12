package com.unique.examine.web.config;

import java.io.IOException;
import java.util.Objects;

import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 将已登录平台账号写入请求上下文，避免系统内权限只信任前端传入的成员 ID。
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthenticatedRequestContextFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthSessionService authSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        bindAccountId(request);
        filterChain.doFilter(request, response);
    }

    private void bindAccountId(HttpServletRequest request) {
        String token = resolveBearer(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (!StringUtils.hasText(token)) {
            return;
        }
        try {
            CurrentUserVO currentUser = authSessionService.me(token);
            if (Objects.isNull(currentUser) || Objects.isNull(currentUser.getAccount())
                    || !StringUtils.hasText(currentUser.getAccount().getAccountId())) {
                return;
            }
            RequestContext context = RequestContextHolder.get();
            if (Objects.nonNull(context)) {
                context.setAccountId(currentUser.getAccount().getAccountId());
                MDC.put("accountId", currentUser.getAccount().getAccountId());
            }
        } catch (RuntimeException ignored) {
            // 鉴权错误仍交给具体接口返回统一异常；这里只负责尽力补齐上下文。
        }
    }

    private String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).strip();
        return StringUtils.hasText(token) ? token : null;
    }
}
