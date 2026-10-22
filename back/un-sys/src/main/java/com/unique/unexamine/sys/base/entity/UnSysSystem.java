package com.unique.unexamine.sys.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 平台内业务系统
 * </p>
 *
 * @author Template Base
 * @since 2026-10-22
 */
@TableName("un_sys_system")
public class UnSysSystem implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务系统主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 系统唯一编码
     */
    @TableField("code")
    private String code;

    /**
     * 系统名称
     */
    @TableField("name")
    private String name;

    /**
     * 系统业务说明
     */
    @TableField("description")
    private String description;

    /**
     * 当前系统所属平台账号主键
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 创建时同步生成的主租户主键
     */
    @TableField("root_tenant_id")
    private Long rootTenantId;

    /**
     * 系统状态
     */
    @TableField("status")
    private String status;

    /**
     * 创建平台账号主键
     */
    @TableField("create_account_id")
    private Long createAccountId;

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

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public void setOwnerAccountId(Long ownerAccountId) {
        this.ownerAccountId = ownerAccountId;
    }

    public Long getRootTenantId() {
        return rootTenantId;
    }

    public void setRootTenantId(Long rootTenantId) {
        this.rootTenantId = rootTenantId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getCreateAccountId() {
        return createAccountId;
    }

    public void setCreateAccountId(Long createAccountId) {
        this.createAccountId = createAccountId;
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
