package com.unique.examine.plat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plat_msg")
public class PlatMsg {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String msgType;
    private String title;
    private String content;
    private String payloadJson;
    private Integer sourceType;
    private Integer priority;
    private LocalDateTime publishTime;
    private LocalDateTime expireTime;
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
