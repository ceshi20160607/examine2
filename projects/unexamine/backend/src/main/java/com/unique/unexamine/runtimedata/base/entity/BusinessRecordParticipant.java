package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("biz_record_participant")
public class BusinessRecordParticipant {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("record_id")
    private Long recordId;

    @TableField("system_member_id")
    private Long systemMemberId;

    @TableField("participant_type")
    private String participantType;

    @TableField("created_by_member_id")
    private Long createdByMemberId;

    @TableField("created_at")
    private LocalDateTime createdAt;

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
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
            ", createdAt = " + createdAt +
            "}";
    }
}
