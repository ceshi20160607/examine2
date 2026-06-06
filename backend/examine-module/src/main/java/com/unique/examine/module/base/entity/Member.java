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
 * 平台账号在系统内的成员扩展，不是独立登录账号。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_member")
@Schema(name = "Member", description = "平台账号在系统内的成员扩展，不是独立登录账号。")
public class Member implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "引用平台账号 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;

    @Schema(description = "系统内成员编码，同系统唯一。")
    private String memberCode;

    @Schema(description = "平台账号展示名快照，列表展示使用。")
    private String displayNameSnapshot;

    @Schema(description = "默认租户 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defaultTenantId;

    @Schema(description = "岗位名称。")
    private String postName;

    @Schema(description = "成员状态：ENABLED、DISABLED。")
    private String status;

    @Schema(description = "是否系统超级管理员成员。")
    private Byte superAdminFlag;

    @Schema(description = "最近进入系统时间。")
    private LocalDateTime lastEnterAt;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
