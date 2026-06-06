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
 * 应用级配置版本和发布快照摘要。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_app_version")
@Schema(name = "AppVersion", description = "应用级配置版本和发布快照摘要。")
public class AppVersion implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "应用版本 ID。")
    @TableId(value = "app_version_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appVersionId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "应用 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "应用版本号，按应用递增。")
    private Integer versionNo;

    @Schema(description = "版本名称。")
    private String versionName;

    @Schema(description = "版本状态。")
    private String publishStatus;

    @Schema(description = "应用、模块、菜单和发布模块摘要快照。")
    private String snapshotJson;

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
