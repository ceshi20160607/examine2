package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 同一范围的工作日志与任务关联
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_work_log_task_link")
public class WorkLogTaskLink {

    /**
     * 日志任务关联主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属工作日志标识
     */
    @TableField("work_log_id")
    private Long workLogId;

    /**
     * 同一工作范围的任务标识
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 关联创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkLogId() {
        return workLogId;
    }

    public void setWorkLogId(Long workLogId) {
        this.workLogId = workLogId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "WorkLogTaskLink{" +
            "id = " + id +
            ", workLogId = " + workLogId +
            ", taskId = " + taskId +
            ", createTime = " + createTime +
            "}";
    }
}
