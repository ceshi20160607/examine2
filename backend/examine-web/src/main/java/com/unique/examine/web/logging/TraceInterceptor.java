package com.unique.examine.web.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class TraceInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod hm) {
            String sig = hm.getBeanType().getSimpleName() + "#" + hm.getMethod().getName();
            request.setAttribute("handlerSig", sig);
        }
        return true;
    }
}

