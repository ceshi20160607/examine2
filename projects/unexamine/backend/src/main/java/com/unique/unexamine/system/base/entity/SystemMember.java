package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台账号在业务系统中的成员关系
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
     * 成员所属业务系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 成员对应平台账号主键
     */
    @TableField("account_id")
    private Long accountId;

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
     * 成员关系创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 成员关系更新时间
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

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
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

    @Override
    public String toString() {
        return "SystemMember{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", accountId = " + accountId +
            ", status = " + status +
            ", joinTime = " + joinTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
