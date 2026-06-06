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
 * OpenAPI 凭证
 * </p>
 *
 * @author codex
 * @since 2026-06-05
 */
@Getter
@Setter
@TableName("un_openapi_credential")
public class Credential implements Serializable {

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
     * 访问 key
     */
    @TableField("access_key")
    private String accessKey;

    /**
     * 密钥哈希或密文摘要
     */
    @TableField("secret_hash")
    private String secretHash;

    /**
     * 密钥版本
     */
    @TableField("secret_version")
    private Integer secretVersion;

    /**
     * 签名算法：HMAC_SHA256
     */
    @TableField("sign_algorithm")
    private String signAlgorithm;

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
