package com.unique.examine.app.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.unique.examine.app.base.entity.AccessLog;
import com.unique.examine.app.base.entity.Client;
import com.unique.examine.app.base.entity.ClientCredential;
import com.unique.examine.app.base.entity.ClientScope;
import com.unique.examine.app.base.entity.Nonce;
import com.unique.examine.app.base.entity.RateLimitCounter;
import com.unique.examine.app.base.entity.RateLimitPolicy;
import com.unique.examine.app.base.service.IAccessLogService;
import com.unique.examine.app.base.service.IClientCredentialService;
import com.unique.examine.app.base.service.IClientScopeService;
import com.unique.examine.app.base.service.IClientService;
import com.unique.examine.app.base.service.IIdempotencyRecordService;
import com.unique.examine.app.base.service.IIpWhitelistService;
import com.unique.examine.app.base.service.INonceService;
import com.unique.examine.app.base.service.IRateLimitCounterService;
import com.unique.examine.app.base.service.IRateLimitPolicyService;
import com.unique.examine.app.manage.enums.OpenApiErrorCode;
import com.unique.examine.app.manage.support.OpenApiConstants;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.service.IModelService;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenApiSecurityServiceImplTest {

    private IClientService clientService;

    private IClientCredentialService credentialService;

    private IClientScopeService scopeService;

    private IIpWhitelistService ipWhitelistService;

    private INonceService nonceService;

    private IRateLimitPolicyService rateLimitPolicyService;

    private IRateLimitCounterService rateLimitCounterService;

    private IAccessLogService accessLogService;

    private IModelService modelService;

    private OpenApiSecurityServiceImpl openApiSecurityService;

    @BeforeEach
    void setUp() {
        clientService = mock(IClientService.class);
        credentialService = mock(IClientCredentialService.class);
        scopeService = mock(IClientScopeService.class);
        ipWhitelistService = mock(IIpWhitelistService.class);
        nonceService = mock(INonceService.class);
        rateLimitPolicyService = mock(IRateLimitPolicyService.class);
        rateLimitCounterService = mock(IRateLimitCounterService.class);
        accessLogService = mock(IAccessLogService.class);
        modelService = mock(IModelService.class);
        openApiSecurityService = new OpenApiSecurityServiceImpl(
                clientService,
                credentialService,
                scopeService,
                ipWhitelistService,
                nonceService,
                mock(IIdempotencyRecordService.class),
                rateLimitPolicyService,
                rateLimitCounterService,
                accessLogService,
                modelService);
    }

    @Test
    void shouldUseOpenApiAccessKeyErrorWhenAccessKeyHeaderMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Request-Id")).thenReturn("req-openapi-ak");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/openapi/v1/records/query");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThatThrownBy(() -> openApiSecurityService.verify(request, "{}", "OPN-001",
                "record:read", "expense_apply", false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(OpenApiErrorCode.ACCESS_KEY_INVALID);

        ArgumentCaptor<AccessLog> logCaptor = ArgumentCaptor.forClass(AccessLog.class);
        verify(accessLogService).updateById(logCaptor.capture());
        assertThat(logCaptor.getValue().getErrorCode()).isEqualTo("OPENAPI_ACCESS_KEY_INVALID");
        assertThat(logCaptor.getValue().getHttpStatus()).isEqualTo(401);
        verify(credentialService, never()).lambdaQuery();
        verify(clientService, never()).getById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldUseOpenApiAccessKeyErrorWhenAccessKeyUnknown() {
        HttpServletRequest request = baseRequest("{}");
        when(credentialService.lambdaQuery()).thenReturn(queryReturningOne(null));

        assertOpenApiError(request, "{}", OpenApiErrorCode.ACCESS_KEY_INVALID);
    }

    @Test
    void shouldUseTimestampErrorWhenTimestampExpired() {
        HttpServletRequest request = validRequest("{}");
        when(request.getHeader("X-OpenApi-Timestamp")).thenReturn("1");
        when(request.getHeader("x-openapi-timestamp")).thenReturn("1");
        mockCredentialAndClient();
        mockIpPass();

        assertOpenApiError(request, "{}", OpenApiErrorCode.TIMESTAMP_EXPIRED);
    }

    @Test
    void shouldUseBodyHashErrorWhenBodyHashMismatch() {
        HttpServletRequest request = validRequest("{}");
        when(request.getHeader("X-OpenApi-Body-Sha256")).thenReturn("bad-body-hash");
        when(request.getHeader("x-openapi-body-sha256")).thenReturn("bad-body-hash");
        mockCredentialAndClient();
        mockIpPass();
        mockNoncePass();

        assertOpenApiError(request, "{}", OpenApiErrorCode.BODY_HASH_MISMATCH);
    }

    @Test
    void shouldUseSignatureErrorWhenSignatureMismatch() {
        HttpServletRequest request = validRequest("{}");
        when(request.getHeader("X-OpenApi-Signature")).thenReturn("bad-signature");
        mockCredentialAndClient();
        mockIpPass();
        mockNoncePass();

        assertOpenApiError(request, "{}", OpenApiErrorCode.SIGNATURE_INVALID);
    }

    @Test
    void shouldUseScopeDeniedWhenScopeNotGranted() {
        HttpServletRequest request = validRequest("{}");
        signRequest(request, "{}");
        mockCredentialAndClient();
        mockIpPass();
        mockNoncePass();
        when(scopeService.lambdaQuery()).thenReturn(queryReturningList(List.of()));

        assertOpenApiError(request, "{}", OpenApiErrorCode.SCOPE_DENIED);
    }

    @Test
    void shouldUseRateLimitedWhenPolicyWindowExceeded() {
        HttpServletRequest request = validRequest("{}");
        signRequest(request, "{}");
        mockCredentialAndClient();
        mockIpPass();
        mockNoncePass();
        when(scopeService.lambdaQuery()).thenReturn(queryReturningList(List.of(new ClientScope()
                .setClientId(10L)
                .setScopeCode("record:read")
                .setStatus("ENABLED"))));
        when(rateLimitPolicyService.lambdaQuery()).thenReturn(queryReturningList(List.of(new RateLimitPolicy()
                .setId(1L)
                .setClientId(10L)
                .setApiId("OPN-001")
                .setScopeCode("record:read")
                .setSourceIp("127.0.0.1")
                .setWindowSeconds(60)
                .setMaxRequests(1)
                .setBurst(0)
                .setStatus("ENABLED"))));
        when(rateLimitCounterService.lambdaQuery()).thenReturn(queryReturningOne(new RateLimitCounter()
                .setPolicyId(1L)
                .setDimensionKey("10:20:30:OPN-001:record:read:127.0.0.1")
                .setRequestCount(1)));

        assertOpenApiError(request, "{}", OpenApiErrorCode.RATE_LIMITED);
    }

    private void assertOpenApiError(HttpServletRequest request, String body, OpenApiErrorCode expected) {
        assertThatThrownBy(() -> openApiSecurityService.verify(request, body, "OPN-001",
                "record:read", "expense_apply", false))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(expected);
    }

    private void mockCredentialAndClient() {
        ClientCredential credential = new ClientCredential()
                .setClientId(10L)
                .setAccessKey("ak-test")
                .setStatus("ACTIVE")
                .setSignSecretEnc(Base64.getEncoder().encodeToString("secret-test".getBytes(StandardCharsets.UTF_8)));
        Client client = new Client()
                .setId(10L)
                .setSystemId(20L)
                .setTenantId(30L)
                .setStatus("ENABLED")
                .setDeleted((byte) 0)
                .setExpiresAt(LocalDateTime.now().plusDays(1));
        when(credentialService.lambdaQuery()).thenReturn(queryReturningOne(credential));
        when(clientService.getById(10L)).thenReturn(client);
        when(modelService.lambdaQuery()).thenReturn(queryReturningOne(new Model()
                .setModuleId(40L)
                .setSystemId(20L)
                .setTenantId(30L)
                .setCode("expense_apply")));
    }

    private void mockIpPass() {
        when(ipWhitelistService.lambdaQuery()).thenReturn(queryReturningList(List.of()));
    }

    private void mockNoncePass() {
        when(nonceService.lambdaQuery()).thenReturn(queryReturningOne((Nonce) null));
    }

    private HttpServletRequest baseRequest(String body) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Request-Id")).thenReturn("req-openapi-matrix");
        when(request.getHeader("x-request-id")).thenReturn("req-openapi-matrix");
        when(request.getHeader("X-OpenApi-AccessKey")).thenReturn("ak-test");
        when(request.getHeader("x-openapi-accesskey")).thenReturn("ak-test");
        when(request.getHeader("X-OpenApi-Body-Sha256")).thenReturn(sha256Hex(body));
        when(request.getHeader("x-openapi-body-sha256")).thenReturn(sha256Hex(body));
        when(request.getHeader("X-OpenApi-Nonce")).thenReturn("nonce-test");
        when(request.getHeader("x-openapi-nonce")).thenReturn("nonce-test");
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/openapi/v1/records/query");
        when(request.getParameterMap()).thenReturn(java.util.Map.of());
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        return request;
    }

    private HttpServletRequest validRequest(String body) {
        HttpServletRequest request = baseRequest(body);
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        when(request.getHeader("X-OpenApi-Timestamp")).thenReturn(timestamp);
        when(request.getHeader("x-openapi-timestamp")).thenReturn(timestamp);
        signRequest(request, body);
        return request;
    }

    private void signRequest(HttpServletRequest request, String body) {
        String requestSignature = signature(request, body);
        when(request.getHeader("X-OpenApi-Signature")).thenReturn(requestSignature);
    }

    private String signature(HttpServletRequest request, String body) {
        String canonicalRequest = request.getMethod() + "\n"
                + request.getRequestURI() + "\n"
                + "\n"
                + OpenApiConstants.SIGNED_HEADER_NAMES.stream()
                .map(header -> header + ":" + request.getHeader(header) + "\n")
                .reduce("", String::concat)
                + "\n"
                + OpenApiConstants.SIGNED_HEADERS + "\n"
                + sha256Hex(body);
        String stringToSign = OpenApiConstants.SIGN_ALGORITHM + "\n"
                + request.getHeader("X-OpenApi-Timestamp") + "\n"
                + sha256Hex(canonicalRequest);
        return hmacSha256Hex("secret-test", stringToSign);
    }

    private String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaQueryChainWrapper<T> queryReturningOne(T entity) {
        return mock(LambdaQueryChainWrapper.class, invocation -> {
            if ("one".equals(invocation.getMethod().getName())) {
                return entity;
            }
            if ("list".equals(invocation.getMethod().getName())) {
                return entity == null ? List.of() : List.of(entity);
            }
            return invocation.getMock();
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> LambdaQueryChainWrapper<T> queryReturningList(List<T> values) {
        return mock(LambdaQueryChainWrapper.class, invocation -> {
            if ("one".equals(invocation.getMethod().getName())) {
                return values.isEmpty() ? null : values.get(0);
            }
            if ("list".equals(invocation.getMethod().getName())) {
                return values;
            }
            return invocation.getMock();
        });
    }
}
