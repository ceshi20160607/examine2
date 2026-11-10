package com.unique.unexamine.examine.manage.strategy;

/**
 * 审批节点策略。
 *
 * <p>节点类型只通过策略扩展，运行服务不再为普通审批、条件容器、抄送和 API 节点堆叠类型分支；新增
 * 节点类型时实现本接口并注册为 Spring Bean 即可。需要自动执行的节点同时实现
 * {@link ExamineAutoNodeStrategy}。</p>
 */
public interface ExamineNodeStrategy {
    /** 是否处理指定节点类型。 */
    boolean supports(String nodeType);

    /** 节点是否需要处理人提交动作。抄送节点等自动节点返回 false。 */
    boolean interactive();

    /** 当前节点是否允许提交指定动作。 */
    boolean supportsAction(String actionCode);

    /** 根据会签规则判断本轮是否完成。 */
    boolean complete(String approvalMode, long pendingUserCount);

    /** 将办理动作转换为实例处理人状态。 */
    String userStatus(String actionCode);

    /** 将办理动作转换为实例节点状态。 */
    String nodeStatus(String actionCode);
}
