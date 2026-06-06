package com.unique.examine.flow.base.entity;

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
 * 模块提交动作与流程版本绑定。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_binding")
@Schema(name = "Binding", description = "模块提交动作与流程版本绑定。")
public class Binding implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "动态模块 ID，逻辑关联 un_module_。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "触发动作，MVP 为提交审批。")
    private String actionCode;

    @Schema(description = "流程模板 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    @Schema(description = "绑定的发布版本 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateVersionId;

    @Schema(description = "ENABLED、DISABLED。")
    private String status;

    @Schema(description = "乐观锁版本。")
    private Integer versionNo;

    @Schema(description = "逻辑删除。")
    private Byte deleted;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
