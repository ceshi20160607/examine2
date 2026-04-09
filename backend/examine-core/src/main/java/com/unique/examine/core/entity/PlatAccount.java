package com.unique.examine.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plat_account")
public class PlatAccount {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String username;
    @JsonIgnore
    private String passwordHash;
    @JsonIgnore
    private String passwordSalt;
    private String mobile;
    private String email;
    private String displayName;
    private String avatarUrl;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
