package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleGroup;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleGroupBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
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
    private final ConfiguredModuleVersionBaseService versionService;
    private final PermissionChecker permissionChecker;
    private final ObjectMapper objectMapper;
    private final SystemTenantBaseService tenantService;

    public RuntimeModuleCatalogService(
            ConfiguredModuleGroupBaseService groupService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModulePublicationBaseService publicationService,
            ConfiguredModuleVersionBaseService versionService,
            PermissionChecker permissionChecker,
            ObjectMapper objectMapper,
            SystemTenantBaseService tenantService) {
        this.groupService = groupService;
        this.moduleService = moduleService;
        this.publicationService = publicationService;
        this.versionService = versionService;
        this.permissionChecker = permissionChecker;
        this.objectMapper = objectMapper;
        this.tenantService = tenantService;
    }

    @Transactional(readOnly = true)
    public List<RuntimeModuleCatalogItem> list(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统", HttpStatus.CONFLICT);
        }
        Long mainTenantId = tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, context.systemId())
                        .eq(SystemTenant::getMain, true)
                        .eq(SystemTenant::getStatus, "ACTIVE"))
                .stream().map(SystemTenant::getId).findFirst().orElseThrow(() -> new DomainException(
                        "MAIN_TENANT_NOT_FOUND", "系统缺少有效主租户", HttpStatus.CONFLICT));
        Set<Long> visibleTenantIds = context.tenantId().equals(mainTenantId)
                ? Set.of(mainTenantId) : Set.of(context.tenantId(), mainTenantId);
        Map<Long, ConfiguredModuleGroup> groups = groupService.selectList(
                        Wrappers.<ConfiguredModuleGroup>lambdaQuery()
                                .eq(ConfiguredModuleGroup::getSystemId, context.systemId())
                                .in(ConfiguredModuleGroup::getOwnerTenantId, visibleTenantIds)
                                .eq(ConfiguredModuleGroup::getStatus, "ACTIVE"))
                .stream().collect(Collectors.toMap(ConfiguredModuleGroup::getId, Function.identity()));
        Map<Long, ConfiguredModulePublication> publications = publicationService.selectList(
                        Wrappers.<ConfiguredModulePublication>lambdaQuery()
                                .eq(ConfiguredModulePublication::getSystemId, context.systemId())
                                .in(ConfiguredModulePublication::getOwnerTenantId, visibleTenantIds))
                .stream().collect(Collectors.toMap(ConfiguredModulePublication::getModuleId, Function.identity()));
        return moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                        .eq(ConfiguredModule::getSystemId, context.systemId())
                        .in(ConfiguredModule::getOwnerTenantId, visibleTenantIds)
                        .eq(ConfiguredModule::getStatus, "ACTIVE"))
                .stream()
                .filter(module -> publications.containsKey(module.getId()))
                .filter(module -> permissionChecker.allows(context, "MODULE", module.getCode(), "LIST"))
                .map(module -> item(module, groups.get(module.getGroupId()), publications.get(module.getId())))
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(RuntimeModuleCatalogItem::groupSortOrder)
                        .thenComparing(RuntimeModuleCatalogItem::groupId)
                        .thenComparing(RuntimeModuleCatalogItem::menuSortOrder)
                        .thenComparing(RuntimeModuleCatalogItem::moduleId))
                .toList();
    }

    private RuntimeModuleCatalogItem item(
            ConfiguredModule module,
            ConfiguredModuleGroup liveGroup,
            ConfiguredModulePublication publication) {
        ConfiguredModuleVersion version = versionService.selectById(publication.getCurrentVersionId());
        if (version == null || version.getSnapshotJson() == null) return null;
        try {
            JsonNode snapshot = objectMapper.readTree(version.getSnapshotJson());
            JsonNode snapshotModule = snapshot.path("module");
            JsonNode snapshotGroup = snapshot.path("group");
            JsonNode menu = firstVisibleMenu(snapshot.path("menus"));
            if (snapshot.has("menus") && menu == null) return null;
            Long groupId = nullableLong(snapshotModule.path("groupId"), module.getGroupId());
            String groupCode = text(snapshotGroup.path("code"), liveGroup == null ? "ungrouped" : liveGroup.getCode());
            String groupName = text(snapshotGroup.path("name"), liveGroup == null ? "其他" : liveGroup.getName());
            Integer groupOrder = snapshotGroup.path("sortOrder").isNumber()
                    ? snapshotGroup.path("sortOrder").asInt()
                    : liveGroup == null ? Integer.MAX_VALUE : liveGroup.getSortOrder();
            String moduleCode = text(snapshotModule.path("code"), module.getCode());
            String moduleName = text(snapshotModule.path("name"), module.getName());
            return new RuntimeModuleCatalogItem(
                    groupId, groupCode, groupName, groupOrder,
                    module.getId(), moduleCode, moduleName,
                    menu == null ? null : nullableLong(menu.path("id"), null),
                    menu == null ? null : nullableLong(menu.path("parentId"), null),
                    menu == null ? moduleCode : text(menu.path("code"), moduleCode),
                    menu == null ? moduleName : text(menu.path("name"), moduleName),
                    menu == null ? null : text(menu.path("icon"), null),
                    menu == null ? "/runtime/" + moduleCode : text(menu.path("routePath"), "/runtime/" + moduleCode),
                    menu == null || !menu.path("sortOrder").isNumber() ? 10 : menu.path("sortOrder").asInt());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot read published module catalog snapshot for module " + module.getId(), exception);
        }
    }

    private JsonNode firstVisibleMenu(JsonNode menus) {
        if (!menus.isArray()) return null;
        JsonNode selected = null;
        for (JsonNode menu : menus) {
            if (!"ACTIVE".equals(menu.path("status").asText()) || !menu.path("visible").asBoolean(false)) continue;
            if (selected == null || menu.path("sortOrder").asInt() < selected.path("sortOrder").asInt()) selected = menu;
        }
        return selected;
    }

    private String text(JsonNode node, String fallback) {
        return node.isTextual() && !node.asText().isBlank() ? node.asText() : fallback;
    }

    private Long nullableLong(JsonNode node, Long fallback) {
        if (node.isIntegralNumber()) return Long.valueOf(node.asLong());
        return fallback;
    }

}
