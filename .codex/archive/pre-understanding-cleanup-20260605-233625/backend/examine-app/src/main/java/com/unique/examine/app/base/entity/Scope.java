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
 * OpenAPI 授权范围
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_openapi_scope")
public class Scope implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 客户端 ID
     */
    @TableField("client_id")
    private Long clientId;

    /**
     * 授权应用 ID
     */
    @TableField("app_id")
    private Long appId;

    /**
     * 授权模块 ID
     */
    @TableField("module_id")
    private Long moduleId;

    /**
     * 授权范围编码
     */
    @TableField("scope_code")
    private String scopeCode;

    /**
     * 动作集合：READ、WRITE、DELETE、FLOW
     */
    @TableField("actions")
    private String actions;

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
