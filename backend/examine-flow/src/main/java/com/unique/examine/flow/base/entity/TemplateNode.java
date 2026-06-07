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
 * 发布版本内节点结构。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_flow_template_node")
@Schema(name = "TemplateNode", description = "发布版本内节点结构。")
public class TemplateNode implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "流程发布版本 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long templateVersionId;

    @Schema(description = "节点稳定编码，版本内唯一。")
    private String nodeKey;

    @Schema(description = "节点名称。")
    private String nodeName;

    @Schema(description = "START、APPROVAL、CC、END。")
    private String nodeType;

    @Schema(description = "审批人策略，如 ROLE、MEMBER、DEPT_MANAGER、INITIATOR。")
    private String actorStrategy;

    @Schema(description = "候选人配置快照。")
    private String actorConfigJson;

    @Schema(description = "是否需要审批意见。")
    private Byte approvalRequired;

    @Schema(description = "图中排序。")
    private Integer sortOrder;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
