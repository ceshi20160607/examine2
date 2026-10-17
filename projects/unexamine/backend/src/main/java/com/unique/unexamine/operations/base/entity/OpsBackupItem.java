package com.unique.unexamine.operations.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * <p>
 * 备份清单内容及完整性校验
 * </p>
 *
 * @author Template Base
 * @since generated
 */
@TableName("un_ops_backup_item")
public class OpsBackupItem {

    /**
     * 备份内容项主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属备份任务主键
     */
    @TableField("backup_id")
    private Long backupId;

    /**
     * 数据库、文件、配置或模板等内容类别
     */
    @TableField("item_type")
    private String itemType;

    /**
     * 备份内容的受控存储位置
     */
    @TableField("storage_uri")
    private String storageUri;

    /**
     * 备份内容字节大小
     */
    @TableField("size_bytes")
    private Long sizeBytes;

    /**
     * 备份内容 SHA-256 校验摘要
     */
    @TableField("sha256")
    private String sha256;

    /**
     * 备份内容校验状态
     */
    @TableField("`status`")
    private String status;

    /**
     * 备份内容登记时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
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
            ", createTime = " + createTime +
            "}";
    }
}
