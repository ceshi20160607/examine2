package com.unique.examine.web.security;

import com.unique.examine.app.entity.po.AppClient;
import com.unique.examine.app.entity.po.AppClientCredential;
import com.unique.examine.app.service.IAppClientCredentialService;
import com.unique.examine.app.service.IAppClientService;
import com.unique.examine.core.security.AuthContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 对外开放接口 {@code /v1/open/**}：使用 accessKey + secret（BCrypt 校验），并解析目标 system/tenant 与代操作人 platId。
 * <p>
 * 认证方式（二选一）：
 * <ul>
 *   <li>{@code Authorization: Basic base64(accessKey:secret)}</li>
 *   <li>{@code X-Access-Key} + {@code X-Secret}</li>
 * </ul>
 * 作用域：
 * <ul>
 *   <li>若 {@code un_app_client.system_id != 0}：固定使用该 client 绑定的 systemId/tenantId</li>
 *   <li>若 {@code system_id == 0}（平台创建的全局 client）：必须传 {@code X-Target-System-Id}，可选 {@code X-Target-Tenant-Id}（默认 0）</li>
 * </ul>
 * 代操作人：必须 {@code X-Acting-Plat-Id}（后续引擎/审计按该 platId）
 */
public class OpenApiAuthenticationFilter extends OncePerRequestFilter {

    public static final String HDR_ACCESS_KEY = "X-Access-Key";
    public static final String HDR_SECRET = "X-Secret";
    public static final String HDR_ACTING_PLAT = "X-Acting-Plat-Id";
    public static final String HDR_TARGET_SYSTEM = "X-Target-System-Id";
    public static final String HDR_TARGET_TENANT = "X-Target-Tenant-Id";

    private final IAppClientCredentialService appClientCredentialService;
    private final IAppClientService appClientService;
    private final PasswordEncoder passwordEncoder;

    public OpenApiAuthenticationFilter(IAppClientCredentialService appClientCredentialService,
                                       IAppClientService appClientService,
                                       PasswordEncoder passwordEncoder) {
        this.appClientCredentialService = appClientCredentialService;
        this.appClientService = appClientService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/v1/open/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String[] akSk = resolveAccessKeySecret(request);
        if (akSk == null || akSk[0] == null || akSk[0].isBlank() || akSk[1] == null) {
            unauthorized(response, "缺少 accessKey/secret");
            return;
        }
        String accessKey = akSk[0].trim();
        String secret = akSk[1];

        AppClientCredential cred = appClientCredentialService.lambdaQuery()
                    .eq(AppClientCredential::getAccessKey, accessKey)
                    .eq(AppClientCredential::getStatus, 1)
                    .last("limit 1")
                    .one();
            if (cred == null) {
                unauthorized(response, "凭证无效");
                return;
            }
            if (!passwordEncoder.matches(secret, cred.getSecretHash())) {
                unauthorized(response, "secret 不匹配");
                return;
            }

            AppClient client = appClientService.getById(cred.getClientId());
            if (client == null || client.getStatus() == null || client.getStatus() != 1) {
                unauthorized(response, "client 已停用");
                return;
            }

            long systemId;
            long tenantId;
            if (client.getSystemId() != null && client.getSystemId() != 0L) {
                systemId = client.getSystemId();
                tenantId = client.getTenantId() == null ? 0L : client.getTenantId();
            } else {
                String ts = request.getHeader(HDR_TARGET_SYSTEM);
                if (ts == null || ts.isBlank()) {
                    unauthorized(response, "全平台 client 须传 " + HDR_TARGET_SYSTEM);
                    return;
                }
                try {
                    systemId = Long.parseLong(ts.trim());
                } catch (NumberFormatException e) {
                    unauthorized(response, HDR_TARGET_SYSTEM + " 非法");
                    return;
                }
                String tt = request.getHeader(HDR_TARGET_TENANT);
                if (tt == null || tt.isBlank()) {
                    tenantId = 0L;
                } else {
                    try {
                        tenantId = Long.parseLong(tt.trim());
                    } catch (NumberFormatException e) {
                        unauthorized(response, HDR_TARGET_TENANT + " 非法");
                        return;
                    }
                }
            }

            String acting = request.getHeader(HDR_ACTING_PLAT);
            if (acting == null || acting.isBlank()) {
                unauthorized(response, "须传 " + HDR_ACTING_PLAT);
                return;
            }
            long actingPlatId;
            try {
                actingPlatId = Long.parseLong(acting.trim());
            } catch (NumberFormatException e) {
                unauthorized(response, HDR_ACTING_PLAT + " 非法");
                return;
            }

            AuthContextHolder.setPlatId(actingPlatId);
            AuthContextHolder.setUsername("openapi:" + client.getClientCode());
            AuthContextHolder.setSystemId(systemId);
            AuthContextHolder.setTenantId(tenantId);
            request.setAttribute("openApiClientId", client.getId());
            request.setAttribute("openApiCredentialId", cred.getId());

        filterChain.doFilter(request, response);
    }

    private static String[] resolveAccessKeySecret(HttpServletRequest request) {
        String basic = request.getHeader("Authorization");
        if (basic != null && basic.regionMatches(true, 0, "Basic ", 0, 6)) {
            String b64 = basic.substring(6).trim();
            try {
                byte[] raw = Base64.getDecoder().decode(b64);
                String s = new String(raw, StandardCharsets.UTF_8);
                int idx = s.indexOf(':');
                if (idx <= 0) {
                    return null;
                }
                return new String[]{s.substring(0, idx), s.substring(idx + 1)};
            } catch (Exception ignore) {
                return null;
            }
        }
        String ak = request.getHeader(HDR_ACCESS_KEY);
        String sk = request.getHeader(HDR_SECRET);
        if (ak != null && sk != null) {
            return new String[]{ak, sk};
        }
        return null;
    }

    private static void unauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        String m = msg == null ? "" : msg.replace("\"", "\\\"");
        response.getWriter().write("{\"code\":401,\"message\":\"" + m + "\"}");
    }
}
