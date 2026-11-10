package com.unique.unexamine.app.manage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

/** 应用受控访问请求与判定结果；请求模型不包含任何密钥持久化字段。 */
public final class AppAccessDto {
    private AppAccessDto() {}

    /** 外部或内部调用的资源动作及幂等标识。 */
    public record AccessRequest(@Schema(description = "应用稳定标识")
                                @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+") String appId,
                                @Schema(description = "应用当前密钥，仅用于本次验证")
                                @NotBlank @Size(max = 512) String secret,
                                @Schema(description = "资源类型")
                                @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String resourceType,
                                @Schema(description = "资源主键，可为空表示资源级授权") @Positive Long resourceId,
                                @Schema(description = "资源动作编码")
                                @NotBlank @Size(max = 80) @Pattern(regexp = "[A-Za-z0-9._:-]+") String actionCode,
                                @Schema(description = "调用方幂等请求标识")
                                @NotBlank @Size(max = 100) @Pattern(regexp = "[A-Za-z0-9._:-]+") String requestId,
                                @Schema(description = "目标系统主键") @Positive Long systemId,
                                @Schema(description = "目标租户主键") @Positive Long tenantId,
                                @Schema(description = "请求时间戳，启用签名时使用 Unix 秒") Long timestamp,
                                @Schema(description = "按发布安全配置生成的请求签名") @Size(max = 256) String signature,
                                @Schema(description = "参与请求摘要的业务参数") @Size(max = 100) Map<String, Object> payload,
                                @JsonIgnore String callerIp) {
        /** 供内部调用方保留原有构造方式；真实来源地址只由 HTTP 入口补入。 */
        public AccessRequest(String appId, String secret, String resourceType, Long resourceId,
                              String actionCode, String requestId, Long systemId, Long tenantId,
                              Map<String, Object> payload) {
            this(appId, secret, resourceType, resourceId, actionCode, requestId, systemId, tenantId,
                    null, null, payload, null);
        }

        /** 将服务端实际接收地址写入授权上下文，不能由调用方请求体伪造。 */
        public AccessRequest withCallerIp(String ip) {
            return new AccessRequest(appId, secret, resourceType, resourceId, actionCode, requestId,
                    systemId, tenantId, timestamp, signature, payload, ip);
        }
    }

    /** 授权结果只返回非敏感应用和发布版本信息。 */
    public record Decision(@Schema(description = "应用主键") Long applicationId,
                           @Schema(description = "应用稳定标识") String appId,
                           @Schema(description = "是否获授权") boolean allowed,
                           @Schema(description = "授权结果编码") String resultCode,
                           @Schema(description = "使用的密钥版本") Integer credentialVersion,
                           @Schema(description = "使用的发布配置版本") Integer publishedVersion,
                           @Schema(description = "应用所属系统") Long systemId,
                           @Schema(description = "应用所属租户") Long tenantId) {}
}
