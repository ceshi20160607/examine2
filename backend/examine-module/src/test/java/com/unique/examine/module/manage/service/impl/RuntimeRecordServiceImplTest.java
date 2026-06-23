package com.unique.examine.module.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.EffectivePermissionVO;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.module.base.entity.Field;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.service.IActionService;
import com.unique.examine.module.base.service.IFieldService;
import com.unique.examine.module.base.service.IMenuService;
import com.unique.examine.module.base.service.IModelService;
import com.unique.examine.module.base.service.IPublishVersionService;
import com.unique.examine.module.base.service.IRecordHistoryService;
import com.unique.examine.module.base.service.IRecordIndexService;
import com.unique.examine.module.base.service.IRecordRelationService;
import com.unique.examine.module.base.service.IRecordService;
import com.unique.examine.module.base.service.IRecordUniqueIndexService;
import com.unique.examine.module.base.service.IRecordValueService;
import com.unique.examine.module.manage.bo.RecordSaveBO;
import com.unique.examine.module.manage.enums.RuntimeRecordErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuntimeRecordServiceImplTest {

    private IModelService modelService;

    private IFieldService fieldService;

    private PermissionService permissionService;

    private RuntimeRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        modelService = mock(IModelService.class);
        fieldService = mock(IFieldService.class);
        permissionService = mock(PermissionService.class);
        service = new RuntimeRecordServiceImpl(modelService, fieldService, mock(IMenuService.class),
                mock(IActionService.class), mock(IPublishVersionService.class), mock(IRecordService.class),
                mock(IRecordValueService.class), mock(IRecordHistoryService.class), mock(IRecordIndexService.class),
                mock(IRecordUniqueIndexService.class), mock(IRecordRelationService.class), permissionService,
                new ObjectMapper(), Optional.empty());
        doNothing().when(permissionService).requireOperation(anyString());
        when(permissionService.currentPermission()).thenReturn(EffectivePermissionVO.empty());
    }

    @Test
    void shouldRejectCreateRecordWhenRequiredFieldMissing() {
        mockModelQuery(publishedModule());
        mockFieldList(List.of(requiredField()));
        RecordSaveBO saveBO = new RecordSaveBO();

        assertThatThrownBy(() -> service.createRecord(100L, 20L, saveBO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(RuntimeRecordErrorCode.FIELD_REQUIRED_MISSING);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockModelQuery(Model one) {
        LambdaQueryChainWrapper query = mock(LambdaQueryChainWrapper.class);
        when(modelService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.one()).thenReturn(one);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockFieldList(List<Field> fields) {
        LambdaQueryChainWrapper query = mock(LambdaQueryChainWrapper.class);
        when(fieldService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.list()).thenReturn(fields);
    }

    private Model publishedModule() {
        return new Model()
                .setModuleId(20L)
                .setSystemId(100L)
                .setTenantId(1L)
                .setAppId(2L)
                .setName("客户")
                .setCode("customer")
                .setModuleStatus("PUBLISHED")
                .setCurrentPublishVersionId(30L)
                .setSortOrder(100)
                .setVersion(1)
                .setDeleteMarker("0")
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }

    private Field requiredField() {
        return new Field()
                .setFieldId(10L)
                .setSystemId(100L)
                .setTenantId(1L)
                .setModuleId(20L)
                .setName("客户名称")
                .setCode("customerName")
                .setFieldType("TEXT")
                .setRequiredFlag((byte) 1)
                .setUniqueFlag((byte) 0)
                .setIndexFlag((byte) 0)
                .setFieldStatus("ENABLED")
                .setSortOrder(1)
                .setVersion(1)
                .setDeleteMarker("0")
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }
}
