package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_platform")
public class PlatformDefinition {

    /**
     * 平台配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台唯一编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 平台展示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 平台标识文件标识
     */
    @TableField("logo_file_id")
    private Long logoFileId;

    /**
     * 平台联系人姓名
     */
    @TableField("contact_name")
    private String contactName;

    /**
     * 平台联系人邮箱
     */
    @TableField("contact_email")
    private String contactEmail;

    /**
     * 平台运行设置
     */
    @TableField("settings_json")
    private String settingsJson;

    /**
     * 平台启用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 平台配置创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 平台配置更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 平台配置乐观锁版本
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

    public Long getLogoFileId() {
        return logoFileId;
    }

    public void setLogoFileId(Long logoFileId) {
        this.logoFileId = logoFileId;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getSettingsJson() {
        return settingsJson;
    }

    public void setSettingsJson(String settingsJson) {
        this.settingsJson = settingsJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
        return "PlatformDefinition{" +
            "id = " + id +
            ", code = " + code +
            ", name = " + name +
            ", logoFileId = " + logoFileId +
            ", contactName = " + contactName +
            ", contactEmail = " + contactEmail +
            ", settingsJson = " + settingsJson +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
