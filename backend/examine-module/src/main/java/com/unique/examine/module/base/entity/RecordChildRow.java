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
 * 子表字段行数据和行顺序。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_record_child_row")
@Schema(name = "RecordChildRow", description = "子表字段行数据和行顺序。")
public class RecordChildRow implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "子表行 ID。")
    @TableId(value = "child_row_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long childRowId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "主记录 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "子表字段 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentFieldId;

    @Schema(description = "行 key，写入 un_module_record_value.row_key。")
    private String rowKey;

    @Schema(description = "行顺序。")
    private Integer rowOrder;

    @Schema(description = "ACTIVE、DELETED。")
    private String rowStatus;

    @Schema(description = "软删除唯一复用标记。")
    private String deleteMarker;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
