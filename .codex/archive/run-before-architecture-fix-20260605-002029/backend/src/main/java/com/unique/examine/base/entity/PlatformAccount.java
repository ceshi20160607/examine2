package com.unique.examine.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("platform_account")
@Schema(description = "platform_account 表实体")
public class PlatformAccount {

    @Schema(description = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @Schema(description = "account")
    @TableField("account")
    private String account;

    @Schema(description = "real_name")
    @TableField("real_name")
    private String realName;

    @Schema(description = "mobile")
    @TableField("mobile")
    private String mobile;

    @Schema(description = "email")
    @TableField("email")
    private String email;

    @Schema(description = "password_hash")
    @TableField("password_hash")
    private String passwordHash;

    @Schema(description = "status")
    @TableField("status")
    private String status;

    @Schema(description = "last_login_at")
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    @Schema(description = "created_at")
    @TableField("created_at")
    private LocalDateTime createdAt;

    @Schema(description = "updated_at")
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
