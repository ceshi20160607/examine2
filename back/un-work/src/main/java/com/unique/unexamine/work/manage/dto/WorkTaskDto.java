package com.unique.unexamine.work.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** WorkTask 的请求与返回模型。 */
public final class WorkTaskDto {
    private WorkTaskDto() {}

    /** 普通任务可先只填一句标题，描述和截止时间可后续补充。 */
    public record CreateRequest(@NotBlank @Size(max=200) String title,
                                @Size(max=10000) String description, LocalDateTime dueTime) {}

    /** 项目任务可指定所属任务组及已有项目成员作为负责人。 */
    public record ProjectCreateRequest(@NotBlank @Size(max=200) String title,
            @Size(max=10000) String description,LocalDateTime dueTime,
            Long taskGroupId,Long ownerAccountId) {}

    /** 状态变更须给出目标状态；阻塞和取消原因保存在操作历史中。 */
    public record StatusRequest(@NotBlank String status, @Size(max=2000) String reason) {}

    /** 编辑普通任务的基础内容；截止时间为空表示清除。 */
    public record EditRequest(@NotBlank @Size(max=200) String title,
                              @Size(max=10000) String description, LocalDateTime dueTime) {}

    /** 指派和转交的目标必须是当前作用域内可用的账号。 */
    public record TransferRequest(@NotNull Long ownerAccountId, @Size(max=2000) String reason) {}

    /** 任务评论保存在同一操作历史中，不另建重复的评论表。 */
    public record CommentRequest(@NotBlank @Size(max=10000) String content) {}

    /** 负责人提交晚于当前截止时间的新日期及延期原因。 */
    public record DeferRequest(@NotNull LocalDateTime dueTime,
                               @NotBlank @Size(max=2000) String reason) {}

    /** 创建人按当前待处理申请的历史 ID 审批，避免处理过期申请。 */
    public record DeferDecision(@NotNull Long requestId, @NotNull Boolean approved,
                                @Size(max=2000) String comment) {}

    /** 延期申请记录提交人、原截止时间和申请时间。 */
    public record DeferView(Long requestId,Long applicantAccountId,LocalDateTime beforeDueTime,
                            LocalDateTime requestedDueTime,String reason,LocalDateTime requestTime) {}

    /** 参与人身份分为协作、关注与验收。 */
    public record MemberRequest(@NotNull Long accountId,@NotBlank String memberType) {}

    /** 任务参与人展示平台账号或所属系统的成员名称。 */
    public record MemberView(Long id,Long accountId,String displayName,String memberType) {}

    /** 逾期是截止时间计算结果，不覆盖任务本身的处理状态。 */
    public record TaskView(Long id, String scopeType, Long systemId, Long tenantId,
                           Long projectId,Long taskGroupId,String title,
                           String description, Long ownerAccountId, Long createAccountId, String status,
                           boolean overdue, BigDecimal progressPercent, LocalDateTime dueTime,
                           LocalDateTime completeTime, LocalDateTime createTime) {}

    /** 看板列固定对应任务状态，保证前端拖拽和空列展示使用稳定顺序。 */
    public record BoardColumn(String status, List<TaskView> records) {}

    /** 看板与本人列表使用同一可见任务数据集，避免两个入口出现授权差异。 */
    public record BoardView(List<BoardColumn> columns) {}

    /** 日历只返回选定日期范围内有截止时间的可见任务。 */
    public record CalendarView(LocalDate from, LocalDate to, List<TaskView> records) {}

    /** 操作历史记录真实执行者及变更前后的状态。 */
    public record HistoryView(Long id, Long operatorAccountId, String operationCode,
                              String beforeJson, String afterJson, String content, LocalDateTime createTime) {}
}
