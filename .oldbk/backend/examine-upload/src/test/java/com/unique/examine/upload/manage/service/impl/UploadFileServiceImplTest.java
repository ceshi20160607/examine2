package com.unique.examine.upload.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.upload.base.entity.File;
import com.unique.examine.upload.base.service.IFileReferenceService;
import com.unique.examine.upload.base.service.IFileService;
import com.unique.examine.upload.base.service.IStorageConfigService;
import com.unique.examine.upload.manage.enums.UploadErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class UploadFileServiceImplTest {

    private IFileService fileService;

    private IFileReferenceService fileReferenceService;

    private IStorageConfigService storageConfigService;

    private PermissionService permissionService;

    private UploadFileServiceImpl service;

    @BeforeEach
    void setUp() {
        fileService = mock(IFileService.class);
        fileReferenceService = mock(IFileReferenceService.class);
        storageConfigService = mock(IStorageConfigService.class);
        permissionService = mock(PermissionService.class);
        service = new UploadFileServiceImpl(fileService, fileReferenceService, storageConfigService, permissionService);
        doNothing().when(permissionService).requireOperation("FILE_UPLOAD");
        doNothing().when(permissionService).requireOperation("FILE_DELETE");
    }

    @Test
    void shouldRejectForbiddenUploadTypeBeforeStorageLookup() {
        MockMultipartFile multipartFile = new MockMultipartFile("file", "setup.exe",
                "application/octet-stream", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.upload(100L, multipartFile, null, null, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UploadErrorCode.TYPE_FORBIDDEN);

        verify(storageConfigService, never()).lambdaQuery();
    }

    @Test
    void shouldRejectDeleteWhenFileReferenced() {
        when(fileService.getById(10L)).thenReturn(referencedFile());

        assertThatThrownBy(() -> service.deleteFile(100L, 10L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(UploadErrorCode.FILE_REFERENCED);
    }

    private File referencedFile() {
        return new File()
                .setId(10L)
                .setSystemId(100L)
                .setTenantId(1L)
                .setStorageConfigId(800L)
                .setFileName("contract.pdf")
                .setExtension("pdf")
                .setContentType("application/pdf")
                .setFileSize(100L)
                .setStorageKey("100/2026/06/07/contract.pdf")
                .setStatus("REFERENCED")
                .setPreviewable((byte) 1)
                .setOwnerMemberId(200L)
                .setRefCount(1)
                .setRequestId("req-1")
                .setDeleted((byte) 0)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }
}
