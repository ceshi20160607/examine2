package com.unique.examine.app.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * AK/SK 凭证、密钥密文、轮换和过期状态。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_openapi_client_credential")
@Schema(name = "ClientCredential", description = "AK/SK 凭证、密钥密文、轮换和过期状态。")
public class ClientCredential implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "OpenAPI 客户端 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long clientId;

    @Schema(description = "AK，外部请求传入。")
    private String accessKey;

    @Schema(description = "secret 哈希，用于辅助校验或审计，不保存明文。")
    private String secretHash;

    @Schema(description = "签名密钥密文。")
    private String signSecretEnc;

    @Schema(description = "脱敏展示值。")
    private String maskedSecret;

    @Schema(description = "签名算法。")
    private String algorithm;

    @Schema(description = "ACTIVE、EXPIRED、REVOKED。")
    private String status;

    @Schema(description = "secret 是否仍处于创建/轮换响应一次性展示窗口。")
    private Byte secretVisibleOnce;

    @Schema(description = "签发时间。")
    private LocalDateTime issuedAt;

    @Schema(description = "凭证过期时间。")
    private LocalDateTime expiresAt;

    @Schema(description = "吊销时间。")
    private LocalDateTime revokedAt;

    @Schema(description = "最近使用时间。")
    private LocalDateTime lastUsedAt;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
