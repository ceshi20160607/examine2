package com.unique.examine.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "用户信息出参")
public class UserVO {
    private Long id;
    private String account;
    private String realName;
    private String mobile;
    private String email;
    private String status;
    private Long systemId;
    private Long tenantId;
}

