package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 受控密钥版本轮换与验证审计
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_secret_rotation")
public class OpsSecretRotation {

    /**
     * 密钥轮换任务主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被轮换的受控密钥引用主键
     */
    @TableField("secret_ref_id")
    private Long secretRefId;

    /**
     * 轮换前密钥版本
     */
    @TableField("from_version")
    private String fromVersion;

    /**
     * 待切换到的密钥版本
     */
    @TableField("to_version")
    private String toVersion;

    /**
     * 新版本验证、切换、旧版停用或回滚状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 双版本验证与失败回滚的审计结果
     */
    @TableField("verification_json")
    private String verificationJson;

    /**
     * 发起轮换的平台账号主键
     */
    @TableField("requested_by_account_id")
    private Long requestedByAccountId;

    /**
     * 轮换开始时间
     */
    @TableField("start_time")
    private LocalDateTime startTime;

    /**
     * 轮换结束或回滚完成时间
     */
    @TableField("finish_time")
    private LocalDateTime finishTime;

    /**
     * 轮换申请时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 轮换任务乐观锁版本
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

    public Long getSecretRefId() {
        return secretRefId;
    }

    public void setSecretRefId(Long secretRefId) {
        this.secretRefId = secretRefId;
    }

    public String getFromVersion() {
        return fromVersion;
    }

    public void setFromVersion(String fromVersion) {
        this.fromVersion = fromVersion;
    }

    public String getToVersion() {
        return toVersion;
    }

    public void setToVersion(String toVersion) {
        this.toVersion = toVersion;
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
        return "OpsSecretRotation{" +
            "id = " + id +
            ", secretRefId = " + secretRefId +
            ", fromVersion = " + fromVersion +
            ", toVersion = " + toVersion +
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
