package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务系统租户及默认主租户
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
     * 所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 系统内租户编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 租户显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 是否系统自动创建的主租户
     */
    @TableField("main_flag")
    private Boolean mainFlag;

    /**
     * 租户启用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 租户创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 租户更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

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

    public Boolean getMainFlag() {
        return mainFlag;
    }

    public void setMainFlag(Boolean mainFlag) {
        this.mainFlag = mainFlag;
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

    @Override
    public String toString() {
        return "SystemTenant{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", code = " + code +
            ", name = " + name +
            ", mainFlag = " + mainFlag +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
