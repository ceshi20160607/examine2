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
 * OpenAPI 客户端
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_openapi_client")
public class Client implements Serializable {

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
     * 客户端编码
     */
    @TableField("client_code")
    private String clientCode;

    /**
     * 客户端名称
     */
    @TableField("client_name")
    private String clientName;

    /**
     * 状态：ENABLED、DISABLED、EXPIRED
     */
    @TableField("status")
    private String status;

    /**
     * 每分钟限流
     */
    @TableField("rate_limit_per_minute")
    private Integer rateLimitPerMinute;

    /**
     * 过期时间
     */
    @TableField("expired_at")
    private LocalDateTime expiredAt;

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
