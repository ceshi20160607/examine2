package com.unique.unexamine.plat.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台基础配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-21
 */
@TableName("un_plat_config")
public class UnPlatConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 平台配置键
     */
    @TableField("config_key")
    private String configKey;

    /**
     * 平台配置内容
     */
    @TableField("config_value")
    private String configValue;

    /**
     * 配置状态
     */
    @TableField("status")
    private String status;

    /**
     * 最近修改账号主键
     */
    @TableField("update_account_id")
    private Long updateAccountId;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUpdateAccountId() {
        return updateAccountId;
    }

    public void setUpdateAccountId(Long updateAccountId) {
        this.updateAccountId = updateAccountId;
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
}
