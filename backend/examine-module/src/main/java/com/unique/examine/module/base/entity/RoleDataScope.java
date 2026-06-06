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
 * 系统角色数据范围规则。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_role_data_scope")
@Schema(name = "RoleDataScope", description = "系统角色数据范围规则。")
public class RoleDataScope implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "租户归属；0 表示系统级。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "系统角色 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long roleId;

    @Schema(description = "资源类型：SYSTEM、MODULE、FLOW、EXPORT、OPENAPI。")
    private String resourceType;

    @Schema(description = "资源 ID；0 表示该类型全局。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resourceId;

    @Schema(description = "数据范围：SELF、DEPT、DEPT_TREE、ALL、CUSTOM。")
    private String scopeType;

    @Schema(description = "部门范围。")
    private String deptIdsJson;

    @Schema(description = "成员范围。")
    private String memberIdsJson;

    @Schema(description = "结构化自定义条件。")
    private String customConditions;

    @Schema(description = "多角色合并规则：INTERSECTION、UNION_LIMITED。MVP 按最小可见规则。")
    private String minVisibleRule;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
