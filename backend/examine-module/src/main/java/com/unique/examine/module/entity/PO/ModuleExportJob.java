package com.unique.examine.module.entity.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_export_job")
@Schema(name = "ModuleExportJob对象", description = "模块导出任务")
public class ModuleExportJob implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long modelId;

    @Schema(description = "导出模板ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tplId;

    @Schema(description = "fileType: csv|xlsx（当前仅 csv）")
    private String fileType;

    @Schema(description = "任务状态：0=pending 1=running 2=success 3=failed")
    private Integer status;

    @Schema(description = "请求 query JSON（DSL）")
    private String queryJson;

    @Schema(description = "结果文件ID（un_upload_file.id）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resultFileId;

    @Schema(description = "错误信息（失败时）")
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;
}

