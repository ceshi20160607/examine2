package com.unique.examine.app.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.examine.app.base.entity.AccessLog;
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
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.base.service.IModelService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenApiSecurityServiceImplTest {

    private IClientService clientService;

    private IClientCredentialService credentialService;

    private IAccessLogService accessLogService;

    private OpenApiSecurityServiceImpl openApiSecurityService;

    @BeforeEach
    void setUp() {
        clientService = mock(IClientService.class);
        credentialService = mock(IClientCredentialService.class);
        accessLogService = mock(IAccessLogService.class);
        openApiSecurityService = new OpenApiSecurityServiceImpl(
                clientService,
                credentialService,
                mock(IClientScopeService.class),
                mock(IIpWhitelistService.class),
                mock(INonceService.class),
                mock(IIdempotencyRecordService.class),
                mock(IRateLimitPolicyService.class),
                mock(IRateLimitCounterService.class),
                accessLogService,
                mock(IModelService.class));
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
}
