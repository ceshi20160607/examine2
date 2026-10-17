package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统成员在具体租户中的准入关系
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_tenant_member")
public class TenantMember {

    /**
     * 租户成员关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户与成员共同所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 所属系统成员主键
     */
    @TableField("member_id")
    private Long memberId;

    /**
     * 租户成员准入状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 加入租户时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 租户成员关系更新时间
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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
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
        return "TenantMember{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", memberId = " + memberId +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            "}";
    }
}
