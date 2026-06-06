package com.unique.examine.flow.manage.enums;

import lombok.Getter;

/**
 * 流程管理错误码。
 */
@Getter
public enum FlowManageErrorCode {

    PARAM_REQUIRED("FLOW_PARAM_REQUIRED", "流程参数缺失"),
    DATA_NOT_FOUND("FLOW_DATA_NOT_FOUND", "流程数据不存在"),
    STATUS_INVALID("FLOW_STATUS_INVALID", "流程状态不允许当前操作"),
    ACTION_TYPE_INVALID("FLOW_ACTION_TYPE_INVALID", "流程任务处理动作不合法"),
    TASK_ASSIGNEE_INVALID("FLOW_TASK_ASSIGNEE_INVALID", "当前账号无权处理该任务");

    private final String code;
    private final String message;

    FlowManageErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
