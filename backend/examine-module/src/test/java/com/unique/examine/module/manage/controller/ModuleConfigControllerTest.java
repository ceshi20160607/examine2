package com.unique.examine.module.manage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.AppSaveBO;
import com.unique.examine.module.manage.service.ModuleConfigService;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModuleConfigControllerTest {

    private ModuleConfigService moduleConfigService;

    private AuthSessionService authSessionService;

    private ModuleConfigController controller;

    @BeforeEach
    void setUp() {
        moduleConfigService = mock(ModuleConfigService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new ModuleConfigController(moduleConfigService, authSessionService);
    }

    @Test
    void shouldRejectAppApiWithoutBearerToken() {
        assertThatThrownBy(() -> controller.listApps("", 100L, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(moduleConfigService, never()).listApps(100L, null, null, null);
    }

    @Test
    void shouldValidateBearerBeforeCreateApp() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());
        AppSaveBO saveBO = new AppSaveBO();
        saveBO.setTenantId("1");
        saveBO.setCode("crm");
        saveBO.setName("客户管理");

        controller.createApp("Bearer token-1", 100L, saveBO);

        verify(authSessionService).me("token-1");
        verify(moduleConfigService).createApp(100L, saveBO);
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
