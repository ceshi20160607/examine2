package com.unique.examine.plat.manage.bo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 刷新 token 入参。
 */
@Data
@Schema(description = "刷新 token 入参")
public class RefreshTokenBO {

    @Schema(description = "刷新凭证")
    @NotBlank(message = "刷新凭证不能为空")
    private String refreshToken;
}
