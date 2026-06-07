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
 * 分片上传预留表。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_upload_file_part")
@Schema(name = "FilePart", description = "分片上传预留表。")
public class FilePart implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "文件 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileId;

    @Schema(description = "分片上传会话 ID。")
    private String uploadId;

    @Schema(description = "分片序号。")
    private Integer partNo;

    @Schema(description = "分片大小。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long partSize;

    @Schema(description = "分片 SHA-256。")
    private String partSha256;

    @Schema(description = "对象存储 ETag。")
    private String storageEtag;

    @Schema(description = "UPLOADED、MERGED、FAILED。")
    private String status;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
