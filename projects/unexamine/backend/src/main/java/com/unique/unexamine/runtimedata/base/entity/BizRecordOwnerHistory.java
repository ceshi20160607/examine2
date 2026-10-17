package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务记录负责人和部门变更历史
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_biz_record_owner_history")
public class BizRecordOwnerHistory {

    /**
     * 本表主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 业务主记录主键
     */
    @TableField("record_id")
    private Long recordId;

    /**
     * 变更前负责人主键
     */
    @TableField("from_owner_member_id")
    private Long fromOwnerMemberId;

    /**
     * 变更后负责人主键
     */
    @TableField("to_owner_member_id")
    private Long toOwnerMemberId;

    /**
     * 变更前负责部门主键
     */
    @TableField("from_department_id")
    private Long fromDepartmentId;

    /**
     * 变更后负责部门主键
     */
    @TableField("to_department_id")
    private Long toDepartmentId;

    /**
     * 业务变更原因
     */
    @TableField("reason")
    private String reason;

    /**
     * 执行变更的系统成员主键
     */
    @TableField("changed_by_member_id")
    private Long changedByMemberId;

    /**
     * 业务变更发生时间
     */
    @TableField("change_time")
    private LocalDateTime changeTime;

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

    public LocalDateTime getChangeTime() {
        return changeTime;
    }

    public void setChangeTime(LocalDateTime changeTime) {
        this.changeTime = changeTime;
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
            ", changeTime = " + changeTime +
            "}";
    }
}
