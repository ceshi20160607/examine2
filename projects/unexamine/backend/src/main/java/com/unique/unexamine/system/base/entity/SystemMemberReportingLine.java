package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 租户成员职位及直属上级关系
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_member_reporting_line")
public class SystemMemberReportingLine {

    /**
     * 成员汇报关系主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 成员所属系统主键
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 成员所属租户主键
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 具有该职位和汇报关系的租户成员主键
     */
    @TableField("tenant_member_id")
    private Long tenantMemberId;

    /**
     * 同租户直属上级成员主键，无上级时为空
     */
    @TableField("manager_tenant_member_id")
    private Long managerTenantMemberId;

    /**
     * 成员在租户组织中的职位名称
     */
    @TableField("position_title")
    private String positionTitle;

    /**
     * 成员汇报关系创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 成员汇报关系更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 汇报关系乐观锁版本
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

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getTenantMemberId() {
        return tenantMemberId;
    }

    public void setTenantMemberId(Long tenantMemberId) {
        this.tenantMemberId = tenantMemberId;
    }

    public Long getManagerTenantMemberId() {
        return managerTenantMemberId;
    }

    public void setManagerTenantMemberId(Long managerTenantMemberId) {
        this.managerTenantMemberId = managerTenantMemberId;
    }

    public String getPositionTitle() {
        return positionTitle;
    }

    public void setPositionTitle(String positionTitle) {
        this.positionTitle = positionTitle;
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
        return "SystemMemberReportingLine{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", tenantMemberId = " + tenantMemberId +
            ", managerTenantMemberId = " + managerTenantMemberId +
            ", positionTitle = " + positionTitle +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
