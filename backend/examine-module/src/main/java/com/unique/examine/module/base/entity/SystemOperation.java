package com.unique.examine.module.base.entity;

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
 * 系统内操作权限目录。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_system_operation")
@Schema(name = "SystemOperation", description = "系统内操作权限目录。")
public class SystemOperation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属菜单；系统级操作可为空。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long menuId;

    @Schema(description = "操作编码，如 SYS_MEMBER_VIEW、RECORD_EDIT。")
    private String code;

    @Schema(description = "操作名称。")
    private String name;

    @Schema(description = "类型：API、BUTTON、FLOW_ACTION、EXPORT、OPENAPI_SCOPE。")
    private String operationType;

    @Schema(description = "资源类型：SYSTEM、MODULE、FIELD、FLOW、EXPORT、OPENAPI。")
    private String resourceType;

    @Schema(description = "资源 ID，跨分片逻辑引用。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resourceId;

    @Schema(description = "对应 API 路径模式。")
    private String apiPattern;

    @Schema(description = "HTTP 方法。")
    private String method;

    @Schema(description = "状态：ENABLED、DISABLED。")
    private String status;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
