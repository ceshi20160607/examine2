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
 * 业务记录发起后的流程实例。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_instance")
@Schema(name = "Instance", description = "业务记录发起后的流程实例。")
public class Instance implements Serializable {

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

    @Schema(description = "动态模块 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "业务记录 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "流程模板 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateId;

    @Schema(description = "发起时发布版本 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateVersionId;

    @Schema(description = "IN_APPROVAL、APPROVED、REJECTED、WITHDRAWN、TERMINATED。")
    private String status;

    @Schema(description = "发起人系统成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long starterMemberId;

    @Schema(description = "当前活跃节点编码列表。")
    private String currentNodeKeys;

    @Schema(description = "发起时间。")
    private LocalDateTime startedAt;

    @Schema(description = "结束时间。")
    private LocalDateTime finishedAt;

    @Schema(description = "发起请求 requestId。")
    private String requestId;

    @Schema(description = "实例乐观锁版本。")
    private Integer versionNo;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
