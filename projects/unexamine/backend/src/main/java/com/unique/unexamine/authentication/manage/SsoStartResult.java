package com.unique.unexamine.authentication.manage;

import java.time.LocalDateTime;

/** 企业登录跳转地址与本次授权状态的失效时间。 */
public record SsoStartResult(String authorizationUrl, LocalDateTime expireTime) {
}
