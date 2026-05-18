package com.unique.examine.web.security;

import com.unique.examine.app.entity.po.AppClient;
import com.unique.examine.app.entity.po.AppClientCredential;
import com.unique.examine.app.security.OpenApiSignatureSupport;
import com.unique.examine.app.security.OpenApiSigningSecretCrypto;
import com.unique.examine.app.service.IAppClientCredentialService;
import com.unique.examine.app.service.IAppClientService;
import com.unique.examine.core.security.AuthContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 对外开放接口 {@code /v1/open/**}：AK/SK 或 HMAC 签名，并解析目标 system/tenant 与代操作人 platId。
 * <p>
 * 认证方式（三选一）：
 * <ul>
 *   <li>签名（推荐）：{@code X-Access-Key} + {@code X-Timestamp} + {@code X-Signature}</li>
 *   <li>{@code Authorization: Basic base64(accessKey:secret)}</li>
 *   <li>{@code X-Access-Key} + {@code X-Secret}（兼容旧客户端）</li>
 * </ul>
 */
public class OpenApiAuthenticationFilter extends OncePerRequestFilter {

    public static final String HDR_ACCESS_KEY = "X-Access-Key";
    public static final String HDR_SECRET = "X-Secret";
    public static final String HDR_TIMESTAMP = "X-Timestamp";
    public static final String HDR_SIGNATURE = "X-Signature";
    public static final String HDR_SIGNATURE_VERSION = "X-Signature-Version";
    public static final String HDR_ACTING_PLAT = "X-Acting-Plat-Id";
    public static final String HDR_TARGET_SYSTEM = "X-Target-System-Id";
    public static final String HDR_TARGET_TENANT = "X-Target-Tenant-Id";

    private final IAppClientCredentialService appClientCredentialService;
    private final IAppClientService appClientService;
    private final PasswordEncoder passwordEncoder;
    private final OpenApiSigningSecretCrypto signingSecretCrypto;

    public OpenApiAuthenticationFilter(IAppClientCredentialService appClientCredentialService,
                                       IAppClientService appClientService,
                                       PasswordEncoder passwordEncoder,
                                       OpenApiSigningSecretCrypto signingSecretCrypto) {
        this.appClientCredentialService = appClientCredentialService;
        this.appClientService = appClientService;
        this.passwordEncoder = passwordEncoder;
        this.signingSecretCrypto = signingSecretCrypto;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/v1/open/")) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletRequest effective = request;
        String signatureHeader = request.getHeader(HDR_SIGNATURE);
        boolean signatureMode = signatureHeader != null && !signatureHeader.isBlank();

        AppClientCredential cred;
        if (signatureMode) {
            effective = new CachedBodyHttpServletRequest(request);
            cred = authenticateBySignature((CachedBodyHttpServletRequest) effective, signatureHeader, response);
        } else {
            cred = authenticateBySecret(request, response);
        }
        if (cred == null) {
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
            String ts = effective.getHeader(HDR_TARGET_SYSTEM);
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
            String tt = effective.getHeader(HDR_TARGET_TENANT);
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

        String acting = effective.getHeader(HDR_ACTING_PLAT);
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
        effective.setAttribute("openApiClientId", client.getId());
        effective.setAttribute("openApiCredentialId", cred.getId());

        filterChain.doFilter(effective, response);
    }

    private AppClientCredential authenticateBySecret(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] akSk = resolveAccessKeySecret(request);
        if (akSk == null || akSk[0] == null || akSk[0].isBlank() || akSk[1] == null) {
            unauthorized(response, "缺少 accessKey/secret 或签名头");
            return null;
        }
        String accessKey = akSk[0].trim();
        String secret = akSk[1];

        AppClientCredential cred = loadActiveCredential(accessKey);
        if (cred == null) {
            unauthorized(response, "凭证无效");
            return null;
        }
        if (!passwordEncoder.matches(secret, cred.getSecretHash())) {
            unauthorized(response, "secret 不匹配");
            return null;
        }
        return cred;
    }

    private AppClientCredential authenticateBySignature(CachedBodyHttpServletRequest request,
                                                          String signatureHeader,
                                                          HttpServletResponse response) throws IOException {
        String accessKey = request.getHeader(HDR_ACCESS_KEY);
        if (accessKey == null || accessKey.isBlank()) {
            unauthorized(response, "签名模式须传 " + HDR_ACCESS_KEY);
            return null;
        }
        accessKey = accessKey.trim();

        String timestamp = request.getHeader(HDR_TIMESTAMP);
        if (!OpenApiSignatureSupport.isTimestampValid(timestamp, Instant.now().getEpochSecond())) {
            unauthorized(response, HDR_TIMESTAMP + " 无效或超出允许偏差");
            return null;
        }

        String version = request.getHeader(HDR_SIGNATURE_VERSION);
        if (version != null && !version.isBlank() && !OpenApiSignatureSupport.VERSION.equals(version.trim())) {
            unauthorized(response, "不支持的 " + HDR_SIGNATURE_VERSION);
            return null;
        }

        AppClientCredential cred = loadActiveCredential(accessKey);
        if (cred == null) {
            unauthorized(response, "凭证无效");
            return null;
        }
        if (cred.getSignSecretEnc() == null || cred.getSignSecretEnc().isBlank()) {
            unauthorized(response, "该凭证未启用签名模式，请轮换 SK 后重试");
            return null;
        }
        String secret = signingSecretCrypto.decrypt(cred.getSignSecretEnc());
        if (secret == null || secret.isBlank()) {
            unauthorized(response, "签名密钥不可用，请轮换 SK");
            return null;
        }

        String path = OpenApiSignatureSupport.canonicalPath(request.getRequestURI(), request.getQueryString());
        String canonical = OpenApiSignatureSupport.buildCanonical(
                request.getMethod(), path, timestamp, request.getCachedBody());
        if (!OpenApiSignatureSupport.verify(secret, canonical, signatureHeader)) {
            unauthorized(response, "签名不匹配");
            return null;
        }
        return cred;
    }

    private AppClientCredential loadActiveCredential(String accessKey) {
        return appClientCredentialService.lambdaQuery()
                .eq(AppClientCredential::getAccessKey, accessKey)
                .eq(AppClientCredential::getStatus, 1)
                .last("limit 1")
                .one();
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
