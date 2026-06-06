package com.unique.examine.app.base.entity;

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
 * 应用版本
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_app_version")
public class Version implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 应用 ID
     */
    @TableField("app_id")
    private Long appId;

    /**
     * 版本号
     */
    @TableField("version_no")
    private Integer versionNo;

    /**
     * 版本名称
     */
    @TableField("version_name")
    private String versionName;

    /**
     * 状态：DRAFT、PUBLISHED、ARCHIVED
     */
    @TableField("status")
    private String status;

    /**
     * 发布配置快照
     */
    @TableField("snapshot_json")
    private String snapshotJson;

    /**
     * 发布时间
     */
    @TableField("published_at")
    private LocalDateTime publishedAt;

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
