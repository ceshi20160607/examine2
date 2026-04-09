package com.unique.examine.app.entity.PO;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * <p>
 * client 凭证（AK/SK）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_app_client_credential")
@Schema(name = "AppClientCredential对象", description = "client 凭证（AK/SK）")
public class AppClientCredential implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "凭证ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "un_app_client.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long clientId;

    @Schema(description = "accessKey（明文，可用于标识 client）")
    private String accessKey;

    @Schema(description = "secret 哈希（建议 BCrypt，不存明文）")
    private String secretHash;

    @Schema(description = "状态：1=启用 2=停用")
    private Integer status;

    @Schema(description = "过期时间（可选）")
    private LocalDateTime expiredTime;

    @Schema(description = "最近使用时间（可选）")
    private LocalDateTime lastUsedTime;

    @Schema(description = "创建人 platId")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
