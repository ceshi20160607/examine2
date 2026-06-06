package com.unique.examine.plat.manage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 当前登录用户回显。
 */
@Data
@Builder
@Schema(description = "当前登录用户回显")
public class CurrentUserVO {

    @Schema(description = "平台账号信息")
    private AuthAccountVO account;
}
