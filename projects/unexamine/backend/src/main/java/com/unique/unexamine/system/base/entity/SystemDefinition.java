package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台内业务系统
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_system")
public class SystemDefinition {

    /**
     * 平台内业务系统主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 平台内系统编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 系统展示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 系统用途说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 系统标识文件标识
     */
    @TableField("logo_file_id")
    private Long logoFileId;

    /**
     * 当前系统所属平台账号标识，所属权转移时更新
     */
    @TableField("creator_account_id")
    private Long creatorAccountId;

    /**
     * 系统租户运行模式
     */
    @TableField("tenant_mode")
    private String tenantMode;

    /**
     * 系统运行状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 系统是否已逻辑删除
     */
    @TableField("deleted")
    private Boolean deleted;

    /**
     * 平台内业务系统创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 平台内业务系统更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 平台内业务系统乐观锁版本
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

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getLogoFileId() {
        return logoFileId;
    }

    public void setLogoFileId(Long logoFileId) {
        this.logoFileId = logoFileId;
    }

    public Long getCreatorAccountId() {
        return creatorAccountId;
    }

    public void setCreatorAccountId(Long creatorAccountId) {
        this.creatorAccountId = creatorAccountId;
    }

    public String getTenantMode() {
        return tenantMode;
    }

    public void setTenantMode(String tenantMode) {
        this.tenantMode = tenantMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
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
        return "SystemDefinition{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", logoFileId = " + logoFileId +
            ", creatorAccountId = " + creatorAccountId +
            ", tenantMode = " + tenantMode +
            ", status = " + status +
            ", deleted = " + deleted +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
