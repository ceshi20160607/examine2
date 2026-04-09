package com.unique.examine.plat.entity.PO;

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
 * 平台登录日志
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_login_log")
@Schema(name = "PlatLoginLog对象", description = "平台登录日志")
public class PlatLoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "日志ID")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "platId；成功时有值")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long platAccountId;

    @Schema(description = "尝试登录的用户名（失败也记录）")
    private String usernameAttempt;

    @Schema(description = "1=成功 0=失败")
    private Integer successFlag;

    @Schema(description = "失败原因")
    private String failReason;

    @Schema(description = "登录 IP")
    private String ip;

    @Schema(description = "User-Agent")
    private String ua;

    @Schema(description = "设备类型（可选）")
    private String device;

    @Schema(description = "创建人 platId（一般同 plat_account_id；失败可为空）")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId（一般为空）")
    @TableField(fill = FieldFill.UPDATE)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long updateUserId;

    @Schema(description = "登录时间")
    private LocalDateTime loginTime;

    @Schema(description = "创建时间")
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @Schema(description = "更新时间")
    @TableField(fill = FieldFill.UPDATE)
    private LocalDateTime updateTime;


}
