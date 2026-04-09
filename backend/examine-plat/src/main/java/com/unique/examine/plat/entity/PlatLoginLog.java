package com.unique.examine.plat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("plat_login_log")
public class PlatLoginLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long platAccountId;
    private String usernameAttempt;
    private Integer successFlag;
    private String failReason;
    private String ip;
    private String ua;
    private String device;
    private LocalDateTime loginTime;
}
