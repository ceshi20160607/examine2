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
 * 自动编号字段的事务内原子序号段。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_serial_sequence")
@Schema(name = "SerialSequence", description = "自动编号字段的事务内原子序号段。")
public class SerialSequence implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "序号规则 ID。")
    @TableId(value = "sequence_id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sequenceId;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "所属模块。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "自动编号字段。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "序号作用域，如年度、月份、租户、模块。")
    private String scopeKey;

    @Schema(description = "当前前缀快照。")
    private String prefixSnapshot;

    @Schema(description = "当前最大序号。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long currentValue;

    @Schema(description = "步长。")
    private Integer stepValue;

    @Schema(description = "乐观锁版本，用于原子更新。")
    private Integer version;

    @Schema(description = "创建时间。")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间。")
    private LocalDateTime updatedAt;
}
