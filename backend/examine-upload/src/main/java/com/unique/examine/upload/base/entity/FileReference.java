package com.unique.examine.upload.base.entity;

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
 * 文件与业务对象、动态字段、导出结果之间的引用关系。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_upload_file_reference")
@Schema(name = "FileReference", description = "文件与业务对象、动态字段、导出结果之间的引用关系。")
public class FileReference implements Serializable {

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

    @Schema(description = "文件 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    @Schema(description = "引用类型，如 MODULE_RECORD_FIELD、EXPORT_RESULT、FLOW_COMMENT。")
    private String bizType;

    @Schema(description = "业务对象 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long bizId;

    @Schema(description = "动态模块 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "业务记录 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "附件字段编码。")
    private String fieldCode;

    @Schema(description = "引用展示名。")
    private String displayName;

    @Schema(description = "排序。")
    private Integer sortOrder;

    @Schema(description = "ACTIVE、UNBOUND。")
    private String status;

    @Schema(description = "绑定人系统成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long boundBy;

    @Schema(description = "绑定时间。")
    private LocalDateTime boundAt;

    @Schema(description = "解绑时间。")
    private LocalDateTime unboundAt;

    @Schema(description = "绑定请求 requestId。")
    private String requestId;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
