package com.unique.core.config;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 跨域过滤器
 * @author click33
 */
@Component
@Order(-200)
public class CorsFilter implements Filter {

    static final String OPTIONS = "OPTIONS";
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        // 允许指定域访问跨域资源
        response.setHeader("Access-Control-Allow-Origin", "*");
        // 允许所有请求方式
        response.setHeader("Access-Control-Allow-Methods", "*");
        // 有效时间
        response.setHeader("Access-Control-Max-Age", "3600");
        // 允许的header参数
        response.setHeader("Access-Control-Allow-Headers", "*");

        // 如果是预检请求，直接返回
        if (OPTIONS.equals(request.getMethod())) {
            System.out.println("=======================浏览器发来了OPTIONS预检请求==========" +request.getMethod() +request.getRequestedSessionId());
            System.out.println("=======================浏览器发来了OPTIONS预检请求==========");
//            response.getWriter().print("");
            return;
        }

         System.out.println(((HttpServletRequest) req).getHeader("Access-Control-Allow-Headers"));
         System.out.println("*********************************过滤器被使用**************************");
        chain.doFilter(req, res);
    }
}
