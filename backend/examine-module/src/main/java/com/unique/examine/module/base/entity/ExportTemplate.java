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
 * 导出模板主表。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_export_template")
@Schema(name = "ExportTemplate", description = "导出模板主表。")
public class ExportTemplate implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "导出模板 ID。")
    @TableId(value = "template_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "模板编码。")
    private String templateCode;

    @Schema(description = "模板名称。")
    private String templateName;

    @Schema(description = "模板状态。")
    private String templateStatus;

    @Schema(description = "结果文件名规则。")
    private String fileNamePattern;

    @Schema(description = "导出格式，MVP 默认 XLSX。")
    private String exportFormat;

    @Schema(description = "是否导出历史，MVP 默认否。")
    private Byte includeHistoryFlag;

    @Schema(description = "表头、冻结列、样式等配置。")
    private String configJson;

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
