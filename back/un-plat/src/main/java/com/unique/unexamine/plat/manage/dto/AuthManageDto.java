package com.unique.unexamine.plat.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** AuthManage 的请求与返回模型。 */
public final class AuthManageDto {
    private AuthManageDto() {}

    /** 平台注册参数；密码只进入哈希计算，不写入日志或响应。 */
    public record RegisterRequest(@NotBlank @Size(min = 3, max = 80) String username,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @NotBlank @Size(max = 100) String displayName) {}

    /** 平台登录参数。 */
    public record LoginRequest(@NotBlank @Size(max = 80) String username,
                               @NotBlank String password) {}

    /** 返回可展示的账号资料，不泄漏密码哈希。 */
    public record AccountView(Long id, String username, String displayName, Long defaultSystemId) {}

    /** 登录成功后给客户端的短期凭据。 */
    public record LoginView(String accessToken, AccountView account) {}
}
