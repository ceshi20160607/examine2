package com.unique.unexamine.work.manage.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** WorkDaily 的请求与返回模型。 */
public final class WorkDailyDto {
    private WorkDailyDto() {}

    /** 项目和任务由用户主动选取，写入前核对当前作用域的可见性。 */
    public record DailyRequest(@NotNull LocalDate logDate,String completedText,
                               String ongoingText,String nextText,String riskText,String helpText,
                               List<Long> projectIds,List<Long> taskIds) {}

    /** 日志只保存稳定业务主键，任务状态变化不会重写日志版本。 */
    public record Relations(List<Long> projectIds,List<Long> taskIds) {}

    /** 当前版本及历史版本使用同一种可展示结构。 */
    public record DailyView(Long id,Long rootId,int versionNo,String scopeType,
                            Long systemId,Long tenantId,Long accountId,LocalDate logDate,
                            String completedText,String ongoingText,String nextText,
                            String riskText,String helpText,Relations relations,String status,
                            LocalDateTime submitTime,LocalDateTime createTime) {}
}
