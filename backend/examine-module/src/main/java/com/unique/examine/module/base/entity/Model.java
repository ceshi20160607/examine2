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
 * 动态模块主表。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_model")
@Schema(name = "Model", description = "动态模块主表。")
public class Model implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "模块 ID，对应 API moduleId。")
    @TableId(value = "module_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属应用。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "模块名称。")
    private String name;

    @Schema(description = "模块编码，同应用下唯一。")
    private String code;

    @Schema(description = "模块描述。")
    private String description;

    @Schema(description = "模块状态。")
    private String moduleStatus;

    @Schema(description = "当前运行态发布版本。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentPublishVersionId;

    @Schema(description = "流程绑定逻辑引用，实际流程表由 DBA-004 设计。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowBindingId;

    @Schema(description = "记录标题字段。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long titleFieldId;

    @Schema(description = "记录编号字段，可为空。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordNoFieldId;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "乐观锁版本。")
    private Integer version;

    @Schema(description = "软删除唯一复用标记。")
    private String deleteMarker;

    @Schema(description = "创建成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @Schema(description = "更新成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
