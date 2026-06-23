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
 * 关联字段保存的记录间关系。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_record_relation")
@Schema(name = "RecordRelation", description = "关联字段保存的记录间关系。")
public class RecordRelation implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "关联关系 ID。")
    @TableId(value = "relation_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long relationId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "来源模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceModuleId;

    @Schema(description = "来源记录。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sourceRecordId;

    @Schema(description = "关联字段。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "主表或子表行标识。")
    private String rowKey;

    @Schema(description = "目标模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetModuleId;

    @Schema(description = "目标记录。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetRecordId;

    @Schema(description = "关系类型。")
    private String relationType;

    @Schema(description = "目标记录标题等展示快照。")
    private String displaySnapshotJson;

    @Schema(description = "软删除唯一复用标记。")
    private String deleteMarker;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
