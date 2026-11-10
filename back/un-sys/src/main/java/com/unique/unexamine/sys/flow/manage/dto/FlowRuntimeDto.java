package com.unique.unexamine.sys.flow.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Flow 运行实例查询与节点办理接口模型。 */
public final class FlowRuntimeDto {
    private FlowRuntimeDto() {}

    /** 平台管理员发起独立 Flow 时的发布版本入口和幂等参数。 */
    public record StartRequest(@jakarta.validation.constraints.NotNull Long flowId,
                               @NotBlank @Size(max = 100) String requestId,
                               @Size(max = 100) Map<String, Object> values) {}

    /** 执行一个流程节点；审批通过、拒绝和取消不属于 Flow 操作。 */
    public record ActionRequest(@NotBlank @Size(max = 100) String requestId,
                                @NotBlank @Size(max = 40) String actionCode,
                                Map<String, Object> payload) {}

    /** 当前 Flow 实例及其仍在等待处理的节点。 */
    public record ExampleView(Long exampleId, Long flowId, Long flowVersionId,
                              @io.swagger.v3.oas.annotations.media.Schema(description = "模块 Flow 命中的绑定主键；平台 Flow 为空")
                              Long bindingId, Long moduleId,
                              Long recordId, String status, LocalDateTime startTime,
                              LocalDateTime finishTime, List<TaskView> tasks) {}

    /** Flow 节点待办及已解析的内部、外部候选处理人。 */
    public record TaskView(Long taskId, String nodeCode, Integer roundNo, String taskType,
                           String status, LocalDateTime dueTime, List<Long> candidateAccountIds,
                           List<ExternalCandidateView> candidateExternalUsers) {}

    /** 外部身份当前可办理的待办摘要，不包含其他候选人的身份资料。 */
    public record ExternalTodoView(
            @io.swagger.v3.oas.annotations.media.Schema(description = "待办主键") Long taskId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "流程实例主键") Long exampleId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "流程定义主键") Long flowId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "节点编码") String nodeCode,
            @io.swagger.v3.oas.annotations.media.Schema(description = "节点轮次") Integer roundNo,
            @io.swagger.v3.oas.annotations.media.Schema(description = "待办类型") String taskType,
            @io.swagger.v3.oas.annotations.media.Schema(description = "待办到期时间") LocalDateTime dueTime,
            @io.swagger.v3.oas.annotations.media.Schema(description = "实例发起时间") LocalDateTime startTime) {}

    /** 系统模块 Flow 的外部待办；关联记录与绑定主键供后续授权查询和办理。 */
    public record SystemExternalTodoView(
            @io.swagger.v3.oas.annotations.media.Schema(description = "待办主键") Long taskId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "流程实例主键") Long exampleId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "Flow 绑定主键") Long bindingId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "业务模块主键") Long moduleId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "业务记录主键") Long recordId,
            @io.swagger.v3.oas.annotations.media.Schema(description = "节点编码") String nodeCode,
            @io.swagger.v3.oas.annotations.media.Schema(description = "节点轮次") Integer roundNo,
            @io.swagger.v3.oas.annotations.media.Schema(description = "待办类型") String taskType,
            @io.swagger.v3.oas.annotations.media.Schema(description = "实例发起时间") LocalDateTime startTime) {}

    /** 外部候选人的应用命名空间和外部身份标识。 */
    public record ExternalCandidateView(Long applicationId, String externalId) {}

    /** 办理后的幂等操作结果。 */
    public record ActionView(Long actionId, Long exampleId, Long taskId, String actionCode,
                             String resultCode, ExampleView example) {}
}
