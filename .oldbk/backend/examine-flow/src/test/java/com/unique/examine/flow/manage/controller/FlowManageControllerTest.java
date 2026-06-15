package com.unique.examine.flow.manage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.flow.manage.bo.FlowTemplateSaveBO;
import com.unique.examine.flow.manage.service.FlowManageService;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlowManageControllerTest {

    private FlowManageService flowManageService;

    private AuthSessionService authSessionService;

    private FlowManageController controller;

    @BeforeEach
    void setUp() {
        flowManageService = mock(FlowManageService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new FlowManageController(flowManageService, authSessionService);
    }

    @Test
    void shouldRejectTemplateListWithoutBearerToken() {
        assertThatThrownBy(() -> controller.listTemplates("", 100L, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(flowManageService, never()).listTemplates(100L, null, null);
    }

    @Test
    void shouldValidateBearerBeforeCreateTemplate() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());
        FlowTemplateSaveBO saveBO = new FlowTemplateSaveBO();

        controller.createTemplate("Bearer token-1", 100L, saveBO);

        verify(authSessionService).me("token-1");
        verify(flowManageService).createTemplate(100L, saveBO);
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
