package com.unique.examine.module.manage.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.module.base.entity.DictItem;
import com.unique.examine.module.base.entity.DictReference;
import com.unique.examine.module.base.entity.DictType;
import com.unique.examine.module.base.service.IDictItemService;
import com.unique.examine.module.base.service.IDictReferenceService;
import com.unique.examine.module.base.service.IDictTypeService;
import com.unique.examine.module.manage.bo.DictDeleteBO;
import com.unique.examine.module.manage.bo.DictItemSaveBO;
import com.unique.examine.module.manage.bo.DictItemUpdateBO;
import com.unique.examine.module.manage.bo.DictStatusBO;
import com.unique.examine.module.manage.bo.DictTypeSaveBO;
import com.unique.examine.module.manage.bo.DictTypeUpdateBO;
import com.unique.examine.module.manage.enums.DictErrorCode;
import com.unique.examine.module.manage.service.DictManageService;
import com.unique.examine.module.manage.vo.DictCacheRefreshVO;
import com.unique.examine.module.manage.vo.DictFieldUsageVO;
import com.unique.examine.module.manage.vo.DictItemVO;
import com.unique.examine.module.manage.vo.DictTypeVO;
import com.unique.examine.module.manage.vo.DictUsageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 系统字典管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class DictManageServiceImpl implements DictManageService {

    private static final long ACTIVE_DELETE_TOKEN = 0L;

    private static final long ROOT_ID = 0L;

    private static final int MAX_DEPTH = 5;

    private static final String SYSTEM_SCOPE = "SYSTEM";

    private static final String TENANT_SCOPE = "TENANT";

    private static final String ENABLED = "ENABLED";

    private static final String DISABLED = "DISABLED";

    private static final String DELETED = "DELETED";

    private final IDictTypeService dictTypeService;

    private final IDictItemService dictItemService;

    private final IDictReferenceService dictReferenceService;

    private final PermissionService permissionService;

    /**
     * 查询字典类型。
     *
     * @param systemId 系统 ID
     * @param scopeType 作用域
     * @param tenantId 租户 ID
     * @param keyword 关键字
     * @param status 状态
     * @return 字典类型列表
     */
    @Override
    public List<DictTypeVO> listTypes(Long systemId, String scopeType, String tenantId, String keyword, String status) {
        permissionService.requireOperation("DICT_VIEW");
        return dictTypeService.lambdaQuery()
                .eq(DictType::getSystemId, systemId)
                .eq(DictType::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .filter(type -> !DELETED.equals(type.getStatus()))
                .filter(type -> !StringUtils.hasText(scopeType) || Objects.equals(type.getScopeType(), scopeType))
                .filter(type -> !StringUtils.hasText(tenantId) || Objects.equals(type.getScopeTenantId(),
                        Long.valueOf(tenantId)))
                .filter(type -> !StringUtils.hasText(status) || Objects.equals(type.getStatus(), status))
                .filter(type -> !StringUtils.hasText(keyword) || contains(type.getCode(), keyword)
                        || contains(type.getName(), keyword))
                .sorted(typeComparator())
                .map(this::toTypeVO)
                .toList();
    }

    /**
     * 创建字典类型。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 字典类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO createType(Long systemId, DictTypeSaveBO saveBO) {
        permissionService.requireOperation("DICT_CREATE");
        Long scopeTenantId = resolveScopeTenantId(saveBO.getScopeType(), saveBO.getTenantId());
        validateEnableStatus(defaultText(saveBO.getStatus(), ENABLED));
        ensureTypeCodeAvailable(systemId, saveBO.getScopeType(), scopeTenantId, saveBO.getCode(), null);
        DictType dictType = new DictType()
                .setSystemId(systemId)
                .setScopeType(saveBO.getScopeType())
                .setScopeTenantId(scopeTenantId)
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setDescription(saveBO.getDescription())
                .setStatus(defaultText(saveBO.getStatus(), ENABLED))
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), 100))
                .setSystemBuiltIn((byte) 0)
                .setCacheVersion(1L)
                .setItemCount(0)
                .setEnabledItemCount(0)
                .setReferencedFlag((byte) 0)
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        dictTypeService.save(dictType);
        return withRefresh(toTypeVO(dictType), dictType);
    }

    /**
     * 更新字典类型。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param updateBO 更新入参
     * @return 字典类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO updateType(Long systemId, Long dictTypeId, DictTypeUpdateBO updateBO) {
        permissionService.requireOperation("DICT_EDIT");
        DictType dictType = activeType(systemId, dictTypeId);
        ensureTypeWritable(dictType);
        ensureVersion(dictType.getCacheVersion(), updateBO.getVersion());
        if (StringUtils.hasText(updateBO.getStatus())) {
            validateEnableStatus(updateBO.getStatus());
        }
        dictType.setName(defaultText(updateBO.getName(), dictType.getName()))
                .setDescription(updateBO.getDescription())
                .setSortOrder(defaultInteger(updateBO.getSortOrder(), dictType.getSortOrder()))
                .setStatus(defaultText(updateBO.getStatus(), dictType.getStatus()));
        refreshType(dictType);
        return withRefresh(toTypeVO(dictType), dictType);
    }

    /**
     * 变更字典类型状态。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param statusBO 状态入参
     * @return 字典类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO changeTypeStatus(Long systemId, Long dictTypeId, DictStatusBO statusBO) {
        permissionService.requireOperation("DICT_STATUS");
        validateEnableStatus(statusBO.getTargetStatus());
        DictType dictType = activeType(systemId, dictTypeId);
        ensureVersion(dictType.getCacheVersion(), statusBO.getVersion());
        if (DISABLED.equals(statusBO.getTargetStatus()) && !typeUsage(systemId, dictTypeId).getCanDisable()) {
            throw new BusinessException(DictErrorCode.TYPE_IN_USE);
        }
        dictType.setStatus(statusBO.getTargetStatus());
        refreshType(dictType);
        return withRefresh(toTypeVO(dictType), dictType);
    }

    /**
     * 查询字典项。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param parentId 父项 ID
     * @param keyword 关键字
     * @param status 状态
     * @param treeMode 是否树模式
     * @return 字典项列表
     */
    @Override
    public List<DictItemVO> listItems(Long systemId, Long dictTypeId, String parentId, String keyword, String status,
            boolean treeMode) {
        permissionService.requireOperation("DICT_VIEW");
        activeType(systemId, dictTypeId);
        List<DictItem> items = activeItems(systemId, dictTypeId).stream()
                .filter(item -> !StringUtils.hasText(parentId) || Objects.equals(item.getParentId(),
                        Long.valueOf(parentId)))
                .filter(item -> !StringUtils.hasText(status) || Objects.equals(item.getStatus(), status))
                .filter(item -> !StringUtils.hasText(keyword) || contains(item.getCode(), keyword)
                        || contains(item.getLabel(), keyword) || contains(item.getValue(), keyword))
                .sorted(itemComparator())
                .toList();
        return treeMode ? toItemTree(items) : items.stream().map(item -> toItemVO(item, List.of())).toList();
    }

    /**
     * 创建字典项。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param saveBO 保存入参
     * @return 字典项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictItemVO createItem(Long systemId, Long dictTypeId, DictItemSaveBO saveBO) {
        permissionService.requireOperation("DICT_ITEM_CREATE");
        DictType dictType = activeType(systemId, dictTypeId);
        if (!ENABLED.equals(dictType.getStatus())) {
            throw new BusinessException(DictErrorCode.STATUS_CONFLICT, "字典类型未启用");
        }
        Long parentId = parseLongOrDefault(saveBO.getParentId(), ROOT_ID);
        DictItem parent = parentId.equals(ROOT_ID) ? null : activeItem(systemId, dictTypeId, parentId);
        String status = defaultText(saveBO.getStatus(), ENABLED);
        validateEnableStatus(status);
        if (Objects.nonNull(parent) && ENABLED.equals(status) && !ENABLED.equals(parent.getStatus())) {
            throw new BusinessException(DictErrorCode.PARENT_DISABLED);
        }
        int depth = Objects.isNull(parent) ? 1 : parent.getDepthLevel() + 1;
        if (depth > MAX_DEPTH) {
            throw new BusinessException(DictErrorCode.DEPTH_EXCEEDED);
        }
        ensureItemCodeValueAvailable(systemId, dictTypeId, parentId, saveBO.getCode(), saveBO.getValue(), null);
        DictItem item = new DictItem()
                .setSystemId(systemId)
                .setDictTypeId(dictTypeId)
                .setParentId(parentId)
                .setCode(saveBO.getCode())
                .setLabel(saveBO.getLabel())
                .setValue(saveBO.getValue())
                .setDescription(saveBO.getDescription())
                .setStatus(status)
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), 100))
                .setDepthLevel(depth)
                .setDepthPath(depthPath(parent))
                .setLeafFlag((byte) 1)
                .setSystemBuiltIn((byte) 0)
                .setReferencedFlag((byte) 0)
                .setExtJson(saveBO.getExt())
                .setCacheVersion(nextVersion(dictType))
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        dictItemService.save(item);
        if (Objects.nonNull(parent)) {
            parent.setLeafFlag((byte) 0);
            dictItemService.updateById(parent);
        }
        refreshTypeCounters(dictType);
        return withRefresh(toItemVO(item, List.of()), dictType);
    }

    /**
     * 更新字典项。
     *
     * @param systemId 系统 ID
     * @param dictItemId 字典项 ID
     * @param updateBO 更新入参
     * @return 字典项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictItemVO updateItem(Long systemId, Long dictItemId, DictItemUpdateBO updateBO) {
        permissionService.requireOperation("DICT_ITEM_EDIT");
        DictItem item = activeItem(systemId, dictItemId);
        ensureItemWritable(item);
        ensureVersion(item.getCacheVersion(), updateBO.getVersion());
        Long parentId = parseLongOrDefault(updateBO.getParentId(), item.getParentId());
        DictItem parent = parentId.equals(ROOT_ID) ? null : activeItem(systemId, item.getDictTypeId(), parentId);
        if (Objects.equals(parentId, item.getId()) || createsCycle(item, parent)) {
            throw new BusinessException(DictErrorCode.PARENT_NOT_FOUND, "不能移动到自身或子级下");
        }
        String targetStatus = defaultText(updateBO.getStatus(), item.getStatus());
        validateEnableStatus(targetStatus);
        if (Objects.nonNull(parent) && ENABLED.equals(targetStatus) && !ENABLED.equals(parent.getStatus())) {
            throw new BusinessException(DictErrorCode.PARENT_DISABLED);
        }
        int depth = Objects.isNull(parent) ? 1 : parent.getDepthLevel() + 1;
        if (depth > MAX_DEPTH) {
            throw new BusinessException(DictErrorCode.DEPTH_EXCEEDED);
        }
        ensureItemCodeValueAvailable(systemId, item.getDictTypeId(), parentId, item.getCode(),
                defaultText(updateBO.getValue(), item.getValue()), item.getId());
        DictType dictType = activeType(systemId, item.getDictTypeId());
        item.setParentId(parentId)
                .setLabel(defaultText(updateBO.getLabel(), item.getLabel()))
                .setValue(defaultText(updateBO.getValue(), item.getValue()))
                .setDescription(updateBO.getDescription())
                .setStatus(targetStatus)
                .setSortOrder(defaultInteger(updateBO.getSortOrder(), item.getSortOrder()))
                .setDepthLevel(depth)
                .setDepthPath(depthPath(parent))
                .setExtJson(updateBO.getExt())
                .setCacheVersion(nextVersion(dictType))
                .setUpdatedAt(LocalDateTime.now());
        dictItemService.updateById(item);
        refreshTypeCounters(dictType);
        return withRefresh(toItemVO(item, List.of()), dictType);
    }

    /**
     * 变更字典项状态。
     *
     * @param systemId 系统 ID
     * @param dictItemId 字典项 ID
     * @param statusBO 状态入参
     * @return 字典项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictItemVO changeItemStatus(Long systemId, Long dictItemId, DictStatusBO statusBO) {
        permissionService.requireOperation("DICT_ITEM_STATUS");
        validateEnableStatus(statusBO.getTargetStatus());
        DictItem item = activeItem(systemId, dictItemId);
        ensureVersion(item.getCacheVersion(), statusBO.getVersion());
        DictType dictType = activeType(systemId, item.getDictTypeId());
        item.setStatus(statusBO.getTargetStatus())
                .setCacheVersion(nextVersion(dictType))
                .setUpdatedAt(LocalDateTime.now());
        dictItemService.updateById(item);
        refreshTypeCounters(dictType);
        return withRefresh(toItemVO(item, List.of()), dictType);
    }

    /**
     * 查询字典使用情况。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @return 使用情况
     */
    @Override
    public DictUsageVO usages(Long systemId, Long dictTypeId) {
        permissionService.requireOperation("DICT_VIEW");
        activeType(systemId, dictTypeId);
        return typeUsage(systemId, dictTypeId);
    }

    /**
     * 删除字典类型。
     *
     * @param systemId 系统 ID
     * @param dictTypeId 字典类型 ID
     * @param deleteBO 删除入参
     * @return 字典类型
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictTypeVO deleteType(Long systemId, Long dictTypeId, DictDeleteBO deleteBO) {
        permissionService.requireOperation("DICT_DELETE");
        DictType dictType = activeType(systemId, dictTypeId);
        ensureTypeWritable(dictType);
        ensureVersion(dictType.getCacheVersion(), deleteBO.getVersion());
        if (!typeUsage(systemId, dictTypeId).getCanDelete()) {
            throw new BusinessException(DictErrorCode.TYPE_IN_USE);
        }
        dictType.setStatus(DELETED)
                .setDeleteToken(dictType.getId());
        refreshType(dictType);
        activeItems(systemId, dictTypeId).stream()
                .filter(item -> !referenced(item))
                .forEach(item -> {
                    item.setStatus(DELETED)
                            .setDeleteToken(item.getId())
                            .setUpdatedAt(LocalDateTime.now());
                    dictItemService.updateById(item);
                });
        return withRefresh(toTypeVO(dictType), dictType);
    }

    /**
     * 删除字典项。
     *
     * @param systemId 系统 ID
     * @param dictItemId 字典项 ID
     * @param deleteBO 删除入参
     * @return 字典项
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DictItemVO deleteItem(Long systemId, Long dictItemId, DictDeleteBO deleteBO) {
        permissionService.requireOperation("DICT_ITEM_DELETE");
        DictItem item = activeItem(systemId, dictItemId);
        ensureItemWritable(item);
        ensureVersion(item.getCacheVersion(), deleteBO.getVersion());
        if (referenced(item)) {
            throw new BusinessException(DictErrorCode.ITEM_IN_USE);
        }
        long enabledChildren = enabledChildrenCount(systemId, item.getId());
        if (enabledChildren > 0) {
            throw new BusinessException(DictErrorCode.HAS_ENABLED_CHILDREN);
        }
        DictType dictType = activeType(systemId, item.getDictTypeId());
        item.setStatus(DELETED)
                .setDeleteToken(item.getId())
                .setCacheVersion(nextVersion(dictType))
                .setUpdatedAt(LocalDateTime.now());
        dictItemService.updateById(item);
        refreshTypeCounters(dictType);
        return withRefresh(toItemVO(item, List.of()), dictType);
    }

    private DictType activeType(Long systemId, Long dictTypeId) {
        DictType dictType = dictTypeService.lambdaQuery()
                .eq(DictType::getSystemId, systemId)
                .eq(DictType::getId, dictTypeId)
                .eq(DictType::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.isNull(dictType) || DELETED.equals(dictType.getStatus())) {
            throw new BusinessException(DictErrorCode.TYPE_NOT_FOUND);
        }
        return dictType;
    }

    private DictItem activeItem(Long systemId, Long dictItemId) {
        DictItem item = dictItemService.lambdaQuery()
                .eq(DictItem::getSystemId, systemId)
                .eq(DictItem::getId, dictItemId)
                .eq(DictItem::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.isNull(item) || DELETED.equals(item.getStatus())) {
            throw new BusinessException(DictErrorCode.ITEM_NOT_FOUND);
        }
        return item;
    }

    private DictItem activeItem(Long systemId, Long dictTypeId, Long dictItemId) {
        DictItem item = dictItemService.lambdaQuery()
                .eq(DictItem::getSystemId, systemId)
                .eq(DictItem::getDictTypeId, dictTypeId)
                .eq(DictItem::getId, dictItemId)
                .eq(DictItem::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.isNull(item) || DELETED.equals(item.getStatus())) {
            throw new BusinessException(DictErrorCode.PARENT_NOT_FOUND);
        }
        return item;
    }

    private List<DictItem> activeItems(Long systemId, Long dictTypeId) {
        return dictItemService.lambdaQuery()
                .eq(DictItem::getSystemId, systemId)
                .eq(DictItem::getDictTypeId, dictTypeId)
                .eq(DictItem::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .filter(item -> !DELETED.equals(item.getStatus()))
                .toList();
    }

    private void ensureTypeCodeAvailable(Long systemId, String scopeType, Long scopeTenantId, String code,
            Long excludeId) {
        DictType dictType = dictTypeService.lambdaQuery()
                .eq(DictType::getSystemId, systemId)
                .eq(DictType::getScopeType, scopeType)
                .eq(DictType::getScopeTenantId, scopeTenantId)
                .eq(DictType::getCode, code)
                .eq(DictType::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.nonNull(dictType) && !Objects.equals(dictType.getId(), excludeId)) {
            throw new BusinessException(DictErrorCode.TYPE_CODE_DUPLICATE);
        }
    }

    private void ensureItemCodeValueAvailable(Long systemId, Long dictTypeId, Long parentId, String code, String value,
            Long excludeId) {
        List<DictItem> siblings = dictItemService.lambdaQuery()
                .eq(DictItem::getSystemId, systemId)
                .eq(DictItem::getDictTypeId, dictTypeId)
                .eq(DictItem::getParentId, parentId)
                .eq(DictItem::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list();
        boolean codeDuplicate = siblings.stream()
                .anyMatch(item -> !Objects.equals(item.getId(), excludeId) && Objects.equals(item.getCode(), code));
        if (codeDuplicate) {
            throw new BusinessException(DictErrorCode.ITEM_CODE_DUPLICATE);
        }
        boolean valueDuplicate = siblings.stream()
                .anyMatch(item -> !Objects.equals(item.getId(), excludeId) && Objects.equals(item.getValue(), value));
        if (valueDuplicate) {
            throw new BusinessException(DictErrorCode.ITEM_VALUE_DUPLICATE);
        }
    }

    private DictUsageVO typeUsage(Long systemId, Long dictTypeId) {
        List<DictReference> references = dictReferenceService.lambdaQuery()
                .eq(DictReference::getSystemId, systemId)
                .eq(DictReference::getDictTypeId, dictTypeId)
                .eq(DictReference::getActiveFlag, (byte) 1)
                .eq(DictReference::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list();
        List<DictFieldUsageVO> fieldUsages = references.stream()
                .filter(reference -> !"RECORD_VALUE".equals(reference.getReferenceType()))
                .map(reference -> DictFieldUsageVO.builder()
                        .moduleId(toId(reference.getModuleId()))
                        .fieldId(toId(reference.getFieldId()))
                        .fieldCode(reference.getFieldCode())
                        .publishedVersionId(toId(reference.getPublishedVersionId()))
                        .status(reference.getReferenceType())
                        .build())
                .toList();
        long recordUsageCount = references.stream()
                .filter(reference -> "RECORD_VALUE".equals(reference.getReferenceType()))
                .map(DictReference::getUsageCount)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();
        long enabledItems = activeItems(systemId, dictTypeId).stream()
                .filter(item -> ENABLED.equals(item.getStatus()))
                .count();
        List<String> reasons = new ArrayList<>();
        if (!fieldUsages.isEmpty()) {
            reasons.add("存在字段或发布版本引用");
        }
        if (recordUsageCount > 0) {
            reasons.add("存在记录值引用");
        }
        if (enabledItems > 0) {
            reasons.add("存在启用字典项");
        }
        return DictUsageVO.builder()
                .dictTypeId(toId(dictTypeId))
                .fieldUsages(fieldUsages)
                .recordUsageCount(recordUsageCount)
                .enabledChildrenCount(enabledItems)
                .canDisable(fieldUsages.isEmpty() && enabledItems == 0)
                .canDelete(fieldUsages.isEmpty() && recordUsageCount == 0)
                .blockingReasons(reasons)
                .build();
    }

    private void refreshTypeCounters(DictType dictType) {
        long itemCount = activeItems(dictType.getSystemId(), dictType.getId()).size();
        long enabledItemCount = activeItems(dictType.getSystemId(), dictType.getId()).stream()
                .filter(item -> ENABLED.equals(item.getStatus()))
                .count();
        dictType.setItemCount((int) itemCount)
                .setEnabledItemCount((int) enabledItemCount);
        refreshType(dictType);
    }

    private void refreshType(DictType dictType) {
        dictType.setCacheVersion(nextVersion(dictType))
                .setReferencedFlag(referencedType(dictType) ? (byte) 1 : (byte) 0)
                .setUpdatedAt(LocalDateTime.now());
        dictTypeService.updateById(dictType);
    }

    private Long nextVersion(DictType dictType) {
        return Objects.isNull(dictType.getCacheVersion()) ? 1L : dictType.getCacheVersion() + 1;
    }

    private boolean referencedType(DictType dictType) {
        return dictReferenceService.lambdaQuery()
                .eq(DictReference::getSystemId, dictType.getSystemId())
                .eq(DictReference::getDictTypeId, dictType.getId())
                .eq(DictReference::getActiveFlag, (byte) 1)
                .eq(DictReference::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .count() > 0;
    }

    private boolean referenced(DictItem item) {
        return Objects.equals(item.getReferencedFlag(), (byte) 1) || dictReferenceService.lambdaQuery()
                .eq(DictReference::getSystemId, item.getSystemId())
                .eq(DictReference::getDictItemId, item.getId())
                .eq(DictReference::getActiveFlag, (byte) 1)
                .eq(DictReference::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .count() > 0;
    }

    private long enabledChildrenCount(Long systemId, Long parentId) {
        return dictItemService.lambdaQuery()
                .eq(DictItem::getSystemId, systemId)
                .eq(DictItem::getParentId, parentId)
                .eq(DictItem::getStatus, ENABLED)
                .eq(DictItem::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .count();
    }

    private boolean createsCycle(DictItem item, DictItem parent) {
        return Objects.nonNull(parent) && StringUtils.hasText(parent.getDepthPath())
                && parent.getDepthPath().contains("/" + item.getId() + "/");
    }

    private void ensureTypeWritable(DictType dictType) {
        if (Objects.equals(dictType.getSystemBuiltIn(), (byte) 1)) {
            throw new BusinessException(DictErrorCode.BUILTIN_READONLY);
        }
    }

    private void ensureItemWritable(DictItem item) {
        if (Objects.equals(item.getSystemBuiltIn(), (byte) 1)) {
            throw new BusinessException(DictErrorCode.BUILTIN_READONLY);
        }
    }

    private void ensureVersion(Long currentVersion, Long requestVersion) {
        if (!Objects.equals(currentVersion, requestVersion)) {
            throw new BusinessException(DictErrorCode.STATUS_CONFLICT);
        }
    }

    private Long resolveScopeTenantId(String scopeType, String tenantId) {
        if (SYSTEM_SCOPE.equals(scopeType)) {
            return ROOT_ID;
        }
        if (TENANT_SCOPE.equals(scopeType) && StringUtils.hasText(tenantId)) {
            return Long.valueOf(tenantId);
        }
        throw new BusinessException(DictErrorCode.SCOPE_INVALID);
    }

    private void validateEnableStatus(String status) {
        if (!ENABLED.equals(status) && !DISABLED.equals(status)) {
            throw new BusinessException(DictErrorCode.STATUS_CONFLICT);
        }
    }

    private List<DictItemVO> toItemTree(List<DictItem> items) {
        Map<Long, List<DictItem>> children = items.stream()
                .sorted(itemComparator())
                .collect(Collectors.groupingBy(DictItem::getParentId, LinkedHashMap::new, Collectors.toList()));
        return toItemTree(children, ROOT_ID);
    }

    private List<DictItemVO> toItemTree(Map<Long, List<DictItem>> children, Long parentId) {
        return children.getOrDefault(parentId, List.of())
                .stream()
                .map(item -> toItemVO(item, toItemTree(children, item.getId())))
                .toList();
    }

    private DictTypeVO toTypeVO(DictType dictType) {
        return DictTypeVO.builder()
                .dictTypeId(toId(dictType.getId()))
                .systemId(toId(dictType.getSystemId()))
                .scopeType(dictType.getScopeType())
                .tenantId(toId(dictType.getScopeTenantId()))
                .code(dictType.getCode())
                .name(dictType.getName())
                .description(dictType.getDescription())
                .status(dictType.getStatus())
                .sortOrder(dictType.getSortOrder())
                .systemBuiltIn(Objects.equals(dictType.getSystemBuiltIn(), (byte) 1))
                .itemCount(dictType.getItemCount())
                .enabledItemCount(dictType.getEnabledItemCount())
                .referenced(Objects.equals(dictType.getReferencedFlag(), (byte) 1))
                .cacheVersion(dictType.getCacheVersion())
                .version(dictType.getCacheVersion())
                .createdAt(dictType.getCreatedAt())
                .updatedAt(dictType.getUpdatedAt())
                .build();
    }

    private DictItemVO toItemVO(DictItem item, List<DictItemVO> children) {
        return DictItemVO.builder()
                .dictItemId(toId(item.getId()))
                .dictTypeId(toId(item.getDictTypeId()))
                .parentId(toId(item.getParentId()))
                .code(item.getCode())
                .label(item.getLabel())
                .value(item.getValue())
                .description(item.getDescription())
                .status(item.getStatus())
                .sortOrder(item.getSortOrder())
                .depthLevel(item.getDepthLevel())
                .depthPath(item.getDepthPath())
                .leaf(Objects.equals(item.getLeafFlag(), (byte) 1))
                .systemBuiltIn(Objects.equals(item.getSystemBuiltIn(), (byte) 1))
                .referenced(Objects.equals(item.getReferencedFlag(), (byte) 1))
                .cacheVersion(item.getCacheVersion())
                .version(item.getCacheVersion())
                .children(children)
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }

    private DictTypeVO withRefresh(DictTypeVO vo, DictType dictType) {
        vo.setCacheRefresh(cacheRefresh(dictType));
        return vo;
    }

    private DictItemVO withRefresh(DictItemVO vo, DictType dictType) {
        vo.setCacheRefresh(cacheRefresh(dictType));
        return vo;
    }

    private DictCacheRefreshVO cacheRefresh(DictType dictType) {
        String tenantKey = ROOT_ID == dictType.getScopeTenantId() ? "system" : String.valueOf(dictType.getScopeTenantId());
        String cacheKey = "DICT:%s:%s:%s:%s:v%s".formatted(dictType.getSystemId(), dictType.getScopeType(), tenantKey,
                dictType.getCode(), dictType.getCacheVersion());
        return DictCacheRefreshVO.builder()
                .dictTypeId(toId(dictType.getId()))
                .cacheVersion(dictType.getCacheVersion())
                .refreshMode("LOCAL_EVICT")
                .refreshedAt(LocalDateTime.now())
                .affectedKeys(List.of(cacheKey))
                .build();
    }

    private Comparator<DictType> typeComparator() {
        return Comparator.comparing(DictType::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(DictType::getCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(DictType::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
    }

    private Comparator<DictItem> itemComparator() {
        return Comparator.comparing(DictItem::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(DictItem::getCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(DictItem::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));
    }

    private String depthPath(DictItem parent) {
        return Objects.isNull(parent) ? "/" : parent.getDepthPath() + parent.getId() + "/";
    }

    private Long parseLongOrDefault(String value, Long defaultValue) {
        return StringUtils.hasText(value) ? Long.valueOf(value) : defaultValue;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return Objects.nonNull(value) ? value : defaultValue;
    }

    private boolean contains(String source, String keyword) {
        return StringUtils.hasText(source) && source.contains(keyword);
    }

    private String toId(Long id) {
        return Objects.isNull(id) ? null : String.valueOf(id);
    }
}
