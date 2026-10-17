package com.unique.unexamine.moduleconfig.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 数据字典当前生效发布版本指针
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_cfg_dictionary_publication")
public class CfgDictionaryPublication {

    /**
     * 主键标识
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属系统标识
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 配置所属租户标识
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 所属字典标识
     */
    @TableField("dictionary_id")
    private Long dictionaryId;

    /**
     * 当前生效版本标识
     */
    @TableField("current_version_id")
    private Long currentVersionId;

    /**
     * 发布系统成员标识
     */
    @TableField("published_by_member_id")
    private Long publishedByMemberId;

    /**
     * 发布时间
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /**
     * 乐观锁版本号
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

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getDictionaryId() {
        return dictionaryId;
    }

    public void setDictionaryId(Long dictionaryId) {
        this.dictionaryId = dictionaryId;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public Long getPublishedByMemberId() {
        return publishedByMemberId;
    }

    public void setPublishedByMemberId(Long publishedByMemberId) {
        this.publishedByMemberId = publishedByMemberId;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "CfgDictionaryPublication{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", ownerTenantId = " + ownerTenantId +
            ", dictionaryId = " + dictionaryId +
            ", currentVersionId = " + currentVersionId +
            ", publishedByMemberId = " + publishedByMemberId +
            ", publishTime = " + publishTime +
            ", version = " + version +
            "}";
    }
}
