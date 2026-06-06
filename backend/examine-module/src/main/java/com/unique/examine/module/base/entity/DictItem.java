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
 * 字典项，支持层级和内置只读。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_dict_item")
@Schema(name = "DictItem", description = "字典项，支持层级和内置只读。")
public class DictItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID，冗余用于隔离和索引。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "字典类型 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dictTypeId;

    @Schema(description = "父字典项 ID，0 表示根项。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @Schema(description = "字典项编码，同父级唯一。")
    private String code;

    @Schema(description = "展示文本。")
    private String label;

    @Schema(description = "业务值，同父级唯一。")
    private String value;

    @Schema(description = "描述。")
    private String description;

    @Schema(description = "状态：ENABLED、DISABLED、DELETED。")
    private String status;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "层级，最大 5。")
    private Integer depthLevel;

    @Schema(description = "路径，形如 /rootId/childId。")
    private String depthPath;

    @Schema(description = "是否叶子节点。")
    private Byte leafFlag;

    @Schema(description = "是否内置只读。")
    private Byte systemBuiltIn;

    @Schema(description = "是否被记录值引用。")
    private Byte referencedFlag;

    @Schema(description = "扩展信息，不允许存敏感信息。")
    private String extJson;

    @Schema(description = "冗余字典类型缓存版本，便于返回和测试断言。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long cacheVersion;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
