package com.unique.examine.upload.entity.po;

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
 * 上传文件主表
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_upload_file")
@Schema(name = "UploadFile对象", description = "上传文件主表")
public class UploadFile implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "文件ID（雪花/分布式ID）")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId；无多租户时固定 0")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "上传人 platId（可选）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploaderPlatId;

    @Schema(description = "创建人 platId（一般同 uploader_plat_id）")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "原始文件名")
    private String originalName;

    @Schema(description = "扩展名（不含点）")
    private String fileExt;

    @Schema(description = "MIME")
    private String contentType;

    @Schema(description = "字节数")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fileSize;

    @Schema(description = "内容摘要（可选，用于秒传/去重/完整性校验）")
    private String sha256;

    @Schema(description = "local|minio|oss（可扩展）")
    private String storageType;

    @Schema(description = "存储配置ID（un_upload_storage_config.id，可为空表示默认配置）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long storageConfigId;

    @Schema(description = "bucket（对象存储）")
    private String bucket;

    @Schema(description = "对象Key/路径（对象存储）")
    private String objectKey;

    @Schema(description = "本地物理路径（storage_type=local 时使用，如 D:\data\uploads\2026\a.png）")
    private String localAbsPath;

    @Schema(description = "外网可访问地址（可为空，按配置/签名动态生成）")
    private String publicUrl;

    @Schema(description = "内网访问地址（可选，如走内网域名/内网 OSS endpoint）")
    private String internalUrl;

    @Schema(description = "1=可用 2=删除/禁用 3=上传中")
    private Integer status;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
