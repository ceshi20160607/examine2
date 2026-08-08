package com.unique.examine.plat.manage.controller;

import com.unique.examine.plat.manage.config.SecurityProperties;
import com.unique.examine.plat.manage.service.IssuedSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;

@Component
public class SessionCookieSupport {
    public static final String ACCESS_COOKIE = "EXAMINE_ACCESS";
    public static final String REFRESH_COOKIE = "EXAMINE_REFRESH";
    public static final String CSRF_COOKIE = "EXAMINE_CSRF";

    private final SecurityProperties properties;

    public SessionCookieSupport(SecurityProperties properties) {
        this.properties = properties;
    }

    public void write(HttpServletResponse response, IssuedSession issued) {
        add(response, cookie(ACCESS_COOKIE, issued.accessToken(), issued.accessTtl(), "/", true));
        add(response, cookie(REFRESH_COOKIE, issued.refreshToken(), issued.refreshTtl(), "/api/v1/auth", true));
        add(response, cookie(CSRF_COOKIE, issued.csrfToken(), issued.refreshTtl(), "/", false));
    }

    public void clear(HttpServletResponse response) {
        add(response, cookie(ACCESS_COOKIE, "", Duration.ZERO, "/", true));
        add(response, cookie(REFRESH_COOKIE, "", Duration.ZERO, "/api/v1/auth", true));
        add(response, cookie(CSRF_COOKIE, "", Duration.ZERO, "/", false));
    }

    public static String value(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private ResponseCookie cookie(String name, String value, Duration maxAge, String path, boolean httpOnly) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.secureCookies())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }

    private static void add(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
