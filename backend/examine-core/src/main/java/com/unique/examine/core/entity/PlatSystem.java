package com.unique.examine.core.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plat_system")
public class PlatSystem {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String name;
    private String iconUrl;
    private Integer multiTenantEnabled;
    private Long defaultTenantId;
    private Integer status;
    private Long ownerPlatAccountId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
