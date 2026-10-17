package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 系统租户对平台身份源的独立配置与发布绑定
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_sso_binding")
public class SysSsoBinding {

    /**
     * 系统身份源绑定主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 系统与身份源共同所属的平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 目标系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 目标租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 平台身份源标识
     */
    @TableField("provider_id")
    private Long providerId;

    /**
     * 固定的平台身份源发布版本标识
     */
    @TableField("provider_version_id")
    private Long providerVersionId;

    /**
     * 该系统租户身份源绑定的配置版本号
     */
    @TableField("binding_version")
    private Integer bindingVersion;

    /**
     * 该绑定允许的租户登录域名集合
     */
    @TableField("tenant_domains_json")
    private String tenantDomainsJson;

    /**
     * 外部部门到系统部门的映射快照
     */
    @TableField("department_mapping_json")
    private String departmentMappingJson;

    /**
     * 外部用户到账号及员工的匹配顺序
     */
    @TableField("member_match_json")
    private String memberMatchJson;

    /**
     * 系统成员即时创建和申请策略
     */
    @TableField("jit_policy_json")
    private String jitPolicyJson;

    /**
     * 最近一次身份映射预演结果
     */
    @TableField("preflight_report_json")
    private String preflightReportJson;

    /**
     * 草稿、启用或停用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 关联的人工确认同步作业标识
     */
    @TableField("sync_job_id")
    private Long syncJobId;

    /**
     * 创建配置的系统成员标识
     */
    @TableField("created_by_member_id")
    private Long createdByMemberId;

    /**
     * 确认配置的系统成员标识
     */
    @TableField("confirmed_by_member_id")
    private Long confirmedByMemberId;

    /**
     * 最近一次预演完成时间
     */
    @TableField("preflight_time")
    private LocalDateTime preflightTime;

    /**
     * 人工确认时间
     */
    @TableField("confirmed_time")
    private LocalDateTime confirmedTime;

    /**
     * 绑定配置创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 绑定配置更新时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;

    /**
     * 乐观锁版本
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

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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

    public Long getProviderId() {
        return providerId;
    }

    public void setProviderId(Long providerId) {
        this.providerId = providerId;
    }

    public Long getProviderVersionId() {
        return providerVersionId;
    }

    public void setProviderVersionId(Long providerVersionId) {
        this.providerVersionId = providerVersionId;
    }

    public Integer getBindingVersion() {
        return bindingVersion;
    }

    public void setBindingVersion(Integer bindingVersion) {
        this.bindingVersion = bindingVersion;
    }

    public String getTenantDomainsJson() {
        return tenantDomainsJson;
    }

    public void setTenantDomainsJson(String tenantDomainsJson) {
        this.tenantDomainsJson = tenantDomainsJson;
    }

    public String getDepartmentMappingJson() {
        return departmentMappingJson;
    }

    public void setDepartmentMappingJson(String departmentMappingJson) {
        this.departmentMappingJson = departmentMappingJson;
    }

    public String getMemberMatchJson() {
        return memberMatchJson;
    }

    public void setMemberMatchJson(String memberMatchJson) {
        this.memberMatchJson = memberMatchJson;
    }

    public String getJitPolicyJson() {
        return jitPolicyJson;
    }

    public void setJitPolicyJson(String jitPolicyJson) {
        this.jitPolicyJson = jitPolicyJson;
    }

    public String getPreflightReportJson() {
        return preflightReportJson;
    }

    public void setPreflightReportJson(String preflightReportJson) {
        this.preflightReportJson = preflightReportJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getSyncJobId() {
        return syncJobId;
    }

    public void setSyncJobId(Long syncJobId) {
        this.syncJobId = syncJobId;
    }

    public Long getCreatedByMemberId() {
        return createdByMemberId;
    }

    public void setCreatedByMemberId(Long createdByMemberId) {
        this.createdByMemberId = createdByMemberId;
    }

    public Long getConfirmedByMemberId() {
        return confirmedByMemberId;
    }

    public void setConfirmedByMemberId(Long confirmedByMemberId) {
        this.confirmedByMemberId = confirmedByMemberId;
    }

    public LocalDateTime getPreflightTime() {
        return preflightTime;
    }

    public void setPreflightTime(LocalDateTime preflightTime) {
        this.preflightTime = preflightTime;
    }

    public LocalDateTime getConfirmedTime() {
        return confirmedTime;
    }

    public void setConfirmedTime(LocalDateTime confirmedTime) {
        this.confirmedTime = confirmedTime;
    }

    public LocalDateTime getCreatedTime() {
        return createdTime;
    }

    public void setCreatedTime(LocalDateTime createdTime) {
        this.createdTime = createdTime;
    }

    public LocalDateTime getUpdatedTime() {
        return updatedTime;
    }

    public void setUpdatedTime(LocalDateTime updatedTime) {
        this.updatedTime = updatedTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "SysSsoBinding{" +
            "id = " + id +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", providerId = " + providerId +
            ", providerVersionId = " + providerVersionId +
            ", bindingVersion = " + bindingVersion +
            ", tenantDomainsJson = " + tenantDomainsJson +
            ", departmentMappingJson = " + departmentMappingJson +
            ", memberMatchJson = " + memberMatchJson +
            ", jitPolicyJson = " + jitPolicyJson +
            ", preflightReportJson = " + preflightReportJson +
            ", status = " + status +
            ", syncJobId = " + syncJobId +
            ", createdByMemberId = " + createdByMemberId +
            ", confirmedByMemberId = " + confirmedByMemberId +
            ", preflightTime = " + preflightTime +
            ", confirmedTime = " + confirmedTime +
            ", createdTime = " + createdTime +
            ", updatedTime = " + updatedTime +
            ", version = " + version +
            "}";
    }
}
