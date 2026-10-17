package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台内的业务系统
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_system")
public class SystemDefinition {

    /**
     * 业务系统主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 系统所属平台账号主键
     */
    @TableField("owner_account_id")
    private Long ownerAccountId;

    /**
     * 系统唯一编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 系统显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 系统用途说明
     */
    @TableField("`description`")
    private String description;

    /**
     * 系统启用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 系统创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 系统更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 系统乐观锁版本
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

    public Long getOwnerAccountId() {
        return ownerAccountId;
    }

    public void setOwnerAccountId(Long ownerAccountId) {
        this.ownerAccountId = ownerAccountId;
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
        return "SystemDefinition{" +
            "id = " + id +
            ", ownerAccountId = " + ownerAccountId +
            ", code = " + code +
            ", name = " + name +
            ", description = " + description +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
