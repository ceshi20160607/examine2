package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务记录转化执行及永久成功身份
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_conversion")
public class BizRecordConversion {

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
     * 来源记录及转化动作共同所属模块
     */
    @TableField("source_module_id")
    private Long sourceModuleId;

    /**
     * 来源业务记录主键
     */
    @TableField("source_record_id")
    private Long sourceRecordId;

    /**
     * 来源模块的转化动作配置主键
     */
    @TableField("action_id")
    private Long actionId;

    /**
     * 成功转化永久唯一键中的目标模块
     */
    @TableField("target_module_id")
    private Long targetModuleId;

    /**
     * 转化状态：处理中、失败、成功或已解除关系
     */
    @TableField("`status`")
    private String status;

    /**
     * 执行转化时固定的字段映射快照
     */
    @TableField("mapping_snapshot_json")
    private String mappingSnapshotJson;

    /**
     * 同租户转化请求的幂等键
     */
    @TableField("idempotency_key")
    private String idempotencyKey;

    /**
     * 创建业务数据的系统成员主键
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 转化执行结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 乐观锁版本
     */
    @Version
    @TableField("version")
    private Integer version;

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

    public Long getActionId() {
        return actionId;
    }

    public void setActionId(Long actionId) {
        this.actionId = actionId;
    }

    public Long getTargetModuleId() {
        return targetModuleId;
    }

    public void setTargetModuleId(Long targetModuleId) {
        this.targetModuleId = targetModuleId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMappingSnapshotJson() {
        return mappingSnapshotJson;
    }

    public void setMappingSnapshotJson(String mappingSnapshotJson) {
        this.mappingSnapshotJson = mappingSnapshotJson;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "BizRecordConversion{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", sourceModuleId = " + sourceModuleId +
            ", sourceRecordId = " + sourceRecordId +
            ", actionId = " + actionId +
            ", targetModuleId = " + targetModuleId +
            ", status = " + status +
            ", mappingSnapshotJson = " + mappingSnapshotJson +
            ", idempotencyKey = " + idempotencyKey +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            ", finishTime = " + finishTime +
            ", version = " + version +
            "}";
    }
}
