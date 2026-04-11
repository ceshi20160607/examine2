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
 * 上传分片表
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_upload_file_part")
@Schema(name = "UploadFilePart对象", description = "上传分片表")
public class UploadFilePart implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "分片ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "systemId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "tenantId；无多租户时固定 0")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long tenantId;

    @Schema(description = "un_upload_file.id")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploadFileId;

    @Schema(description = "创建人 platId")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "一次分片上传会话ID（前端/后端协商产生）")
    private String uploadSession;

    @Schema(description = "分片序号，从 1 开始")
    private Integer partNo;

    @Schema(description = "分片大小（字节）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long partSize;

    @Schema(description = "对象存储返回的 etag（可选）")
    private String etag;

    @Schema(description = "分片摘要（可选）")
    private String sha256;

    @Schema(description = "1=已上传 2=待上传 3=已合并/归档")
    private Integer status;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
