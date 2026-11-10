package com.unique.unexamine.app.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.app.base.entity.UnApp;
import com.unique.unexamine.app.base.entity.UnAppCall;
import com.unique.unexamine.app.base.service.IUnAppCallService;
import com.unique.unexamine.app.base.service.IUnAppService;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.app.manage.dto.AppAccessDto.AccessRequest;
import com.unique.unexamine.app.manage.dto.AppAccessDto.Decision;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.entity.UnPlatDepartment;
import com.unique.unexamine.plat.base.entity.UnPlatMember;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.plat.base.service.IUnPlatDepartmentService;
import com.unique.unexamine.plat.base.service.IUnPlatMemberService;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 应用凭据及发布授权快照的统一校验服务。
 *
 * <p>应用表只保存安全引用，当前实现约定安全引用为 {@code sha256:<64位十六进制摘要>}；
 * 请求幂等键与请求摘要写入应用调用表，重试不能复用不同参数。</p>
 */
@Service
public class AppAccessService {
    private final IUnAppService apps;
    private final IUnAppCallService calls;
    private final ObjectMapper json;
    private final IUnPlatAccountService platformAccounts;
    private final IUnPlatMemberService platformMembers;
    private final IUnPlatDepartmentService platformDepartments;
    private final IUnSysMemberService systemMembers;
    private final IUnSysTenantMemberService systemTenantMembers;
    private final IUnSysDepartmentService systemDepartments;
    /** 单体部署下的应用调用窗口；实例重启后窗口自然清空，幂等记录仍由数据库保留。 */
    private final ConcurrentHashMap<String, Deque<Long>> rateWindows = new ConcurrentHashMap<>();

    /** 注入应用、组织状态与调用审计基础服务。 */
    public AppAccessService(IUnAppService apps, IUnAppCallService calls, ObjectMapper json,
                            IUnPlatAccountService platformAccounts, IUnPlatMemberService platformMembers,
                            IUnPlatDepartmentService platformDepartments, IUnSysMemberService systemMembers,
                            IUnSysTenantMemberService systemTenantMembers, IUnSysDepartmentService systemDepartments) {
        this.apps = apps;
        this.calls = calls;
        this.json = json;
        this.platformAccounts = platformAccounts;
        this.platformMembers = platformMembers;
        this.platformDepartments = platformDepartments;
        this.systemMembers = systemMembers;
        this.systemTenantMembers = systemTenantMembers;
        this.systemDepartments = systemDepartments;
    }

    /** 校验外部调用；Flow、examine 和其他受控入口共用此方法。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BaseException.class)
    public Decision authorizeExternal(AccessRequest request) {
        return authorize(request, true, true, null);
    }

    /** 系统记录待办先授权资源动作，逐条记录的负责人范围由查询服务按真实记录过滤。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BaseException.class)
    public Decision authorizeExternalTodo(AccessRequest request) {
        if (request == null || !"TODO".equals(request.actionCode())
                || !Set.of("FLOW", "EXAMINE").contains(request.resourceType())
                || request.systemId() == null || request.tenantId() == null)
            throw invalidRequest("系统外部待办授权资料无效");
        return authorize(request, true, true, null, true);
    }

    /** 固定本次查询的当前发布授权和有效组织范围；记录负责人由服务端读取，调用方不能指定。 */
    public BiPredicate<Long, Long> externalTodoRecordScope(Decision decision, AccessRequest request) {
        if (decision == null || !decision.allowed() || request == null
                || !"TODO".equals(request.actionCode())
                || !Objects.equals(decision.appId(), request.appId()))
            throw invalidRequest("系统外部待办上下文无效");
        UnApp app = apps.getById(decision.applicationId());
        if (app == null || !("PUBLISHED".equals(app.getStatus()) || "ACTIVE".equals(app.getStatus())))
            throw denied("应用不存在或未启用");
        checkScope(app, request);
        try {
            Map<String, Object> config = json.readValue(app.getPublishedConfigJson(), new TypeReference<>() {});
            Object raw = config.get("permissions");
            if (!(raw instanceof List<?>)) raw = config.get("resources");
            if (!(raw instanceof List<?> grants)) throw denied("应用未获授权查询待办");
            List<BiPredicate<Long, Long>> rules = new ArrayList<>();
            for (Object item : grants) {
                if (!(item instanceof Map<?, ?> grant)
                        || !request.resourceType().equals(String.valueOf(grant.get("resourceType")))
                        || !"TODO".equals(String.valueOf(grant.get("actionCode")))) continue;
                Object resource = grant.get("resourceId");
                if (resource != null && !"*".equals(String.valueOf(resource))
                        && !String.valueOf(request.resourceId()).equals(String.valueOf(resource))) continue;
                List<Long> accountIds = scopeIds(grant.get("accountIds"));
                List<Long> departmentIds = scopeIds(grant.get("departmentIds"));
                Set<Long> accounts = accountIds.isEmpty() ? Set.of() : activeAccountIds(app, accountIds);
                Set<Long> departments = Set.copyOf(departmentIds);
                if (!departmentIds.isEmpty() && !activeDepartmentIds(app, departmentIds).containsAll(departments))
                    throw denied("应用授权部门已失效，请修正发布配置");
                if (!accountIds.isEmpty() && accounts.isEmpty())
                    throw denied("应用授权人员已全部失效，请修正发布配置");
                Map<String, Boolean> validPairs = new java.util.HashMap<>();
                rules.add((owner, dept) -> (accountIds.isEmpty() || owner != null && accounts.contains(owner))
                        && (departmentIds.isEmpty() || dept != null && departments.contains(dept))
                        && (accountIds.isEmpty() || departmentIds.isEmpty() || validPairs.computeIfAbsent(
                                owner + ":" + dept, ignored -> activeAccountInDepartment(app, owner, dept))));
            }
            if (rules.isEmpty()) throw denied("应用未获授权查询待办");
            return (owner, dept) -> rules.stream().anyMatch(rule -> rule.test(owner, dept));
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布授权快照损坏", ex);
        }
    }

    /**
     * 已完成记录边界预检后的完整授权；clientPayload 是预检使用的调用方原始参数，
     * 验签不包含服务端后来补入的真实负责人，幂等摘要仍包含真实负责人；限流不重复计数。
     */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BaseException.class)
    public Decision authorizeExternalAfterPreflight(AccessRequest request, Map<String, Object> clientPayload) {
        if (clientPayload == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部记录调用缺少原始业务参数");
        return authorize(request, true, false, clientPayload);
    }

    /** 校验内部调用；内部应用同样必须经过发布授权快照。 */
    @Transactional(rollbackFor = Exception.class, noRollbackFor = BaseException.class)
    public Decision authorizeInternal(AccessRequest request) {
        return authorize(request, false, true, null);
    }

    /**
     * 授权记录与实际业务结果分开提交；运行服务失败回滚后仍保留本次调用的最终失败码。
     * 结果摘要只保存授权决定，不写入审批意见、业务字段或外部响应正文。
     */
    public <T> T executeExternal(Decision decision, String requestId, Supplier<T> operation) {
        if (decision == null || !decision.allowed() || requestId == null || operation == null)
            throw invalidRequest("应用业务调用上下文无效");
        long started = System.nanoTime();
        T result;
        try {
            result = operation.get();
        } catch (RuntimeException ex) {
            finishCall(decision, requestId, "HTTP_" + failureStatus(ex), started);
            throw ex;
        }
        finishCall(decision, requestId, "SUCCESS", started);
        return result;
    }

    /** 审计结果码与统一异常入口的 HTTP 状态保持一致。 */
    private int failureStatus(RuntimeException ex) {
        if (ex instanceof BaseException business) return business.getCode();
        if (ex instanceof ResponseStatusException status) return status.getStatusCode().value();
        if (ex instanceof IllegalArgumentException) return 400;
        if (ex instanceof NoSuchElementException) return 404;
        if (ex instanceof DataIntegrityViolationException) return 409;
        return 500;
    }

    /** 更新原调用审计，不改变用于同键异参检查的请求摘要和授权决定。 */
    private void finishCall(Decision decision, String requestId, String resultCode, long started) {
        long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        boolean updated = calls.lambdaUpdate()
                .eq(UnAppCall::getApplicationId, decision.applicationId())
                .eq(UnAppCall::getRequestId, requestId)
                .isNull(UnAppCall::getDeletedTime)
                .set(UnAppCall::getResultCode, resultCode)
                .set(UnAppCall::getDurationMs, (int) Math.min(Integer.MAX_VALUE, millis))
                .update();
        // MySQL 在重放时可能因结果码和毫秒值均未变化而报告零行更新。
        if (!updated && calls.count(new LambdaQueryWrapper<UnAppCall>()
                .eq(UnAppCall::getApplicationId, decision.applicationId())
                .eq(UnAppCall::getRequestId, requestId)
                .isNull(UnAppCall::getDeletedTime)) == 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用调用审计记录不存在");
    }

    /**
     * 外部业务记录边界查询前的无副作用预检。
     *
     * <p>预检只确认应用仍启用、密钥、访问模式、系统租户范围及请求安全参数，
     * 不写入调用幂等记录，但会把本次有效请求计入限流额度。业务记录负责人补入请求后仍必须再次执行完整授权。</p>
     */
    public void verifyExternalRequest(AccessRequest request) {
        validateRequest(request);
        UnApp app = apps.getOne(new LambdaQueryWrapper<UnApp>().eq(UnApp::getAppId, request.appId())
                .isNull(UnApp::getDeletedTime), false);
        if (app == null || !("PUBLISHED".equals(app.getStatus()) || "ACTIVE".equals(app.getStatus())))
            throw denied("应用不存在或未启用");
        checkCredential(app, request.secret());
        checkMode(app, true);
        checkSecurity(app, request, true, null);
        checkScope(app, request);
    }

    /**
     * 外部运行请求的签名业务参数包含完整请求体，去掉密钥、签名和已单独签署的时间戳。
     * 原有字段值仍位于顶层供授权范围判断；完整请求置于保留键中，节点、记录和意见不能被替换后复用签名或请求号。
     */
    public Map<String, Object> externalPayload(Map<String, Object> values, String externalId, Object request) {
        if (request == null) throw invalidRequest("外部业务请求不能为空");
        Map<String, Object> payload = new LinkedHashMap<>(values == null ? Map.of() : values);
        payload.put("__externalId", externalId);
        try {
            Map<String, Object> business = json.convertValue(request, new TypeReference<LinkedHashMap<String, Object>>() {});
            business.remove("secret");
            business.remove("signature");
            business.remove("timestamp");
            payload.put("__businessRequest", business);
            return payload;
        } catch (IllegalArgumentException ex) {
            throw invalidRequest("外部业务参数无法编码");
        }
    }

    private Decision authorize(AccessRequest request, boolean external, boolean consumeRateLimit,
                               Map<String, Object> clientPayload) {
        return authorize(request, external, consumeRateLimit, clientPayload, false);
    }

    private Decision authorize(AccessRequest request, boolean external, boolean consumeRateLimit,
                               Map<String, Object> clientPayload, boolean deferRecordScope) {
        validateRequest(request);
        // 应用行锁把“读取幂等记录—写入调用审计”串行化，避免同一 requestId 并发产生两次授权结果。
        UnApp app = apps.getOne(new LambdaQueryWrapper<UnApp>().eq(UnApp::getAppId, request.appId())
                .isNull(UnApp::getDeletedTime).last("FOR UPDATE"), false);
        // 管理域发布后的应用状态为 PUBLISHED；兼容已有 ACTIVE 状态的已启用应用。
        if (app == null || !("PUBLISHED".equals(app.getStatus()) || "ACTIVE".equals(app.getStatus())))
            throw denied("应用不存在或未启用");
        String hash = hash(request);
        UnAppCall previous = calls.getOne(new LambdaQueryWrapper<UnAppCall>().eq(UnAppCall::getApplicationId, app.getId())
                .eq(UnAppCall::getRequestId, request.requestId()), false);
        if (previous != null) {
            // 幂等重放也必须重新校验凭据和安全边界；幂等只复用结果，不能变成绕过认证的入口。
            checkCredential(app, request.secret());
            checkMode(app, external);
            checkSecurity(app, request, false, clientPayload);
            checkScope(app, request);
            if (!Objects.equals(previous.getRequestHash(), hash)) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                    "相同请求幂等标识对应的参数已变化");
            Decision replay = readDecision(previous.getResponseJson());
            if (!replay.allowed()) throw accessFailure(replay.resultCode());
            // 历史幂等结果不能延续已撤销的资源或组织范围授权，重放仍以当前发布快照判定。
            if (!matchesPublishedGrant(app, request, deferRecordScope)) throw denied("应用未获授权执行该资源动作");
            return replay;
        }
        String resultCode = "SUCCESS";
        boolean allowed = true;
        int credentialVersion = app.getSecretVersion() == null ? 1 : app.getSecretVersion();
        BaseException failure = null;
        try {
            credentialVersion = checkCredential(app, request.secret());
            checkMode(app, external);
            checkSecurity(app, request, consumeRateLimit, clientPayload);
            checkScope(app, request);
            if (!matchesPublishedGrant(app, request, deferRecordScope)) { allowed = false; resultCode = "FORBIDDEN"; }
        } catch (BaseException ex) {
            allowed = false;
            resultCode = ex.getCode() == SystemCodeEnum.SYSTEM_NOT_LOGIN.getCode()
                    ? "UNAUTHORIZED" : "FORBIDDEN";
            failure = ex;
        }
        Decision decision = new Decision(app.getId(), app.getAppId(), allowed, resultCode,
                credentialVersion, app.getPublishedVersion(), app.getSystemId(), app.getTenantId());
        saveCall(app, request, hash, decision);
        if (!allowed) throw failure == null ? accessFailure(resultCode) : failure;
        return decision;
    }

    /** 幂等重放只保存结果编码，因此按编码恢复认证或授权失败的 HTTP 语义。 */
    private BaseException accessFailure(String resultCode) {
        if ("UNAUTHORIZED".equals(resultCode))
            return new BaseException(SystemCodeEnum.SYSTEM_NOT_LOGIN, "应用密钥无效");
        return new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "应用未获授权执行该资源动作");
    }

    /** 密钥只与安全引用做恒定时间摘要比较，不把明文写入日志或数据库。 */
    private int checkCredential(UnApp app, String secret) {
        String reference = app.getSecretRef();
        if (reference == null || !reference.startsWith("sha256:")) throw denied("应用密钥引用不可用");
        String actual = sha256(secret);
        if (matches(reference, actual)) return app.getSecretVersion() == null ? 1 : app.getSecretVersion();
        if (app.getPreviousSecretRef() != null && app.getPreviousSecretExpireTime() != null
                && !app.getPreviousSecretExpireTime().isBefore(LocalDateTime.now())
                && matches(app.getPreviousSecretRef(), actual))
            return Math.max(1, (app.getSecretVersion() == null ? 1 : app.getSecretVersion()) - 1);
        throw new BaseException(SystemCodeEnum.SYSTEM_NOT_LOGIN, "应用密钥无效");
    }

    private boolean matches(String reference, String actual) {
        if (!reference.startsWith("sha256:")) return false;
        String expected = reference.substring("sha256:".length()).toLowerCase();
        return expected.length() == actual.length()
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), actual.getBytes(StandardCharsets.US_ASCII));
    }

    private void checkMode(UnApp app, boolean external) {
        String mode = app.getAccessMode() == null ? "" : app.getAccessMode().toUpperCase();
        boolean valid = external ? SetMode.external(mode) : SetMode.internal(mode);
        if (!valid) throw denied("应用访问模式不允许当前入口");
    }

    private void checkScope(UnApp app, AccessRequest request) {
        String resourceType = request.resourceType().toUpperCase(Locale.ROOT);
        if ("SYSTEM".equalsIgnoreCase(app.getScopeType())) {
            if (resourceType.startsWith("PLATFORM_")) throw denied("系统应用不能调用平台资源");
            if (request.systemId() == null || !Objects.equals(app.getSystemId(), request.systemId()))
                throw denied("应用不属于目标业务系统");
            if (request.tenantId() == null || !Objects.equals(app.getTenantId(), request.tenantId()))
                throw denied("应用不属于目标租户");
            return;
        }
        // 平台应用只能开放平台资源；系统资源必须使用目标系统自己发布的应用。
        if ("PLATFORM".equalsIgnoreCase(app.getScopeType())
                && request.systemId() == null && request.tenantId() == null
                && !Set.of("MODULE", "FLOW", "EXAMINE").contains(resourceType)) return;
        throw denied("应用作用域与目标上下文不匹配");
    }

    /** 服务也可能被内部任务直接调用，因此不能只依赖 Controller 的 Bean Validation。 */
    private void validateRequest(AccessRequest request) {
        if (request == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用访问请求不能为空");
        if (blankOrTooLong(request.appId(), 100) || !request.appId().matches("[A-Za-z0-9._:-]+"))
            throw invalidRequest("应用标识无效");
        if (blankOrTooLong(request.secret(), 512)) throw invalidRequest("应用密钥无效");
        if (blankOrTooLong(request.resourceType(), 80) || !request.resourceType().matches("[A-Za-z0-9._:-]+"))
            throw invalidRequest("资源类型无效");
        if (blankOrTooLong(request.actionCode(), 80) || !request.actionCode().matches("[A-Za-z0-9._:-]+"))
            throw invalidRequest("资源动作无效");
        if (blankOrTooLong(request.requestId(), 100) || !request.requestId().matches("[A-Za-z0-9._:-]+"))
            throw invalidRequest("请求幂等标识无效");
        if (positiveOrNull(request.resourceId())) throw invalidRequest("资源主键无效");
        if (positiveOrNull(request.systemId())) throw invalidRequest("目标系统主键无效");
        if (positiveOrNull(request.tenantId())) throw invalidRequest("目标租户主键无效");
        // 外部运行入口在最多100个业务字段之外补充身份与完整请求摘要两个保留键。
        if (request.payload() != null && request.payload().size() > 102)
            throw invalidRequest("请求业务参数过多");
        if (request.timestamp() != null && (request.timestamp() < 1 || request.timestamp() > 9_999_999_999L))
            throw invalidRequest("请求时间戳无效");
        if (request.signature() != null && request.signature().length() > 256)
            throw invalidRequest("请求签名过长");
    }

    private boolean blankOrTooLong(String value, int maxLength) {
        return value == null || value.isBlank() || value.length() > maxLength;
    }

    private boolean positiveOrNull(Long value) {
        return value != null && value < 1;
    }

    private BaseException invalidRequest(String message) {
        return new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, message);
    }

    /** 按已发布安全配置执行签名、时间戳、IP 白名单和单实例限流。 */
    private void checkSecurity(UnApp app, AccessRequest request, boolean consumeRateLimit,
                               Map<String, Object> clientPayload) {
        SecurityConfig security = security(app);
        String ip = request.callerIp();
        if (!security.ipAllowList().isEmpty() && (ip == null || !security.ipAllowList().contains(ip)))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "调用来源地址未被应用允许");
        if (security.signatureRequired() || request.signature() != null) {
            if (request.timestamp() == null || request.timestamp() < 1)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "签名请求必须提供 Unix 秒时间戳");
            long now = Instant.now().getEpochSecond();
            if (Math.abs(now - request.timestamp()) > security.timestampWindowSeconds())
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "请求时间戳已过期或尚未生效");
            if (request.signature() == null || request.signature().isBlank())
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "应用请求签名无效");
            String expected = hmac(request.secret(), canonical(request, clientPayload));
            String actual = request.signature().trim().toLowerCase();
            if (actual.length() != expected.length()
                    || !MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII),
                    expected.getBytes(StandardCharsets.US_ASCII)))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "应用请求签名无效");
        }
        if (consumeRateLimit && security.rateLimitPerMinute() != null)
            consumeRate(app.getAppId(), security.rateLimitPerMinute());
    }

    /** 发布快照损坏时拒绝调用，不能把安全配置异常降级为默认放行。 */
    private SecurityConfig security(UnApp app) {
        if (app.getPublishedConfigJson() == null || app.getPublishedConfigJson().isBlank())
            return SecurityConfig.DEFAULT;
        try {
            JsonNode root = json.readTree(app.getPublishedConfigJson());
            JsonNode node = root == null ? null : root.get("security");
            if (node == null || node.isNull()) return SecurityConfig.DEFAULT;
            if (!node.isObject()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布安全配置损坏");
            Set<String> supported = Set.of("signatureRequired", "timestampWindowSeconds",
                    "ipAllowList", "rateLimitPerMinute");
            node.fieldNames().forEachRemaining(key -> {
                if (!supported.contains(key)) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                        "应用发布安全配置包含未支持项: " + key);
            });
            Set<String> ips = new HashSet<>();
            JsonNode allow = node.get("ipAllowList");
            if (allow != null && !allow.isArray())
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布安全配置损坏");
            if (allow != null) allow.forEach(item -> {
                if (!item.isTextual() || item.asText().isBlank() || !item.asText().equals(item.asText().trim()))
                    throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布安全配置损坏");
                ips.add(item.asText());
            });
            Integer rate = node.has("rateLimitPerMinute") ? node.get("rateLimitPerMinute").asInt() : null;
            int window = node.has("timestampWindowSeconds") ? node.get("timestampWindowSeconds").asInt(300) : 300;
            if (node.has("signatureRequired") && !node.get("signatureRequired").isBoolean())
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布安全配置损坏");
            if (window < 30 || window > 900 || rate != null && (rate < 1 || rate > 100000))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布安全配置损坏");
            return new SecurityConfig(node.path("signatureRequired").asBoolean(false), window,
                    ips, rate);
        } catch (BaseException ex) { throw ex; }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布安全配置损坏", ex); }
        catch (RuntimeException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布安全配置损坏", ex); }
    }

    private String canonical(AccessRequest request, Map<String, Object> clientPayload) {
        // 仅外部记录二次授权使用预检时的原始参数；普通入口必须签署调用方提交的负责人字段。
        Map<String, Object> signedPayload = clientPayload == null ? request.payload() : clientPayload;
        Map<String, Object> signed = new LinkedHashMap<>();
        signed.put("resourceType", request.resourceType());
        signed.put("resourceId", request.resourceId());
        signed.put("actionCode", request.actionCode());
        signed.put("systemId", request.systemId());
        signed.put("tenantId", request.tenantId());
        signed.put("payload", signedPayload);
        return request.appId() + "\n" + request.requestId() + "\n" + request.resourceType() + "\n"
                + String.valueOf(request.resourceId()) + "\n" + request.actionCode() + "\n"
                + String.valueOf(request.systemId()) + "\n" + String.valueOf(request.tenantId()) + "\n"
                + sha256(write(signed)) + "\n" + request.timestamp();
    }

    private String hmac(String secret, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "应用请求签名计算失败", ex); }
    }

    private void consumeRate(String appId, int limit) {
        long now = System.currentTimeMillis();
        Deque<Long> window = rateWindows.computeIfAbsent(appId, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() <= now - 60_000L) window.removeFirst();
            if (window.size() >= limit) throw new BaseException(SystemCodeEnum.SYSTEM_TOO_MANY, "应用调用频率超过限制");
            window.addLast(now);
        }
    }

    private record SecurityConfig(boolean signatureRequired, int timestampWindowSeconds,
                                  Set<String> ipAllowList, Integer rateLimitPerMinute) {
        private static final SecurityConfig DEFAULT = new SecurityConfig(false, 300, Set.of(), null);
    }

    /** 只读取发布快照，草稿授权不能直接对外生效。支持 resourceId 为空的资源级授权。 */
    private boolean matchesPublishedGrant(UnApp app, AccessRequest request) {
        return matchesPublishedGrant(app, request, false);
    }

    private boolean matchesPublishedGrant(UnApp app, AccessRequest request, boolean deferRecordScope) {
        if (app.getPublishedConfigJson() == null || app.getPublishedConfigJson().isBlank()) return false;
        try {
            Map<String, Object> root = json.readValue(app.getPublishedConfigJson(), new TypeReference<>() {});
            Object raw = root.get("permissions");
            if (!(raw instanceof List<?>)) raw = root.get("resources");
            if (!(raw instanceof List<?> grants)) return false;
            return grants.stream().filter(Map.class::isInstance).map(Map.class::cast).anyMatch(grant -> {
                String type = String.valueOf(grant.get("resourceType"));
                String action = String.valueOf(grant.get("actionCode"));
                Object resource = grant.get("resourceId");
                return request.resourceType().equals(type) && request.actionCode().equals(action)
                        && (resource == null || "*".equals(String.valueOf(resource))
                        || String.valueOf(request.resourceId()).equals(String.valueOf(resource)))
                        && (deferRecordScope || scopeMatches(app, grant, request));
            });
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布授权快照损坏", ex);
        }
    }

    /** 应用授权中的人员/部门范围只对本次资源请求携带的负责人上下文生效。 */
    private boolean scopeMatches(UnApp app, Map<?, ?> grant, AccessRequest request) {
        List<Long> accountIds = scopeIds(grant.get("accountIds"));
        List<Long> departmentIds = scopeIds(grant.get("departmentIds"));
        // 部门是配置边界，任一已失效就阻断当前授权，要求管理员先修正发布配置。
        if (!departmentIds.isEmpty() && !activeDepartmentIds(app, departmentIds).containsAll(departmentIds))
            throw denied("应用授权部门已失效，请修正发布配置");
        if (request.payload() == null)
            return accountIds.isEmpty() && departmentIds.isEmpty();
        Long ownerAccountId = number(request.payload().get("ownerAccountId"));
        Long ownerDeptId = number(request.payload().get("ownerDeptId"));
        // 人员状态变化只排除失效人员，仍保留同一授权项中的其他有效人员。
        Set<Long> validAccounts = accountIds.isEmpty() ? Set.of() : activeAccountIds(app, accountIds);
        boolean accountMatches = accountIds.isEmpty()
                || (ownerAccountId != null && validAccounts.contains(ownerAccountId));
        boolean departmentMatches = departmentIds.isEmpty()
                || (ownerDeptId != null && departmentIds.contains(ownerDeptId));
        if (!accountIds.isEmpty() && validAccounts.isEmpty())
            throw denied("应用授权人员已全部失效，请修正发布配置");
        if (!accountMatches || !departmentMatches) return false;
        // 同时携带人员和部门时，必须验证二者属于同一条有效成员关系，不能拼接两个独立授权范围。
        return ownerAccountId == null || ownerDeptId == null
                || activeAccountInDepartment(app, ownerAccountId, ownerDeptId);
    }

    /** 校验请求中的负责人账号与部门来自同一有效的平台或租户成员关系。 */
    private boolean activeAccountInDepartment(UnApp app, Long accountId, Long departmentId) {
        if ("PLATFORM".equalsIgnoreCase(app.getScopeType())) {
            return platformMembers.list(new LambdaQueryWrapper<UnPlatMember>()
                            .eq(UnPlatMember::getAccountId, accountId)
                            .eq(UnPlatMember::getDepartmentId, departmentId)
                            .eq(UnPlatMember::getStatus, "ACTIVE"))
                    .stream().findAny().isPresent();
        }
        List<Long> memberIds = systemMembers.list(new LambdaQueryWrapper<UnSysMember>()
                        .eq(UnSysMember::getSystemId, app.getSystemId())
                        .eq(UnSysMember::getStatus, "ACTIVE")
                        .eq(UnSysMember::getAccountId, accountId))
                .stream().map(UnSysMember::getId).toList();
        return !memberIds.isEmpty() && systemTenantMembers.list(new LambdaQueryWrapper<UnSysTenantMember>()
                        .eq(UnSysTenantMember::getSystemId, app.getSystemId())
                        .eq(UnSysTenantMember::getTenantId, app.getTenantId())
                        .eq(UnSysTenantMember::getDepartmentId, departmentId)
                        .eq(UnSysTenantMember::getStatus, "ACTIVE")
                        .in(UnSysTenantMember::getMemberId, memberIds))
                .stream().findAny().isPresent();
    }

    /** 读取发布快照中的正整数组织 ID，非法快照不能静默放大授权范围。 */
    private List<Long> scopeIds(Object configured) {
        if (!(configured instanceof List<?> list) || list.isEmpty()) return List.of();
        Set<Long> ids = new HashSet<>();
        for (Object value : list) {
            Long id = number(value);
            if (id == null || id < 1 || !ids.add(id))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用发布授权快照中的组织 ID 无效");
        }
        return ids.stream().sorted().toList();
    }

    private Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ex) { return null; }
    }

    /** 按应用作用域解析仍有效的授权人员；系统应用还必须保留系统和租户成员关系。 */
    private Set<Long> activeAccountIds(UnApp app, List<Long> accountIds) {
        if ("PLATFORM".equalsIgnoreCase(app.getScopeType())) {
            Set<Long> accounts = platformAccounts.list(new LambdaQueryWrapper<UnPlatAccount>()
                            .in(UnPlatAccount::getId, accountIds).eq(UnPlatAccount::getStatus, "ACTIVE"))
                    .stream().map(UnPlatAccount::getId).collect(Collectors.toSet());
            Set<Long> members = platformMembers.list(new LambdaQueryWrapper<UnPlatMember>()
                            .in(UnPlatMember::getAccountId, accountIds).eq(UnPlatMember::getStatus, "ACTIVE"))
                    .stream().map(UnPlatMember::getAccountId).collect(Collectors.toSet());
            accounts.retainAll(members);
            return accounts;
        }
        Set<Long> accounts = platformAccounts.list(new LambdaQueryWrapper<UnPlatAccount>()
                        .in(UnPlatAccount::getId, accountIds).eq(UnPlatAccount::getStatus, "ACTIVE"))
                .stream().map(UnPlatAccount::getId).collect(Collectors.toSet());
        Set<Long> memberIds = systemMembers.list(new LambdaQueryWrapper<UnSysMember>()
                        .eq(UnSysMember::getSystemId, app.getSystemId()).eq(UnSysMember::getStatus, "ACTIVE")
                        .in(UnSysMember::getAccountId, accountIds))
                .stream().map(UnSysMember::getId).collect(Collectors.toSet());
        if (memberIds.isEmpty()) return Set.of();
        Set<Long> tenantMemberIds = systemTenantMembers.list(new LambdaQueryWrapper<UnSysTenantMember>()
                        .eq(UnSysTenantMember::getSystemId, app.getSystemId())
                        .eq(UnSysTenantMember::getTenantId, app.getTenantId())
                        .eq(UnSysTenantMember::getStatus, "ACTIVE")
                        .in(UnSysTenantMember::getMemberId, memberIds))
                .stream().map(UnSysTenantMember::getMemberId).collect(Collectors.toSet());
        if (tenantMemberIds.isEmpty()) return Set.of();
        Set<Long> valid = systemMembers.list(new LambdaQueryWrapper<UnSysMember>()
                        .in(UnSysMember::getId, tenantMemberIds))
                .stream().map(UnSysMember::getAccountId).collect(Collectors.toSet());
        valid.retainAll(accounts);
        return valid;
    }

    /** 按应用作用域解析仍有效的授权部门。 */
    private Set<Long> activeDepartmentIds(UnApp app, List<Long> departmentIds) {
        if ("PLATFORM".equalsIgnoreCase(app.getScopeType()))
            return platformDepartments.list(new LambdaQueryWrapper<UnPlatDepartment>()
                            .in(UnPlatDepartment::getId, departmentIds)
                            .eq(UnPlatDepartment::getStatus, "ACTIVE"))
                    .stream().map(UnPlatDepartment::getId).collect(Collectors.toSet());
        return systemDepartments.list(new LambdaQueryWrapper<UnSysDepartment>()
                        .eq(UnSysDepartment::getSystemId, app.getSystemId())
                        .eq(UnSysDepartment::getTenantId, app.getTenantId())
                        .in(UnSysDepartment::getId, departmentIds)
                        .eq(UnSysDepartment::getStatus, "ACTIVE"))
                .stream().map(UnSysDepartment::getId).collect(Collectors.toSet());
    }

    private void saveCall(UnApp app, AccessRequest request, String hash, Decision decision) {
        UnAppCall call = new UnAppCall();
        call.setApplicationId(app.getId());
        // 记录实际验证通过的密钥版本；轮换期间使用旧密钥时不能误记为当前版本。
        call.setCredentialVersion(decision.credentialVersion());
        call.setSystemId(request.systemId());
        call.setTenantId(request.tenantId());
        call.setResourceType(request.resourceType());
        call.setResourceId(request.resourceId());
        call.setActionCode(request.actionCode());
        call.setRequestId(request.requestId());
        call.setRequestHash(hash);
        call.setResultCode(decision.resultCode());
        call.setResponseJson(write(decision));
        call.setCallerIp(request.callerIp());
        call.setDurationMs(0);
        Map<String, Object> permission = new LinkedHashMap<>();
        permission.put("publishedVersion", decision.publishedVersion());
        permission.put("resourceType", request.resourceType());
        permission.put("actionCode", request.actionCode());
        call.setPermissionJson(write(permission));
        call.setCreateTime(LocalDateTime.now());
        calls.addOrUpdate(call);
    }

    private Decision readDecision(String source) {
        try { return json.readValue(source, Decision.class); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "应用幂等响应损坏", ex); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "应用调用摘要无法编码", ex); }
    }

    private String hash(AccessRequest request) {
        try {
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("resourceType", request.resourceType());
            content.put("resourceId", request.resourceId());
            content.put("actionCode", request.actionCode());
            content.put("systemId", request.systemId());
            content.put("tenantId", request.tenantId());
            content.put("payload", request.payload());
            return sha256(json.writeValueAsString(content));
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "应用请求内容无法编码", ex);
        }
    }

    private String sha256(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "系统不支持 SHA-256", ex); }
    }

    private BaseException denied(String message) { return new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, message); }

    private static final class SetMode {
        private SetMode() {}
        static boolean external(String mode) { return "EXTERNAL".equals(mode) || "BOTH".equals(mode); }
        static boolean internal(String mode) { return "INTERNAL".equals(mode) || "BOTH".equals(mode); }
    }
}
