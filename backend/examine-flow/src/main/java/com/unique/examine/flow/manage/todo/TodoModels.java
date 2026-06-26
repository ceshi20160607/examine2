package com.unique.examine.flow.manage.todo;

import com.unique.examine.core.api.PageResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 待办工作台 API 模型。
 */
public final class TodoModels {

    private TodoModels() {
    }

    /**
     * 待办查询请求。
     *
     * @param scope 待办范围：platform/system
     * @param typeCode 左侧类型树选中的类型编码
     * @param keyword 关键字
     * @param status 待办状态
     * @param priority 优先级
     * @param assigneeId 处理人 ID
     * @param dueRange 到期时间范围表达式
     * @param traceId 链路追踪 ID
     */
    public record TodoSearchRequest(String scope, String typeCode, String keyword, String status, String priority,
                                    String assigneeId, String dueRange, String traceId) {
    }

    /**
     * 待办工作台返回结果。
     *
     * @param scope 待办范围
     * @param systemId 系统 ID，平台待办为空
     * @param tenantId 租户 ID
     * @param selectedTypeCode 当前选中的类型编码
     * @param typeTree 左侧待办类型树
     * @param page 右侧待办分页列表
     * @param traceId 链路追踪 ID
     */
    public record TodoSearchResult(String scope, String systemId, String tenantId, String selectedTypeCode,
                                   List<TodoTypeNode> typeTree, PageResult<TodoRowView> page, String traceId) {
    }

    /**
     * 待办动作请求。
     *
     * @param idempotencyKey 幂等键
     * @param comment 审批通过意见
     * @param reason 拒绝或转交原因
     * @param transferTargetId 转交目标系统成员 ID
     * @param transferTargetName 转交目标系统成员名称
     */
    public record TodoActionRequest(String idempotencyKey, String comment, String reason,
                                    String transferTargetId, String transferTargetName) {
    }

    /**
     * 待办类型树节点。
     *
     * @param typeCode 类型编码
     * @param typeName 类型名称
     * @param count 当前类型待办数量
     * @param icon 前端图标编码
     * @param children 子类型节点
     */
    public record TodoTypeNode(String typeCode, String typeName, Integer count, String icon,
                               List<TodoTypeNode> children) {
    }

    /**
     * 待办行视图。
     *
     * @param todoId 待办 ID
     * @param scope 待办范围
     * @param type 待办类型
     * @param title 待办标题
     * @param sourceName 来源名称
     * @param moduleCode 业务模块编码
     * @param objectTitle 关联对象标题
     * @param assigneeId 处理人 ID
     * @param dueAt 到期时间
     * @param status 状态
     * @param priority 优先级
     * @param target 跳转目标
     * @param primaryAction 主动作
     * @param actionPermissions 行动作权限
     * @param traceId 链路追踪 ID
     */
    public record TodoRowView(String todoId, String scope, String type, String title, String sourceName,
                              String moduleCode, String objectTitle, String assigneeId, LocalDateTime dueAt,
                              String status, String priority, TodoTarget target, TodoAction primaryAction,
                              List<TodoAction> actionPermissions, String traceId) {
    }

    /**
     * 待办跳转目标。
     *
     * @param targetType 目标类型
     * @param routeName 前端路由名称
     * @param params 路由参数
     * @param requiresSystemSwitch 是否需要先完成系统切换
     */
    public record TodoTarget(String targetType, String routeName, Map<String, String> params,
                             boolean requiresSystemSwitch) {
    }

    /**
     * 待办动作。
     *
     * @param actionCode 动作编码
     * @param actionName 动作名称
     * @param enabled 是否可执行
     * @param disabledReason 禁用原因
     * @param resultDrawerCode 动作结果承接抽屉编码
     */
    public record TodoAction(String actionCode, String actionName, boolean enabled, String disabledReason,
                             String resultDrawerCode) {
    }

    /**
     * 待办动作结果。
     *
     * @param todoId 待办 ID
     * @param actionCode 动作编码
     * @param status 动作后的待办状态
     * @param message 结果说明
     * @param traceId 链路追踪 ID
     * @param auditLogId 审计日志 ID
     * @param target 后续跳转目标
     * @param operatedAt 操作时间
     */
    public record TodoActionResult(String todoId, String actionCode, String status, String message,
                                   String traceId, String auditLogId, TodoTarget target,
                                   LocalDateTime operatedAt) {
    }
}
