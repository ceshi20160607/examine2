package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务记录的负责人以外的团队参与成员
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_participant")
public class BusinessRecordParticipant {

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
     * 业务主记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 参与记录的系统成员主键
     */
    @TableField("system_member_id")
    private Long systemMemberId;

    /**
     * 参与关系类型，例如协作人或关注人
     */
    @TableField("participant_type")
    private String participantType;

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

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getSystemMemberId() {
        return systemMemberId;
    }

    public void setSystemMemberId(Long systemMemberId) {
        this.systemMemberId = systemMemberId;
    }

    public String getParticipantType() {
        return participantType;
    }

    public void setParticipantType(String participantType) {
        this.participantType = participantType;
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

    @Override
    public String toString() {
        return "BusinessRecordParticipant{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", recordId = " + recordId +
            ", systemMemberId = " + systemMemberId +
            ", participantType = " + participantType +
            ", createdByMemberId = " + createdByMemberId +
            ", createTime = " + createTime +
            "}";
    }
}
