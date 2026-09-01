package com.unique.unexamine.work.base.entity;

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
@TableName("work_log_revision")
public class WorkLogRevision {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("work_log_id")
    private Long workLogId;

    @TableField("revision_number")
    private Integer revisionNumber;

    @TableField("snapshot_json")
    private String snapshotJson;

    @TableField("revision_reason")
    private String revisionReason;

    @TableField("revised_by_account_id")
    private Long revisedByAccountId;

    @TableField("revised_at")
    private LocalDateTime revisedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getWorkLogId() {
        return workLogId;
    }

    public void setWorkLogId(Long workLogId) {
        this.workLogId = workLogId;
    }

    public Integer getRevisionNumber() {
        return revisionNumber;
    }

    public void setRevisionNumber(Integer revisionNumber) {
        this.revisionNumber = revisionNumber;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public void setSnapshotJson(String snapshotJson) {
        this.snapshotJson = snapshotJson;
    }

    public String getRevisionReason() {
        return revisionReason;
    }

    public void setRevisionReason(String revisionReason) {
        this.revisionReason = revisionReason;
    }

    public Long getRevisedByAccountId() {
        return revisedByAccountId;
    }

    public void setRevisedByAccountId(Long revisedByAccountId) {
        this.revisedByAccountId = revisedByAccountId;
    }

    public LocalDateTime getRevisedAt() {
        return revisedAt;
    }

    public void setRevisedAt(LocalDateTime revisedAt) {
        this.revisedAt = revisedAt;
    }

    @Override
    public String toString() {
        return "WorkLogRevision{" +
            "id = " + id +
            ", workLogId = " + workLogId +
            ", revisionNumber = " + revisionNumber +
            ", snapshotJson = " + snapshotJson +
            ", revisionReason = " + revisionReason +
            ", revisedByAccountId = " + revisedByAccountId +
            ", revisedAt = " + revisedAt +
            "}";
    }
}
