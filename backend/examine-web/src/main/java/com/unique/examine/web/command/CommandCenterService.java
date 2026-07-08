package com.unique.examine.web.command;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.module.base.entity.ModuleDefinition;
import com.unique.examine.module.base.service.ModuleDefinitionBaseService;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.manage.common.CurrentAccountProvider;
import com.unique.examine.plat.manage.common.PlatformAccessGuard;
import com.unique.examine.plat.manage.common.SystemAccessGuard;
import com.unique.examine.plat.manage.context.ContextModels.SwitchOption;
import com.unique.examine.plat.manage.context.SystemContextService;
import com.unique.examine.web.command.CommandCenterModels.CommandCenterItem;
import com.unique.examine.web.command.CommandCenterModels.CommandCenterResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Builds a single role-aware entry list across platform and system shells.
 */
@Service
public class CommandCenterService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String PUBLISHED = "PUBLISHED";

    private final CurrentAccountProvider currentAccountProvider;
    private final SystemContextService systemContextService;
    private final PlatformAccessGuard platformAccessGuard;
    private final SystemAccessGuard systemAccessGuard;
    private final ModuleDefinitionBaseService moduleDefinitionBaseService;

    public CommandCenterService(CurrentAccountProvider currentAccountProvider,
                                SystemContextService systemContextService,
                                PlatformAccessGuard platformAccessGuard,
                                SystemAccessGuard systemAccessGuard,
                                ModuleDefinitionBaseService moduleDefinitionBaseService) {
        this.currentAccountProvider = currentAccountProvider;
        this.systemContextService = systemContextService;
        this.platformAccessGuard = platformAccessGuard;
        this.systemAccessGuard = systemAccessGuard;
        this.moduleDefinitionBaseService = moduleDefinitionBaseService;
    }

    public CommandCenterResponse search(String keyword, String systemId) {
        PlatAccount account = currentAccountProvider.currentAccount();
        String traceId = RequestContext.current().traceId();
        List<CommandCenterItem> items = new ArrayList<>();
        boolean canManagePlatform = platformAccessGuard.canManagePlatform(account.getId());

        addPlatformCommands(items, canManagePlatform, traceId);
        List<SwitchOption> systems = systemContextService.options();
        List<SwitchOption> scopedSystems = scopedSystems(systems, systemId);
        for (SwitchOption option : scopedSystems) {
            addSystemCommands(items, account.getId(), option, traceId);
        }

        List<CommandCenterItem> filtered = filter(items, keyword).stream()
                .sorted(Comparator.comparing(CommandCenterItem::groupName)
                        .thenComparing(CommandCenterItem::disabled)
                        .thenComparing(CommandCenterItem::label))
                .toList();
        return new CommandCenterResponse(blankToNull(keyword), blankToNull(systemId), traceId, filtered);
    }

    private void addPlatformCommands(List<CommandCenterItem> items, boolean canManagePlatform, String traceId) {
        items.add(command("platform.workbench", "平台工作台", "平台", "查看可进入系统、平台待办和平台消息",
                "/platform", "NAVIGATION", false, null, traceId));
        items.add(command("platform.todos", "平台待办", "平台", "处理平台授权、任务和审批事项",
                "/platform/todos", "NAVIGATION", false, null, traceId));
        items.add(command("platform.messages", "平台消息", "平台", "查看平台通知和任务结果",
                "/platform/messages", "NAVIGATION", false, null, traceId));
        items.add(command("platform.apps", "应用入口", "平台", "查看当前账号可进入的系统和租户",
                "/platform/apps", "NAVIGATION", false, null, traceId));
        items.add(command("platform.flow", "平台 Flow", "平台", "运行平台级流程和任务健康检查",
                "/platform/flow", "NAVIGATION", false, null, traceId));
        items.add(command("platform.admin", "平台后台", "平台后台", "系统生命周期、角色、SSO、运维和授权管理",
                "/platform/admin", "ADMIN", !canManagePlatform, canManagePlatform ? null : "当前账号没有平台后台权限。", traceId));
    }

    private void addSystemCommands(List<CommandCenterItem> items, Long accountId, SwitchOption option, String traceId) {
        boolean switchable = option.switchable();
        String disabledReason = switchable ? null : option.disabledReason();
        String systemId = option.systemId();
        String group = "系统 / " + option.systemName();
        items.add(command("system." + systemId + ".dashboard", option.systemName() + " 工作台", group,
                "进入当前系统首页和业务模块", "/systems/" + systemId + "/dashboard",
                "NAVIGATION", !switchable, disabledReason, traceId));
        items.add(command("system." + systemId + ".work", option.systemName() + " 工作管理", group,
                "处理项目任务、普通任务和日报", "/systems/" + systemId + "/work",
                "NAVIGATION", !switchable, disabledReason, traceId));
        items.add(command("system." + systemId + ".todos", option.systemName() + " 待办", group,
                "处理当前系统审批和提醒事项", "/systems/" + systemId + "/todos",
                "NAVIGATION", !switchable, disabledReason, traceId));
        items.add(command("system." + systemId + ".messages", option.systemName() + " 消息", group,
                "查看当前系统消息流", "/systems/" + systemId + "/messages",
                "NAVIGATION", !switchable, disabledReason, traceId));

        boolean canManageSystem = switchable && systemAccessGuard.canManageSystem(accountId, systemId);
        items.add(command("system." + systemId + ".admin", option.systemName() + " 系统后台", "系统后台",
                "配置组织、角色、模块、字段、流程、字典和权限", "/systems/" + systemId + "/admin",
                "ADMIN", !canManageSystem, canManageSystem ? null : "当前账号没有该系统后台权限。", traceId));

        if (switchable) {
            addRuntimeModuleCommands(items, systemId, option.tenantId(), option.systemName(), traceId);
        }
    }

    private void addRuntimeModuleCommands(List<CommandCenterItem> items, String systemId, String tenantId,
                                          String systemName, String traceId) {
        Long parsedSystemId = parseLong(systemId);
        Long parsedTenantId = parseLong(tenantId);
        if (Objects.isNull(parsedSystemId) || Objects.isNull(parsedTenantId)) {
            return;
        }
        List<ModuleDefinition> modules = moduleDefinitionBaseService.list(new LambdaQueryWrapper<ModuleDefinition>()
                .eq(ModuleDefinition::getSystemId, parsedSystemId)
                .eq(ModuleDefinition::getTenantId, parsedTenantId)
                .eq(ModuleDefinition::getStatus, ENABLED)
                .eq(ModuleDefinition::getDeleted, DELETED_NO)
                .eq(ModuleDefinition::getPublishStatus, PUBLISHED)
                .orderByAsc(ModuleDefinition::getId)
                .last("LIMIT 12"));
        for (ModuleDefinition module : modules) {
            items.add(command("system." + systemId + ".module." + module.getId(),
                    module.getModuleName(), "业务模块 / " + systemName,
                    "打开已发布业务模块", "/systems/" + systemId + "/modules/" + module.getId(),
                    "RUNTIME_MODULE", false, null, traceId));
        }
    }

    private List<SwitchOption> scopedSystems(List<SwitchOption> systems, String systemId) {
        if (!StringUtils.hasText(systemId)) {
            return systems;
        }
        return systems.stream()
                .filter(option -> Objects.equals(option.systemId(), systemId))
                .toList();
    }

    private List<CommandCenterItem> filter(List<CommandCenterItem> items, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return dedupe(items);
        }
        String normalized = keyword.toLowerCase(Locale.ROOT).trim();
        return dedupe(items).stream()
                .filter(item -> contains(item.label(), normalized)
                        || contains(item.groupName(), normalized)
                        || contains(item.description(), normalized)
                        || contains(item.route(), normalized))
                .toList();
    }

    private List<CommandCenterItem> dedupe(List<CommandCenterItem> items) {
        Map<String, CommandCenterItem> result = new LinkedHashMap<>();
        for (CommandCenterItem item : items) {
            result.putIfAbsent(item.commandId(), item);
        }
        return List.copyOf(result.values());
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private CommandCenterItem command(String commandId, String label, String groupName, String description,
                                      String route, String commandType, boolean disabled,
                                      String disabledReason, String traceId) {
        return new CommandCenterItem(commandId, label, groupName, description, route, commandType,
                disabled, disabledReason, traceId);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
