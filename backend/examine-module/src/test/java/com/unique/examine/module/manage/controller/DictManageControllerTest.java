package com.unique.examine.module.manage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.DictTypeSaveBO;
import com.unique.examine.module.manage.service.DictManageService;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DictManageControllerTest {

    private DictManageService dictManageService;

    private AuthSessionService authSessionService;

    private DictManageController controller;

    @BeforeEach
    void setUp() {
        dictManageService = mock(DictManageService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new DictManageController(dictManageService, authSessionService);
    }

    @Test
    void shouldRejectDictApiWithoutBearerToken() {
        assertThatThrownBy(() -> controller.listTypes("", 100L, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(dictManageService, never()).listTypes(100L, null, null, null, null);
    }

    @Test
    void shouldValidateBearerBeforeCreateType() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());
        DictTypeSaveBO saveBO = new DictTypeSaveBO();
        saveBO.setScopeType("SYSTEM");
        saveBO.setCode("priority");
        saveBO.setName("优先级");

        controller.createType("Bearer token-1", 100L, saveBO);

        verify(authSessionService).me("token-1");
        verify(dictManageService).createType(100L, saveBO);
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
