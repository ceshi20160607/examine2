package com.unique.unexamine.base.entity;

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
 * @since 2026-10-19
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
     * 平台内唯一的业务系统编码
     */
    @TableField("system_code")
    private String systemCode;

    /**
     * 业务系统名称
     */
    @TableField("system_name")
    private String systemName;

    /**
     * 业务系统用途说明
     */
    @TableField("description")
    private String description;

    /**
     * 负责该系统的平台成员主键
     */
    @TableField("owner_plat_member_id")
    private Long ownerPlatMemberId;

    /**
     * 系统状态：1启用、0停用
     */
    @TableField("status")
    private Byte status;

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

    public String getSystemCode() {
        return systemCode;
    }

    public void setSystemCode(String systemCode) {
        this.systemCode = systemCode;
    }

    public String getSystemName() {
        return systemName;
    }

    public void setSystemName(String systemName) {
        this.systemName = systemName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOwnerPlatMemberId() {
        return ownerPlatMemberId;
    }

    public void setOwnerPlatMemberId(Long ownerPlatMemberId) {
        this.ownerPlatMemberId = ownerPlatMemberId;
    }

    public Byte getStatus() {
        return status;
    }

    public void setStatus(Byte status) {
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
}
