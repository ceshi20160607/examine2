package com.unique.unexamine.foundation.manage.productivity;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.foundation.base.entity.CoreSetting;
import com.unique.unexamine.foundation.base.service.CoreSettingBaseService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleCatalogItem;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleCatalogService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

@Service
public class CommandCenterService {
    private static final String CATEGORY = "UI_COMMAND_CENTER";
    private static final int RESULT_LIMIT = 40;

    private final CoreSettingBaseService settingService;
    private final RuntimeModuleCatalogService catalogService;
    private final RuntimeDataService runtimeDataService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate withoutOuterTransaction;

    public CommandCenterService(
            CoreSettingBaseService settingService,
            RuntimeModuleCatalogService catalogService,
            RuntimeDataService runtimeDataService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.settingService = settingService;
        this.catalogService = catalogService;
        this.runtimeDataService = runtimeDataService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.withoutOuterTransaction = new TransactionTemplate(transactionManager);
        this.withoutOuterTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
    }

    @Transactional
    public CommandCenterModels.View discover(AuthenticatedContext context, String query, String traceId) {
        requireContext(context);
        String normalized = query == null ? "" : query.strip();
        if (normalized.length() > 100) {
            throw new DomainException("COMMAND_QUERY_INVALID", "命令搜索不能超过 100 个字符", HttpStatus.BAD_REQUEST);
        }
        CoreSetting setting = find(context);
        StoredState stored = read(setting);
        Resolution stateResolution = resolveState(context, stored, traceId);
        if (!stateResolution.invalidated().isEmpty()) {
            setting = persist(context, setting, stateResolution.state(), null);
        }
        LinkedHashMap<String, CommandCenterModels.Item> candidates = baseCommands(context);
        if (!normalized.isBlank()) {
            addRecordResults(context, normalized, candidates, traceId);
        }
        List<CommandCenterModels.Item> results = candidates.values().stream()
                .filter(item -> matches(item, normalized)).limit(RESULT_LIMIT).toList();
        return view(context, results, stateResolution.state(), setting,
                stateResolution.favoriteItems(), stateResolution.recentItems(), stateResolution.invalidated());
    }

    @Transactional
    public CommandCenterModels.View updateState(
            AuthenticatedContext context, CommandCenterModels.UpdateStateRequest request, String traceId) {
        requireContext(context);
        CoreSetting setting = find(context);
        requireExpectedVersion(setting, request.expectedVersion());
        StoredState desired = new StoredState(distinct(request.favoriteIds(), 20), distinct(request.recentIds(), 10));
        Resolution resolved = resolveState(context, desired, traceId);
        setting = persist(context, setting, resolved.state(), request.expectedVersion());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "COMMAND_CENTER_STATE_SAVED", "CORE_SETTING", setting.getId().toString(), "SUCCESS",
                Map.of("favoriteCount", resolved.state().favoriteIds().size(),
                        "recentCount", resolved.state().recentIds().size(),
                        "invalidatedCount", resolved.invalidated().size()));
        return view(context, List.of(), resolved.state(), setting,
                resolved.favoriteItems(), resolved.recentItems(), resolved.invalidated());
    }

    @Transactional
    public CommandCenterModels.Execution execute(
            AuthenticatedContext context, String commandId, String traceId) {
        requireContext(context);
        CommandCenterModels.Item command;
        try {
            command = isolated(() -> resolveCommand(context, commandId, traceId));
        } catch (DomainException exception) {
            removeInvalidCommand(context, commandId);
            throw new DomainException("COMMAND_NO_LONGER_AVAILABLE",
                    "命令目标已失效或权限已变化，请刷新命令中心", HttpStatus.CONFLICT);
        }
        CoreSetting setting = find(context);
        StoredState stored = read(setting);
        List<String> recent = new ArrayList<>();
        recent.add(command.id());
        recent.addAll(stored.recentIds());
        StoredState next = new StoredState(stored.favoriteIds(), distinct(recent, 10));
        Resolution resolved = resolveState(context, next, traceId);
        setting = persist(context, setting, resolved.state(), null);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("commandId", command.id());
        detail.put("target", command.target());
        detail.put("moduleCode", command.moduleCode());
        detail.put("recordId", command.recordId());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "COMMAND_CENTER_EXECUTED", "COMMAND", command.id(), "SUCCESS", detail);
        return new CommandCenterModels.Execution(command,
                new CommandCenterModels.State(resolved.state().favoriteIds(), resolved.state().recentIds(), setting.getVersion()),
                resolved.invalidated());
    }

    private CommandCenterModels.View view(
            AuthenticatedContext context,
            List<CommandCenterModels.Item> results,
            StoredState stored,
            CoreSetting setting,
            List<CommandCenterModels.Item> favorites,
            List<CommandCenterModels.Item> recent,
            List<CommandCenterModels.InvalidatedItem> invalidated) {
        return new CommandCenterModels.View(results, favorites, recent,
                new CommandCenterModels.State(stored.favoriteIds(), stored.recentIds(),
                        setting == null ? null : setting.getVersion()), invalidated);
    }

    private Resolution resolveState(AuthenticatedContext context, StoredState state, String traceId) {
        List<CommandCenterModels.InvalidatedItem> invalidated = new ArrayList<>();
        List<CommandCenterModels.Item> favorites = resolveIds(context, state.favoriteIds(), invalidated, traceId);
        List<CommandCenterModels.Item> recent = resolveIds(context, state.recentIds(), invalidated, traceId);
        return new Resolution(
                new StoredState(favorites.stream().map(CommandCenterModels.Item::id).toList(),
                        recent.stream().map(CommandCenterModels.Item::id).toList()),
                favorites, recent, invalidated);
    }

    private List<CommandCenterModels.Item> resolveIds(
            AuthenticatedContext context,
            List<String> ids,
            List<CommandCenterModels.InvalidatedItem> invalidated,
            String traceId) {
        List<CommandCenterModels.Item> result = new ArrayList<>();
        for (String id : distinct(ids, 20)) {
            try {
                result.add(isolated(() -> resolveCommand(context, id, traceId)));
            } catch (DomainException exception) {
                if (invalidated.stream().noneMatch(item -> item.id().equals(id))) {
                    invalidated.add(new CommandCenterModels.InvalidatedItem(id,
                            "目标已删除、停用或不再具有访问权限"));
                }
            }
        }
        return result;
    }

    private LinkedHashMap<String, CommandCenterModels.Item> baseCommands(AuthenticatedContext context) {
        if (context.systemId() == null) return platformCommands(context);
        LinkedHashMap<String, CommandCenterModels.Item> commands = new LinkedHashMap<>();
        put(commands, item("menu:home", "MENU", "页面与菜单", "系统首页", "返回当前系统工作台", "HOME", null, null));
        List<RuntimeModuleCatalogItem> modules = catalogService.list(context);
        for (RuntimeModuleCatalogItem module : modules) {
            put(commands, item("module:" + module.moduleCode(), "MODULE", "业务模块",
                    module.menuName() == null ? module.moduleName() : module.menuName(),
                    module.groupName() + " · 打开列表", "RUNTIME_MODULE", module.moduleCode(), null));
            if (permissionChecker.allows(context, "MODULE", module.moduleCode(), "CREATE")) {
                put(commands, item("create:" + module.moduleCode(), "CREATE", "快捷创建",
                        "新建" + module.moduleName(), "打开真实" + module.moduleName() + "表单",
                        "RUNTIME_CREATE", module.moduleCode(), null));
            }
        }
        if (permissionChecker.allows(context, "CONFIG", "MODULE", "MANAGE")) {
            put(commands, item("config:modules", "MENU", "页面与菜单", "模块配置",
                    "设计字段、页面、动作并发布版本", "MODULE_CONFIG", null, null));
        }
        if (permissionChecker.allows(context, "AUDIT", "EVENT", "VIEW")) {
            put(commands, item("audit:events", "MENU", "页面与菜单", "操作审计",
                    "查看当前系统和租户的审计事件", "AUDIT", null, null));
        }
        if (permissionChecker.allows(context, "CONFIG", "SYSTEM", "MANAGE")) {
            put(commands, item("config:system", "MENU", "页面与菜单", "系统与租户",
                    "维护系统信息、租户与模式", "SYSTEM_SETTINGS", null, null));
        }
        return commands;
    }

    private LinkedHashMap<String, CommandCenterModels.Item> platformCommands(AuthenticatedContext context) {
        LinkedHashMap<String, CommandCenterModels.Item> commands = new LinkedHashMap<>();
        put(commands, item("platform:systems", "MENU", "平台页面", "系统",
                "选择有权进入的系统或申请访问", "PLATFORM_SYSTEMS", null, null));
        put(commands, item("platform:dashboard", "MENU", "平台页面", "仪表盘",
                "汇总有权访问的系统、待办、任务和消息", "PLATFORM_DASHBOARD", null, null));
        put(commands, item("platform:tasks", "MENU", "平台页面", "任务",
                "普通任务、项目任务和每日日志", "PLATFORM_TASKS", null, null));
        put(commands, item("platform:todos", "MENU", "平台页面", "待办",
                "审批、今日需联系记录和其他待办", "PLATFORM_TODOS", null, null));
        put(commands, item("platform:messages", "MENU", "平台页面", "消息",
                "通知消息及处理记录", "PLATFORM_MESSAGES", null, null));
        put(commands, item("platform:profile", "MENU", "平台页面", "个人中心",
                "账号资料、安全和会话", "PLATFORM_PROFILE", null, null));
        if (hasResource(context, "FLOW")) {
            put(commands, item("platform:flows", "MENU", "平台页面", "Flow",
                    "系统内 Flow 和经应用开放的外部 Flow", "PLATFORM_FLOWS", null, null));
        }
        if (hasResource(context, "APPLICATION")) {
            put(commands, item("platform:applications", "MENU", "平台页面", "应用",
                    "系统之间及平台内外访问的授权桥接", "PLATFORM_APPLICATIONS", null, null));
        }
        if (hasResource(context, "AI")) {
            put(commands, item("platform:ai", "MENU", "平台页面", "AI",
                    "通过获权 AI 能力连接整个平台", "PLATFORM_AI", null, null));
        }
        if (permissionChecker.allows(context, "PLATFORM", "CONFIGURATION", "MANAGE")
                || permissionChecker.allows(context, "PLATFORM", "IDENTITY_PROVIDER", "MANAGE")) {
            put(commands, item("platform:admin", "MENU", "平台页面", "平台后台",
                    "平台信息、组织权限、配置与运维入口", "PLATFORM_ADMIN", null, null));
        }
        return commands;
    }

    private void addRecordResults(
            AuthenticatedContext context,
            String query,
            LinkedHashMap<String, CommandCenterModels.Item> commands,
            String traceId) {
        if (context.systemId() == null) return;
        for (RuntimeModuleCatalogItem module : catalogService.list(context)) {
            try {
                for (RuntimeRecordView record : isolated(() -> runtimeDataService.list(context, module.moduleCode(),
                        "ACTIVE", "ALL", query, "[]", "updatedAt", "DESC", 1, 5, traceId)).records()) {
                    put(commands, recordItem(module, record));
                }
            } catch (DomainException ignored) {
                // One inaccessible module must not make the whole global search fail.
            }
        }
    }

    private CommandCenterModels.Item resolveCommand(
            AuthenticatedContext context, String commandId, String traceId) {
        if (commandId == null || commandId.isBlank()) {
            throw unavailable();
        }
        CommandCenterModels.Item fixed = baseCommands(context).get(commandId);
        if (fixed != null) return fixed;
        String[] parts = commandId.split(":");
        if (parts.length == 3 && "record".equals(parts[0]) && parts[1].matches("[a-z][a-z0-9_-]{0,99}")
                && parts[2].matches("[1-9][0-9]*")) {
            String moduleCode = parts[1];
            RuntimeModuleCatalogItem module = catalogService.list(context).stream()
                    .filter(item -> item.moduleCode().equals(moduleCode)).findFirst().orElseThrow(this::unavailable);
            RuntimeRecordView record = runtimeDataService.detail(context, moduleCode, Long.valueOf(parts[2]), traceId);
            if (Boolean.TRUE.equals(record.deleted())) throw unavailable();
            return recordItem(module, record);
        }
        throw unavailable();
    }

    private CommandCenterModels.Item recordItem(RuntimeModuleCatalogItem module, RuntimeRecordView record) {
        return item("record:" + module.moduleCode() + ":" + record.id(), "RECORD", "业务数据",
                record.title(), module.moduleName() + " · " + (record.recordNumber() == null ? "#" + record.id() : record.recordNumber()),
                "RUNTIME_RECORD", module.moduleCode(), record.id());
    }

    private boolean matches(CommandCenterModels.Item item, String query) {
        if (query.isBlank()) return true;
        String needle = query.toLowerCase(Locale.ROOT);
        return item.label().toLowerCase(Locale.ROOT).contains(needle)
                || item.description().toLowerCase(Locale.ROOT).contains(needle)
                || item.groupName().toLowerCase(Locale.ROOT).contains(needle);
    }

    private CoreSetting persist(
            AuthenticatedContext context, CoreSetting setting, StoredState state, Integer expectedVersion) {
        if (setting == null) {
            if (expectedVersion != null) {
                throw new DomainException("COMMAND_STATE_VERSION_CONFLICT", "快捷状态已变化，请刷新后重试", HttpStatus.CONFLICT);
            }
            setting = new CoreSetting();
            setting.setContextType(context.systemId() == null ? "PLATFORM" : "TENANT");
            setting.setPlatformId(context.platformId());
            setting.setSystemId(context.systemId());
            setting.setTenantId(context.tenantId());
            setting.setCategory(CATEGORY);
            setting.setSettingKey(settingKey(context));
            setting.setValueType("JSON");
            setting.setSensitive(false);
            setting.setStatus("ACTIVE");
            setting.setValueJson(toJson(state));
            settingService.insert(setting);
            return settingService.selectById(setting.getId());
        }
        if (expectedVersion != null && !expectedVersion.equals(setting.getVersion())) {
            throw new DomainException("COMMAND_STATE_VERSION_CONFLICT", "快捷状态已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        setting.setValueJson(toJson(state));
        setting.setStatus("ACTIVE");
        if (settingService.updateById(setting) != 1) {
            throw new DomainException("COMMAND_STATE_VERSION_CONFLICT", "快捷状态已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        return settingService.selectById(setting.getId());
    }

    private void removeInvalidCommand(AuthenticatedContext context, String commandId) {
        CoreSetting setting = find(context);
        if (setting == null) return;
        StoredState stored = read(setting);
        StoredState changed = new StoredState(stored.favoriteIds().stream().filter(id -> !id.equals(commandId)).toList(),
                stored.recentIds().stream().filter(id -> !id.equals(commandId)).toList());
        persist(context, setting, changed, null);
    }

    private void requireExpectedVersion(CoreSetting setting, Integer expectedVersion) {
        if (setting == null && expectedVersion != null) {
            throw new DomainException("COMMAND_STATE_VERSION_CONFLICT", "快捷状态已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
        if (setting != null && (expectedVersion == null || !expectedVersion.equals(setting.getVersion()))) {
            throw new DomainException("COMMAND_STATE_VERSION_CONFLICT", "快捷状态已变化，请刷新后重试", HttpStatus.CONFLICT);
        }
    }

    private CoreSetting find(AuthenticatedContext context) {
        var query = Wrappers.<CoreSetting>lambdaQuery()
                .eq(CoreSetting::getContextType, context.systemId() == null ? "PLATFORM" : "TENANT")
                .eq(CoreSetting::getPlatformId, context.platformId())
                .eq(CoreSetting::getCategory, CATEGORY)
                .eq(CoreSetting::getSettingKey, settingKey(context));
        if (context.systemId() == null) {
            query.isNull(CoreSetting::getSystemId).isNull(CoreSetting::getTenantId);
        } else {
            query.eq(CoreSetting::getSystemId, context.systemId()).eq(CoreSetting::getTenantId, context.tenantId());
        }
        return settingService.selectList(query)
                .stream().findFirst().orElse(null);
    }

    private StoredState read(CoreSetting setting) {
        if (setting == null || setting.getValueJson() == null) return new StoredState(List.of(), List.of());
        try {
            StoredState state = objectMapper.readValue(setting.getValueJson(), StoredState.class);
            return new StoredState(state.favoriteIds() == null ? List.of() : state.favoriteIds(),
                    state.recentIds() == null ? List.of() : state.recentIds());
        } catch (JsonProcessingException exception) {
            throw new DomainException("COMMAND_STATE_INVALID", "快捷状态无法读取", HttpStatus.CONFLICT);
        }
    }

    private String toJson(StoredState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException exception) {
            throw new DomainException("COMMAND_STATE_INVALID", "快捷状态无法保存", HttpStatus.BAD_REQUEST);
        }
    }

    private List<String> distinct(List<String> ids, int maximum) {
        if (ids == null) return List.of();
        return new LinkedHashSet<>(ids).stream().filter(id -> id != null && !id.isBlank()).limit(maximum).toList();
    }

    private void requireContext(AuthenticatedContext context) {
        if (context.accountId() == null || context.platformId() == null) {
            throw new DomainException("COMMAND_CONTEXT_REQUIRED", "当前登录上下文无法使用命令中心", HttpStatus.CONFLICT);
        }
        if (context.systemId() != null && (context.tenantId() == null || context.memberId() == null)) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "系统上下文不完整，请重新进入系统", HttpStatus.CONFLICT);
        }
    }

    private boolean hasResource(AuthenticatedContext context, String resourceType) {
        return context.permissions().stream().anyMatch(grant ->
                "*".equals(grant.resourceType()) || resourceType.equals(grant.resourceType()));
    }

    private String settingKey(AuthenticatedContext context) {
        return context.accountId() + ":command-center";
    }

    private void put(Map<String, CommandCenterModels.Item> commands, CommandCenterModels.Item item) {
        commands.putIfAbsent(item.id(), item);
    }

    private CommandCenterModels.Item item(
            String id, String groupCode, String groupName, String label, String description,
            String target, String moduleCode, Long recordId) {
        return new CommandCenterModels.Item(id, groupCode, groupName, label, description, target, moduleCode, recordId);
    }

    private DomainException unavailable() {
        return new DomainException("COMMAND_NOT_AVAILABLE", "命令目标不存在或没有访问权限", HttpStatus.NOT_FOUND);
    }

    private <T> T isolated(Supplier<T> action) {
        return withoutOuterTransaction.execute(status -> action.get());
    }

    private record StoredState(List<String> favoriteIds, List<String> recentIds) {
    }

    private record Resolution(
            StoredState state,
            List<CommandCenterModels.Item> favoriteItems,
            List<CommandCenterModels.Item> recentItems,
            List<CommandCenterModels.InvalidatedItem> invalidated) {
    }
}
