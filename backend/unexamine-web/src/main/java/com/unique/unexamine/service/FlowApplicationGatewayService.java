package com.unique.unexamine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.gateway.ApplicationAuthorization;
import com.unique.unexamine.core.gateway.ApplicationAuthorizationRequest;
import com.unique.unexamine.core.gateway.ApplicationConfig;
import com.unique.unexamine.core.gateway.FlowDefinition;
import com.unique.unexamine.core.gateway.FlowNode;
import com.unique.unexamine.core.gateway.FlowRun;
import com.unique.unexamine.core.gateway.FlowRunRequest;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FlowApplicationGatewayService {
    private final ObjectMapper objectMapper;
    private final Path flowPath = Path.of("data", "flow-definitions.json");
    private final Path runPath = Path.of("data", "flow-runs.json");
    private final Path applicationPath = Path.of("data", "applications.json");
    private final Map<String, FlowDefinition> flows = new LinkedHashMap<>();
    private final Map<String, FlowRun> runs = new LinkedHashMap<>();
    private final Map<String, ApplicationConfig> applications = new LinkedHashMap<>();

    public FlowApplicationGatewayService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws IOException {
        loadFlows();
        loadRuns();
        loadApplications();
    }

    public synchronized List<FlowDefinition> flows() {
        return new ArrayList<>(flows.values());
    }

    public synchronized FlowDefinition flow(String flowCode) {
        FlowDefinition flow = flows.get(flowCode);
        if (flow == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow 不存在");
        }
        return flow;
    }

    public synchronized FlowRun run(String runId) {
        FlowRun run = runs.get(runId);
        if (run == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flow 运行记录不存在");
        }
        return run;
    }

    public synchronized List<FlowRun> runs() {
        return new ArrayList<>(runs.values());
    }

    public synchronized List<ApplicationConfig> applications() {
        return new ArrayList<>(applications.values());
    }

    public synchronized ApplicationConfig application(String appCode) {
        ApplicationConfig application = applications.get(appCode);
        if (application == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "应用不存在");
        }
        return application;
    }

    public synchronized FlowRun runFlow(String flowCode, FlowRunRequest request, String source) {
        FlowDefinition flow = flow(flowCode);
        String moduleCode = request == null || request.moduleCode() == null || request.moduleCode().isBlank()
                ? firstOrDefault(flow.boundModules(), "customer-record")
                : request.moduleCode();
        if (!flow.boundModules().contains(moduleCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Flow 未关联该业务模块");
        }
        String recordId = request == null || request.recordId() == null || request.recordId().isBlank()
                ? "record-from-" + source.toLowerCase()
                : request.recordId();
        String runId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        String traceId = "trace-" + UUID.randomUUID().toString().substring(0, 12);
        FlowRun run = new FlowRun(
                runId,
                flowCode,
                moduleCode,
                recordId,
                "RUNNING",
                firstOrDefault(flow.nodes().stream().map(FlowNode::nodeId).toList(), "start"),
                traceId,
                "已生成审批待办并通知审批人：" + String.join(",", flow.approvers()),
                source + " 调用 Flow，绑定模块 " + moduleCode + "，traceId=" + traceId,
                OffsetDateTime.now().toString()
        );
        runs.put(runId, run);
        try {
            saveRuns();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Flow 运行记录保存失败", e);
        }
        return run;
    }

    public synchronized ApplicationAuthorization authorize(String appCode, ApplicationAuthorizationRequest request) {
        ApplicationConfig application = application(appCode);
        if (request == null || request.targetType() == null || request.targetCode() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "授权目标不能为空");
        }
        String targetType = request.targetType().toUpperCase();
        if ("FLOW".equals(targetType)) {
            flow(request.targetCode());
        }
        String scope = request.scope() == null || request.scope().isBlank() ? "FLOW_RUN" : request.scope();
        ApplicationAuthorization authorization = new ApplicationAuthorization(
                "auth-" + UUID.randomUUID().toString().substring(0, 8),
                targetType,
                request.targetCode(),
                scope,
                "enabled",
                OffsetDateTime.now().toString()
        );
        List<ApplicationAuthorization> nextAuthorizations = new ArrayList<>(application.authorizations());
        nextAuthorizations.add(0, authorization);
        ApplicationConfig updated = new ApplicationConfig(
                application.appCode(),
                application.appName(),
                application.owner(),
                application.type(),
                application.scopes(),
                application.secretRef(),
                application.callbackUrl(),
                application.rateLimit(),
                application.status(),
                nextAuthorizations,
                OffsetDateTime.now().toString()
        );
        applications.put(appCode, updated);
        try {
            saveApplications();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "应用授权保存失败", e);
        }
        return authorization;
    }

    public synchronized FlowRun invokeAuthorizedFlow(String appCode, String flowCode, FlowRunRequest request) {
        ApplicationConfig application = application(appCode);
        if (!"enabled".equals(application.status())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "应用未启用");
        }
        boolean authorized = application.authorizations().stream().anyMatch(item ->
                "enabled".equals(item.status())
                        && "FLOW".equals(item.targetType())
                        && flowCode.equals(item.targetCode())
                        && application.scopes().contains(item.scope())
        );
        if (!authorized) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "应用未授权该 Flow");
        }
        return runFlow(flowCode, request, "OPENAPI:" + appCode);
    }

    private void loadFlows() throws IOException {
        if (Files.exists(flowPath)) {
            List<FlowDefinition> saved = objectMapper.readValue(flowPath.toFile(), new TypeReference<>() {
            });
            saved.forEach(item -> flows.put(item.flowCode(), item));
        }
        if (flows.isEmpty()) {
            seedFlows().forEach(item -> flows.put(item.flowCode(), item));
            saveFlows();
        }
    }

    private void loadRuns() throws IOException {
        if (Files.exists(runPath)) {
            List<FlowRun> saved = objectMapper.readValue(runPath.toFile(), new TypeReference<>() {
            });
            saved.forEach(item -> runs.put(item.runId(), item));
        }
    }

    private void loadApplications() throws IOException {
        if (Files.exists(applicationPath)) {
            List<ApplicationConfig> saved = objectMapper.readValue(applicationPath.toFile(), new TypeReference<>() {
            });
            saved.forEach(item -> applications.put(item.appCode(), item));
        }
        if (applications.isEmpty()) {
            seedApplications().forEach(item -> applications.put(item.appCode(), item));
            saveApplications();
        }
    }

    private void saveFlows() throws IOException {
        Files.createDirectories(flowPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(flowPath.toFile(), flows());
    }

    private void saveRuns() throws IOException {
        Files.createDirectories(runPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(runPath.toFile(), runs());
    }

    private void saveApplications() throws IOException {
        Files.createDirectories(applicationPath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(applicationPath.toFile(), applications());
    }

    private List<FlowDefinition> seedFlows() {
        String now = OffsetDateTime.now().toString();
        return List.of(
                new FlowDefinition(
                        "customer-approval",
                        "客户档案审批 Flow",
                        "v1",
                        "published",
                        List.of("customer-record", "service-ticket"),
                        List.of("external-crm", "partner-ticket-api"),
                        List.of("销售主管", "服务经理"),
                        List.of("business-record", "member-directory", "external-crm"),
                        List.of(
                                new FlowNode("start", "发起", "start", "system", "create-instance"),
                                new FlowNode("manager-approve", "主管审批", "approval", "销售主管", "approve-or-reject"),
                                new FlowNode("external-sync", "外部同步", "external-api", "external-crm", "sync-status"),
                                new FlowNode("finish", "完成", "end", "system", "writeback-status")
                        ),
                        List.of("提交审批", "审批通过", "审批拒绝", "外部同步", "状态回写", "消息通知"),
                        now
                ),
                new FlowDefinition(
                        "material-request-flow",
                        "物料申请 Flow",
                        "v1",
                        "published",
                        List.of("material-request", "quality-check"),
                        List.of("erp-stock-service"),
                        List.of("生产主管", "仓库管理员"),
                        List.of("business-record", "inventory-service"),
                        List.of(
                                new FlowNode("start", "发起", "start", "system", "create-instance"),
                                new FlowNode("stock-check", "库存校验", "data-source", "erp-stock-service", "check-stock"),
                                new FlowNode("warehouse-approve", "仓库审批", "approval", "仓库管理员", "approve-or-reject"),
                                new FlowNode("finish", "完成", "end", "system", "writeback-status")
                        ),
                        List.of("库存校验", "提交审批", "审批处理", "状态回写", "消息通知"),
                        now
                )
        );
    }

    private List<ApplicationConfig> seedApplications() {
        String now = OffsetDateTime.now().toString();
        return List.of(
                new ApplicationConfig(
                        "open-gateway",
                        "开放服务网关应用",
                        "平台管理员",
                        "external-openapi",
                        List.of("FLOW_RUN", "SERVICE_READ", "SERVICE_WRITE"),
                        "secret://open-gateway/current",
                        "https://callback.example.com/unexamine",
                        "1000/min",
                        "enabled",
                        List.of(),
                        now
                ),
                new ApplicationConfig(
                        "internal-assistant",
                        "内部助手应用",
                        "系统管理员",
                        "internal-app",
                        List.of("SERVICE_READ", "AI_ASSIST"),
                        "secret://internal-assistant/current",
                        "internal://assistant/callback",
                        "2000/min",
                        "enabled",
                        List.of(new ApplicationAuthorization("auth-seed-service", "SERVICE", "business-record-read", "SERVICE_READ", "enabled", now)),
                        now
                )
        );
    }

    private String firstOrDefault(List<String> values, String fallback) {
        return values == null || values.isEmpty() ? fallback : values.get(0);
    }
}
