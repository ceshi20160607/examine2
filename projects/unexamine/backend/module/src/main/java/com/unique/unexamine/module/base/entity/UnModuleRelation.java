package com.unique.unexamine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 模块关联关系
 * </p>
 *
 * @author Template Base
 * @since 2026-10-19
 */
@TableName("un_module_relation")
public class UnModuleRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块记录关联主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 使用的模块关联配置主键
     */
    @TableField("relation_config_id")
    private Long relationConfigId;

    /**
     * 关联起点记录主键
     */
    @TableField("source_record_id")
    private Long sourceRecordId;

    /**
     * 关联终点记录主键
     */
    @TableField("target_record_id")
    private Long targetRecordId;

    /**
     * 建立关联的成员主键
     */
    @TableField("create_member_id")
    private Long createMemberId;

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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getRelationConfigId() {
        return relationConfigId;
    }

    public void setRelationConfigId(Long relationConfigId) {
        this.relationConfigId = relationConfigId;
    }

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(Long targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public Long getCreateMemberId() {
        return createMemberId;
    }

    public void setCreateMemberId(Long createMemberId) {
        this.createMemberId = createMemberId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
