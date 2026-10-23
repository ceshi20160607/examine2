package com.unique.unexamine.sys.flow.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 流程异常与人工处置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
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
     * 异常所属流程实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 异常所属节点待办主键
     */
    @TableField("task_id")
    private Long taskId;

    /**
     * 流程异常类型编码
     */
    @TableField("exception_code")
    private String exceptionCode;

    /**
     * 错误原因及当时执行上下文快照
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 待处理已重试或人工关闭状态
     */
    @TableField("status")
    private String status;

    /**
     * 实际处置平台账号主键
     */
    @TableField("handler_account_id")
    private Long handlerAccountId;

    /**
     * 人工处置或重试结果摘要
     */
    @TableField("resolution_json")
    private String resolutionJson;

    /**
     * 异常发现时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 异常处置完成时间
     */
    @TableField("resolve_time")
    private LocalDateTime resolveTime;

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

    public String getExceptionCode() {
        return exceptionCode;
    }

    public void setExceptionCode(String exceptionCode) {
        this.exceptionCode = exceptionCode;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getHandlerAccountId() {
        return handlerAccountId;
    }

    public void setHandlerAccountId(Long handlerAccountId) {
        this.handlerAccountId = handlerAccountId;
    }

    public String getResolutionJson() {
        return resolutionJson;
    }

    public void setResolutionJson(String resolutionJson) {
        this.resolutionJson = resolutionJson;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getResolveTime() {
        return resolveTime;
    }

    public void setResolveTime(LocalDateTime resolveTime) {
        this.resolveTime = resolveTime;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }
}
