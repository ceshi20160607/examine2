package com.unique.examine.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "登录令牌出参")
public class AuthTokenVO {
    @Schema(description = "访问令牌") private String accessToken;
    @Schema(description = "令牌类型") private String tokenType;
    @Schema(description = "是否需要重置初始密码") private Boolean resetRequired;
    @Schema(description = "当前用户") private UserVO user;
}

