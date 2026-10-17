package com.unique.unexamine.platform.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 平台统一身份源配置
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_plat_sso_provider")
public class PlatSsoProvider {

    /**
     * 平台身份源主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属平台标识
     */
    @TableField("platform_id")
    private Long platformId;

    /**
     * 平台范围内身份源编码
     */
    @TableField("`code`")
    private String code;

    /**
     * 身份源显示名称
     */
    @TableField("`name`")
    private String name;

    /**
     * 身份源认证协议
     */
    @TableField("protocol")
    private String protocol;

    /**
     * 身份源发行者标识
     */
    @TableField("`issuer`")
    private String issuer;

    /**
     * 身份源客户端标识
     */
    @TableField("client_id")
    private String clientId;

    /**
     * 客户端密钥的受控引用
     */
    @TableField("client_secret_ref")
    private String clientSecretRef;

    /**
     * 身份源公共元数据
     */
    @TableField("metadata_json")
    private String metadataJson;

    /**
     * 身份源启用状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 当前发布的身份源版本标识
     */
    @TableField("published_version_id")
    private Long publishedVersionId;

    /**
     * 当前发布的身份源版本号
     */
    @TableField("published_version_number")
    private Integer publishedVersionNumber;

    /**
     * 身份源创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 身份源更新时间
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

    public Long getPlatformId() {
        return platformId;
    }

    public void setPlatformId(Long platformId) {
        this.platformId = platformId;
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
        return "PlatSsoProvider{" +
            "id = " + id +
            ", platformId = " + platformId +
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
            ", createTime = " + createTime +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
