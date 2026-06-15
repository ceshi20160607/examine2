package com.unique.examine.upload.manage.controller;

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
import com.unique.examine.upload.manage.bo.FileQueryBO;
import com.unique.examine.upload.manage.service.UploadFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UploadFileControllerTest {

    private UploadFileService uploadFileService;

    private AuthSessionService authSessionService;

    private UploadFileController controller;

    @BeforeEach
    void setUp() {
        uploadFileService = mock(UploadFileService.class);
        authSessionService = mock(AuthSessionService.class);
        controller = new UploadFileController(uploadFileService, authSessionService);
    }

    @Test
    void shouldRejectFileListWithoutBearerToken() {
        assertThatThrownBy(() -> controller.queryFiles("", 100L, new FileQueryBO()))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.UNAUTHORIZED);

        verify(uploadFileService, never()).queryFiles(100L, new FileQueryBO());
    }

    @Test
    void shouldValidateBearerBeforeFileDetail() {
        when(authSessionService.me("token-1")).thenReturn(currentUser());

        controller.fileDetail("Bearer token-1", 100L, 10L);

        verify(authSessionService).me("token-1");
        verify(uploadFileService).fileDetail(100L, 10L);
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
