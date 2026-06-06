package com.unique.examine.plat.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录请求 DTO。
 */
@Data
@Schema(description = "登录请求 DTO")
public class PlatformLoginDTO {

    @Schema(description = "登录账号")
    private String username;

    @Schema(description = "登录密码")
    private String password;
}
