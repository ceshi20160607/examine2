package com.unique.unexamine.system.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 业务系统访问域名
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_sys_system_domain")
public class SysSystemDomain {

    /**
     * 业务系统访问域名主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属业务系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 访问地址绑定的系统租户标识
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 域名用途类型
     */
    @TableField("domain_type")
    private String domainType;

    /**
     * 访问域名主机名
     */
    @TableField("`host`")
    private String host;

    /**
     * 系统访问路径前缀
     */
    @TableField("base_path")
    private String basePath;

    /**
     * 是否要求加密传输
     */
    @TableField("tls_required")
    private Boolean tlsRequired;

    /**
     * 域名归属验证时间
     */
    @TableField("verify_time")
    private LocalDateTime verifyTime;

    /**
     * 业务系统访问域名状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 业务系统访问域名创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 业务系统访问域名更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 业务系统访问域名乐观锁版本
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

    public String getDomainType() {
        return domainType;
    }

    public void setDomainType(String domainType) {
        this.domainType = domainType;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public Boolean getTlsRequired() {
        return tlsRequired;
    }

    public void setTlsRequired(Boolean tlsRequired) {
        this.tlsRequired = tlsRequired;
    }

    public LocalDateTime getVerifyTime() {
        return verifyTime;
    }

    public void setVerifyTime(LocalDateTime verifyTime) {
        this.verifyTime = verifyTime;
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
        return "SysSystemDomain{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", domainType = " + domainType +
            ", host = " + host +
            ", basePath = " + basePath +
            ", tlsRequired = " + tlsRequired +
            ", verifyTime = " + verifyTime +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
