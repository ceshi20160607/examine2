package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 任务状态与业务操作历史
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_work_task_history")
public class UnWorkTaskHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 任务操作历史主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属工作任务主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 实际操作平台账号主键
     */
    @TableField("operator_account_id")
    private Long operatorAccountId;

    /**
     * 任务创建转交延期评论等操作类型
     */
    @TableField("operation_code")
    private String operationCode;

    /**
     * 操作前业务值摘要
     */
    @TableField("before_json")
    private String beforeJson;

    /**
     * 操作后业务值摘要
     */
    @TableField("after_json")
    private String afterJson;

    /**
     * 评论进度说明或操作原因
     */
    @TableField("content")
    private String content;

    /**
     * 操作时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 逻辑删除时间，空表示未删除
     */
    @TableLogic
    @TableField("deleted_time")
    private LocalDateTime deletedTime;

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

    public Long getOperatorAccountId() {
        return operatorAccountId;
    }

    public void setOperatorAccountId(Long operatorAccountId) {
        this.operatorAccountId = operatorAccountId;
    }

    public String getOperationCode() {
        return operationCode;
    }

    public void setOperationCode(String operationCode) {
        this.operationCode = operationCode;
    }

    public String getBeforeJson() {
        return beforeJson;
    }

    public void setBeforeJson(String beforeJson) {
        this.beforeJson = beforeJson;
    }

    public String getAfterJson() {
        return afterJson;
    }

    public void setAfterJson(String afterJson) {
        this.afterJson = afterJson;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
