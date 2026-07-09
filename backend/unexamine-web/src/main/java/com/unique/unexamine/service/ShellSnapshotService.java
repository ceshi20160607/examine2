package com.unique.unexamine.service;

import com.unique.unexamine.core.shell.BusinessModule;
import com.unique.unexamine.core.shell.ModuleGroup;
import com.unique.unexamine.core.shell.ShellActionItem;
import com.unique.unexamine.core.shell.ShellMetric;
import com.unique.unexamine.core.shell.ShellNavItem;
import com.unique.unexamine.core.shell.ShellSnapshot;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ShellSnapshotService {
    public ShellSnapshot snapshot(String requestedContext) {
        return snapshot(requestedContext, "system".equalsIgnoreCase(requestedContext) ? "示例业务系统" : "平台");
    }

    public ShellSnapshot snapshot(String requestedContext, String activeName) {
        String context = "system".equalsIgnoreCase(requestedContext) ? "system" : "platform";
        return new ShellSnapshot(
                context,
                activeName,
                navigation(context),
                adminNavigation(),
                moduleGroups(),
                flowManagement(),
                applicationGateway(),
                dashboardMetrics(context),
                workItems(context),
                todoItems(context),
                messageItems(context),
                aiItems(context),
                layoutRules()
        );
    }

    private List<ShellNavItem> navigation(String context) {
        return List.of(
                nav("workbench", "工作台", context, "按当前上下文展示统计和仪表盘"),
                nav("flow", "Flow", context, "配置流程、节点、审批人、数据源和外部联动"),
                nav("application", "应用", context, "授权系统内通信和对外服务暴露"),
                nav("work", "工作", context, "工作统计、项目任务、普通任务、日报"),
                nav("ai", "AI", context, "AI 解析和辅助扩展入口"),
                nav("todo", "待办", context, "配置驱动的提醒、审批、今日需回复"),
                nav("message", "消息", context, "与我相关的消息和操作反馈"),
                nav("admin", "后台", context, "进入当前上下文后台管理"),
                nav("profile", "个人信息", context, "个人资料、切换系统、退出")
        );
    }

    private List<ShellNavItem> adminNavigation() {
        return List.of(
                nav("basic", "基础信息", "admin", "系统基础信息和默认工作台配置"),
                nav("org", "组织架构", "admin", "部门、成员、外部身份映射"),
                nav("role", "角色管理", "admin", "菜单、动作、字段和数据范围权限"),
                nav("module", "模块管理", "admin", "模块组、模块、字段、页面、动作、打印模板"),
                nav("flow-admin", "流程管理", "admin", "流程图、审批节点、动作、发布检查"),
                nav("app-admin", "应用管理", "admin", "应用授权、scope、SecretRef、回调、限流"),
                nav("dashboard", "仪表盘管理", "admin", "工作台统计组件和数据源绑定"),
                nav("dict", "字典管理", "admin", "字典项、颜色、状态、引用影响"),
                nav("work-config", "工作配置", "admin", "项目任务、普通任务、日报字段和看板"),
                nav("ai-config", "AI配置", "admin", "模型、策略、权限、脱敏、审计"),
                nav("datasource", "数据源配置", "admin", "外部数据源和查询视图"),
                nav("log", "日志管理", "admin", "登录日志、业务日志、导入导出和任务日志")
        );
    }

    private List<ModuleGroup> moduleGroups() {
        return List.of(
                new ModuleGroup("customer", "客户业务", List.of(
                        module("customer-record", "客户档案", "承载客户基础资料和关联流程"),
                        module("service-ticket", "服务工单", "承载工单处理、审批和消息闭环")
                )),
                new ModuleGroup("production", "生产协作", List.of(
                        module("material-request", "物料申请", "示例审批型业务模块"),
                        module("quality-check", "质量检查", "示例检查记录和打印模板")
                ))
        );
    }

    private List<String> flowManagement() {
        return List.of("流程图", "业务模块关联", "外部系统关联", "审批人", "数据源", "审批节点", "动作", "发布检查", "运行读回");
    }

    private List<String> applicationGateway() {
        return List.of("应用凭证", "授权 scope", "关联 Flow", "关联系统服务", "回调", "限流", "审计", "停用");
    }

    private List<ShellMetric> dashboardMetrics(String context) {
        if ("system".equals(context)) {
            return List.of(
                    metric("module-records", "业务记录", "128", "+12 本周", "当前系统内模块记录、附件和审批状态的统计口径"),
                    metric("pending-approval", "待处理审批", "9", "3 个临期", "来自流程节点和待办模板的审批事项"),
                    metric("open-work", "进行中工作", "24", "7 个今日到期", "项目任务、普通任务和日报草稿的汇总"),
                    metric("integration-health", "对外服务", "6", "2 个 Flow 授权", "应用授权、OpenAPI 调用和 SecretRef 状态")
            );
        }
        return List.of(
                metric("systems", "授权系统", "12", "+2 本月", "平台账号可访问系统、启停和授权申请统计"),
                metric("platform-flows", "平台 Flow", "7", "1 个失败重试", "平台级流程、任务和外部系统编排统计"),
                metric("platform-apps", "平台应用", "5", "3 个启用", "平台应用授权、scope 和调用审计概览"),
                metric("ops-health", "运维健康", "UP", "最近备份 02:00", "版本、部署、备份、限流和日志健康状态")
        );
    }

    private List<ShellActionItem> workItems(String context) {
        if ("system".equals(context)) {
            return List.of(
                    item("project-vehicle-crm", "客户回访项目", "跨客户档案和服务工单的项目任务", "项目任务", "进行中", "销售一组", "今天 18:00", List.of("项目", "客户业务")),
                    item("task-qc-follow", "首件质量复核", "质量检查记录审批前的普通任务", "普通任务", "待处理", "质检组", "明天 10:00", List.of("质量", "临期")),
                    item("daily-admin", "今日日报草稿", "从已处理待办、消息和业务日志生成，提交前人工确认", "日报", "草稿", "当前用户", "今天 20:00", List.of("日报", "AI草稿"))
            );
        }
        return List.of(
                item("platform-release", "平台发布窗口确认", "检查健康、日志和授权变更后安排发布", "平台任务", "进行中", "平台管理员", "今天 17:30", List.of("发布", "运维")),
                item("support-tenant", "租户授权申请处理", "平台成员提交的系统访问申请", "授权任务", "待审核", "平台管理员", "今天 16:00", List.of("授权", "待办")),
                item("ops-daily", "平台运营日报", "汇总系统健康、任务失败和应用调用情况", "日报", "草稿", "平台成员", "今天 20:00", List.of("日报", "日志"))
        );
    }

    private List<ShellActionItem> todoItems(String context) {
        if ("system".equals(context)) {
            return List.of(
                    item("todo-approval-customer", "客户档案审批", "客户档案审批 Flow 等待销售主管处理", "待我审批", "待处理", "销售主管", "今天 15:30", List.of("Flow", "客户档案")),
                    item("todo-reply-ticket", "今日需回复工单", "服务工单被 @ 当前用户，需要今日回复", "今日需回复", "临期", "当前用户", "今天 18:00", List.of("工单", "@我")),
                    item("todo-remind-qc", "质检复核提醒", "后台工作配置提前 1 天生成提醒", "提醒动作", "未读", "质检组", "明天 09:00", List.of("提醒", "质量"))
            );
        }
        return List.of(
                item("todo-platform-auth", "系统授权申请", "平台普通成员申请进入销售协作系统", "待我审批", "待处理", "平台管理员", "今天 16:00", List.of("授权", "系统切换")),
                item("todo-flow-failed", "平台 Flow 失败重试", "外部同步节点失败，等待管理员确认重试", "任务失败", "失败", "平台管理员", "今天 14:20", List.of("Flow", "重试")),
                item("todo-release-remind", "发布窗口提醒", "后台配置提前 2 小时提醒发布检查", "提醒动作", "未读", "平台管理员", "今天 17:00", List.of("发布", "提醒"))
        );
    }

    private List<ShellActionItem> messageItems(String context) {
        if ("system".equals(context)) {
            return List.of(
                    item("msg-mentioned", "@我的业务评论", "服务工单中有人 @ 我补充处理意见", "@我", "未读", "服务经理", "今天 13:45", List.of("@我", "工单")),
                    item("msg-approval-copy", "审批抄送", "物料申请 Flow 已通过并抄送给当前用户", "抄送", "已读", "生产主管", "今天 11:20", List.of("审批", "抄送")),
                    item("msg-export-done", "导出任务完成", "客户档案导出文件已生成，可在任务日志查看", "导入导出", "未读", "系统", "今天 10:10", List.of("导出", "后台任务"))
            );
        }
        return List.of(
                item("msg-platform-auth", "授权申请已提交", "平台授权申请进入待办队列并记录日志", "授权", "未读", "系统", "今天 12:00", List.of("授权", "待办")),
                item("msg-platform-log", "系统健康检查完成", "平台体检通过，备份和应用授权状态已写入日志", "运维", "已读", "系统", "今天 09:30", List.of("健康", "日志")),
                item("msg-agent", "平台 Agent 已生成任务", "Agent 只生成平台任务、平台消息和平台日志", "AI", "未读", "平台 Agent", "今天 09:10", List.of("AI", "任务"))
        );
    }

    private List<ShellActionItem> aiItems(String context) {
        if ("system".equals(context)) {
            return List.of(
                    item("ai-query", "查询权限内业务数据", "按系统成员、角色、字段权限和数据范围查询模块统计", "系统 Agent", "可用", "AI配置", "策略 v1", List.of("统计", "脱敏")),
                    item("ai-daily", "生成日报草稿", "从任务、待办、消息和业务日志整理草稿，提交前人工确认", "工作 Agent", "可用", "AI配置", "策略 v1", List.of("日报", "人工确认")),
                    item("ai-config-draft", "辅助生成配置草稿", "辅助生成模块字段、流程草稿和字典项，不直接发布", "配置 Agent", "受控", "系统管理员", "策略 v1", List.of("配置", "审计"))
            );
        }
        return List.of(
                item("ai-platform-health", "平台健康问答", "查询系统健康、授权、平台 Flow 和模型额度", "平台 Agent", "可用", "平台AI配置", "策略 v1", List.of("平台", "健康")),
                item("ai-platform-task", "生成平台任务", "确认后只写入平台任务、平台消息和平台日志", "平台 Agent", "受控", "平台管理员", "策略 v1", List.of("任务", "审计")),
                item("ai-policy", "模型授权策略", "平台后台配置模型、额度、脱敏和外发限制", "后台配置", "草稿", "平台管理员", "策略 v1", List.of("模型", "权限"))
        );
    }

    private List<String> layoutRules() {
        return List.of("列表主界面使用左侧标签页/分类", "详情使用右侧标签页/工作区", "行点击打开详情并保留列表上下文");
    }

    private ShellMetric metric(String code, String label, String value, String trend, String description) {
        return new ShellMetric(code, label, value, trend, description);
    }

    private ShellActionItem item(String code, String title, String description, String category, String status, String owner, String dueAt, List<String> tags) {
        return new ShellActionItem(code, title, description, category, status, owner, dueAt, tags);
    }

    private ShellNavItem nav(String code, String label, String scope, String description) {
        return new ShellNavItem(code, label, scope, description);
    }

    private BusinessModule module(String code, String label, String purpose) {
        return new BusinessModule(code, label, purpose, List.of("字段", "动作", "页面", "打印模板", "权限", "导入导出"));
    }
}
