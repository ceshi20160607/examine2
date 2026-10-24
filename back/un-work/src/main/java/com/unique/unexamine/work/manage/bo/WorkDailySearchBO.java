package com.unique.unexamine.work.manage.bo;

import com.unique.unexamine.core.bo.SearchBO;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/** 工作日志按日期、状态与关联项目任务筛选。 */
public class WorkDailySearchBO extends SearchBO {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
    private String status;
    private Long projectId;
    private Long taskId;

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }
    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
}
