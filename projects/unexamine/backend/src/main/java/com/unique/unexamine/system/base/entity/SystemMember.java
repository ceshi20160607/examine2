package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统成员
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_member")
public class SystemMember {

    /**
     * 系统成员主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 对应平台账号标识
     */
    @TableField("account_id")
    private Long accountId;

    /**
     * 系统内成员工号
     */
    @TableField("employee_number")
    private String employeeNumber;

    /**
     * 系统内成员显示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 系统成员状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 加入系统时间
     */
    @TableField("join_time")
    private LocalDateTime joinTime;

    /**
     * 系统成员创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 系统成员更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 系统成员乐观锁版本
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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getJoinTime() {
        return joinTime;
    }

    public void setJoinTime(LocalDateTime joinTime) {
        this.joinTime = joinTime;
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
        return "SystemMember{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", accountId = " + accountId +
            ", employeeNumber = " + employeeNumber +
            ", displayName = " + displayName +
            ", status = " + status +
            ", joinTime = " + joinTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
