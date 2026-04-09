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
 * 平台账号
 * </p>
 *
 * @author UNIQUE
 * @since 2026-04-09
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_account")
@Schema(name = "PlatAccount对象", description = "平台账号")
public class PlatAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "平台账号主键；逻辑 platId")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "登录名")
    private String username;

    @Schema(description = "密码哈希（BCrypt 等）")
    private String passwordHash;

    @Schema(description = "盐（如算法自带盐可不使用）")
    private String passwordSalt;

    @Schema(description = "手机号")
    private String mobile;

    @Schema(description = "邮箱")
    private String email;

    @Schema(description = "展示名/昵称")
    private String displayName;

    @Schema(description = "头像地址")
    private String avatarUrl;

    @Schema(description = "1=正常 2=禁用")
    private Integer status;

    @Schema(description = "最后登录时间")
    private LocalDateTime lastLoginTime;

    @Schema(description = "最后登录 IP")
    private String lastLoginIp;

    @Schema(description = "创建人 platId")
    @TableField(fill = FieldFill.INSERT)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUserId;

    @Schema(description = "更新人 platId")
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
