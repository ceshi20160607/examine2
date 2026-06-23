package com.unique.examine.app.manage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.examine.app.manage.bo.OpenApiClientSaveBO;
import com.unique.examine.app.manage.service.OpenApiManageService;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenApiManageControllerTest {

    private OpenApiManageService openApiManageService;

    private AuthSessionService authSessionService;

    private OpenApiManageController controller;

    @BeforeEach
    void setUp() {
        openApiManageService = mock(OpenApiManageService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new OpenApiManageController(openApiManageService, authSessionService);
    }

    @Test
    void shouldRejectClientListWithoutBearerToken() {
        assertThatThrownBy(() -> controller.listClients("", 100L, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(openApiManageService, never()).listClients(100L, null, null, null);
    }

    @Test
    void shouldValidateBearerBeforeCreateClient() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());
        OpenApiClientSaveBO saveBO = new OpenApiClientSaveBO();
        saveBO.setTenantId(10L);
        saveBO.setCode("third_party");
        saveBO.setName("第三方系统");

        controller.createClient("Bearer token-1", 100L, saveBO);

        verify(authSessionService).me("token-1");
        verify(openApiManageService).createClient(100L, saveBO);
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
