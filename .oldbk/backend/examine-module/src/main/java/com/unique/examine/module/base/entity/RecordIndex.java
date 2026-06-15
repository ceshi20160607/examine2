package com.unique.examine.module.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 动态字段查询、排序、筛选 typed index。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_record_index")
@Schema(name = "RecordIndex", description = "动态字段查询、排序、筛选 typed index。")
public class RecordIndex implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "索引值 ID。")
    @TableId(value = "index_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long indexId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "记录 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "字段 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "字段编码快照。")
    private String fieldCode;

    @Schema(description = "行标识。")
    private String rowKey;

    @Schema(description = "文本查询索引。")
    private String indexText;

    @Schema(description = "数值索引。")
    private BigDecimal indexNumber;

    @Schema(description = "日期时间索引。")
    private LocalDateTime indexDatetime;

    @Schema(description = "日期索引。")
    private LocalDate indexDate;

    @Schema(description = "开关索引。")
    private Byte indexBool;

    @Schema(description = "IN、多值、关联等 hash 索引。")
    private String indexHash;

    @Schema(description = "记录状态快照，用于过滤删除记录。")
    private String recordStatus;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
