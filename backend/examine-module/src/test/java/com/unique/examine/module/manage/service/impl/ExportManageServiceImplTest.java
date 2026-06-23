package com.unique.examine.module.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.module.base.entity.ExportJob;
import com.unique.examine.module.base.service.IExportJobLogService;
import com.unique.examine.module.base.service.IExportJobService;
import com.unique.examine.module.base.service.IExportTemplateFieldService;
import com.unique.examine.module.base.service.IExportTemplateService;
import com.unique.examine.module.base.service.IFieldService;
import com.unique.examine.module.base.service.IModelService;
import com.unique.examine.module.base.service.IPublishVersionService;
import com.unique.examine.module.base.service.IRecordService;
import com.unique.examine.module.base.service.IRecordValueService;
import com.unique.examine.module.manage.enums.ExportErrorCode;
import com.unique.examine.upload.manage.service.UploadFileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExportManageServiceImplTest {

    private IExportJobService exportJobService;

    private PermissionService permissionService;

    private ExportManageServiceImpl service;

    @BeforeEach
    void setUp() {
        exportJobService = mock(IExportJobService.class);
        permissionService = mock(PermissionService.class);
        service = new ExportManageServiceImpl(mock(IExportTemplateService.class), mock(IExportTemplateFieldService.class),
                exportJobService, mock(IExportJobLogService.class), mock(IModelService.class), mock(IFieldService.class),
                mock(IPublishVersionService.class), mock(IRecordService.class), mock(IRecordValueService.class),
                permissionService, mock(UploadFileService.class), new ObjectMapper());
        doNothing().when(permissionService).requireOperation("EXPORT_JOB_RETRY");
    }

    @Test
    void shouldRejectRetryWhenJobAlreadySuccess() {
        when(exportJobService.getById(10L)).thenReturn(successJob());

        assertThatThrownBy(() -> service.retryJob(100L, 10L, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ExportErrorCode.JOB_STATUS_CONFLICT);
    }

    private ExportJob successJob() {
        return new ExportJob()
                .setJobId(10L)
                .setSystemId(100L)
                .setModuleId(20L)
                .setJobStatus("SUCCESS")
                .setProgress(100)
                .setRetryableFlag((byte) 0)
                .setRetryCount(0)
                .setMaxRetryCount(3)
                .setCreatedBy(200L)
                .setVersion(1)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }
}
