package com.unique.unexamine.work.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** WorkProject 的请求与返回模型。 */
public final class WorkProjectDto {
    private WorkProjectDto() {}

    /** 项目编号及名称属于当前作用域，负责人默认创建者。 */
    public record CreateRequest(@NotBlank @Size(max=80) String code,
                                @NotBlank @Size(max=160) String name,
                                @Size(max=10000) String description,
                                LocalDateTime startTime,LocalDateTime dueTime) {}

    /** 项目编号保持稳定，基础资料和期限由负责人维护。 */
    public record EditRequest(@NotBlank @Size(max=160) String name,
                              @Size(max=10000) String description,
                              LocalDateTime startTime,LocalDateTime dueTime) {}

    /** 项目列表及详情使用同一基础数据。 */
    public record ProjectView(Long id,String scopeType,Long systemId,Long tenantId,
                              String code,String name,String description,Long ownerAccountId,
                              Long createAccountId,String status,BigDecimal progressPercent,
                              LocalDateTime startTime,LocalDateTime dueTime,LocalDateTime createTime) {}

    /** 项目任务统计直接来自当前未归档任务，预警不改变任务状态。 */
    public record TaskSummary(Long projectId,BigDecimal progressPercent,long total,long completed,
                              long blocked,long overdue,long dueSoon,long unassigned) {}

    /** 项目协作和观察成员由负责人维护。 */
    public record MemberRequest(@NotNull Long accountId,@NotBlank String memberType) {}

    /** 项目参与人的有效身份及展示名称。 */
    public record MemberView(Long id,Long accountId,String displayName,String memberType) {}

    /** 任务组可指定已有项目成员为负责人；顺序留空时追加到末尾。 */
    public record GroupRequest(@NotBlank @Size(max=120) String name,Long ownerAccountId,
                               Integer sortNo,LocalDateTime dueTime) {}

    /** 项目负责人维护任务组名称、负责人、顺序、状态和期限。 */
    public record GroupEditRequest(@NotBlank @Size(max=120) String name,Long ownerAccountId,
                                   @NotBlank String status,@NotNull Integer sortNo,
                                   LocalDateTime dueTime) {}

    /** 项目任务组当前数据。 */
    public record GroupView(Long id,Long projectId,String name,Long ownerAccountId,
                            String status,BigDecimal progressPercent,int sortNo,LocalDateTime dueTime) {}
}
