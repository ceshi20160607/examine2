package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批运行异常与处置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_examine_exception")
public class UnExamineException implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批异常主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 异常所属审批实例主键
     */
    @TableField("example_id")
    private Long exampleId;

    /**
     * 异常所在审批实例节点主键
     */
    @TableField("example_node_id")
    private Long exampleNodeId;

    /**
     * 空审批人超时外部调用等异常编码
     */
    @TableField("exception_code")
    private String exceptionCode;

    /**
     * 异常时审批上下文与失败原因快照
     */
    @TableField("detail_json")
    private String detailJson;

    /**
     * 待处理已重试或人工关闭状态
     */
    @TableField("status")
    private String status;

    /**
     * 人工处置平台账号主键
     */
    @TableField("handler_account_id")
    private Long handlerAccountId;

    /**
     * 异常处置动作和结果摘要
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

    public Long getExampleNodeId() {
        return exampleNodeId;
    }

    public void setExampleNodeId(Long exampleNodeId) {
        this.exampleNodeId = exampleNodeId;
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
}
