package com.unique.examine.plat.manage.platformflowapp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.manage.task.PersistedAsyncTaskService;
import com.unique.examine.core.task.AsyncTaskStatus;
import com.unique.examine.core.task.AsyncTaskView;
import com.unique.examine.messagelog.base.entity.AuditBusinessLog;
import com.unique.examine.messagelog.base.entity.MessageMessage;
import com.unique.examine.messagelog.base.entity.MessageTodo;
import com.unique.examine.messagelog.base.service.AuditBusinessLogBaseService;
import com.unique.examine.messagelog.base.service.MessageMessageBaseService;
import com.unique.examine.messagelog.base.service.MessageTodoBaseService;
import com.unique.examine.plat.base.entity.PlatAccount;
import com.unique.examine.plat.manage.common.CurrentAccountProvider;
import com.unique.examine.plat.manage.common.PlatformAccessGuard;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationActionRequest;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationActionResult;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationQuery;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationSaveRequest;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformAuthorizationView;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowActionRequest;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowQuery;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowRunResult;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowSaveRequest;
import com.unique.examine.plat.manage.platformflowapp.PlatformFlowApplicationModels.PlatformFlowView;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Platform-level Flow and Application authorization service with persistence and readback.
 */
@Service
public class PlatformFlowApplicationService {

    private static final String SCOPE_PLATFORM = "platform";
    private static final String AUDIT_SCOPE_PLATFORM = "PLATFORM";
    private static final String BOUNDARY = "NO_SYSTEM_BUSINESS_WRITE";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentAccountProvider currentAccountProvider;
    private final PlatformAccessGuard platformAccessGuard;
    private final PersistedAsyncTaskService asyncTaskService;
    private final MessageTodoBaseService todoBaseService;
    private final MessageMessageBaseService messageBaseService;
    private final AuditBusinessLogBaseService auditBaseService;
    private final AtomicBoolean schemaReady = new AtomicBoolean(false);

    public PlatformFlowApplicationService(JdbcTemplate jdbcTemplate,
                                          ObjectMapper objectMapper,
                                          CurrentAccountProvider currentAccountProvider,
                                          PlatformAccessGuard platformAccessGuard,
                                          PersistedAsyncTaskService asyncTaskService,
                                          MessageTodoBaseService todoBaseService,
                                          MessageMessageBaseService messageBaseService,
                                          AuditBusinessLogBaseService auditBaseService) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.currentAccountProvider = currentAccountProvider;
        this.platformAccessGuard = platformAccessGuard;
        this.asyncTaskService = asyncTaskService;
        this.todoBaseService = todoBaseService;
        this.messageBaseService = messageBaseService;
        this.auditBaseService = auditBaseService;
    }

    public PageResult<PlatformFlowView> flows(PageRequest pageRequest, PlatformFlowQuery query) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        boolean canManage = platformAccessGuard.canManagePlatform(account.getId());
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        QueryParts parts = flowQuery(query);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM un_platform_flow_definition " + parts.where(), Long.class, parts.args().toArray());
        List<Object> args = new ArrayList<>(parts.args());
        args.add(offset);
        args.add(pageSize);
        List<PlatformFlowView> records = jdbcTemplate.query("SELECT * FROM un_platform_flow_definition " + parts.where()
                + " ORDER BY updated_at DESC, id DESC LIMIT ?,?", flowMapper(canManage), args.toArray());
        return new PageResult<>(records, pageNo, pageSize, total == null ? 0 : total, offset + records.size() < (total == null ? 0 : total));
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatformFlowView createFlow(PlatformFlowSaveRequest request) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        requireAdmin(account.getId());
        requireText(request == null ? null : request.flowName(), "平台 Flow 名称不能为空");
        String flowCode = normalizeCode(request.flowCode(), "platform_flow_" + shortTrace());
        Long existing = idByText("SELECT id FROM un_platform_flow_definition WHERE flow_code=? AND deleted=0 LIMIT 1", flowCode);
        if (existing != null) {
            return flowDetail(String.valueOf(existing));
        }
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO un_platform_flow_definition "
                            + "(flow_code,flow_name,trigger_source,affected_systems,node_summary,retry_policy,compensation_policy,status,created_by,trace_id,audit_log_id,created_at,updated_at,deleted) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, flowCode);
            ps.setString(2, request.flowName());
            ps.setString(3, safeText(request.triggerSource(), "平台授权 / 应用接入 / 运维任务"));
            ps.setString(4, toJson(safeList(request.affectedSystems(), List.of("platform"))));
            ps.setString(5, safeText(request.nodeSummary(), "触发、检查、平台反馈、结束"));
            ps.setString(6, safeText(request.retryPolicy(), "失败可重试 2 次"));
            ps.setString(7, safeText(request.compensationPolicy(), "失败生成平台补偿任务"));
            ps.setString(8, "ENABLED");
            ps.setLong(9, account.getId());
            ps.setString(10, RequestContext.current().traceId());
            ps.setString(11, auditLogId());
            ps.setTimestamp(12, Timestamp.valueOf(now));
            ps.setTimestamp(13, Timestamp.valueOf(now));
            return ps;
        }, keys);
        String flowId = String.valueOf(keys.getKey().longValue());
        Feedback feedback = writeFeedback("PLATFORM_FLOW_CREATE", "PLATFORM_FLOW", flowId,
                "平台 Flow 草稿已保存", "平台 Flow " + request.flowName() + " 已持久化", account.getId());
        jdbcTemplate.update("UPDATE un_platform_flow_definition SET latest_todo_id=?, latest_message_id=?, audit_log_id=?, updated_at=? WHERE id=?",
                feedback.todoId(), feedback.messageId(), feedback.auditLogId(), Timestamp.valueOf(LocalDateTime.now()), Long.valueOf(flowId));
        return flowDetail(flowId);
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatformFlowView updateFlow(String flowId, PlatformFlowSaveRequest request) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        requireAdmin(account.getId());
        long id = parseRequiredId(flowId, "平台 Flow 编号格式不正确");
        requireFlowRow(id);
        jdbcTemplate.update("UPDATE un_platform_flow_definition SET flow_name=?, trigger_source=?, affected_systems=?, node_summary=?, retry_policy=?, compensation_policy=?, trace_id=?, audit_log_id=?, updated_at=? WHERE id=? AND deleted=0",
                safeText(request.flowName(), "平台 Flow"),
                safeText(request.triggerSource(), "平台授权 / 应用接入 / 运维任务"),
                toJson(safeList(request.affectedSystems(), List.of("platform"))),
                safeText(request.nodeSummary(), "触发、检查、平台反馈、结束"),
                safeText(request.retryPolicy(), "失败可重试 2 次"),
                safeText(request.compensationPolicy(), "失败生成平台补偿任务"),
                RequestContext.current().traceId(), auditLogId(), Timestamp.valueOf(LocalDateTime.now()), id);
        Feedback feedback = writeFeedback("PLATFORM_FLOW_UPDATE", "PLATFORM_FLOW", String.valueOf(id),
                "平台 Flow 配置已更新", "平台 Flow 配置已持久化并可回读", account.getId());
        jdbcTemplate.update("UPDATE un_platform_flow_definition SET latest_todo_id=?, latest_message_id=?, audit_log_id=? WHERE id=?",
                feedback.todoId(), feedback.messageId(), feedback.auditLogId(), id);
        return flowDetail(flowId);
    }

    public PlatformFlowView flowDetail(String flowId) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        boolean canManage = platformAccessGuard.canManagePlatform(account.getId());
        long id = parseRequiredId(flowId, "平台 Flow 编号格式不正确");
        List<PlatformFlowView> rows = jdbcTemplate.query("SELECT * FROM un_platform_flow_definition WHERE id=? AND deleted=0 LIMIT 1", flowMapper(canManage), id);
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "平台 Flow 不存在");
        }
        return rows.get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatformFlowRunResult runFlowAction(String flowId, String action, PlatformFlowActionRequest request) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        requireAdmin(account.getId());
        long id = parseRequiredId(flowId, "平台 Flow 编号格式不正确");
        FlowRow flow = requireFlowRow(id);
        String normalizedAction = switch (safeText(action, "run-check")) {
            case "retry" -> "retry";
            case "compensate" -> "compensate";
            default -> "run-check";
        };
        String suffix = shortTrace();
        String runBatchId = "batch_pf_" + normalizedAction.replace('-', '_') + "_" + suffix;
        String taskId = "TASK-PF-" + normalizedAction.toUpperCase().replace('-', '_') + "-" + suffix;
        AsyncTaskView task = asyncTaskService.upsert(taskId, "PLATFORM_FLOW_" + normalizedAction.toUpperCase().replace('-', '_'),
                safeText(request == null ? null : request.idempotencyKey(), runBatchId),
                "run-check".equals(normalizedAction) ? AsyncTaskStatus.SUCCESS : AsyncTaskStatus.QUEUED,
                "run-check".equals(normalizedAction) ? 100 : 0,
                true, false, "compensate".equals(normalizedAction), null, null, null, 1, 0, account.getId(),
                Map.of("flowId", id, "flowCode", flow.flowCode(), "action", normalizedAction, "boundary", BOUNDARY));
        LocalDateTime now = LocalDateTime.now();
        String auditLogId = auditLogId();
        String retryTaskId = "retry".equals(normalizedAction) ? task.taskId() : "TASK-PF-RETRY-" + suffix;
        String compensationTaskId = "compensate".equals(normalizedAction) ? task.taskId() : "TASK-PF-COMP-" + suffix;
        jdbcTemplate.update("INSERT INTO un_platform_flow_run_batch "
                        + "(flow_id,run_batch_id,action_code,status,task_id,retry_task_id,compensation_task_id,idempotency_key,trace_id,audit_log_id,created_by,created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, runBatchId, normalizedAction, task.status().name(), task.taskId(), retryTaskId, compensationTaskId,
                safeText(request == null ? null : request.idempotencyKey(), runBatchId),
                RequestContext.current().traceId(), auditLogId, account.getId(), Timestamp.valueOf(now));
        Feedback feedback = writeFeedback("PLATFORM_FLOW_" + normalizedAction.toUpperCase().replace('-', '_'),
                "PLATFORM_FLOW", String.valueOf(id), "平台 Flow 批次已生成", flow.flowName() + " / " + runBatchId,
                account.getId());
        jdbcTemplate.update("UPDATE un_platform_flow_definition SET current_run_batch_id=?, latest_task_id=?, latest_todo_id=?, latest_message_id=?, trace_id=?, audit_log_id=?, updated_at=? WHERE id=?",
                runBatchId, task.taskId(), feedback.todoId(), feedback.messageId(), RequestContext.current().traceId(),
                feedback.auditLogId(), Timestamp.valueOf(now), id);
        return new PlatformFlowRunResult(String.valueOf(id), runBatchId, normalizedAction, task.status().name(), task.taskId(),
                retryTaskId, compensationTaskId, RequestContext.current().traceId(), feedback.auditLogId(),
                feedback.todoId(), feedback.messageId(), BOUNDARY, now);
    }

    public PageResult<PlatformAuthorizationView> authorizations(PageRequest pageRequest, PlatformAuthorizationQuery query) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        boolean canManage = platformAccessGuard.canManagePlatform(account.getId());
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        QueryParts parts = authorizationQuery(query);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM un_platform_application_authorization " + parts.where(), Long.class, parts.args().toArray());
        List<Object> args = new ArrayList<>(parts.args());
        args.add(offset);
        args.add(pageSize);
        List<PlatformAuthorizationView> records = jdbcTemplate.query("SELECT * FROM un_platform_application_authorization " + parts.where()
                + " ORDER BY updated_at DESC, id DESC LIMIT ?,?", authorizationMapper(canManage), args.toArray());
        return new PageResult<>(records, pageNo, pageSize, total == null ? 0 : total, offset + records.size() < (total == null ? 0 : total));
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatformAuthorizationView createAuthorization(PlatformAuthorizationSaveRequest request) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        requireText(request == null ? null : request.applicationName(), "应用名称不能为空");
        String requestId = "REQ-PLAT-AUTH-" + shortTrace();
        String changeId = "AUTH-CHG-PLAT-" + shortTrace();
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keys = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("INSERT INTO un_platform_application_authorization "
                            + "(application_name,application_type,target_system_id,target_tenant_id,module_scope,scope,expiry_at,data_isolation,request_id,authorization_change_id,approval_status,status,requested_by,trace_id,audit_log_id,created_at,updated_at,deleted) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.applicationName());
            ps.setString(2, safeText(request.applicationType(), "平台应用"));
            ps.setString(3, safeText(request.targetSystemId(), "platform"));
            ps.setString(4, safeText(request.targetTenantId(), "platform"));
            ps.setString(5, toJson(safeList(request.moduleScope(), List.of("platform.authorization", "platform.flow"))));
            ps.setString(6, toJson(safeList(request.scope(), List.of("platform:authorization:read", "platform:authorization:request"))));
            ps.setString(7, safeText(request.expiryAt(), "2026-12-31"));
            ps.setString(8, safeText(request.dataIsolation(), "平台范围"));
            ps.setString(9, requestId);
            ps.setString(10, changeId);
            ps.setString(11, safeText(request.approvalStatus(), "PENDING"));
            ps.setString(12, "PENDING");
            ps.setLong(13, account.getId());
            ps.setString(14, RequestContext.current().traceId());
            ps.setString(15, auditLogId());
            ps.setTimestamp(16, Timestamp.valueOf(now));
            ps.setTimestamp(17, Timestamp.valueOf(now));
            return ps;
        }, keys);
        String authorizationId = String.valueOf(keys.getKey().longValue());
        Feedback feedback = writeFeedback("PLATFORM_AUTHORIZATION_REQUEST", "PLATFORM_AUTHORIZATION", authorizationId,
                "平台应用授权申请已提交", request.applicationName() + " / " + requestId, account.getId());
        jdbcTemplate.update("UPDATE un_platform_application_authorization SET latest_todo_id=?, latest_message_id=?, audit_log_id=? WHERE id=?",
                feedback.todoId(), feedback.messageId(), feedback.auditLogId(), Long.valueOf(authorizationId));
        return authorizationDetail(authorizationId);
    }

    public PlatformAuthorizationView authorizationDetail(String authorizationId) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        boolean canManage = platformAccessGuard.canManagePlatform(account.getId());
        long id = parseRequiredId(authorizationId, "平台授权编号格式不正确");
        List<PlatformAuthorizationView> rows = jdbcTemplate.query("SELECT * FROM un_platform_application_authorization WHERE id=? AND deleted=0 LIMIT 1", authorizationMapper(canManage), id);
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "平台应用授权不存在");
        }
        return rows.get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatformAuthorizationActionResult adjustAuthorization(String authorizationId, PlatformAuthorizationActionRequest request) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        requireAdmin(account.getId());
        long id = parseRequiredId(authorizationId, "平台授权编号格式不正确");
        AuthorizationRow row = requireAuthorizationRow(id);
        String changeId = "AUTH-CHG-PLAT-" + shortTrace();
        jdbcTemplate.update("UPDATE un_platform_application_authorization SET scope=?, expiry_at=?, approval_status=?, status=?, authorization_change_id=?, trace_id=?, audit_log_id=?, updated_at=? WHERE id=? AND deleted=0",
                toJson(safeList(request == null ? null : request.scope(), row.scope())),
                safeText(request == null ? null : request.expiryAt(), row.expiryAt()),
                safeText(request == null ? null : request.approvalStatus(), "APPROVED"),
                "APPROVED", changeId, RequestContext.current().traceId(), auditLogId(),
                Timestamp.valueOf(LocalDateTime.now()), id);
        return authorizationActionResult(id, "adjust", "APPROVED", account.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public PlatformAuthorizationActionResult disableAuthorization(String authorizationId, PlatformAuthorizationActionRequest request) {
        ensureReady();
        PlatAccount account = currentAccountProvider.currentAccount();
        requireAdmin(account.getId());
        long id = parseRequiredId(authorizationId, "平台授权编号格式不正确");
        requireAuthorizationRow(id);
        String changeId = "AUTH-CHG-DISABLE-" + shortTrace();
        jdbcTemplate.update("UPDATE un_platform_application_authorization SET approval_status=?, status=?, authorization_change_id=?, trace_id=?, audit_log_id=?, updated_at=? WHERE id=? AND deleted=0",
                "DISABLED", "DISABLED", changeId, RequestContext.current().traceId(), auditLogId(),
                Timestamp.valueOf(LocalDateTime.now()), id);
        return authorizationActionResult(id, "disable", "DISABLED", account.getId());
    }

    private PlatformAuthorizationActionResult authorizationActionResult(long id, String action, String status, Long accountId) {
        AuthorizationRow row = requireAuthorizationRow(id);
        Feedback feedback = writeFeedback("PLATFORM_AUTHORIZATION_" + action.toUpperCase(), "PLATFORM_AUTHORIZATION",
                String.valueOf(id), "平台应用授权已" + ("disable".equals(action) ? "停用" : "调整"),
                row.applicationName() + " / " + row.authorizationChangeId(), accountId);
        jdbcTemplate.update("UPDATE un_platform_application_authorization SET latest_todo_id=?, latest_message_id=?, audit_log_id=? WHERE id=?",
                feedback.todoId(), feedback.messageId(), feedback.auditLogId(), id);
        return new PlatformAuthorizationActionResult(String.valueOf(id), row.requestId(), row.authorizationChangeId(),
                action, status, RequestContext.current().traceId(), feedback.auditLogId(), feedback.todoId(),
                feedback.messageId(), BOUNDARY, LocalDateTime.now());
    }

    private void ensureReady() {
        if (schemaReady.compareAndSet(false, true)) {
            ensureSchema();
        }
        seedDefaultsIfEmpty();
    }

    private void ensureSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS un_platform_flow_definition ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "flow_code VARCHAR(80) NOT NULL,"
                + "flow_name VARCHAR(120) NOT NULL,"
                + "trigger_source VARCHAR(160) NOT NULL,"
                + "affected_systems TEXT NOT NULL,"
                + "node_summary VARCHAR(500) NOT NULL,"
                + "retry_policy VARCHAR(255) NOT NULL,"
                + "compensation_policy VARCHAR(255) NOT NULL,"
                + "status VARCHAR(40) NOT NULL,"
                + "current_run_batch_id VARCHAR(120) NULL,"
                + "latest_task_id VARCHAR(120) NULL,"
                + "latest_todo_id VARCHAR(120) NULL,"
                + "latest_message_id VARCHAR(120) NULL,"
                + "created_by BIGINT NOT NULL,"
                + "trace_id VARCHAR(80) NOT NULL,"
                + "audit_log_id VARCHAR(80) NOT NULL,"
                + "created_at DATETIME NOT NULL,"
                + "updated_at DATETIME NOT NULL,"
                + "deleted TINYINT NOT NULL DEFAULT 0,"
                + "UNIQUE KEY uk_platform_flow_code (flow_code, deleted),"
                + "KEY idx_platform_flow_status (status, updated_at))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS un_platform_flow_run_batch ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "flow_id BIGINT NOT NULL,"
                + "run_batch_id VARCHAR(120) NOT NULL,"
                + "action_code VARCHAR(40) NOT NULL,"
                + "status VARCHAR(40) NOT NULL,"
                + "task_id VARCHAR(120) NOT NULL,"
                + "retry_task_id VARCHAR(120) NULL,"
                + "compensation_task_id VARCHAR(120) NULL,"
                + "idempotency_key VARCHAR(160) NOT NULL,"
                + "trace_id VARCHAR(80) NOT NULL,"
                + "audit_log_id VARCHAR(80) NOT NULL,"
                + "created_by BIGINT NOT NULL,"
                + "created_at DATETIME NOT NULL,"
                + "UNIQUE KEY uk_platform_flow_batch (run_batch_id),"
                + "KEY idx_platform_flow_run (flow_id, created_at))");
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS un_platform_application_authorization ("
                + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                + "application_name VARCHAR(120) NOT NULL,"
                + "application_type VARCHAR(80) NOT NULL,"
                + "target_system_id VARCHAR(80) NOT NULL,"
                + "target_tenant_id VARCHAR(80) NOT NULL,"
                + "module_scope TEXT NOT NULL,"
                + "scope TEXT NOT NULL,"
                + "expiry_at VARCHAR(40) NOT NULL,"
                + "data_isolation VARCHAR(160) NOT NULL,"
                + "request_id VARCHAR(120) NOT NULL,"
                + "authorization_change_id VARCHAR(120) NOT NULL,"
                + "approval_status VARCHAR(40) NOT NULL,"
                + "status VARCHAR(40) NOT NULL,"
                + "requested_by BIGINT NOT NULL,"
                + "latest_todo_id VARCHAR(120) NULL,"
                + "latest_message_id VARCHAR(120) NULL,"
                + "trace_id VARCHAR(80) NOT NULL,"
                + "audit_log_id VARCHAR(80) NOT NULL,"
                + "created_at DATETIME NOT NULL,"
                + "updated_at DATETIME NOT NULL,"
                + "deleted TINYINT NOT NULL DEFAULT 0,"
                + "UNIQUE KEY uk_platform_auth_request (request_id),"
                + "KEY idx_platform_auth_status (status, approval_status, updated_at))");
    }

    private void seedDefaultsIfEmpty() {
        PlatAccount account = currentAccountProvider.currentAccount();
        Long flowCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM un_platform_flow_definition WHERE deleted=0", Long.class);
        if (flowCount != null && flowCount == 0) {
            insertSeedFlow("PFLOW-AUTH-CHANGE", "授权变更 Flow", "授权申请 / 审批回调", List.of("platform-authorization"), "申请、审批、消息、日志", account.getId());
            insertSeedFlow("PFLOW-APP-ACCESS", "应用接入 Flow", "OpenAPI / Webhook / SecretRef 轮换", List.of("platform-application"), "接入检查、回调、失败重试", account.getId());
            insertSeedFlow("PFLOW-OPS-HEALTH", "平台运维 Flow", "体检 / 发布检查 / 容量预警", List.of("platform-ops"), "体检、预警、补偿任务", account.getId());
        }
        Long authCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM un_platform_application_authorization WHERE deleted=0", Long.class);
        if (authCount != null && authCount == 0) {
            insertSeedAuthorization("PLATFORM", "平台授权中心", "平台授权", "platform", "platform", List.of("platform.authorization", "platform.todo", "platform.message"), List.of("platform:authorization:read", "platform:authorization:request"), "APPROVED", "APPROVED", account.getId());
            insertSeedAuthorization("OPENAPI", "系统对外应用授权", "OpenAPI 应用", "system-context-required", "system-context-required", List.of("system.openapi", "system.module.scope"), List.of("system:openapi:read", "system:module:scope"), "PENDING", "PENDING", account.getId());
            insertSeedAuthorization("FLOW", "Flow 开放应用", "Flow 调用", "platform-flow", "platform", List.of("platform.flow"), List.of("platform:flow:read", "platform:flow:invoke"), "PENDING", "PENDING", account.getId());
        }
    }

    private void insertSeedFlow(String code, String name, String trigger, List<String> affected, String nodeSummary, Long accountId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO un_platform_flow_definition (flow_code,flow_name,trigger_source,affected_systems,node_summary,retry_policy,compensation_policy,status,created_by,trace_id,audit_log_id,created_at,updated_at,deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                code, name, trigger, toJson(affected), nodeSummary, "失败可重试 2 次", "失败生成平台补偿任务", "ENABLED",
                accountId, RequestContext.current().traceId(), auditLogId(), Timestamp.valueOf(now), Timestamp.valueOf(now));
    }

    private void insertSeedAuthorization(String seedCode, String name, String type, String targetSystem, String targetTenant,
                                         List<String> modules, List<String> scope, String approvalStatus,
                                         String status, Long accountId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO un_platform_application_authorization (application_name,application_type,target_system_id,target_tenant_id,module_scope,scope,expiry_at,data_isolation,request_id,authorization_change_id,approval_status,status,requested_by,trace_id,audit_log_id,created_at,updated_at,deleted) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                name, type, targetSystem, targetTenant, toJson(modules), toJson(scope), "2026-12-31", "平台范围",
                "REQ-SEED-" + seedCode + "-" + shortTrace(), "AUTH-CHG-SEED-" + seedCode + "-" + shortTrace(), approvalStatus, status, accountId,
                RequestContext.current().traceId(), auditLogId(), Timestamp.valueOf(now), Timestamp.valueOf(now));
    }

    private Feedback writeFeedback(String actionCode, String objectType, String objectId, String title,
                                   String content, Long accountId) {
        RequestContext context = RequestContext.current();
        String auditId = auditLogId();
        AuditBusinessLog log = new AuditBusinessLog();
        log.setLogType("BUSINESS");
        log.setScope(AUDIT_SCOPE_PLATFORM);
        log.setOperatorId(accountId);
        log.setActionCode(actionCode);
        log.setObjectType(objectType);
        log.setObjectId(objectId);
        log.setResult("SUCCESS");
        log.setRequestId(context.requestId());
        log.setTraceId(context.traceId());
        log.setAuditLogId(auditId);
        log.setFieldDiff(toJson(Map.of("boundary", BOUNDARY)));
        log.setPermissionSnapshot(toJson(Map.of("platformAdmin", platformAccessGuard.canManagePlatform(accountId))));
        log.setCreatedAt(LocalDateTime.now());
        auditBaseService.saveEntity(log);

        String targetType = objectType.equals("PLATFORM_AUTHORIZATION") ? "platform_auth" : "platform_task";
        MessageTodo todo = new MessageTodo();
        todo.setScope(SCOPE_PLATFORM);
        todo.setTodoType(objectType.equals("PLATFORM_AUTHORIZATION") ? "platform_authorization" : "platform_task");
        todo.setTitle(title);
        todo.setSourceName(objectType);
        todo.setAssigneeId(accountId);
        todo.setStatus("PENDING");
        todo.setPriority("NORMAL");
        todo.setTargetPayload(toJson(Map.of("scope", SCOPE_PLATFORM, "targetType", targetType, "targetId", objectId, "requiresSystemSwitch", false, "fallbackAction", "OPEN_PLATFORM_WORKBENCH")));
        todo.setPrimaryAction(toJson(Map.of("actionCode", "open", "label", "查看平台反馈")));
        todo.setTraceId(context.traceId());
        todo.setCreatedAt(LocalDateTime.now());
        todoBaseService.saveEntity(todo);

        MessageMessage message = new MessageMessage();
        message.setScope(SCOPE_PLATFORM);
        message.setTemplateCode(objectType.equals("PLATFORM_AUTHORIZATION") ? "platform_authorization_feedback" : "platform_flow_feedback");
        message.setReceiverId(accountId);
        message.setTitle(title);
        message.setContent(content + "，traceId=" + context.traceId());
        message.setMessageType("platform_feedback");
        message.setTargetPayload(toJson(Map.of("scope", SCOPE_PLATFORM, "targetType", targetType, "targetId", objectId, "requiresSystemSwitch", false, "fallbackAction", "OPEN_PLATFORM_WORKBENCH")));
        message.setReadStatus(0);
        message.setArchiveStatus(0);
        message.setCreatedAt(LocalDateTime.now());
        messageBaseService.saveEntity(message);
        return new Feedback(String.valueOf(todo.getId()), String.valueOf(message.getId()), auditId);
    }

    private QueryParts flowQuery(PlatformFlowQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        clauses.add("WHERE deleted=0");
        if (query != null && StringUtils.hasText(query.status())) {
            clauses.add("status=?");
            args.add(query.status());
        }
        if (query != null && StringUtils.hasText(query.keyword())) {
            clauses.add("(flow_code LIKE ? OR flow_name LIKE ? OR trigger_source LIKE ?)");
            String keyword = "%" + query.keyword() + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }
        return new QueryParts(String.join(" AND ", clauses), args);
    }

    private QueryParts authorizationQuery(PlatformAuthorizationQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        clauses.add("WHERE deleted=0");
        if (query != null && StringUtils.hasText(query.status())) {
            clauses.add("status=?");
            args.add(query.status());
        }
        if (query != null && StringUtils.hasText(query.approvalStatus())) {
            clauses.add("approval_status=?");
            args.add(query.approvalStatus());
        }
        if (query != null && StringUtils.hasText(query.keyword())) {
            clauses.add("(application_name LIKE ? OR application_type LIKE ? OR request_id LIKE ? OR authorization_change_id LIKE ?)");
            String keyword = "%" + query.keyword() + "%";
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }
        return new QueryParts(String.join(" AND ", clauses), args);
    }

    private RowMapper<PlatformFlowView> flowMapper(boolean canManage) {
        return (rs, rowNum) -> new PlatformFlowView(String.valueOf(rs.getLong("id")), rs.getString("flow_code"),
                rs.getString("flow_name"), rs.getString("trigger_source"), readList(rs.getString("affected_systems")),
                rs.getString("node_summary"), rs.getString("status"), rs.getString("retry_policy"),
                rs.getString("compensation_policy"), rs.getString("current_run_batch_id"), rs.getString("trace_id"),
                rs.getString("audit_log_id"), rs.getString("latest_task_id"), rs.getString("latest_todo_id"),
                rs.getString("latest_message_id"), canManage ? "ADMIN_MUTATION_ALLOWED" : "VIEW_ONLY_REQUEST_ALLOWED",
                canManage, toLocalDateTime(rs.getTimestamp("created_at")), toLocalDateTime(rs.getTimestamp("updated_at")));
    }

    private RowMapper<PlatformAuthorizationView> authorizationMapper(boolean canManage) {
        return (rs, rowNum) -> new PlatformAuthorizationView(String.valueOf(rs.getLong("id")),
                rs.getString("application_name"), rs.getString("application_type"), rs.getString("target_system_id"),
                rs.getString("target_tenant_id"), readList(rs.getString("module_scope")), readList(rs.getString("scope")),
                rs.getString("expiry_at"), rs.getString("data_isolation"), rs.getString("request_id"),
                rs.getString("authorization_change_id"), rs.getString("approval_status"), rs.getString("status"),
                rs.getString("trace_id"), rs.getString("audit_log_id"), rs.getString("latest_todo_id"),
                rs.getString("latest_message_id"), canManage ? "ADMIN_MUTATION_ALLOWED" : "VIEW_AND_REQUEST_ONLY",
                canManage, toLocalDateTime(rs.getTimestamp("created_at")), toLocalDateTime(rs.getTimestamp("updated_at")));
    }

    private FlowRow requireFlowRow(long id) {
        List<FlowRow> rows = jdbcTemplate.query("SELECT * FROM un_platform_flow_definition WHERE id=? AND deleted=0 LIMIT 1", (rs, rowNum) ->
                new FlowRow(rs.getLong("id"), rs.getString("flow_code"), rs.getString("flow_name")), id);
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "平台 Flow 不存在");
        }
        return rows.get(0);
    }

    private AuthorizationRow requireAuthorizationRow(long id) {
        List<AuthorizationRow> rows = jdbcTemplate.query("SELECT * FROM un_platform_application_authorization WHERE id=? AND deleted=0 LIMIT 1", (rs, rowNum) ->
                new AuthorizationRow(rs.getLong("id"), rs.getString("application_name"), readList(rs.getString("scope")),
                        rs.getString("expiry_at"), rs.getString("request_id"), rs.getString("authorization_change_id")), id);
        if (rows.isEmpty()) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "平台应用授权不存在");
        }
        return rows.get(0);
    }

    private void requireAdmin(Long accountId) {
        if (!platformAccessGuard.canManagePlatform(accountId)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "当前账号只能查看或发起申请，不能执行平台管理动作");
        }
    }

    private Long idByText(String sql, String value) {
        List<Long> ids = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), value);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private int pageNo(PageRequest pageRequest) {
        return pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
    }

    private int pageSize(PageRequest pageRequest) {
        return pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
    }

    private long parseRequiredId(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private void requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private String normalizeCode(String value, String fallback) {
        String source = StringUtils.hasText(value) ? value : fallback;
        return source.trim().toLowerCase().replaceAll("[^a-z0-9_]+", "_");
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private List<String> safeList(List<String> values, List<String> fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.stream().filter(StringUtils::hasText).map(String::trim).toList();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private List<String> readList(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (Exception ex) {
            return List.of(value);
        }
    }

    private String auditLogId() {
        RequestContext context = RequestContext.current();
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String shortTrace() {
        String traceId = RequestContext.current().traceId();
        String suffix = traceId.length() <= 8 ? traceId : traceId.substring(traceId.length() - 8);
        String normalized = suffix.replaceAll("[^A-Za-z0-9]", "");
        return StringUtils.hasText(normalized) ? normalized : Long.toString(System.nanoTime(), 36);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record QueryParts(String where, List<Object> args) {
    }

    private record Feedback(String todoId, String messageId, String auditLogId) {
    }

    private record FlowRow(Long id, String flowCode, String flowName) {
    }

    private record AuthorizationRow(Long id, String applicationName, List<String> scope, String expiryAt,
                                    String requestId, String authorizationChangeId) {
    }
}
