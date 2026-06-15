package com.unique.examine.plat.base.entity;

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
 * 密码策略、会话策略、文件存储、OpenAPI 全局策略和审计保留配置。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_config")
@Schema(name = "Config", description = "密码策略、会话策略、文件存储、OpenAPI 全局策略和审计保留配置。")
public class Config implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "配置 key，全局唯一。")
    private String configKey;

    @Schema(description = "配置名称。")
    private String configName;

    @Schema(description = "配置值；敏感字段只保存密文或引用。")
    private String configValue;

    @Schema(description = "是否敏感配置。")
    private Byte sensitiveFlag;

    @Schema(description = "状态：ENABLED、DISABLED。")
    private String status;

    @Schema(description = "备注。")
    private String remark;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
