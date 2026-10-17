package com.unique.unexamine.application.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 应用当前生效版本指针
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_app_publication")
public class AppPublication {

    /**
     * 发布指针主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属应用主键
     */
    @TableField("application_id")
    private Long applicationId;

    /**
     * 当前生效应用版本主键
     */
    @TableField("current_version_id")
    private Long currentVersionId;

    /**
     * 修改发布指针的平台账号主键
     */
    @TableField("updated_by_account_id")
    private Long updatedByAccountId;

    /**
     * 指针更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 乐观锁版本
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

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
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
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
