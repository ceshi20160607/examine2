package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleGroupBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RuntimeModuleCatalogService {
    private final ConfiguredModuleGroupBaseService groupService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModulePublicationBaseService publicationService;
    private final PermissionChecker permissionChecker;

    public RuntimeModuleCatalogService(
            ConfiguredModuleGroupBaseService groupService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModulePublicationBaseService publicationService,
            PermissionChecker permissionChecker) {
        this.groupService = groupService;
        this.moduleService = moduleService;
        this.publicationService = publicationService;
        this.permissionChecker = permissionChecker;
    }

    @Transactional(readOnly = true)
    public List<RuntimeModuleCatalogItem> list(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
        Map<Long, ConfiguredModuleGroup> groups = groupService.selectList(
                        Wrappers.<ConfiguredModuleGroup>lambdaQuery()
                                .eq(ConfiguredModuleGroup::getSystemId, context.systemId())
                                .eq(ConfiguredModuleGroup::getOwnerTenantId, context.tenantId())
                                .eq(ConfiguredModuleGroup::getStatus, "ACTIVE"))
                .stream().collect(Collectors.toMap(ConfiguredModuleGroup::getId, Function.identity()));
        Set<Long> publishedModuleIds = publicationService.selectList(
                        Wrappers.<ConfiguredModulePublication>lambdaQuery()
                                .eq(ConfiguredModulePublication::getSystemId, context.systemId())
                                .eq(ConfiguredModulePublication::getOwnerTenantId, context.tenantId()))
                .stream().map(ConfiguredModulePublication::getModuleId).collect(Collectors.toSet());
        return moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                        .eq(ConfiguredModule::getSystemId, context.systemId())
                        .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                        .eq(ConfiguredModule::getStatus, "ACTIVE"))
                .stream()
                .filter(module -> publishedModuleIds.contains(module.getId()))
                .filter(module -> permissionChecker.allows(context, "MODULE", module.getCode(), "LIST"))
                .map(module -> item(module, groups.get(module.getGroupId())))
                .sorted(Comparator.comparing(RuntimeModuleCatalogItem::groupSortOrder)
                        .thenComparing(RuntimeModuleCatalogItem::groupId)
                        .thenComparing(RuntimeModuleCatalogItem::moduleId))
                .toList();
    }

    private RuntimeModuleCatalogItem item(ConfiguredModule module, ConfiguredModuleGroup group) {
        return new RuntimeModuleCatalogItem(
                module.getGroupId(),
                group == null ? "ungrouped" : group.getCode(),
                group == null ? "其他" : group.getName(),
                group == null ? Integer.MAX_VALUE : group.getSortOrder(),
                module.getId(), module.getCode(), module.getName());
    }
}
