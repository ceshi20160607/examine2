package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务记录之间由关联字段定义的独立关系
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_relation")
public class BizRecordRelation {

    /**
     * 本表主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务数据所属系统
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 业务数据归属租户
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 来源业务记录所属模块
     */
    @TableField("source_module_id")
    private Long sourceModuleId;

    /**
     * 来源业务记录主键
     */
    @TableField("source_record_id")
    private Long sourceRecordId;

    /**
     * 来源模块中的关联字段配置主键
     */
    @TableField("field_id")
    private Long fieldId;

    /**
     * 目标业务记录所属模块
     */
    @TableField("target_module_id")
    private Long targetModuleId;

    /**
     * 目标业务记录主键
     */
    @TableField("target_record_id")
    private Long targetRecordId;

    /**
     * 多值关联的显示顺序
     */
    @TableField("relation_order")
    private Integer relationOrder;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getSourceModuleId() {
        return sourceModuleId;
    }

    public void setSourceModuleId(Long sourceModuleId) {
        this.sourceModuleId = sourceModuleId;
    }

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public Long getFieldId() {
        return fieldId;
    }

    public void setFieldId(Long fieldId) {
        this.fieldId = fieldId;
    }

    public Long getTargetModuleId() {
        return targetModuleId;
    }

    public void setTargetModuleId(Long targetModuleId) {
        this.targetModuleId = targetModuleId;
    }

    public Long getTargetRecordId() {
        return targetRecordId;
    }

    public void setTargetRecordId(Long targetRecordId) {
        this.targetRecordId = targetRecordId;
    }

    public Integer getRelationOrder() {
        return relationOrder;
    }

    public void setRelationOrder(Integer relationOrder) {
        this.relationOrder = relationOrder;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "BizRecordRelation{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", sourceModuleId = " + sourceModuleId +
            ", sourceRecordId = " + sourceRecordId +
            ", fieldId = " + fieldId +
            ", targetModuleId = " + targetModuleId +
            ", targetRecordId = " + targetRecordId +
            ", relationOrder = " + relationOrder +
            ", createTime = " + createTime +
            "}";
    }
}
