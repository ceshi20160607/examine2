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
 * 发布版本内连线结构。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_template_line")
@Schema(name = "TemplateLine", description = "发布版本内连线结构。")
public class TemplateLine implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "流程发布版本 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateVersionId;

    @Schema(description = "连线稳定编码，版本内唯一。")
    private String lineKey;

    @Schema(description = "起点节点编码。")
    private String fromNodeKey;

    @Schema(description = "终点节点编码。")
    private String toNodeKey;

    @Schema(description = "ALWAYS、EXPRESSION。")
    private String conditionMode;

    @Schema(description = "条件匹配顺序。")
    private Integer sortOrder;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
