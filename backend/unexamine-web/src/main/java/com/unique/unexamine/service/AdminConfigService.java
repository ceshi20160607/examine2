package com.unique.unexamine.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.admin.AdminConfigItem;
import com.unique.unexamine.core.admin.AdminConfigUpdateRequest;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminConfigService {
    private final ObjectMapper objectMapper;
    private final Path storePath = Path.of("data", "admin-config.json");
    private final Map<String, AdminConfigItem> configs = new LinkedHashMap<>();

    public AdminConfigService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() throws IOException {
        if (Files.exists(storePath)) {
            List<AdminConfigItem> saved = objectMapper.readValue(storePath.toFile(), new TypeReference<>() {
            });
            saved.forEach(item -> configs.put(item.code(), item));
        }
        if (configs.isEmpty()) {
            seedDefaults().forEach(item -> configs.put(item.code(), item));
            save();
        }
    }

    public synchronized List<AdminConfigItem> list() {
        return new ArrayList<>(configs.values());
    }

    public synchronized AdminConfigItem get(String code) {
        AdminConfigItem item = configs.get(code);
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "配置项不存在");
        }
        return item;
    }

    public synchronized AdminConfigItem update(String code, AdminConfigUpdateRequest request) {
        AdminConfigItem current = get(code);
        String description = request == null || request.description() == null || request.description().isBlank()
                ? current.description()
                : request.description();
        String status = request == null || request.status() == null || request.status().isBlank()
                ? current.status()
                : request.status();
        AdminConfigItem updated = new AdminConfigItem(
                current.code(),
                current.label(),
                current.group(),
                description,
                status,
                current.fields(),
                OffsetDateTime.now().toString()
        );
        configs.put(code, updated);
        try {
            save();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "配置保存失败", e);
        }
        return updated;
    }

    private void save() throws IOException {
        Files.createDirectories(storePath.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), list());
    }

    private List<AdminConfigItem> seedDefaults() {
        String now = OffsetDateTime.now().toString();
        return List.of(
                item("basic", "系统基础信息", "基础", "维护系统名称、默认工作台、登录展示和上下文统计口径", List.of("系统名称", "默认首页", "统计口径"), now),
                item("org", "组织架构", "权限", "维护部门、成员、外部身份映射和成员绑定", List.of("部门树", "成员", "身份映射"), now),
                item("role", "角色管理", "权限", "维护菜单、动作、字段和数据范围权限", List.of("菜单权限", "动作权限", "字段权限", "数据范围"), now),
                item("module", "模块管理", "业务模块", "维护模块组、模块、字段、动作、页面和打印模板", List.of("模块组", "字段", "动作", "页面", "打印模板"), now),
                item("flow", "流程管理", "Flow", "维护流程图、节点、审批人、数据源、动作和发布检查", List.of("流程图", "审批节点", "数据源", "动作", "发布检查"), now),
                item("application", "应用管理", "应用", "维护应用授权、scope、SecretRef、回调、限流和审计", List.of("scope", "SecretRef", "回调", "限流", "审计"), now),
                item("dashboard", "仪表盘管理", "工作台", "配置平台或系统工作台统计组件和数据源", List.of("统计组件", "数据源", "显示规则"), now),
                item("dict", "字典管理", "基础", "维护状态、颜色、图标、排序和引用影响", List.of("字典项", "颜色", "图标", "引用影响"), now),
                item("work", "工作配置", "工作", "配置项目任务、普通任务、日报字段和看板", List.of("项目任务字段", "普通任务字段", "日报字段", "看板"), now),
                item("app-config", "应用配置", "应用", "配置内部通信和对外开放策略", List.of("授权策略", "开放服务", "停用策略"), now),
                item("ai", "AI配置", "AI", "配置模型、策略、权限、脱敏和审计", List.of("模型", "策略", "脱敏", "审计"), now),
                item("datasource", "数据源配置", "集成", "配置外部数据源、查询视图和引用范围", List.of("连接", "查询视图", "引用范围"), now),
                item("log", "日志管理", "运维", "查看登录日志、业务日志、导入导出和后台任务", List.of("登录日志", "业务日志", "导入导出", "后台任务"), now)
        );
    }

    private AdminConfigItem item(String code, String label, String group, String description, List<String> fields, String now) {
        return new AdminConfigItem(code, label, group, description, "draft", fields, now);
    }
}