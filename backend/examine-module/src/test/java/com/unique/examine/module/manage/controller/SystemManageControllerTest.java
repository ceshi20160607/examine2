package com.unique.examine.module.manage.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.RolePermissionSaveBO;
import com.unique.examine.module.manage.bo.SystemEnterBO;
import com.unique.examine.module.manage.service.SystemRbacService;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.AuthAccountVO;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SystemManageControllerTest {

    private SystemRbacService systemRbacService;

    private AuthSessionService authSessionService;

    private SystemContextController systemContextController;

    private SystemRbacController systemRbacController;

    @BeforeEach
    void setUp() {
        systemRbacService = mock(SystemRbacService.class);
        authSessionService = mock(AuthSessionService.class);
        systemContextController = new SystemContextController(systemRbacService, authSessionService);
        systemRbacController = new SystemRbacController(systemRbacService, authSessionService);
    }

    @Test
    void shouldRejectSystemApiWithoutBearerToken() {
        assertThatThrownBy(() -> systemContextController.profile("", 100L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(systemRbacService, never()).profile(100L);
    }

    @Test
    void shouldResolveCurrentAccountWhenEnterSystem() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());

        systemContextController.enter("Bearer token-1", 100L, new SystemEnterBO());

        verify(authSessionService).me("token-1");
        verify(systemRbacService).enterSystem(200L, 100L, new SystemEnterBO());
    }

    @Test
    void shouldValidateBearerBeforeSavingRolePermissions() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());

        systemRbacController.saveRolePermissions("Bearer token-1", 100L, 300L, new RolePermissionSaveBO());

        verify(authSessionService).me("token-1");
        verify(systemRbacService).saveRolePermissions(any(Long.class), any(Long.class), any(RolePermissionSaveBO.class));
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
