package com.unique.unexamine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.BusinessModuleConfig;
import com.unique.unexamine.core.module.ModuleAction;
import com.unique.unexamine.core.module.ModuleField;
import com.unique.unexamine.core.module.ModulePage;
import com.unique.unexamine.core.module.PrintTemplate;
import com.unique.unexamine.core.module.RecordCreateRequest;
import com.unique.unexamine.core.module.RuntimeRecord;
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
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BusinessModuleService {
    private final ObjectMapper objectMapper;
    private final Path modulePath = Path.of("data", "business-modules.json");
    private final Path recordPath = Path.of("data", "module-records.json");
    private final Map<String, BusinessModuleConfig> modules = new LinkedHashMap<>();
    private final Map<String, List<RuntimeRecord>> records = new LinkedHashMap<>();

    public BusinessModuleService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws IOException {
        loadModules();
        loadRecords();
    }

    public synchronized List<BusinessModuleConfig> modules() {
        return new ArrayList<>(modules.values());
    }

    public synchronized BusinessModuleConfig module(String moduleCode) {
        BusinessModuleConfig config = modules.get(moduleCode);
        if (config == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "业务模块不存在");
        }
        return config;
    }

    public synchronized List<RuntimeRecord> records(String moduleCode) {
        module(moduleCode);
        return new ArrayList<>(records.getOrDefault(moduleCode, List.of()));
    }

    public synchronized RuntimeRecord record(String moduleCode, String recordId) {
        module(moduleCode);
        return records.getOrDefault(moduleCode, List.of()).stream()
                .filter(item -> item.recordId().equals(recordId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在"));
    }

    public synchronized RuntimeRecord create(String moduleCode, RecordCreateRequest request) {
        BusinessModuleConfig module = module(moduleCode);
        String title = request == null || request.title() == null || request.title().isBlank()
                ? module.moduleName() + "记录"
                : request.title();
        Map<String, String> values = new LinkedHashMap<>();
        if (request != null && request.values() != null) {
            values.putAll(request.values());
        }
        RuntimeRecord created = new RuntimeRecord(
                "rec-" + UUID.randomUUID().toString().substring(0, 8),
                moduleCode,
                title,
                "draft",
                values,
                OffsetDateTime.now().toString()
        );
        records.computeIfAbsent(moduleCode, ignored -> new ArrayList<>()).add(0, created);
        try {
            saveRecords();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "记录保存失败", e);
        }
        return created;
    }

    private void loadModules() throws IOException {
        if (Files.exists(modulePath)) {
            List<BusinessModuleConfig> saved = objectMapper.readValue(modulePath.toFile(), new TypeReference<>() {
            });
            saved.forEach(item -> modules.put(item.moduleCode(), item));
        }
        if (modules.isEmpty()) {
            seedModules().forEach(item -> modules.put(item.moduleCode(), item));
            saveModules();
        }
    }

    private void loadRecords() throws IOException {
        if (Files.exists(recordPath)) {
            List<RuntimeRecord> saved = objectMapper.readValue(recordPath.toFile(), new TypeReference<>() {
            });
            saved.forEach(item -> records.computeIfAbsent(item.moduleCode(), ignored -> new ArrayList<>()).add(item));
        }
        if (records.isEmpty()) {
            seedRecords().forEach(item -> records.computeIfAbsent(item.moduleCode(), ignored -> new ArrayList<>()).add(item));
            saveRecords();
        }
    }

    private void saveModules() throws IOException {
        Files.createDirectories(modulePath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(modulePath.toFile(), modules());
    }

    private void saveRecords() throws IOException {
        Files.createDirectories(recordPath.getParent());
        List<RuntimeRecord> flat = records.values().stream().flatMap(List::stream).collect(Collectors.toList());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(recordPath.toFile(), flat);
    }

    private List<BusinessModuleConfig> seedModules() {
        return List.of(
                module("customer-record", "客户档案", "customer", "客户业务", "承载客户基础资料和关联流程"),
                module("service-ticket", "服务工单", "customer", "客户业务", "承载工单处理、审批和消息闭环"),
                module("material-request", "物料申请", "production", "生产协作", "示例审批型业务模块"),
                module("quality-check", "质量检查", "production", "生产协作", "示例检查记录和打印模板")
        );
    }

    private List<RuntimeRecord> seedRecords() {
        String now = OffsetDateTime.now().toString();
        return List.of(
                record("rec-customer-001", "customer-record", "华东重点客户", "active", Map.of("owner", "销售一组", "level", "A", "phone", "13800000000"), now),
                record("rec-ticket-001", "service-ticket", "设备巡检异常处理", "processing", Map.of("owner", "服务团队", "priority", "high"), now),
                record("rec-material-001", "material-request", "产线补料申请", "pending", Map.of("owner", "生产计划", "amount", "120"), now),
                record("rec-quality-001", "quality-check", "首件质量检查", "passed", Map.of("owner", "质检组", "batch", "QC-2026-001"), now)
        );
    }

    private BusinessModuleConfig module(String code, String name, String groupCode, String groupName, String purpose) {
        return new BusinessModuleConfig(
                code,
                name,
                groupCode,
                groupName,
                purpose,
                List.of(
                        new ModuleField("title", "标题", "text", true),
                        new ModuleField("owner", "负责人", "member", true),
                        new ModuleField("status", "状态", "dict", true),
                        new ModuleField("attachment", "附件", "file", false)
                ),
                List.of(
                        new ModuleAction("create", "新建", "primary"),
                        new ModuleAction("edit", "编辑", "secondary"),
                        new ModuleAction("submit-flow", "提交审批", "flow"),
                        new ModuleAction("export", "导出", "secondary")
                ),
                List.of(
                        new ModulePage("list", "列表页", "left-tabs-list"),
                        new ModulePage("detail", "详情页", "right-tabs-detail"),
                        new ModulePage("print", "打印页", "print-template")
                ),
                List.of(
                        new PrintTemplate("standard", "标准打印模板"),
                        new PrintTemplate("audit", "审批留痕模板")
                )
        );
    }

    private RuntimeRecord record(String id, String moduleCode, String title, String status, Map<String, String> values, String now) {
        return new RuntimeRecord(id, moduleCode, title, status, values, now);
    }
}