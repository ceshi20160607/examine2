package com.unique.examine.upload.entity.PO;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
 * <p>
 * 上传存储配置（不含密钥）
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_upload_storage_config")
@Schema(name = "UploadStorageConfig对象", description = "上传存储配置（不含密钥）")
public class UploadStorageConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "存储配置ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId；无多租户时固定 0")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "local|minio|oss（可扩展）")
    private String storageType;

    @Schema(description = "创建人 platId")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "本地存储根目录（storage_type=local），如 D:\\data\\uploads")
    private String localRootPath;

    @Schema(description = "本地文件对外访问域名/前缀（可选），如 https://static.example.com/uploads")
    private String localPublicBaseUrl;

    @Schema(description = "S3/OSS endpoint（local 可为空）")
    private String endpoint;

    @Schema(description = "region（可选）")
    private String region;

    @Schema(description = "默认 bucket（对象存储）")
    private String bucket;

    @Schema(description = "基础路径前缀（对象存储 key 前缀）")
    private String basePath;

    @Schema(description = "公开访问域名（可选，用于拼 public_url 或生成签名URL）")
    private String publicBaseUrl;

    @Schema(description = "扩展参数（如 ACL、是否私有、签名有效期秒数等）")
    private String paramJson;

    @Schema(description = "1=启用 2=停用")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
