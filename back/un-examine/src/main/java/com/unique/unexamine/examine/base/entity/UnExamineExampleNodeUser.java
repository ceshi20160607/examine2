package com.unique.unexamine.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 审批实例节点实际处理人员
 * </p>
 *
 * @author Template Base
 * @since 2026-10-23
 */
@TableName("un_examine_example_node_user")
public class UnExamineExampleNodeUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 审批实例节点处理人主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属审批实例节点主键
     */
    @TableField("example_node_id")
    private Long exampleNodeId;

    /**
     * 内部审批平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 外部审批人身份标识
     */
    @TableField("external_id")
    private String externalId;

    /**
     * 外部审批身份所属应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 待处理通过拒绝转办或抄送已读状态
     */
    @TableField("status")
    private String status;

    /**
     * 审批意见及处理原因
     */
    @TableField("opinion")
    private String opinion;

    /**
     * 字段回写附件和代理加签操作快照
     */
    @TableField("action_json")
    private String actionJson;

    /**
     * 实际办理时间
     */
    @TableField("action_time")
    private LocalDateTime actionTime;

    /**
     * 待办创建时间
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

    public Long getExampleNodeId() {
        return exampleNodeId;
    }

    public void setExampleNodeId(Long exampleNodeId) {
        this.exampleNodeId = exampleNodeId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOpinion() {
        return opinion;
    }

    public void setOpinion(String opinion) {
        this.opinion = opinion;
    }

    public String getActionJson() {
        return actionJson;
    }

    public void setActionJson(String actionJson) {
        this.actionJson = actionJson;
    }

    public LocalDateTime getActionTime() {
        return actionTime;
    }

    public void setActionTime(LocalDateTime actionTime) {
        this.actionTime = actionTime;
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
