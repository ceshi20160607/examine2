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
 * 系统/租户下业务应用主表。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_app")
@Schema(name = "App", description = "系统/租户下业务应用主表。")
public class App implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "应用 ID。")
    @TableId(value = "app_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long appId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户；单租户系统使用默认租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "应用名称。")
    private String name;

    @Schema(description = "应用编码，同系统同租户唯一。")
    private String code;

    @Schema(description = "应用图标。")
    private String icon;

    @Schema(description = "应用描述。")
    private String description;

    @Schema(description = "应用状态。")
    private String appStatus;

    @Schema(description = "当前应用版本。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentAppVersionId;

    @Schema(description = "模块数量冗余，用于列表展示。")
    private Integer moduleCount;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "乐观锁版本。")
    private Integer version;

    @Schema(description = "软删除唯一复用标记；未删除固定为 0，删除后写入应用 ID 或删除批次。")
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
