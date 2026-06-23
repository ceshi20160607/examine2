package com.unique.examine.app.base.entity;

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
 * 外部客户端，绑定系统、租户和状态。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_openapi_client")
@Schema(name = "Client", description = "外部客户端，绑定系统、租户和状态。")
public class Client implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "绑定系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "绑定租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "客户端编码。")
    private String code;

    @Schema(description = "客户端名称。")
    private String name;

    @Schema(description = "DRAFT、ENABLED、DISABLED、EXPIRED。")
    private String status;

    @Schema(description = "客户端默认数据范围快照。")
    private String dataScopeJson;

    @Schema(description = "管理端展示用限流策略快照。")
    private String rateLimitPolicyJson;

    @Schema(description = "客户端过期时间。")
    private LocalDateTime expiresAt;

    @Schema(description = "最近调用时间。")
    private LocalDateTime lastUsedAt;

    @Schema(description = "乐观锁版本。")
    private Integer versionNo;

    @Schema(description = "逻辑删除。")
    private Byte deleted;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
