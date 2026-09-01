package com.unique.unexamine.operations.base.entity;

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
@TableName("ops_backup_item")
public class OpsBackupItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("backup_id")
    private Long backupId;

    @TableField("item_type")
    private String itemType;

    @TableField("storage_uri")
    private String storageUri;

    @TableField("size_bytes")
    private Long sizeBytes;

    @TableField("sha256")
    private String sha256;

    @TableField("`status`")
    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBackupId() {
        return backupId;
    }

    public void setBackupId(Long backupId) {
        this.backupId = backupId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getStorageUri() {
        return storageUri;
    }

    public void setStorageUri(String storageUri) {
        this.storageUri = storageUri;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "OpsBackupItem{" +
            "id = " + id +
            ", backupId = " + backupId +
            ", itemType = " + itemType +
            ", storageUri = " + storageUri +
            ", sizeBytes = " + sizeBytes +
            ", sha256 = " + sha256 +
            ", status = " + status +
            ", createdAt = " + createdAt +
            "}";
    }
}
