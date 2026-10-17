package com.unique.unexamine.ai.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 不可变系统模型授权版本
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ai_system_model_grant_version")
public class AiSystemModelGrantVersion {

    /**
     * 不可变模型授权版本主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统模型授权标识
     */
    @TableField("grant_id")
    private Long grantId;

    /**
     * 授权版本所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 授权发布版本号
     */
    @TableField("version_number")
    private Integer versionNumber;

    /**
     * 授权额度、外发、脱敏及模型范围快照
     */
    @TableField("policy_snapshot_json")
    private String policySnapshotJson;

    /**
     * 发布授权的平台账号标识
     */
    @TableField("published_by_account_id")
    private Long publishedByAccountId;

    /**
     * 授权版本发布时间
     */
    @TableField("published_time")
    private LocalDateTime publishedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getGrantId() {
        return grantId;
    }

    public void setGrantId(Long grantId) {
        this.grantId = grantId;
    }

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getPolicySnapshotJson() {
        return policySnapshotJson;
    }

    public void setPolicySnapshotJson(String policySnapshotJson) {
        this.policySnapshotJson = policySnapshotJson;
    }

    public Long getPublishedByAccountId() {
        return publishedByAccountId;
    }

    public void setPublishedByAccountId(Long publishedByAccountId) {
        this.publishedByAccountId = publishedByAccountId;
    }

    public LocalDateTime getPublishedTime() {
        return publishedTime;
    }

    public void setPublishedTime(LocalDateTime publishedTime) {
        this.publishedTime = publishedTime;
    }

    @Override
    public String toString() {
        return "AiSystemModelGrantVersion{" +
            "id = " + id +
            ", grantId = " + grantId +
            ", platformId = " + platformId +
            ", versionNumber = " + versionNumber +
            ", policySnapshotJson = " + policySnapshotJson +
            ", publishedByAccountId = " + publishedByAccountId +
            ", publishedTime = " + publishedTime +
            "}";
    }
}
