package com.unique.unexamine.examine.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 审批运行实例查询与节点办理接口模型。 */
public final class ExamineRuntimeDto {
    private ExamineRuntimeDto() {}

    /** 审批办理请求；支持 APPROVE、REJECT、TRANSFER、RETURN，CANCEL 由审批发起人或管理人员执行。 */
    public record ActionRequest(@NotBlank @Size(max = 100) String requestId,
                                @NotBlank @Size(max = 40) String actionCode,
                                String opinion, Map<String, Object> payload) {}

    /** 平台审批发起参数；发起人从登录会话读取，不能由请求体伪造。 */
    public record StartRequest(@jakarta.validation.constraints.NotNull Long examineId,
                               Map<String, Object> values) {}

    /** 审批实例及其节点执行历史。 */
    public record ExampleView(Long exampleId, Long examineId, Integer publishedVersion,
                              Long moduleId, Long recordId, String status,
                              LocalDateTime startTime, LocalDateTime finishTime,
                              List<NodeView> nodes, List<LogView> logs,
                              @Schema(description = "审批期间按节点顺序保存的字段变更历史")
                              List<FieldChangeView> fieldChanges) {}

    /** 审批实例节点和本轮处理人。 */
    public record NodeView(Long nodeId, String nodeCode, String nodeType, String status,
                           LocalDateTime startTime, LocalDateTime finishTime,
                           List<UserView> users, List<String> readableFieldKeys,
                           List<String> writableFieldKeys,
                           @Schema(description = "按本节点可见字段配置读取的审批运行快照值")
                           Map<String, Object> readableFieldValues) {}

    /** 审批实例中的实际处理人状态。 */
    public record UserView(Long userId, Long accountId, String externalId, Long applicationId,
                           String status, String opinion, LocalDateTime actionTime) {}

    /** 外部应用当前处理人的审批待办摘要，不包含其他审批人的资料。 */
    public record ExternalTodoView(
            @Schema(description = "当前处理人待办主键") Long userId,
            @Schema(description = "审批实例主键") Long exampleId,
            @Schema(description = "审批定义主键") Long examineId,
            @Schema(description = "当前审批节点主键") Long nodeId,
            @Schema(description = "节点编码") String nodeCode,
            @Schema(description = "节点轮次") Integer roundNo,
            @Schema(description = "节点类型") String nodeType,
            @Schema(description = "实例发起时间") LocalDateTime startTime) {}

    /** 系统记录审批的外部待办；定义及记录主键供后续受控查询和办理。 */
    public record SystemExternalTodoView(
            @Schema(description = "当前处理人待办主键") Long userId,
            @Schema(description = "审批实例主键") Long exampleId,
            @Schema(description = "审批定义主键") Long examineId,
            @Schema(description = "业务模块主键") Long moduleId,
            @Schema(description = "业务记录主键") Long recordId,
            @Schema(description = "审批节点主键") Long nodeId,
            @Schema(description = "节点编码") String nodeCode,
            @Schema(description = "节点轮次") Integer roundNo,
            @Schema(description = "节点类型") String nodeType,
            @Schema(description = "实例发起时间") LocalDateTime startTime) {}

    /** 审批实例不可变操作日志。 */
    public record LogView(Long logId, Long nodeId, Long accountId, String eventCode,
                          String detailJson, LocalDateTime createTime) {}

    /** 审批运行期间的字段变更链；业务主表在审批终态前不改写。 */
    public record FieldChangeView(@Schema(description = "字段英文 key") String fieldKey,
                                  @Schema(description = "修改前值") Object beforeValue,
                                  @Schema(description = "修改后值") Object afterValue,
                                  @Schema(description = "发生修改的实例节点") Long nodeId,
                                  @Schema(description = "发生修改的节点编码") String nodeCode,
                                  @Schema(description = "实际修改人账号") Long accountId,
                                  @Schema(description = "外部修改人所属应用主键") Long applicationId,
                                  @Schema(description = "应用命名空间内的外部修改人标识") String externalId,
                                  @Schema(description = "修改时间") LocalDateTime changeTime) {}

    /** 办理结果和刷新后的审批实例。 */
    public record ActionView(Long exampleId, Long nodeId, Long userId, String actionCode,
                             String resultCode, ExampleView example) {}
}
