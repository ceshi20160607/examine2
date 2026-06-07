package com.unique.examine.plat.base.entity;

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
 * 自定义系统容器，承载系统编码、租户模式、创建人和状态。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_system")
@Schema(name = "System", description = "自定义系统容器，承载系统编码、租户模式、创建人和状态。")
public class System implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "系统编码，全局唯一。")
    private String code;

    @Schema(description = "系统名称。")
    private String name;

    @Schema(description = "系统描述。")
    private String description;

    @Schema(description = "租户模式：SINGLE、MULTI。")
    private String tenantMode;

    @Schema(description = "默认租户 ID，创建系统事务内回填。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long defaultTenantId;

    @Schema(description = "创建人平台账号 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerAccountId;

    @Schema(description = "创建人在系统内的成员扩展 ID，初始化完成后回填。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerMemberId;

    @Schema(description = "系统状态：DRAFT、ENABLED、DISABLED、ARCHIVED。")
    private String status;

    @Schema(description = "可选系统访问域名或入口标识。")
    private String domain;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
