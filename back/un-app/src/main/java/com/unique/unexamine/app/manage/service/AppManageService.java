package com.unique.unexamine.app.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.app.base.entity.UnApp;
import com.unique.unexamine.app.base.entity.UnAppEvent;
import com.unique.unexamine.app.base.service.IUnAppEventService;
import com.unique.unexamine.app.base.service.IUnAppService;
import com.unique.unexamine.app.manage.dto.AppManageDto.AppView;
import com.unique.unexamine.app.manage.dto.AppManageDto.EventRequest;
import com.unique.unexamine.app.manage.dto.AppManageDto.EventView;
import com.unique.unexamine.app.manage.dto.AppManageDto.SaveRequest;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatDepartment;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.entity.UnPlatLogOpt;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatDepartmentService;
import com.unique.unexamine.plat.base.service.IUnPlatLogOptService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import com.unique.unexamine.plat.manage.service.AuthManageService;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.net.URI;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台或系统应用的配置、密钥轮换、授权发布和事件发布服务。 */
@Service
public class AppManageService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> ACCESS_MODES = Set.of("INTERNAL", "EXTERNAL", "BOTH");
    private final IUnAppService apps;
    private final IUnAppEventService events;
    private final IUnSysLogOptService logs;
    private final IUnPlatLogOptService platformLogs;
    private final IUnPlatAccountService platformAccounts;
    private final IUnPlatMemberService platformMembers;
    private final IUnPlatDepartmentService platformDepartments;
    private final IUnSysMemberService systemMembers;
    private final IUnSysTenantMemberService systemTenantMembers;
    private final IUnSysDepartmentService systemDepartments;
    private final AuthManageService auth;
    private final SystemManageService systems;
    private final ObjectMapper json;

    /** 注入应用基础服务、平台及系统管理权限和审计日志。 */
    public AppManageService(IUnAppService apps, IUnAppEventService events,
            IUnSysLogOptService logs, IUnPlatLogOptService platformLogs,
            IUnPlatAccountService platformAccounts, IUnPlatMemberService platformMembers,
            IUnPlatDepartmentService platformDepartments, IUnSysMemberService systemMembers,
            IUnSysTenantMemberService systemTenantMembers, IUnSysDepartmentService systemDepartments,
            AuthManageService auth, SystemManageService systems,
            ObjectMapper json) {
        this.apps = apps;
        this.events = events;
        this.logs = logs;
        this.platformLogs = platformLogs;
        this.platformAccounts = platformAccounts;
        this.platformMembers = platformMembers;
        this.platformDepartments = platformDepartments;
        this.systemMembers = systemMembers;
        this.systemTenantMembers = systemTenantMembers;
        this.systemDepartments = systemDepartments;
        this.auth = auth;
        this.systems = systems;
        this.json = json;
    }

    /** 查询平台应用。 */
    public AppView queryPlatform(String token, Long appId) {
        auth.requirePlatformAdmin(token);
        requirePositive(appId, "应用主键无效");
        return view(ownedPlatform(appId), null);
    }

    /** 按平台范围列出当前管理员可维护的应用，不返回任何密钥明文。 */
    public List<AppView> listPlatform(String token) {
        auth.requirePlatformAdmin(token);
        return apps.list(new LambdaQueryWrapper<UnApp>()
                        .eq(UnApp::getScopeType, "PLATFORM")
                        .orderByDesc(UnApp::getUpdateTime).orderByDesc(UnApp::getId))
                .stream().map(app -> view(app, null)).toList();
    }

    /** 查询系统租户应用。 */
    public AppView querySystem(String token, Long systemId, Long tenantId, Long appId) {
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        requirePositive(appId, "应用主键无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        return view(ownedSystem(context, appId), null);
    }

    /** 按系统租户范围列出应用，系统管理员只能看到本系统当前租户的应用。 */
    public List<AppView> listSystem(String token, Long systemId, Long tenantId) {
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        return apps.list(new LambdaQueryWrapper<UnApp>()
                        .eq(UnApp::getScopeType, "SYSTEM")
                        .eq(UnApp::getSystemId, context.systemId())
                        .eq(UnApp::getTenantId, context.tenantId())
                        .orderByDesc(UnApp::getUpdateTime).orderByDesc(UnApp::getId))
                .stream().map(app -> view(app, null)).toList();
    }

    /** 保存平台应用草稿。 */
    @Transactional(rollbackFor = Exception.class)
    public AppView savePlatform(String token, SaveRequest request) {
        UnPlatAccount account = auth.requirePlatformAdmin(token);
        validateSaveRequest(request);
        UnApp app = request.id() == null ? new UnApp() : ownedPlatformForUpdate(request.id());
        return save(app, request, null, account.getId(), "PLATFORM");
    }

    /** 保存系统租户应用草稿。 */
    @Transactional(rollbackFor = Exception.class)
    public AppView saveSystem(String token, Long systemId, Long tenantId, SaveRequest request) {
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        validateSaveRequest(request);
        UnApp app = request.id() == null ? new UnApp() : ownedSystemForUpdate(context, request.id());
        return save(app, request, context, context.accountId(), "SYSTEM");
    }

    /** 发布平台应用的授权快照。 */
    @Transactional(rollbackFor = Exception.class)
    public AppView publishPlatform(String token, Long appId) {
        UnPlatAccount account = auth.requirePlatformAdmin(token);
        requirePositive(appId, "应用主键无效");
        UnApp app = ownedPlatformForUpdate(appId);
        publish(app, null, account.getId());
        return view(app, null);
    }

    /** 发布系统租户应用的授权快照。 */
    @Transactional(rollbackFor = Exception.class)
    public AppView publishSystem(String token, Long systemId, Long tenantId, Long appId) {
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        requirePositive(appId, "应用主键无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnApp app = ownedSystemForUpdate(context, appId);
        publish(app, context, context.accountId());
        return view(app, null);
    }

    /** 保存应用事件草稿；事件发布后才会进入外部推送配置。 */
    @Transactional(rollbackFor = Exception.class)
    public EventView saveEventPlatform(String token, Long appId, EventRequest request) {
        UnPlatAccount account = auth.requirePlatformAdmin(token);
        requirePositive(appId, "应用主键无效");
        UnApp app = ownedPlatformForUpdate(appId);
        validateEventRequest(request);
        return saveEvent(app, request, null, account.getId());
    }

    /** 查询平台应用下的全部事件草稿和已发布配置。 */
    public List<EventView> listEventsPlatform(String token, Long appId) {
        auth.requirePlatformAdmin(token);
        requirePositive(appId, "应用主键无效");
        UnApp app = ownedPlatform(appId);
        return events.list(new LambdaQueryWrapper<UnAppEvent>()
                        .eq(UnAppEvent::getApplicationId, app.getId())
                        .orderByDesc(UnAppEvent::getUpdateTime).orderByDesc(UnAppEvent::getId))
                .stream().map(this::eventView).toList();
    }

    /** 保存系统租户应用事件草稿。 */
    @Transactional(rollbackFor = Exception.class)
    public EventView saveEventSystem(String token, Long systemId, Long tenantId,
                                     Long appId, EventRequest request) {
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        requirePositive(appId, "应用主键无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnApp app = ownedSystem(context, appId);
        validateEventRequest(request);
        return saveEvent(app, request, context, context.accountId());
    }

    /** 查询系统租户应用下的全部事件草稿和已发布配置。 */
    public List<EventView> listEventsSystem(String token, Long systemId, Long tenantId, Long appId) {
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        requirePositive(appId, "应用主键无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnApp app = ownedSystem(context, appId);
        return events.list(new LambdaQueryWrapper<UnAppEvent>()
                        .eq(UnAppEvent::getApplicationId, app.getId())
                        .orderByDesc(UnAppEvent::getUpdateTime).orderByDesc(UnAppEvent::getId))
                .stream().map(this::eventView).toList();
    }

    /** 发布平台应用事件快照。 */
    @Transactional(rollbackFor = Exception.class)
    public EventView publishEventPlatform(String token, Long appId, Long eventId) {
        UnPlatAccount account = auth.requirePlatformAdmin(token);
        requirePositive(appId, "应用主键无效");
        requirePositive(eventId, "事件主键无效");
        return publishEvent(ownedPlatformForUpdate(appId), eventId, null, account.getId());
    }

    /** 发布系统租户应用事件快照。 */
    @Transactional(rollbackFor = Exception.class)
    public EventView publishEventSystem(String token, Long systemId, Long tenantId,
                                        Long appId, Long eventId) {
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        requirePositive(appId, "应用主键无效");
        requirePositive(eventId, "事件主键无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        return publishEvent(ownedSystemForUpdate(context, appId), eventId, context, context.accountId());
    }

    private AppView save(UnApp app, SaveRequest request, SystemContext context,
                         Long accountId, String scopeType) {
        validateSaveRequest(request);
        if (!ACCESS_MODES.contains(request.accessMode().trim().toUpperCase()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用访问模式无效");
        validateCallbackUrl(request.callbackUrl());
        JsonNode config = request.config() == null ? json.createObjectNode() : object(request.config(), "应用授权配置");
        String issuedSecret = null;
        if (app.getId() == null) {
            app.setScopeType(scopeType);
            app.setSystemId(context == null ? null : context.systemId());
            app.setTenantId(context == null ? null : context.tenantId());
            app.setCreateAccountId(accountId);
            app.setCreateTime(LocalDateTime.now());
            app.setPublishedVersion(0);
            String secret = request.secret();
            if (secret == null || secret.isBlank()) secret = generateSecret();
            app.setSecretRef(reference(secret));
            app.setSecretVersion(1);
            issuedSecret = secret;
        } else {
            if (!app.getAppId().equals(request.appId().trim()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用标识创建后不能修改");
            if (request.secret() != null && !request.secret().isBlank()) {
                rotate(app, request.secret());
                issuedSecret = request.secret();
            }
        }
        app.setAppId(request.appId().trim());
        app.setName(request.name().trim());
        app.setDescription(request.description());
        app.setAccessMode(request.accessMode().trim().toUpperCase());
        app.setCallbackUrl(request.callbackUrl());
        app.setConfigJson(write(config));
        app.setStatus("DRAFT");
        app.setUpdateTime(LocalDateTime.now());
        try { apps.addOrUpdate(app); }
        catch (DuplicateKeyException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用标识已存在");
        }
        audit(context, accountId, "APP_SAVE", app.getId());
        return view(app, issuedSecret);
    }

    private void publish(UnApp app, SystemContext context, Long accountId) {
        JsonNode config = object(readJson(app.getConfigJson()), "应用授权配置");
        validatePermissions(config, app);
        app.setPublishedConfigJson(write(config));
        app.setPublishedVersion((app.getPublishedVersion() == null ? 0 : app.getPublishedVersion()) + 1);
        app.setStatus("PUBLISHED");
        app.setUpdateTime(LocalDateTime.now());
        apps.addOrUpdate(app);
        audit(context, accountId, "APP_PUBLISH", app.getId());
    }

    private EventView saveEvent(UnApp app, EventRequest request, SystemContext context, Long accountId) {
        validateEventRequest(request);
        JsonNode config = request.config() == null ? json.createObjectNode() : object(request.config(), "事件配置");
        validateEvent(request.eventCode(), request.resourceType(), request.callbackUrl(), config);
        validateEventScope(app, request.eventCode(), request.resourceType());
        UnAppEvent event = request.id() == null ? new UnAppEvent() : events.getOne(new LambdaQueryWrapper<UnAppEvent>()
                .eq(UnAppEvent::getId, request.id()).last("FOR UPDATE"), false);
        if (request.id() != null && event == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "应用事件不存在");
        if (event.getId() != null && !app.getId().equals(event.getApplicationId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "事件不属于当前应用");
        if (event.getId() == null) {
            event.setApplicationId(app.getId());
            event.setCreateTime(LocalDateTime.now());
        }
        event.setEventCode(request.eventCode().trim());
        event.setResourceType(request.resourceType().trim());
        event.setResourceId(request.resourceId());
        event.setCallbackUrl(request.callbackUrl());
        event.setConfigJson(write(config));
        event.setStatus("DRAFT");
        event.setUpdateTime(LocalDateTime.now());
        events.addOrUpdate(event);
        audit(context, accountId, "APP_EVENT_SAVE", event.getId());
        return eventView(event);
    }

    private EventView publishEvent(UnApp app, Long eventId, SystemContext context, Long accountId) {
        UnAppEvent event = events.getOne(new LambdaQueryWrapper<UnAppEvent>()
                .eq(UnAppEvent::getId, eventId).last("FOR UPDATE"), false);
        if (event == null || !app.getId().equals(event.getApplicationId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "应用事件不存在");
        JsonNode config = object(readJson(event.getConfigJson()), "事件配置");
        validateEvent(event.getEventCode(), event.getResourceType(), event.getCallbackUrl(), config);
        validateEventScope(app, event.getEventCode(), event.getResourceType());
        ObjectNode published = (ObjectNode) config.deepCopy();
        String callback = event.getCallbackUrl() == null || event.getCallbackUrl().isBlank()
                ? app.getCallbackUrl() : event.getCallbackUrl();
        if (callback == null || callback.isBlank())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件发布必须配置回调地址");
        published.put("callbackUrl", callback);
        published.put("eventCode", event.getEventCode());
        published.put("resourceType", event.getResourceType());
        if (event.getResourceId() != null) published.put("resourceId", event.getResourceId());
        event.setPublishedConfigJson(write(published));
        event.setStatus("PUBLISHED");
        event.setUpdateTime(LocalDateTime.now());
        events.addOrUpdate(event);
        audit(context, accountId, "APP_EVENT_PUBLISH", event.getId());
        return eventView(event);
    }

    /** 事件发布前固定协议和重试边界，避免运行态接受任意 URL 或无限重试配置。 */
    private void validateEvent(String eventCode, String resourceType, String callbackUrl, JsonNode config) {
        if (eventCode == null || !eventCode.trim().matches("[A-Za-z0-9._:-]{1,100}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件编码无效");
        if (resourceType == null || !resourceType.trim().matches("[A-Za-z0-9._:-]{1,40}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件资源类型无效");
        validateCallbackUrl(callbackUrl);
        if (config == null || !config.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件配置必须是 JSON 对象");
        JsonNode retry = config.get("retry");
        if (retry != null && (!retry.canConvertToInt() || retry.intValue() < 0 || retry.intValue() > 5))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件重试次数必须在0到5之间");
        JsonNode timeout = config.get("timeoutMs");
        if (timeout != null && (!timeout.canConvertToInt() || timeout.intValue() < 100
                || timeout.intValue() > 30000))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件超时时间必须在100到30000毫秒之间");
        JsonNode filters = config.get("filters");
        if (filters != null && !filters.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件过滤配置必须是对象");
    }

    /** 订阅类型与事件实际发出空间一致，避免保存可发布但永远收不到的跨空间事件。 */
    private void validateEventScope(UnApp app, String eventCode, String resourceType) {
        boolean platform = "PLATFORM".equalsIgnoreCase(app.getScopeType());
        boolean system = "SYSTEM".equalsIgnoreCase(app.getScopeType());
        if (!platform && !system)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用作用域无效");
        String expected;
        if (eventCode.startsWith("FLOW_")) expected = platform ? "PLATFORM_FLOW" : "FLOW";
        else if (eventCode.startsWith("EXAMINE_")) expected = platform ? "PLATFORM_EXAMINE" : "EXAMINE";
        else expected = "MODULE";
        if (platform && "MODULE".equals(expected) || !expected.equals(resourceType.trim()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,
                    "事件资源类型与应用作用域不匹配，期望 " + expected);
    }

    /** 回调地址只允许明确的 HTTP(S) 协议，不接受本地文件、脚本或带用户密码的地址。 */
    private void validateCallbackUrl(String callbackUrl) {
        if (callbackUrl == null || callbackUrl.isBlank()) return;
        if (callbackUrl.length() > 500) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件回调地址过长");
        try {
            URI uri = URI.create(callbackUrl.trim());
            if (!Set.of("http", "https").contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
                throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件回调地址无效");
        }
    }

    private void rotate(UnApp app, String secret) {
        app.setPreviousSecretRef(app.getSecretRef());
        app.setPreviousSecretExpireTime(LocalDateTime.now().plusHours(24));
        app.setSecretRef(reference(secret));
        app.setSecretVersion((app.getSecretVersion() == null ? 0 : app.getSecretVersion()) + 1);
    }

    /** 发布前固定授权引用的组织边界，避免失效账号或部门进入已生效快照。 */
    private void validatePermissions(JsonNode config, UnApp app) {
        validateSecurity(config);
        JsonNode permissions = config.get("permissions");
        if (permissions == null) permissions = config.get("resources");
        if (permissions == null || !permissions.isArray())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用发布配置必须包含 permissions 数组");
        Set<String> keys = new HashSet<>();
        for (JsonNode permission : permissions) {
            if (!permission.isObject() || text(permission, "resourceType") == null
                    || text(permission, "actionCode") == null)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用授权项必须包含资源类型和动作");
            String key = text(permission, "resourceType") + ":" + text(permission, "actionCode") + ":"
                    + String.valueOf(permission.get("resourceId"));
            if (!keys.add(key)) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用授权项重复");
            List<Long> accountIds = validateList(permission, "accountIds");
            List<Long> departmentIds = validateList(permission, "departmentIds");
            validateScopeReferences(app, accountIds, departmentIds);
        }
    }

    /** 发布前固定应用访问安全策略，运行时不能把错误配置静默降级为默认放行。 */
    private void validateSecurity(JsonNode config) {
        JsonNode security = config.get("security");
        if (security == null || security.isNull()) return;
        if (!security.isObject()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用安全配置必须是 JSON 对象");
        Set<String> supported = Set.of("signatureRequired", "timestampWindowSeconds",
                "ipAllowList", "rateLimitPerMinute");
        security.fieldNames().forEachRemaining(key -> {
            if (!supported.contains(key)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,
                    "应用安全配置包含未支持项: " + key);
        });
        JsonNode signature = security.get("signatureRequired");
        if (signature != null && !signature.isBoolean())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "signatureRequired 必须是开关值");
        JsonNode window = security.get("timestampWindowSeconds");
        if (window != null && (!window.canConvertToInt() || window.intValue() < 30 || window.intValue() > 900))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "timestampWindowSeconds 必须在30到900之间");
        JsonNode rate = security.get("rateLimitPerMinute");
        if (rate != null && (!rate.canConvertToInt() || rate.intValue() < 1 || rate.intValue() > 100000))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "rateLimitPerMinute 必须在1到100000之间");
        JsonNode ips = security.get("ipAllowList");
        if (ips != null) {
            if (!ips.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "ipAllowList 必须是数组");
            Set<String> values = new HashSet<>();
            for (JsonNode ip : ips) {
                if (!ip.isTextual() || ip.asText().isBlank() || ip.asText().length() > 64
                        || !ip.asText().equals(ip.asText().trim()) || !values.add(ip.asText()))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "ipAllowList 包含无效或重复地址");
            }
        }
    }

    private List<Long> validateList(JsonNode node, String key) {
        if (!node.has(key) || node.get(key).isNull()) return List.of();
        if (!node.get(key).isArray())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, key + " 必须是数组");
        Set<Long> ids = new HashSet<>();
        for (JsonNode item : node.get(key)) {
            if (!item.isIntegralNumber() || item.longValue() < 1 || !ids.add(item.longValue()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, key + " 必须是互不重复的正整数");
        }
        return ids.stream().sorted().toList();
    }

    /** 系统应用只能引用当前系统租户组织，平台应用只能引用平台组织。 */
    private void validateScopeReferences(UnApp app, List<Long> accountIds, List<Long> departmentIds) {
        if (accountIds.isEmpty() && departmentIds.isEmpty()) return;
        if ("PLATFORM".equalsIgnoreCase(app.getScopeType())) {
            Set<Long> activeAccounts = platformAccounts.list(new LambdaQueryWrapper<UnPlatAccount>()
                            .in(UnPlatAccount::getId, accountIds.isEmpty() ? List.of(-1L) : accountIds)
                            .eq(UnPlatAccount::getStatus, "ACTIVE"))
                    .stream().map(UnPlatAccount::getId).collect(java.util.stream.Collectors.toSet());
            Set<Long> activeMembers = platformMembers.list(new LambdaQueryWrapper<UnPlatMember>()
                            .in(UnPlatMember::getAccountId, accountIds.isEmpty() ? List.of(-1L) : accountIds)
                            .eq(UnPlatMember::getStatus, "ACTIVE"))
                    .stream().map(UnPlatMember::getAccountId).collect(java.util.stream.Collectors.toSet());
            Set<Long> activeDepartments = platformDepartments.list(new LambdaQueryWrapper<UnPlatDepartment>()
                            .in(UnPlatDepartment::getId, departmentIds.isEmpty() ? List.of(-1L) : departmentIds)
                            .eq(UnPlatDepartment::getStatus, "ACTIVE"))
                    .stream().map(UnPlatDepartment::getId).collect(java.util.stream.Collectors.toSet());
            if (!activeAccounts.containsAll(accountIds) || !activeMembers.containsAll(accountIds)
                    || !activeDepartments.containsAll(departmentIds))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台应用授权引用了失效人员或部门");
            return;
        }
        if (app.getSystemId() == null || app.getTenantId() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "系统应用缺少系统或租户范围");
        Set<Long> activeAccounts = platformAccounts.list(new LambdaQueryWrapper<UnPlatAccount>()
                        .in(UnPlatAccount::getId, accountIds.isEmpty() ? List.of(-1L) : accountIds)
                        .eq(UnPlatAccount::getStatus, "ACTIVE"))
                .stream().map(UnPlatAccount::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> activeMemberIds = systemMembers.list(new LambdaQueryWrapper<UnSysMember>()
                        .eq(UnSysMember::getSystemId, app.getSystemId()).eq(UnSysMember::getStatus, "ACTIVE")
                        .in(UnSysMember::getAccountId, accountIds.isEmpty() ? List.of(-1L) : accountIds))
                .stream().map(UnSysMember::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> activeOwners = systemTenantMembers.list(new LambdaQueryWrapper<UnSysTenantMember>()
                        .eq(UnSysTenantMember::getSystemId, app.getSystemId())
                        .eq(UnSysTenantMember::getTenantId, app.getTenantId())
                        .eq(UnSysTenantMember::getStatus, "ACTIVE")
                        .in(UnSysTenantMember::getMemberId, activeMemberIds.isEmpty() ? List.of(-1L) : activeMemberIds))
                .stream().map(UnSysTenantMember::getMemberId).collect(java.util.stream.Collectors.toSet());
        Set<Long> activeDepartments = systemDepartments.list(new LambdaQueryWrapper<UnSysDepartment>()
                        .eq(UnSysDepartment::getSystemId, app.getSystemId())
                        .eq(UnSysDepartment::getTenantId, app.getTenantId())
                        .eq(UnSysDepartment::getStatus, "ACTIVE")
                        .in(UnSysDepartment::getId, departmentIds.isEmpty() ? List.of(-1L) : departmentIds))
                .stream().map(UnSysDepartment::getId).collect(java.util.stream.Collectors.toSet());
        if (!activeAccounts.containsAll(accountIds) || activeOwners.size() != accountIds.size()
                || !activeDepartments.containsAll(departmentIds))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "系统应用授权引用了当前租户外或已失效人员、部门");
    }

    private UnApp ownedPlatform(Long id) {
        UnApp app = apps.queryById(id);
        if (app == null || !"PLATFORM".equalsIgnoreCase(app.getScopeType()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台应用不存在");
        return app;
    }

    private UnApp ownedSystem(SystemContext context, Long id) {
        UnApp app = apps.queryById(id);
        if (app == null || !"SYSTEM".equalsIgnoreCase(app.getScopeType())
                || !context.systemId().equals(app.getSystemId())
                || !context.tenantId().equals(app.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "系统应用不存在");
        return app;
    }

    /** 写入平台应用前锁定应用行，串行化密钥轮换和发布版本递增。 */
    private UnApp ownedPlatformForUpdate(Long id) {
        UnApp app = apps.getOne(new LambdaQueryWrapper<UnApp>()
                .eq(UnApp::getId, id).last("FOR UPDATE"), false);
        if (app == null || !"PLATFORM".equalsIgnoreCase(app.getScopeType()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台应用不存在");
        return app;
    }

    /** 写入系统应用前锁定应用行，串行化密钥轮换和发布版本递增。 */
    private UnApp ownedSystemForUpdate(SystemContext context, Long id) {
        UnApp app = apps.getOne(new LambdaQueryWrapper<UnApp>()
                .eq(UnApp::getId, id).last("FOR UPDATE"), false);
        if (app == null || !"SYSTEM".equalsIgnoreCase(app.getScopeType())
                || !context.systemId().equals(app.getSystemId())
                || !context.tenantId().equals(app.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "系统应用不存在");
        return app;
    }

    private AppView view(UnApp app, String issuedSecret) {
        return new AppView(app.getId(), app.getScopeType(), app.getSystemId(), app.getTenantId(), app.getAppId(),
                app.getName(), app.getDescription(), app.getAccessMode(), app.getCallbackUrl(), app.getStatus(),
                app.getSecretVersion(), app.getPublishedVersion(), readJson(app.getConfigJson()),
                readJson(app.getPublishedConfigJson()), issuedSecret, app.getUpdateTime());
    }

    private EventView eventView(UnAppEvent event) {
        return new EventView(event.getId(), event.getApplicationId(), event.getEventCode(), event.getResourceType(),
                event.getResourceId(), event.getCallbackUrl(), event.getStatus(), readJson(event.getConfigJson()),
                readJson(event.getPublishedConfigJson()), event.getUpdateTime());
    }

    private JsonNode object(JsonNode node, String label) {
        if (node == null || !node.isObject())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, label + "必须是 JSON 对象");
        return node;
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) return json.createObjectNode();
        try { return json.readTree(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用配置格式无效"); }
    }

    private String write(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用配置无法保存"); }
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node.get(key);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private String generateSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private String reference(String secret) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "应用密钥摘要无法生成", ex); }
    }

    /** 服务层也执行保存请求校验，避免绕过 HTTP Bean Validation 后写入不可发布的应用配置。 */
    private void validateSaveRequest(SaveRequest request) {
        if (request == null || blankOrTooLong(request.appId(), 100)
                || !request.appId().trim().matches("[A-Za-z0-9._:-]+")
                || blankOrTooLong(request.name(), 120)
                || blankOrTooLong(request.accessMode(), 30))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用资料无效");
        if (request.description() != null && request.description().length() > 500)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用说明过长");
        if (request.secret() != null && (request.secret().length() < 16 || request.secret().length() > 200))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用密钥长度无效");
    }

    /** 服务层校验事件主键和编码，发布事件时不会把非法资源引用写入草稿。 */
    private void validateEventRequest(EventRequest request) {
        if (request == null || blankOrTooLong(request.eventCode(), 100)
                || !request.eventCode().trim().matches("[A-Za-z0-9._:-]+")
                || blankOrTooLong(request.resourceType(), 40)
                || !request.resourceType().trim().matches("[A-Za-z0-9._:-]+"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用事件资料无效");
        if (request.resourceId() != null && request.resourceId() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件资源主键无效");
        if (request.callbackUrl() != null && request.callbackUrl().length() > 500)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "事件回调地址过长");
    }

    private boolean blankOrTooLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private void requirePositive(Long value, String message) {
        if (value == null || value < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, message);
    }

    private void audit(SystemContext context, Long accountId, String action, Long objectId) {
        if (context == null) {
            UnPlatLogOpt log = new UnPlatLogOpt();
            log.setAccountId(accountId);
            log.setActionCode(action);
            log.setObjectType("APP");
            log.setObjectId(objectId);
            log.setCreateTime(LocalDateTime.now());
            platformLogs.addOrUpdate(log);
            return;
        }
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId());
        log.setTenantId(context.tenantId());
        log.setAccountId(accountId);
        log.setActionCode(action);
        log.setObjectType("APP");
        log.setObjectId(objectId);
        logs.addOrUpdate(log);
    }
}
