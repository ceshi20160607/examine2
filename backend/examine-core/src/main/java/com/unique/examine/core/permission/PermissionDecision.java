package com.unique.examine.core.permission;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

/**
 * 权限判定结果。
 */
@Data
@Builder
@Schema(description = "权限判定结果")
public class PermissionDecision {

    @Schema(description = "是否允许")
    private boolean allowed;

    @Schema(description = "拒绝原因编码")
    private String reasonCode;

    @Schema(description = "拒绝说明")
    private String message;

    /**
     * 允许访问。
     *
     * @return 允许结果
     */
    public static PermissionDecision allow() {
        return PermissionDecision.builder().allowed(true).build();
    }

    /**
     * 拒绝访问。
     *
     * @param reasonCode 拒绝原因
     * @param message 拒绝说明
     * @return 拒绝结果
     */
    public static PermissionDecision deny(String reasonCode, String message) {
        return PermissionDecision.builder()
                .allowed(false)
                .reasonCode(reasonCode)
                .message(message)
                .build();
    }
}
