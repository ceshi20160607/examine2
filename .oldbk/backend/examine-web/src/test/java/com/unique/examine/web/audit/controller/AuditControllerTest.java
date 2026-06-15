package com.unique.examine.web.audit.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import com.unique.examine.web.audit.bo.AuditLogQueryBO;
import com.unique.examine.web.audit.service.AuditOpsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuditControllerTest {

    private AuditOpsService auditOpsService;

    private AuthSessionService authSessionService;

    private AuditController controller;

    @BeforeEach
    void setUp() {
        auditOpsService = mock(AuditOpsService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new AuditController(auditOpsService, authSessionService);
    }

    @Test
    void shouldRejectAuditListWithoutBearerToken() {
        AuditLogQueryBO queryBO = new AuditLogQueryBO();

        assertThatThrownBy(() -> controller.systemOperationLogs("", 100L, queryBO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(auditOpsService, never()).systemOperationLogs(100L, queryBO);
    }

    @Test
    void shouldValidateBearerBeforeQueryOpenApiLogs() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());
        AuditLogQueryBO queryBO = new AuditLogQueryBO();
        queryBO.setRequestId("req-1");

        controller.systemOpenApiLogs("Bearer token-1", 100L, queryBO);

        verify(authSessionService).me("token-1");
        verify(auditOpsService).systemOpenApiLogs(100L, queryBO);
    }

    private CurrentUserVO currentUser() {
        return CurrentUserVO.builder()
                .account(AuthAccountVO.builder()
                        .accountId("200")
                        .loginName("owner")
                        .displayName("owner")
                        .status("NORMAL")
                        .build())
                .build();
    }
}
