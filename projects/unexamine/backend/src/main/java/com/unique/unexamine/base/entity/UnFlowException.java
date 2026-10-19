package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 流程异常与处置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_flow_exception")
public class UnFlowException implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 流程异常主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 异常关联的待办任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 异常类型，如 NO_ASSIGNEE、RULE_ERROR、JOB_ERROR
     */
    @TableField("exception_type")
    private String exceptionType;

    /**
     * 异常说明
     */
    @TableField("detail")
    private String detail;

    /**
     * 处置状态：OPEN 待处理，RESOLVED 已解决
     */
    @TableField("status")
    private String status;

    /**
     * 处置成员主键
     */
    @TableField("handle_member_id")
    private Long handleMemberId;

    /**
     * 处置说明
     */
    @TableField("handle_remark")
    private String handleRemark;

    /**
     * 异常发生时间
     */
    @TableField("occur_time")
    private LocalDateTime occurTime;

    /**
     * 异常完成处置时间
     */
    @TableField("handle_time")
    private LocalDateTime handleTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getExampleId() {
        return exampleId;
    }

    public void setExampleId(Long exampleId) {
        this.exampleId = exampleId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getHandleMemberId() {
        return handleMemberId;
    }

    public void setHandleMemberId(Long handleMemberId) {
        this.handleMemberId = handleMemberId;
    }

    public String getHandleRemark() {
        return handleRemark;
    }

    public void setHandleRemark(String handleRemark) {
        this.handleRemark = handleRemark;
    }

    public LocalDateTime getOccurTime() {
        return occurTime;
    }

    public void setOccurTime(LocalDateTime occurTime) {
        this.occurTime = occurTime;
    }

    public LocalDateTime getHandleTime() {
        return handleTime;
    }

    public void setHandleTime(LocalDateTime handleTime) {
        this.handleTime = handleTime;
    }
}
