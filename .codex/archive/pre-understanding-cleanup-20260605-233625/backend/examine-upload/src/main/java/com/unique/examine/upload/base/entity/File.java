package com.unique.examine.upload.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 上传文件
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_upload_file")
public class File implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 租户 ID
     */
    @TableField("tenant_id")
    private Long tenantId;

    /**
     * 存储配置 ID
     */
    @TableField("storage_config_id")
    private Long storageConfigId;

    /**
     * 原始文件名
     */
    @TableField("original_name")
    private String originalName;

    /**
     * 文件扩展名
     */
    @TableField("file_ext")
    private String fileExt;

    /**
     * MIME 类型
     */
    @TableField("mime_type")
    private String mimeType;

    /**
     * 文件大小
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 存储路径
     */
    @TableField("storage_path")
    private String storagePath;

    /**
     * 文件哈希
     */
    @TableField("sha256")
    private String sha256;

    /**
     * 状态：TEMP、REFERENCED、DELETED
     */
    @TableField("status")
    private String status;

    /**
     * 上传人账号 ID
     */
    @TableField("uploaded_by")
    private Long uploadedBy;

    /**
     * 创建人账号 ID
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 更新人账号 ID
     */
    @TableField("updated_by")
    private Long updatedBy;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 逻辑删除：0-否，1-是
     */
    @TableField("deleted")
    @TableLogic
    private Byte deleted;
}
