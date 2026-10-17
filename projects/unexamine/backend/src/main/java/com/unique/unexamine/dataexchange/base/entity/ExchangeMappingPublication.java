package com.unique.unexamine.dataexchange.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.time.LocalDateTime;

/**
 * <p>
 * 模块导入导出映射当前生效版本指针
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_exchange_mapping_publication")
public class ExchangeMappingPublication {

    /**
     * 映射当前发布指针主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 映射所属租户主键
     */
    @TableField("owner_tenant_id")
    private Long ownerTenantId;

    /**
     * 映射草稿主键
     */
    @TableField("mapping_id")
    private Long mappingId;

    /**
     * 当前生效的不可变映射版本
     */
    @TableField("current_version_id")
    private Long currentVersionId;

    /**
     * 最近发布或回滚的系统成员主键
     */
    @TableField("updated_by_member_id")
    private Long updatedByMemberId;

    /**
     * 最近切换发布时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;

    /**
     * 发布指针乐观锁版本
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

    public Long getOwnerTenantId() {
        return ownerTenantId;
    }

    public void setOwnerTenantId(Long ownerTenantId) {
        this.ownerTenantId = ownerTenantId;
    }

    public Long getMappingId() {
        return mappingId;
    }

    public void setMappingId(Long mappingId) {
        this.mappingId = mappingId;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public Long getUpdatedByMemberId() {
        return updatedByMemberId;
    }

    public void setUpdatedByMemberId(Long updatedByMemberId) {
        this.updatedByMemberId = updatedByMemberId;
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
        return "ExchangeMappingPublication{" +
            "id = " + id +
            ", ownerTenantId = " + ownerTenantId +
            ", mappingId = " + mappingId +
            ", currentVersionId = " + currentVersionId +
            ", updatedByMemberId = " + updatedByMemberId +
            ", updateTime = " + updateTime +
            ", version = " + version +
            "}";
    }
}
