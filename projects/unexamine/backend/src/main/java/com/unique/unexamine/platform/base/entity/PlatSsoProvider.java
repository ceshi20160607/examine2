package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("plat_sso_provider")
public class PlatSsoProvider {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("`code`")
    private String code;

    @TableField("`name`")
    private String name;

    @TableField("protocol")
    private String protocol;

    @TableField("`issuer`")
    private String issuer;

    @TableField("client_id")
    private String clientId;

    @TableField("client_secret_ref")
    private String clientSecretRef;

    @TableField("metadata_json")
    private String metadataJson;

    @TableField("`status`")
    private String status;

    @TableField("published_version_id")
    private Long publishedVersionId;

    @TableField("published_version_number")
    private Integer publishedVersionNumber;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    @Version
    @TableField("version")
    private Integer version;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getClientSecretRef() {
        return clientSecretRef;
    }

    public void setClientSecretRef(String clientSecretRef) {
        this.clientSecretRef = clientSecretRef;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getPublishedVersionId() {
        return publishedVersionId;
    }

    public void setPublishedVersionId(Long publishedVersionId) {
        this.publishedVersionId = publishedVersionId;
    }

    public Integer getPublishedVersionNumber() {
        return publishedVersionNumber;
    }

    public void setPublishedVersionNumber(Integer publishedVersionNumber) {
        this.publishedVersionNumber = publishedVersionNumber;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "PlatSsoProvider{" +
            "id = " + id +
            ", code = " + code +
            ", name = " + name +
            ", protocol = " + protocol +
            ", issuer = " + issuer +
            ", clientId = " + clientId +
            ", clientSecretRef = " + clientSecretRef +
            ", metadataJson = " + metadataJson +
            ", status = " + status +
            ", publishedVersionId = " + publishedVersionId +
            ", publishedVersionNumber = " + publishedVersionNumber +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            ", version = " + version +
            "}";
    }
}
