package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 备份恢复演练及可恢复性证据
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_restore_drill")
public class OpsRestoreDrill {

    /**
     * 恢复演练主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被验证备份任务主键
     */
    @TableField("backup_id")
    private Long backupId;

    /**
     * 隔离恢复环境编码
     */
    @TableField("environment_code")
    private String environmentCode;

    /**
     * 恢复演练执行结果状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 恢复完整性及业务验收结果
     */
    @TableField("verification_json")
    private String verificationJson;

    /**
     * 发起演练的平台账号主键
     */
    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    /**
     * 恢复演练开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 恢复演练结束时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 恢复演练申请时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 恢复演练乐观锁版本
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

    public Long getBackupId() {
        return backupId;
    }

    public void setBackupId(Long backupId) {
        this.backupId = backupId;
    }

    public String getEnvironmentCode() {
        return environmentCode;
    }

    public void setEnvironmentCode(String environmentCode) {
        this.environmentCode = environmentCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getVerificationJson() {
        return verificationJson;
    }

    public void setVerificationJson(String verificationJson) {
        this.verificationJson = verificationJson;
    }

    public Long getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public void setRequestedByAccountId(Long requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getFinishTime() {
        return finishTime;
    }

    public void setFinishTime(LocalDateTime finishTime) {
        this.finishTime = finishTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "OpsRestoreDrill{" +
            "id = " + id +
            ", backupId = " + backupId +
            ", environmentCode = " + environmentCode +
            ", status = " + status +
            ", verificationJson = " + verificationJson +
            ", requestedByAccountId = " + requestedByAccountId +
            ", startTime = " + startTime +
            ", finishTime = " + finishTime +
            ", createTime = " + createTime +
            ", version = " + version +
            "}";
    }
}
