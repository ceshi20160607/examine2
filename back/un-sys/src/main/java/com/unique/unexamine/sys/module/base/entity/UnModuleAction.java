package com.unique.unexamine.sys.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 菜单操作的模块动作执行配置
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_module_action")
public class UnModuleAction implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 模块动作执行配置主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务模块主键
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 定义动作的租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 动作对应的唯一菜单主键
     */
    @TableField("menu_id")
    private Long menuId;

    /**
     * 基础动作或字段更新执行类型
     */
    @TableField("execution_type")
    private String executionType;

    /**
     * 草稿动作是否启用
     */
    @TableField("enabled_flag")
    private Boolean enabledFlag;

    /**
     * 草稿字段条件转化映射及执行配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 运行态发布的动作执行配置
     */
    @TableField("published_config_json")
    private String publishedConfigJson;

    /**
     * 已发布动作是否启用
     */
    @TableField("published_enabled_flag")
    private Boolean publishedEnabledFlag;

    /**
     * 动作发布状态
     */
    @TableField("status")
    private String status;

    /**
     * 动作发布版本号
     */
    @TableField("published_version")
    private Integer publishedVersion;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    public String getExecutionType() {
        return executionType;
    }

    public void setExecutionType(String executionType) {
        this.executionType = executionType;
    }

    public Boolean getEnabledFlag() {
        return enabledFlag;
    }

    public void setEnabledFlag(Boolean enabledFlag) {
        this.enabledFlag = enabledFlag;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getPublishedConfigJson() {
        return publishedConfigJson;
    }

    public void setPublishedConfigJson(String publishedConfigJson) {
        this.publishedConfigJson = publishedConfigJson;
    }

    public Boolean getPublishedEnabledFlag() {
        return publishedEnabledFlag;
    }

    public void setPublishedEnabledFlag(Boolean publishedEnabledFlag) {
        this.publishedEnabledFlag = publishedEnabledFlag;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPublishedVersion() {
        return publishedVersion;
    }

    public void setPublishedVersion(Integer publishedVersion) {
        this.publishedVersion = publishedVersion;
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
