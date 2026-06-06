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
 * 动态业务记录主表。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_record")
@Schema(name = "Record", description = "动态业务记录主表。")
public class Record implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "记录 ID。")
    @TableId(value = "record_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属应用。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "创建或最后保存时使用的发布版本。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishVersionId;

    @Schema(description = "业务编号或自动编号。")
    private String recordNo;

    @Schema(description = "列表展示标题冗余。")
    private String title;

    @Schema(description = "业务记录状态。")
    private String recordStatus;

    @Schema(description = "流程摘要状态。")
    private String flowStatus;

    @Schema(description = "流程实例逻辑引用。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long flowInstanceId;

    @Schema(description = "是否流程锁定。")
    private Byte lockedFlag;

    @Schema(description = "记录乐观锁版本。")
    private Integer recordVersion;

    @Schema(description = "唯一索引用记录状态标记；删除后写入记录 ID。")
    private String activeUniqueMarker;

    @Schema(description = "创建成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @Schema(description = "更新成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updatedBy;

    @Schema(description = "删除成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deletedBy;

    @Schema(description = "删除时间。")
    private LocalDateTime deletedAt;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
