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
 * 系统级或租户级字典类型。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_dict_type")
@Schema(name = "DictType", description = "系统级或租户级字典类型。")
public class DictType implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "作用域：SYSTEM、TENANT。")
    private String scopeType;

    @Schema(description = "作用域租户 ID；SYSTEM 为 0，TENANT 必须为有效租户 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long scopeTenantId;

    @Schema(description = "字典类型编码，同作用域唯一。")
    private String code;

    @Schema(description = "字典类型名称。")
    private String name;

    @Schema(description = "描述。")
    private String description;

    @Schema(description = "状态：ENABLED、DISABLED、DELETED。")
    private String status;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "是否内置只读。")
    private Byte systemBuiltIn;

    @Schema(description = "字典缓存版本；写操作成功后递增。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long cacheVersion;

    @Schema(description = "字典项数量冗余。")
    private Integer itemCount;

    @Schema(description = "启用字典项数量冗余。")
    private Integer enabledItemCount;

    @Schema(description = "是否存在字段或记录引用。")
    private Byte referencedFlag;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
