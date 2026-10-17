package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统成员一次性邀请
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_member_invitation")
public class SystemMemberInvitation {

    /**
     * 系统成员邀请主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 邀请加入的系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 邀请加入的租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 指定受邀平台账号标识，为空表示持链接的登录账号可接受
     */
    @TableField("target_account_id")
    private Long targetAccountId;

    /**
     * 一次性邀请令牌摘要
     */
    @TableField("token_hash")
    private String tokenHash;

    /**
     * 接受后所属部门标识
     */
    @TableField("department_id")
    private Long departmentId;

    /**
     * 接受后授予的当前租户角色标识列表
     */
    @TableField("role_ids_json")
    private String roleIdsJson;

    /**
     * 接受后使用的系统内工号
     */
    @TableField("employee_number")
    private String employeeNumber;

    /**
     * 接受后使用的岗位名称
     */
    @TableField("position_title")
    private String positionTitle;

    /**
     * 邀请状态，PENDING、ACCEPTED 或 REVOKED
     */
    @TableField("`status`")
    private String status;

    /**
     * 邀请失效时间
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 发出邀请的管理员账号标识
     */
    @TableField("created_by_account_id")
    private Long createdByAccountId;

    /**
     * 实际接受邀请的平台账号标识
     */
    @TableField("accepted_account_id")
    private Long acceptedAccountId;

    /**
     * 邀请接受时间
     */
    @TableField("accept_time")
    private LocalDateTime acceptTime;

    /**
     * 邀请创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 邀请更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 邀请乐观锁版本
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

    public Long getTargetAccountId() {
        return targetAccountId;
    }

    public void setTargetAccountId(Long targetAccountId) {
        this.targetAccountId = targetAccountId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(Long departmentId) {
        this.departmentId = departmentId;
    }

    public String getRoleIdsJson() {
        return roleIdsJson;
    }

    public void setRoleIdsJson(String roleIdsJson) {
        this.roleIdsJson = roleIdsJson;
    }

    public String getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(String employeeNumber) {
        this.employeeNumber = employeeNumber;
    }

    public String getPositionTitle() {
        return positionTitle;
    }

    public void setPositionTitle(String positionTitle) {
        this.positionTitle = positionTitle;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getExpireTime() {
        return expireTime;
    }

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public Long getAcceptedAccountId() {
        return acceptedAccountId;
    }

    public void setAcceptedAccountId(Long acceptedAccountId) {
        this.acceptedAccountId = acceptedAccountId;
    }

    public LocalDateTime getAcceptTime() {
        return acceptTime;
    }

    public void setAcceptTime(LocalDateTime acceptTime) {
        this.acceptTime = acceptTime;
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
        return "SystemMemberInvitation{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", targetAccountId = " + targetAccountId +
            ", tokenHash = " + tokenHash +
            ", departmentId = " + departmentId +
            ", roleIdsJson = " + roleIdsJson +
            ", employeeNumber = " + employeeNumber +
            ", positionTitle = " + positionTitle +
            ", status = " + status +
            ", expireTime = " + expireTime +
            ", createdByAccountId = " + createdByAccountId +
            ", acceptedAccountId = " + acceptedAccountId +
            ", acceptTime = " + acceptTime +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
