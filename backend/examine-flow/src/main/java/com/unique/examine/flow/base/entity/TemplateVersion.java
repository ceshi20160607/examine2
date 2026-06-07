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
 * 流程发布版本和结构快照。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_template_version")
@Schema(name = "TemplateVersion", description = "流程发布版本和结构快照。")
public class TemplateVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "流程模板 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    @Schema(description = "发布版本号，同模板递增。")
    private Integer versionNo;

    @Schema(description = "PUBLISHED、DISCARDED。")
    private String status;

    @Schema(description = "发布说明。")
    private String publishComment;

    @Schema(description = "发布时流程图完整快照，用于历史解释。")
    private String graphSnapshotJson;

    @Schema(description = "发布检查结果快照。")
    private String checkResultJson;

    @Schema(description = "发布人系统成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishedBy;

    @Schema(description = "发布时间。")
    private LocalDateTime publishedAt;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
