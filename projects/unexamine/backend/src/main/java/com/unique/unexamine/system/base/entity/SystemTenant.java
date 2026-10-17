package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统租户
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_tenant")
public class SystemTenant {

    /**
     * 系统租户主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统内租户编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 租户展示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 租户专属登录页标题，空值使用租户名称
     */
    @TableField("login_title")
    private String loginTitle;

    /**
     * 租户专属登录页说明，空值使用系统默认说明
     */
    @TableField("login_subtitle")
    private String loginSubtitle;

    /**
     * 是否为系统主租户
     */
    @TableField("is_main")
    private Boolean main;

    /**
     * 主租户唯一标记
     */
    @TableField("main_marker")
    private String mainMarker;

    /**
     * 创建租户的平台账号标识
     */
    @TableField("creator_account_id")
    private Long creatorAccountId;

    /**
     * 系统租户状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 系统租户创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 系统租户更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 系统租户乐观锁版本
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

    public Long getSystemId() {
        return systemId;
    }

    public void setSystemId(Long systemId) {
        this.systemId = systemId;
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

    public String getLoginTitle() {
        return loginTitle;
    }

    public void setLoginTitle(String loginTitle) {
        this.loginTitle = loginTitle;
    }

    public String getLoginSubtitle() {
        return loginSubtitle;
    }

    public void setLoginSubtitle(String loginSubtitle) {
        this.loginSubtitle = loginSubtitle;
    }

    public Boolean getMain() {
        return main;
    }

    public void setMain(Boolean main) {
        this.main = main;
    }

    public String getMainMarker() {
        return mainMarker;
    }

    public void setMainMarker(String mainMarker) {
        this.mainMarker = mainMarker;
    }

    public Long getCreatorAccountId() {
        return creatorAccountId;
    }

    public void setCreatorAccountId(Long creatorAccountId) {
        this.creatorAccountId = creatorAccountId;
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
        return "SystemTenant{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", code = " + code +
            ", name = " + name +
            ", loginTitle = " + loginTitle +
            ", loginSubtitle = " + loginSubtitle +
            ", main = " + main +
            ", mainMarker = " + mainMarker +
            ", creatorAccountId = " + creatorAccountId +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
