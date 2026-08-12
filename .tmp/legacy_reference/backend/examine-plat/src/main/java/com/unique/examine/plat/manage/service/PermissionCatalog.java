package com.unique.examine.plat.manage.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class PermissionCatalog {
    public static final List<Definition> PLATFORM = List.of(
            definition("platform.runtime.access", "进入平台工作台", "SHELL"),
            definition("platform.admin.access", "进入平台后台", "SHELL"),
            definition("platform.system.manage", "管理系统生命周期", "ACTION"),
            definition("platform.organization.manage", "管理平台组织账号", "ACTION"),
            definition("platform.role.manage", "管理平台角色权限", "ACTION"),
            definition("platform.permission.explain", "预览平台有效权限", "ACTION"),
            definition("platform.ai.agent.use", "Use platform AI Agent", "MENU"),
            definition("platform.ai.policy.manage", "Manage platform AI Agent policy", "ACTION"),
            definition("platform.task.read", "Read personal platform tasks", "DATA"),
            definition("platform.task.create", "Create personal platform tasks", "ACTION"),
            definition("platform.task.manage", "Manage personal platform tasks", "ACTION"),
            definition("platform.openapi.application.manage", "Manage platform OpenAPI applications", "ACTION"),
            definition("platform.dashboard.manage", "Manage platform dashboards", "ACTION"),
            definition("platform.dashboard.view", "View platform dashboard", "MENU"),
            definition("platform.flow.manage", "Manage platform flows", "ACTION"),
            definition("platform.flow.read", "Read platform flows", "MENU"),
            definition("platform.flow.start", "Start platform flows", "ACTION"),
            definition("platform.audit.view", "查看平台审计", "ACTION"),
            definition("platform.settings.manage", "管理平台全局设置", "ACTION")
    );

    public static final List<Definition> SYSTEM = List.of(
            definition("module.config.manage", "Manage module configuration", "ACTION"),
            definition("flow.external-task.work", "Process external flow tasks", "ACTION"),
            definition("work.project.access", "Access work projects", "MENU"),
            definition("work.project.create", "Create work projects", "ACTION"),
            definition("work.project.manage", "Manage all work projects", "DATA"),
            definition("work.report.access", "Access daily work reports", "MENU"),
            definition("work.report.create", "Create daily work reports", "ACTION"),
            definition("work.report.manage", "Manage team daily work reports", "DATA"),
            definition("system.runtime.access", "进入系统业务页", "SHELL"),
            definition("system.admin.access", "进入系统后台", "SHELL"),
            definition("system.workbench.view", "查看系统工作台", "MENU"),
            definition("runtime.saved_view.manage", "管理个人保存视图", "ACTION"),
            definition("runtime.favorite.manage", "管理个人收藏", "ACTION"),
            definition("ai.agent.use", "使用系统 AI Agent", "MENU"),
            definition("ai.policy.manage", "管理系统 AI Agent 策略", "ACTION"),
            definition("system.settings.manage", "管理系统设置", "ACTION"),
            definition("system.settings.description.edit", "编辑系统说明", "FIELD"),
            definition("system.tenant.manage", "管理租户", "ACTION"),
            definition("system.organization.manage", "管理系统组织", "ACTION"),
            definition("system.member.manage", "管理系统成员", "ACTION"),
            definition("system.member.identity.view", "查看成员账号身份", "FIELD"),
            definition("system.member.list", "查看授权范围内成员", "DATA"),
            definition("system.role.manage", "管理系统角色权限", "ACTION"),
            definition("system.permission.explain", "预览系统有效权限", "ACTION"),
            definition("system.audit.view", "查看系统审计日志", "ACTION"),
            definition("system.access.review", "审核成员和租户申请", "ACTION"),
            definition("openapi.application.manage", "管理 OpenAPI 应用", "ACTION"),
            definition("flow.definition.manage", "管理流程定义", "ACTION"),
            definition("flow.instance.start", "发起流程实例", "ACTION"),
            definition("flow.instance.decide", "处理流程任务", "ACTION"),
            definition("flow.instance.read", "查看流程实例", "DATA"),
            definition("flow.instance.withdraw", "撤回本人发起的流程实例", "ACTION"),
            definition("flow.instance.terminate", "终止待处理流程实例", "ACTION"),
            definition("flow.instance.urge", "催办待处理流程实例", "ACTION"),
            definition("flow.instance.comment", "评论流程实例", "ACTION"),
            definition("flow.instance.transfer", "转交当前流程审批任务", "ACTION"),
            definition("flow.instance.add-sign", "在当前流程步骤前后加签", "ACTION"),
            definition("flow.instance.return", "退回当前流程审批任务", "ACTION"),
            definition("flow.instance.claim", "认领开放流程审批任务", "ACTION"),
            definition("flow.instance.cancel-claim", "取消当前流程任务认领", "ACTION"),
            definition("flow.instance.reduce-sign", "移除未来流程审批步骤", "ACTION"),
            definition("flow.instance.copy", "抄送流程实例", "ACTION"),
            definition("work.task.access", "进入工作任务", "MENU"),
            definition("work.task.create", "创建工作任务", "ACTION"),
            definition("work.task.manage", "管理全部工作任务", "DATA"),
            definition("event.message.access", "进入消息中心", "MENU"),
            definition("event.template.manage", "管理系统消息模板", "ACTION"),
            definition("file.create", "上传文件", "ACTION"),
            definition("file.read", "查看文件", "DATA"),
            definition("file.reference", "关联文件", "ACTION"),
            definition("file.manage", "管理全部文件", "DATA")
    );

    private static final Map<String, Definition> BY_CODE = java.util.stream.Stream
            .concat(PLATFORM.stream(), SYSTEM.stream())
            .collect(Collectors.toUnmodifiableMap(Definition::code, Function.identity()));

    private PermissionCatalog() {
    }

    public static Definition require(String code) {
        var definition = BY_CODE.get(code);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown permission code: " + code);
        }
        return definition;
    }

    private static Definition definition(String code, String name, String resourceType) {
        return new Definition(code, name, resourceType);
    }

    public record Definition(String code, String name, String resourceType) {
    }
}
