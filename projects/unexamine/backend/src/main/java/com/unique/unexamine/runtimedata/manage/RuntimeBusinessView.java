package com.unique.unexamine.runtimedata.manage;

/** 审批业务只读描述；提交内容与当前正式记录独立授权，空值不能由旧流程变量补回。 */
public record RuntimeBusinessView(String moduleCode, String actionCode,
        RuntimeRecordView proposed, RuntimeRecordView current, String message, RuntimeRecordView submitted) {
    /** 未发生节点字段提交时保留原两份业务投影，不以流程变量补造提交内容。 */
    public RuntimeBusinessView(String moduleCode, String actionCode,
                              RuntimeRecordView proposed, RuntimeRecordView current, String message) {
        this(moduleCode, actionCode, proposed, current, message, null);
    }
}
