package com.unique.examine.upload.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * <p>
 * 文件存储配置
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_upload_storage_config")
public class StorageConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 存储配置编码
     */
    @TableField("config_code")
    private String configCode;

    /**
     * 类型：LOCAL、S3、MINIO、OSS
     */
    @TableField("storage_type")
    private String storageType;

    /**
     * 桶名
     */
    @TableField("bucket_name")
    private String bucketName;

    /**
     * 访问端点
     */
    @TableField("endpoint")
    private String endpoint;

    /**
     * 基础路径
     */
    @TableField("base_path")
    private String basePath;

    /**
     * 扩展配置
     */
    @TableField("config_json")
    private String configJson;

    /**
     * 状态：ENABLED、DISABLED
     */
    @TableField("status")
    private String status;

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
}
