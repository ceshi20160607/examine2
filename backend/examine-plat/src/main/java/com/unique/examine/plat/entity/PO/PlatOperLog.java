package com.unique.examine.plat.entity.po;

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
 * 平台操作日志
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-10
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_oper_log")
@Schema(name = "PlatOperLog对象", description = "平台操作日志")
public class PlatOperLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "操作人 platId")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long platAccountId;

    @Schema(description = "操作时间")
    private LocalDateTime operTime;

    @Schema(description = "模块编码（如 platform/upload/module/flow）")
    private String moduleCode;

    @Schema(description = "动作编码（建议 method+path 或业务动作码）")
    private String actionCode;

    @Schema(description = "资源类型（可选）")
    private String resourceType;

    @Schema(description = "资源标识（可选）")
    private String resourceId;

    @Schema(description = "操作详情（可选，JSON）")
    private String detailJson;

    @Schema(description = "客户端 IP")
    private String ip;

    @Schema(description = "请求追踪ID（requestId）")
    private String requestId;

    @Schema(description = "创建人 platId（一般同 plat_account_id）")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId（一般为空）")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
