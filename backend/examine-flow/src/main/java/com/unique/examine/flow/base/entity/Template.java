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
 * 流程模板主表。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_template")
@Schema(name = "Template", description = "流程模板主表。")
public class Template implements Serializable {

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

    @Schema(description = "流程模板编码，同系统租户内唯一。")
    private String code;

    @Schema(description = "流程模板名称。")
    private String name;

    @Schema(description = "DRAFT、PUBLISHED、DISABLED。")
    private String status;

    @Schema(description = "当前发布版本 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentVersionId;

    @Schema(description = "模板说明。")
    private String description;

    @Schema(description = "乐观锁版本。")
    private Integer versionNo;

    @Schema(description = "逻辑删除。")
    private Byte deleted;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
