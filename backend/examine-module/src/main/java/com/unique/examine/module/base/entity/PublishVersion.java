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
 * 模块发布版本快照，运行态只读。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_publish_version")
@Schema(name = "PublishVersion", description = "模块发布版本快照，运行态只读。")
public class PublishVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "发布版本 ID。")
    @TableId(value = "publish_version_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishVersionId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "应用 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "模块 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "模块发布版本号。")
    private Integer versionNo;

    @Schema(description = "发布状态。")
    private String publishStatus;

    @Schema(description = "字段定义、字段权限基础信息、选项和唯一配置快照。")
    private String fieldSnapshotJson;

    @Schema(description = "列表、表单、详情 schema 快照。")
    private String pageSnapshotJson;

    @Schema(description = "菜单和动作配置快照。")
    private String menuActionSnapshotJson;

    @Schema(description = "流程绑定快照。")
    private String flowBindingSnapshotJson;

    @Schema(description = "可用导出模板摘要快照。")
    private String exportTemplateSnapshotJson;

    @Schema(description = "发布检查结果。")
    private String checkResultJson;

    @Schema(description = "发布说明。")
    private String publishRemark;

    @Schema(description = "发布成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishedBy;

    @Schema(description = "发布时间。")
    private LocalDateTime publishedAt;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
