package com.unique.examine.module.manage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.ExportJobQueryBO;
import com.unique.examine.module.manage.service.ExportManageService;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportManageControllerTest {

    private ExportManageService exportManageService;

    private AuthSessionService authSessionService;

    private ExportManageController controller;

    @BeforeEach
    void setUp() {
        exportManageService = mock(ExportManageService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new ExportManageController(exportManageService, authSessionService);
    }

    @Test
    void shouldRejectJobListWithoutBearerToken() {
        assertThatThrownBy(() -> controller.listJobs("", 100L, new ExportJobQueryBO()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(exportManageService, never()).listJobs(any(), any());
    }

    @Test
    void shouldValidateBearerBeforeJobDetail() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());

        controller.jobDetail("Bearer token-1", 100L, 20L);

        verify(authSessionService).me("token-1");
        verify(exportManageService).jobDetail(100L, 20L);
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
