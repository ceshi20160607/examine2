package com.unique.examine.core.base.entity;

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
 * DB migration 状态查询落点。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_sys_migration_status")
@Schema(name = "MigrationStatus", description = "DB migration 状态查询落点。")
public class MigrationStatus implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "migration 版本。")
    private String version;

    @Schema(description = "migration 描述。")
    private String description;

    @Schema(description = "SUCCESS、FAILED、PENDING、REPAIRED。")
    private String status;

    @Schema(description = "校验值。")
    private String checksum;

    @Schema(description = "安装时间。")
    private LocalDateTime installedAt;

    @Schema(description = "执行耗时。")
    private Integer executionTimeMs;

    @Schema(description = "失败摘要。")
    private String errorMessage;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
