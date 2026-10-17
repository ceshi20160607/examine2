package com.unique.unexamine.file.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台或系统的文件存储后端配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_file_storage_backend")
public class FileStorageBackend {

    /**
     * 存储后端主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 后端作用域：PLATFORM 或 SYSTEM
     */
    @TableField("context_type")
    private String contextType;

    /**
     * 后端所属平台
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 系统后端所属系统，平台后端为空
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 后端作用域主体，平台取平台标识，系统取系统标识
     */
    @TableField("scope_owner_id")
    private Long scopeOwnerId;

    /**
     * 同一作用域内的后端编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 存储后端显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 存储类型：LOCAL 或 S3
     */
    @TableField("backend_type")
    private String backendType;

    /**
     * S3 兼容服务地址
     */
    @TableField("endpoint")
    private String endpoint;

    /**
     * 本地目录或对象存储桶名称
     */
    @TableField("bucket_name")
    private String bucketName;

    /**
     * S3 凭证的受控密钥引用主键，不保存凭证明文或密文
     */
    @TableField("credential_ref_id")
    private Long credentialRefId;

    /**
     * 非敏感存储参数，禁止写入访问密钥
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 后端启用状态
     */
    @TableField("`status`")
    private String status;

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

    public String getContextType() {
        return contextType;
    }

    public void setContextType(String contextType) {
        this.contextType = contextType;
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

    public Long getScopeOwnerId() {
        return scopeOwnerId;
    }

    public void setScopeOwnerId(Long scopeOwnerId) {
        this.scopeOwnerId = scopeOwnerId;
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

    public String getBackendType() {
        return backendType;
    }

    public void setBackendType(String backendType) {
        this.backendType = backendType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getBucketName() {
        return bucketName;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public Long getCredentialRefId() {
        return credentialRefId;
    }

    public void setCredentialRefId(Long credentialRefId) {
        this.credentialRefId = credentialRefId;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
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
        return "FileStorageBackend{" +
            "id = " + id +
            ", contextType = " + contextType +
            ", platformId = " + platformId +
            ", systemId = " + systemId +
            ", scopeOwnerId = " + scopeOwnerId +
            ", code = " + code +
            ", name = " + name +
            ", backendType = " + backendType +
            ", endpoint = " + endpoint +
            ", bucketName = " + bucketName +
            ", credentialRefId = " + credentialRefId +
            ", configJson = " + configJson +
            ", status = " + status +
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
