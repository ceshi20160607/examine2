package com.unique.unexamine.runtimedata.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("biz_record_index")
public class BizRecordIndex {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("system_id")
    private Long systemId;

    @TableField("tenant_id")
    private Long tenantId;

    @TableField("module_id")
    private Long moduleId;

    @TableField("record_id")
    private Long recordId;

    @TableField("query_index_id")
    private Long queryIndexId;

    @TableField("index_key_hash")
    private String indexKeyHash;

    @TableField("unique_key_hash")
    private String uniqueKeyHash;

    @TableField("index_key_text")
    private String indexKeyText;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

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

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getQueryIndexId() {
        return queryIndexId;
    }

    public void setQueryIndexId(Long queryIndexId) {
        this.queryIndexId = queryIndexId;
    }

    public String getIndexKeyHash() {
        return indexKeyHash;
    }

    public void setIndexKeyHash(String indexKeyHash) {
        this.indexKeyHash = indexKeyHash;
    }

    public String getUniqueKeyHash() {
        return uniqueKeyHash;
    }

    public void setUniqueKeyHash(String uniqueKeyHash) {
        this.uniqueKeyHash = uniqueKeyHash;
    }

    public String getIndexKeyText() {
        return indexKeyText;
    }

    public void setIndexKeyText(String indexKeyText) {
        this.indexKeyText = indexKeyText;
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

    @Override
    public String toString() {
        return "BizRecordIndex{" +
            "id = " + id +
            ", systemId = " + systemId +
            ", tenantId = " + tenantId +
            ", moduleId = " + moduleId +
            ", recordId = " + recordId +
            ", queryIndexId = " + queryIndexId +
            ", indexKeyHash = " + indexKeyHash +
            ", uniqueKeyHash = " + uniqueKeyHash +
            ", indexKeyText = " + indexKeyText +
            ", createdAt = " + createdAt +
            ", updatedAt = " + updatedAt +
            "}";
    }
}
