package com.unique.unexamine.work.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 工作日志修订快照
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_work_log_revision")
public class WorkLogRevision {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 关联工作日志标识
     */
    @TableField("work_log_id")
    private Long workLogId;

    /**
     * 日志修订序号
     */
    @TableField("revision_number")
    private Integer revisionNumber;

    /**
     * 日志修订内容快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 日志修订原因
     */
    @TableField("revision_reason")
    private String revisionReason;

    /**
     * 修订人平台账号标识
     */
    @TableField("revised_by_account_id")
    private Long revisedByAccountId;

    /**
     * 日志修订时间
     */
    @TableField("revise_time")
    private LocalDateTime reviseTime;

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

    public LocalDateTime getReviseTime() {
        return reviseTime;
    }

    public void setReviseTime(LocalDateTime reviseTime) {
        this.reviseTime = reviseTime;
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
            ", reviseTime = " + reviseTime +
            "}";
    }
}
