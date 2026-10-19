package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 任务状态与业务操作历史
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_work_task_history")
public class UnWorkTaskHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务历史主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属工作任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 操作的系统成员主键
     */
    @TableField("operator_member_id")
    private Long operatorMemberId;

    /**
     * 状态或业务操作编码
     */
    @TableField("action_code")
    private String actionCode;

    /**
     * 操作前的相关业务值
     */
    @TableField("before_data")
    private String beforeData;

    /**
     * 操作后的相关业务值
     */
    @TableField("after_data")
    private String afterData;

    /**
     * 操作发生时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getOperatorMemberId() {
        return operatorMemberId;
    }

    public void setOperatorMemberId(Long operatorMemberId) {
        this.operatorMemberId = operatorMemberId;
    }

    public String getActionCode() {
        return actionCode;
    }

    public void setActionCode(String actionCode) {
        this.actionCode = actionCode;
    }

    public String getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(String beforeData) {
        this.beforeData = beforeData;
    }

    public String getAfterData() {
        return afterData;
    }

    public void setAfterData(String afterData) {
        this.afterData = afterData;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
