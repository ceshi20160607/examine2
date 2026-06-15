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
 * 列表、表单、详情 schema 草稿。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_page_schema")
@Schema(name = "PageSchema", description = "列表、表单、详情 schema 草稿。")
public class PageSchema implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "页面 schema ID。")
    @TableId(value = "schema_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long schemaId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "LIST、FORM、DETAIL。")
    private String pageType;

    @Schema(description = "schema 编码。")
    private String schemaCode;

    @Schema(description = "schema 名称。")
    private String schemaName;

    @Schema(description = "列表列、筛选、排序、表单分区或详情区块配置。")
    private String schemaJson;

    @Schema(description = "草稿版本号。")
    private Integer draftVersion;

    @Schema(description = "草稿状态。")
    private String schemaStatus;

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
