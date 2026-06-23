package com.unique.examine.module.manage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.RecordSaveBO;
import com.unique.examine.module.manage.service.RuntimeRecordService;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimeRecordControllerTest {

    private RuntimeRecordService runtimeRecordService;

    private AuthSessionService authSessionService;

    private RuntimeRecordController controller;

    @BeforeEach
    void setUp() {
        runtimeRecordService = mock(RuntimeRecordService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new RuntimeRecordController(runtimeRecordService, authSessionService);
    }

    @Test
    void shouldRejectRuntimeMenuWithoutBearerToken() {
        assertThatThrownBy(() -> controller.runtimeMenus("", 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(runtimeRecordService, never()).runtimeMenus(100L);
    }

    @Test
    void shouldValidateBearerBeforeCreateRecord() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());
        RecordSaveBO saveBO = new RecordSaveBO();

        controller.createRecord("Bearer token-1", 100L, 20L, saveBO);

        verify(authSessionService).me("token-1");
        verify(runtimeRecordService).createRecord(100L, 20L, saveBO);
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
