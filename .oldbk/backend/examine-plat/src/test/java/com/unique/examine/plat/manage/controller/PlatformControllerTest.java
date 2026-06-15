package com.unique.examine.plat.manage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.service.PlatformCenterService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlatformControllerTest {

    private PlatformCenterService platformCenterService;

    private AuthSessionService authSessionService;

    private PlatformController controller;

    @BeforeEach
    void setUp() {
        platformCenterService = mock(PlatformCenterService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new PlatformController(platformCenterService, authSessionService);
    }

    @Test
    void shouldRejectPlatformApiWithoutBearerToken() {
        assertThatThrownBy(() -> controller.listSystems(""))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(platformCenterService, never()).listSystems();
    }

    @Test
    void shouldValidateBearerBeforeQueryPlatformApi() {
        when(authSessionService.me("token-1")).thenReturn(CurrentUserVO.builder()
                .account(AuthAccountVO.builder()
                        .accountId("1001")
                        .loginName("admin")
                        .displayName("admin")
                        .status("NORMAL")
                        .build())
                .build());
        when(platformCenterService.listSystems()).thenReturn(List.of());

        controller.listSystems("Bearer token-1");

        verify(authSessionService).me("token-1");
        verify(platformCenterService).listSystems();
    }
}
