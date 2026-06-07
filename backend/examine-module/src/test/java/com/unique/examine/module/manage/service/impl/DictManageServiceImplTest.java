package com.unique.examine.module.manage.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.module.base.entity.DictItem;
import com.unique.examine.module.base.entity.DictReference;
import com.unique.examine.module.base.entity.DictType;
import com.unique.examine.module.base.service.IDictItemService;
import com.unique.examine.module.base.service.IDictReferenceService;
import com.unique.examine.module.base.service.IDictTypeService;
import com.unique.examine.module.manage.bo.DictTypeSaveBO;
import com.unique.examine.module.manage.bo.DictTypeUpdateBO;
import com.unique.examine.module.manage.enums.DictErrorCode;
import com.unique.examine.module.manage.vo.DictItemVO;
import com.unique.examine.module.manage.vo.DictUsageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DictManageServiceImplTest {

    private IDictTypeService dictTypeService;

    private IDictItemService dictItemService;

    private IDictReferenceService dictReferenceService;

    private PermissionService permissionService;

    private DictManageServiceImpl service;

    @BeforeEach
    void setUp() {
        dictTypeService = mock(IDictTypeService.class);
        dictItemService = mock(IDictItemService.class);
        dictReferenceService = mock(IDictReferenceService.class);
        permissionService = mock(PermissionService.class);
        service = new DictManageServiceImpl(dictTypeService, dictItemService, dictReferenceService, permissionService);
        doNothing().when(permissionService).requireOperation(anyString());
    }

    @Test
    void shouldRejectDuplicatedDictTypeCode() {
        mockTypeQuery(type(1L, "priority"));
        DictTypeSaveBO saveBO = new DictTypeSaveBO();
        saveBO.setScopeType("SYSTEM");
        saveBO.setCode("priority");
        saveBO.setName("优先级");

        assertThatThrownBy(() -> service.createType(100L, saveBO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DictErrorCode.TYPE_CODE_DUPLICATE);
    }

    @Test
    void shouldRejectBuiltInTypeUpdate() {
        DictType dictType = type(1L, "priority");
        dictType.setSystemBuiltIn((byte) 1);
        mockTypeQuery(dictType);
        DictTypeUpdateBO updateBO = new DictTypeUpdateBO();
        updateBO.setName("优先级");
        updateBO.setVersion(1L);

        assertThatThrownBy(() -> service.updateType(100L, 1L, updateBO))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DictErrorCode.BUILTIN_READONLY);
    }

    @Test
    void shouldReportUsageBlockingDeleteWhenReferenced() {
        mockTypeQuery(type(1L, "priority"));
        mockReferenceQuery(List.of(reference("PUBLISHED_FIELD", 0L), reference("RECORD_VALUE", 3L)));
        mockItemQuery(List.of());

        DictUsageVO usage = service.usages(100L, 1L);

        assertThat(usage.getCanDelete()).isFalse();
        assertThat(usage.getRecordUsageCount()).isEqualTo(3L);
        assertThat(usage.getBlockingReasons()).contains("存在字段或发布版本引用", "存在记录值引用");
    }

    @Test
    void shouldReturnTreeItems() {
        mockTypeQuery(type(1L, "priority"));
        mockItemQuery(List.of(item(10L, 0L, "p1"), item(11L, 10L, "p1_1")));

        List<DictItemVO> items = service.listItems(100L, 1L, null, null, null, true);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).getChildren()).hasSize(1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockTypeQuery(DictType one) {
        LambdaQueryChainWrapper query = mock(LambdaQueryChainWrapper.class);
        when(dictTypeService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.one()).thenReturn(one);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockItemQuery(List<DictItem> items) {
        LambdaQueryChainWrapper query = mock(LambdaQueryChainWrapper.class);
        when(dictItemService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.list()).thenReturn(items);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void mockReferenceQuery(List<DictReference> references) {
        LambdaQueryChainWrapper query = mock(LambdaQueryChainWrapper.class);
        when(dictReferenceService.lambdaQuery()).thenReturn(query);
        when(query.eq(any(), any())).thenReturn(query);
        when(query.list()).thenReturn(references);
    }

    private DictType type(Long id, String code) {
        return new DictType()
                .setId(id)
                .setSystemId(100L)
                .setScopeType("SYSTEM")
                .setScopeTenantId(0L)
                .setCode(code)
                .setName("优先级")
                .setStatus("ENABLED")
                .setSortOrder(100)
                .setSystemBuiltIn((byte) 0)
                .setCacheVersion(1L)
                .setItemCount(0)
                .setEnabledItemCount(0)
                .setReferencedFlag((byte) 0)
                .setDeleteToken(0L)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }

    private DictItem item(Long id, Long parentId, String code) {
        return new DictItem()
                .setId(id)
                .setSystemId(100L)
                .setDictTypeId(1L)
                .setParentId(parentId)
                .setCode(code)
                .setLabel(code)
                .setValue(code)
                .setStatus("ENABLED")
                .setSortOrder(100)
                .setDepthLevel(parentId == 0 ? 1 : 2)
                .setDepthPath(parentId == 0 ? "/" : "/10/")
                .setLeafFlag((byte) 1)
                .setSystemBuiltIn((byte) 0)
                .setReferencedFlag((byte) 0)
                .setCacheVersion(1L)
                .setDeleteToken(0L)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
    }

    private DictReference reference(String referenceType, Long usageCount) {
        return new DictReference()
                .setSystemId(100L)
                .setDictTypeId(1L)
                .setDictItemId(0L)
                .setReferenceType(referenceType)
                .setFieldCode("priority")
                .setUsageCount(usageCount)
                .setActiveFlag((byte) 1)
                .setDeleteToken(0L);
    }
}
