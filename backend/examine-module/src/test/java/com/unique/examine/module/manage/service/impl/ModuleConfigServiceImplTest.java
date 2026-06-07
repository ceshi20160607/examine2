package com.unique.examine.module.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.module.base.entity.Field;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.service.IActionService;
import com.unique.examine.module.base.service.IAppService;
import com.unique.examine.module.base.service.IFieldOptionService;
import com.unique.examine.module.base.service.IFieldService;
import com.unique.examine.module.base.service.IMenuService;
import com.unique.examine.module.base.service.IModelService;
import com.unique.examine.module.base.service.IPageSchemaService;
import com.unique.examine.module.base.service.IPublishVersionService;
import com.unique.examine.module.base.service.IRecordService;
import com.unique.examine.module.manage.bo.FieldSaveBO;
import com.unique.examine.module.manage.bo.PageSchemaSaveBO;
import com.unique.examine.module.manage.bo.PublishRequestBO;
import com.unique.examine.module.manage.enums.ModuleConfigErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModuleConfigServiceImplTest {

    private IAppService appService;

    private IModelService modelService;

    private IFieldService fieldService;

    private IFieldOptionService fieldOptionService;

    private IPageSchemaService pageSchemaService;

    private IMenuService menuService;

    private IActionService actionService;

    private IPublishVersionService publishVersionService;

    private IRecordService recordService;

    private PermissionService permissionService;

    private ModuleConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        appService = mock(IAppService.class);
        modelService = mock(IModelService.class);
        fieldService = mock(IFieldService.class);
        fieldOptionService = mock(IFieldOptionService.class);
        pageSchemaService = mock(IPageSchemaService.class);
        menuService = mock(IMenuService.class);
        actionService = mock(IActionService.class);
        publishVersionService = mock(IPublishVersionService.class);
        recordService = mock(IRecordService.class);
        permissionService = mock(PermissionService.class);
        service = new ModuleConfigServiceImpl(appService, modelService, fieldService, fieldOptionService,
                pageSchemaService, menuService, actionService, publishVersionService, recordService, permissionService,
                new ObjectMapper());
        doNothing().when(permissionService).requireOperation(anyString());
    }

    @Test
    void shouldRejectDuplicatedFieldCode() {
        mockModelQuery(module());
        mockFieldOne(field(10L, "customerName"));
        FieldSaveBO saveBO = new FieldSaveBO();
        saveBO.setName("客户名称");
        saveBO.setCode("customerName");
        saveBO.setFieldType("TEXT");

        assertThatThrownBy(() -> service.createField(100L, 20L, saveBO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ModuleConfigErrorCode.FIELD_CODE_DUPLICATED);
    }

    @Test
    void shouldRejectUnsupportedFieldType() {
        mockModelQuery(module());
        FieldSaveBO saveBO = new FieldSaveBO();
        saveBO.setName("客户名称");
        saveBO.setCode("customerName");
        saveBO.setFieldType("SCRIPT");

        assertThatThrownBy(() -> service.createField(100L, 20L, saveBO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ModuleConfigErrorCode.FIELD_TYPE_UNSUPPORTED);
    }

    @Test
    void shouldRejectPageSchemaWhenFieldMissing() {
        mockModelQuery(module());
        mockFieldList(List.of(field(10L, "customerName")));
        PageSchemaSaveBO saveBO = new PageSchemaSaveBO();
        saveBO.setSchema(Map.of("columns", List.of(Map.of("fieldId", "999"))));

        assertThatThrownBy(() -> service.savePageSchema(100L, 20L, "LIST", saveBO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ModuleConfigErrorCode.MODULE_PAGE_FIELD_MISSING);
    }

    @Test
    void shouldRejectPublishWhenModuleVersionConflict() {
        mockModelQuery(module());
        PublishRequestBO requestBO = new PublishRequestBO();
        requestBO.setModuleVersion(99);
        requestBO.setPublishRemark("首次发布");

        assertThatThrownBy(() -> service.publish(100L, 20L, requestBO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ModuleConfigErrorCode.MODULE_CONFIG_VERSION_CONFLICT);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockModelQuery(Model one) {
        LambdaQueryChainWrapper query = mock(LambdaQueryChainWrapper.class);
        when(modelService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.one()).thenReturn(one);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void mockFieldOne(Field one) {
        LambdaQueryChainWrapper query = mock(LambdaQueryChainWrapper.class);
        when(fieldService.lambdaQuery()).thenReturn(query);
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

    private Model module() {
        return new Model()
                .setModuleId(20L)
                .setSystemId(100L)
                .setTenantId(1L)
                .setAppId(2L)
                .setName("客户")
                .setCode("customer")
                .setModuleStatus("DRAFT")
                .setSortOrder(100)
                .setVersion(1)
                .setDeleteMarker("0")
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }

    private Field field(Long fieldId, String code) {
        return new Field()
                .setFieldId(fieldId)
                .setSystemId(100L)
                .setTenantId(1L)
                .setModuleId(20L)
                .setName("客户名称")
                .setCode(code)
                .setFieldType("TEXT")
                .setFieldStatus("ENABLED")
                .setDeleteMarker("0")
                .setVersion(1)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }
}
