package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("app_publication")
public class AppPublication {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("application_id")
    private Long applicationId;

    @TableField("current_version_id")
    private Long currentVersionId;

    @TableField("updated_by_account_id")
    private Long updatedByAccountId;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public Long getUpdatedByAccountId() {
        return updatedByAccountId;
    }

    public void setUpdatedByAccountId(Long updatedByAccountId) {
        this.updatedByAccountId = updatedByAccountId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "AppPublication{" +
            "id = " + id +
            ", applicationId = " + applicationId +
            ", currentVersionId = " + currentVersionId +
            ", updatedByAccountId = " + updatedByAccountId +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
