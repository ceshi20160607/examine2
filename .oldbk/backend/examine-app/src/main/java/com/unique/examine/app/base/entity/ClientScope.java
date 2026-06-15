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
 * scope、模块、动作、字段读写权限和数据范围。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_openapi_client_scope")
@Schema(name = "ClientScope", description = "scope、模块、动作、字段读写权限和数据范围。")
public class ClientScope implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "OpenAPI 客户端 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long clientId;

    @Schema(description = "绑定系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "绑定租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "scope，如 record:read、record:create、flow:task:handle、file:download。")
    private String scopeCode;

    @Schema(description = "限定动态模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "字段可读、可写授权。")
    private String fieldPermissionJson;

    @Schema(description = "数据范围规则。")
    private String dataScopeJson;

    @Schema(description = "ENABLED、DISABLED。")
    private String status;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
