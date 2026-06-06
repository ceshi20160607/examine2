package com.unique.examine.plat.base.entity;

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
 * 全局登录主体，承载登录名、密码哈希、状态和安全字段。 entity.
 *
 * @author examine-generator
 * @since 2026-06-06
 */
@Getter
@Setter
@Accessors(chain = true)
@TableName("un_plat_account")
@Schema(name = "Account", description = "全局登录主体，承载登录名、密码哈希、状态和安全字段。")
public class Account implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Primary key ID.")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "登录名，全局唯一。")
    private String loginName;

    @Schema(description = "密码哈希，不返回前端。")
    private String passwordHash;

    @Schema(description = "展示名称。")
    private String displayName;

    @Schema(description = "手机号。")
    private String mobile;

    @Schema(description = "邮箱。")
    private String email;

    @Schema(description = "账号状态：NORMAL、DISABLED、LOCKED。")
    private String status;

    @Schema(description = "是否首次登录必须改密。")
    private Byte firstLoginChangePwd;

    @Schema(description = "连续登录失败次数。")
    private Integer failedLoginCount;

    @Schema(description = "锁定截止时间。")
    private LocalDateTime lockedUntil;

    @Schema(description = "最近登录时间。")
    private LocalDateTime lastLoginAt;

    @Schema(description = "Soft delete unique reuse marker; active rows use 0.")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long deleteToken;

    @Schema(description = "Created time.")
    private LocalDateTime createdAt;

    @Schema(description = "Updated time.")
    private LocalDateTime updatedAt;
}
