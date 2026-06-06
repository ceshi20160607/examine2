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
 * OpenAPI 幂等记录
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_openapi_idempotent")
public class Idempotent implements Serializable {

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
     * 幂等键
     */
    @TableField("idempotent_key")
    private String idempotentKey;

    /**
     * 请求摘要
     */
    @TableField("request_hash")
    private String requestHash;

    /**
     * 响应摘要
     */
    @TableField("response_hash")
    private String responseHash;

    /**
     * 状态：PROCESSING、SUCCESS、FAILED
     */
    @TableField("status")
    private String status;

    /**
     * 过期时间
     */
    @TableField("expired_at")
    private LocalDateTime expiredAt;

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
