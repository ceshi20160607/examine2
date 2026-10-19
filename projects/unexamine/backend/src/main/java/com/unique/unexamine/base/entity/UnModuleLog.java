package com.unique.unexamine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块变动记录
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_log")
public class UnModuleLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块变动日志主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 记录所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 记录所属模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 发生变动的业务记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 执行变动的成员主键
     */
    @TableField("actor_member_id")
    private Long actorMemberId;

    /**
     * 业务操作编码
     */
    @TableField("operation_code")
    private String operationCode;

    /**
     * 变动前涉及的字段和值
     */
    @TableField("before_json")
    private String beforeJson;

    /**
     * 变动后涉及的字段和值
     */
    @TableField("after_json")
    private String afterJson;

    /**
     * 变动发生时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getActorMemberId() {
        return actorMemberId;
    }

    public void setActorMemberId(Long actorMemberId) {
        this.actorMemberId = actorMemberId;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
