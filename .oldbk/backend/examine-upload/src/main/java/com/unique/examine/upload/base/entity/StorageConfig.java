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
 * 存储配置和默认存储策略。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_upload_storage_config")
@Schema(name = "StorageConfig", description = "存储配置和默认存储策略。")
public class StorageConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统；为空表示平台默认配置。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "所属租户。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "存储配置编码。")
    private String code;

    @Schema(description = "存储配置名称。")
    private String name;

    @Schema(description = "LOCAL、S3、MINIO、OSS。")
    private String storageType;

    @Schema(description = "对象存储 endpoint。")
    private String endpoint;

    @Schema(description = "bucket。")
    private String bucketName;

    @Schema(description = "本地或对象根路径。")
    private String rootPath;

    @Schema(description = "非敏感配置。")
    private String configJson;

    @Schema(description = "密钥引用，不保存明文。")
    private String secretRef;

    @Schema(description = "是否默认配置。")
    private Byte defaultFlag;

    @Schema(description = "ENABLED、DISABLED。")
    private String status;

    @Schema(description = "逻辑删除。")
    private Byte deleted;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
