package com.unique.examine.web.ops.controller;

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
import com.unique.examine.web.audit.service.AuditOpsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpsControllerTest {

    private AuditOpsService auditOpsService;

    private AuthSessionService authSessionService;

    private OpsController controller;

    @BeforeEach
    void setUp() {
        auditOpsService = mock(AuditOpsService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new OpsController(auditOpsService, authSessionService);
    }

    @Test
    void shouldRejectHealthWithoutBearerToken() {
        assertThatThrownBy(() -> controller.health("", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(auditOpsService, never()).health("req-1");
    }

    @Test
    void shouldValidateBearerBeforeHealthComponents() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());

        controller.healthComponents("Bearer token-1", "req-2");

        verify(authSessionService).me("token-1");
        verify(auditOpsService).healthComponents("req-2");
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
