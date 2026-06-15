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
 * 字段级唯一和组合唯一 typed hash 索引。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_record_unique_index")
@Schema(name = "RecordUniqueIndex", description = "字段级唯一和组合唯一 typed hash 索引。")
public class RecordUniqueIndex implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "唯一索引记录 ID。")
    @TableId(value = "unique_index_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uniqueIndexId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "字段级唯一写 FIELD:{fieldId}，组合唯一写配置编码。")
    private String constraintCode;

    @Schema(description = "参与唯一字段 ID。")
    private String fieldIdsJson;

    @Schema(description = "参与唯一字段编码。")
    private String fieldCodesJson;

    @Schema(description = "typed value 组合 hash。")
    private String combinedValueHash;

    @Schema(description = "冲突提示展示值。")
    private String displayValuesJson;

    @Schema(description = "记录 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "非删除记录为 ACTIVE，记录软删除后写入记录 ID。")
    private String activeUniqueMarker;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
