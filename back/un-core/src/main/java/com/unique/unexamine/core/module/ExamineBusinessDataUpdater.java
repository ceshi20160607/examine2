package com.unique.unexamine.core.module;

/** 审批终态将运行快照字段提交到模块业务数据的跨模块端口。 */
public interface ExamineBusinessDataUpdater {
    /**
     * 审批运行阶段先校验自动节点准备写入的字段，不能等到终态才让外部响应污染运行快照。
     * 没有业务记录的审批（例如平台审批）由实现方按作用域忽略此调用。
     */
    void validate(ExamineBusinessUpdateContext context);

    /** 按模块字段规则保存审批自动节点或人工节点产生的业务值。 */
    void update(ExamineBusinessUpdateContext context);
}
