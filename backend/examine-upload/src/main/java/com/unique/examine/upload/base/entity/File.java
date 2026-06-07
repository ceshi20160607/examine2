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
 * 文件元数据、临时状态、对象存储定位和安全属性。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_upload_file")
@Schema(name = "File", description = "文件元数据、临时状态、对象存储定位和安全属性。")
public class File implements Serializable {

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

    @Schema(description = "存储配置 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long storageConfigId;

    @Schema(description = "原始文件名。")
    private String fileName;

    @Schema(description = "扩展名。")
    private String extension;

    @Schema(description = "MIME 类型。")
    private String contentType;

    @Schema(description = "文件大小，字节。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileSize;

    @Schema(description = "文件内容 SHA-256。")
    private String sha256;

    @Schema(description = "对象存储 key 或本地相对路径。")
    private String storageKey;

    @Schema(description = "TEMP、REFERENCED、DELETED、EXPIRED。")
    private String status;

    @Schema(description = "是否支持预览。")
    private Byte previewable;

    @Schema(description = "上传人系统成员 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long ownerMemberId;

    @Schema(description = "当前有效引用数。")
    private Integer refCount;

    @Schema(description = "临时文件过期时间。")
    private LocalDateTime tempExpiresAt;

    @Schema(description = "删除或过期时间。")
    private LocalDateTime deletedAt;

    @Schema(description = "上传请求 requestId。")
    private String requestId;

    @Schema(description = "逻辑删除。")
    private Byte deleted;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
