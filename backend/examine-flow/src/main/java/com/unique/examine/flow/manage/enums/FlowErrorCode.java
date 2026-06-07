package com.unique.examine.flow.manage.enums;

import com.unique.examine.core.error.ErrorCode;
import com.unique.examine.core.error.ErrorCodeNamespace;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 流程错误码。
 */
@Getter
@RequiredArgsConstructor
public enum FlowErrorCode implements ErrorCode {

    /** 流程模板不存在。 */
    TEMPLATE_NOT_FOUND("FLOW_TEMPLATE_NOT_FOUND", "流程模板不存在", HttpStatus.NOT_FOUND, false),

    /** 流程模板发布检查失败。 */
    TEMPLATE_CHECK_FAILED("FLOW_TEMPLATE_CHECK_FAILED", "流程模板发布检查失败", HttpStatus.CONFLICT, false),

    /** 流程模板未发布。 */
    TEMPLATE_NOT_PUBLISHED("FLOW_TEMPLATE_NOT_PUBLISHED", "流程模板未发布", HttpStatus.CONFLICT, false),

    /** 流程绑定不存在。 */
    BINDING_MISSING("FLOW_BINDING_MISSING", "流程绑定不存在", HttpStatus.CONFLICT, false),

    /** 流程实例不存在。 */
    INSTANCE_NOT_FOUND("FLOW_INSTANCE_NOT_FOUND", "流程实例不存在", HttpStatus.NOT_FOUND, false),

    /** 流程实例状态冲突。 */
    INSTANCE_STATUS_CONFLICT("FLOW_INSTANCE_STATUS_CONFLICT", "流程实例状态冲突", HttpStatus.CONFLICT, false),

    /** 流程任务不存在。 */
    TASK_NOT_FOUND("FLOW_TASK_NOT_FOUND", "流程任务不存在", HttpStatus.NOT_FOUND, false),

    /** 流程任务已处理。 */
    TASK_ALREADY_HANDLED("FLOW_TASK_ALREADY_HANDLED", "流程任务已处理", HttpStatus.CONFLICT, false),

    /** 当前成员不是任务候选人或处理人。 */
    TASK_ACTOR_INVALID("FLOW_TASK_ACTOR_INVALID", "当前成员不是任务候选人或处理人", HttpStatus.FORBIDDEN, false),

    /** 流程动作原因必填。 */
    ACTION_REASON_REQUIRED("FLOW_ACTION_REASON_REQUIRED", "流程动作原因必填", HttpStatus.BAD_REQUEST, false);

    private final String code;

    private final String message;

    private final HttpStatus httpStatus;

    private final boolean retryable;

    @Override
    public ErrorCodeNamespace getNamespace() {
        return ErrorCodeNamespace.FLOW;
    }
}
