package com.unique.examine.module.base.entity;

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
 * 字典被字段、发布版本、记录值引用的摘要。 entity.
 *
 * @author examine-generator
 * @since generated
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_module_dict_reference")
@Schema(name = "DictReference", description = "字典被字段、发布版本、记录值引用的摘要。")
public class DictReference implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "所属系统 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long systemId;

    @Schema(description = "字典类型 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dictTypeId;

    @Schema(description = "字典项 ID；0 表示类型级引用。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dictItemId;

    @Schema(description = "引用类型：FIELD_CONFIG、PUBLISHED_FIELD、RECORD_VALUE。")
    private String referenceType;

    @Schema(description = "模块 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long moduleId;

    @Schema(description = "字段 ID。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fieldId;

    @Schema(description = "字段编码快照。")
    private String fieldCode;

    @Schema(description = "发布版本 ID，逻辑引用 DBA-003。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long publishedVersionId;

    @Schema(description = "记录 ID，逻辑引用 DBA-003。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long recordId;

    @Schema(description = "引用数量摘要；记录值批量统计可累加。")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long usageCount;

    @Schema(description = "当前是否有效引用。")
    private Byte activeFlag;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
