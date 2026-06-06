package com.unique.examine.app.base.entity;

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
 * 可配置应用
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_app_application")
public class Application implements Serializable {

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
     * 系统 ID
     */
    @TableField("system_id")
    private Long systemId;

    /**
     * 应用编码
     */
    @TableField("app_code")
    private String appCode;

    /**
     * 应用名称
     */
    @TableField("app_name")
    private String appName;

    /**
     * 状态：DRAFT、PUBLISHED、DISABLED
     */
    @TableField("status")
    private String status;

    /**
     * 当前发布版本 ID
     */
    @TableField("published_version_id")
    private Long publishedVersionId;

    /**
     * 可见范围：TENANT、ROLE、CUSTOM
     */
    @TableField("visible_scope")
    private String visibleScope;

    /**
     * 应用说明
     */
    @TableField("description")
    private String description;

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
