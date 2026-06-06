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
 * 记录变更、状态、附件和发布版本历史快照。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_record_history")
@Schema(name = "RecordHistory", description = "记录变更、状态、附件和发布版本历史快照。")
public class RecordHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "历史 ID。")
    @TableId(value = "history_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long historyId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "记录 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "变更后的记录版本。")
    private Integer recordVersion;

    @Schema(description = "本次变更解释使用的发布版本。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishVersionId;

    @Schema(description = "CREATE、UPDATE、DELETE、SUBMIT、FLOW_UPDATE、RESTORE。")
    private String operationType;

    @Schema(description = "变更前状态。")
    private String beforeStatus;

    @Schema(description = "变更后状态。")
    private String afterStatus;

    @Schema(description = "变更字段编码数组。")
    private String changedFieldsJson;

    @Schema(description = "变更前字段值和展示快照。")
    private String beforeSnapshotJson;

    @Schema(description = "变更后字段值、附件和展示快照。")
    private String afterSnapshotJson;

    @Schema(description = "请求追踪 ID。")
    private String requestId;

    @Schema(description = "操作成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long operatorMemberId;

    @Schema(description = "保存备注或系统说明。")
    private String remark;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
