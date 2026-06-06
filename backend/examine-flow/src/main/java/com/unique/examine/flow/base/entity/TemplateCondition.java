package com.unique.examine.flow.base.entity;

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
 * 连线条件表达式结构化存储。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_template_condition")
@Schema(name = "TemplateCondition", description = "连线条件表达式结构化存储。")
public class TemplateCondition implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "连线 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long lineId;

    @Schema(description = "动态字段编码。")
    private String fieldCode;

    @Schema(description = "条件操作符，如 EQ、NE、GT、IN、EMPTY。")
    private String operator;

    @Schema(description = "比较值快照。")
    private String compareValueJson;

    @Schema(description = "复杂表达式结构。")
    private String expressionJson;

    @Schema(description = "条件排序。")
    private Integer sortOrder;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
