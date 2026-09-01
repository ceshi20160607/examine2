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
@TableName("biz_record_owner_history")
public class BizRecordOwnerHistory {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("record_id")
    private Long recordId;

    @TableField("from_owner_member_id")
    private Long fromOwnerMemberId;

    @TableField("to_owner_member_id")
    private Long toOwnerMemberId;

    @TableField("from_department_id")
    private Long fromDepartmentId;

    @TableField("to_department_id")
    private Long toDepartmentId;

    @TableField("reason")
    private String reason;

    @TableField("changed_by_member_id")
    private Long changedByMemberId;

    @TableField("changed_at")
    private LocalDateTime changedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getFromOwnerMemberId() {
        return fromOwnerMemberId;
    }

    public void setFromOwnerMemberId(Long fromOwnerMemberId) {
        this.fromOwnerMemberId = fromOwnerMemberId;
    }

    public Long getToOwnerMemberId() {
        return toOwnerMemberId;
    }

    public void setToOwnerMemberId(Long toOwnerMemberId) {
        this.toOwnerMemberId = toOwnerMemberId;
    }

    public Long getFromDepartmentId() {
        return fromDepartmentId;
    }

    public void setFromDepartmentId(Long fromDepartmentId) {
        this.fromDepartmentId = fromDepartmentId;
    }

    public Long getToDepartmentId() {
        return toDepartmentId;
    }

    public void setToDepartmentId(Long toDepartmentId) {
        this.toDepartmentId = toDepartmentId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getChangedByMemberId() {
        return changedByMemberId;
    }

    public void setChangedByMemberId(Long changedByMemberId) {
        this.changedByMemberId = changedByMemberId;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }

    @Override
    public String toString() {
        return "BizRecordOwnerHistory{" +
            "id = " + id +
            ", recordId = " + recordId +
            ", fromOwnerMemberId = " + fromOwnerMemberId +
            ", toOwnerMemberId = " + toOwnerMemberId +
            ", fromDepartmentId = " + fromDepartmentId +
            ", toDepartmentId = " + toDepartmentId +
            ", reason = " + reason +
            ", changedByMemberId = " + changedByMemberId +
            ", changedAt = " + changedAt +
            "}";
    }
}
