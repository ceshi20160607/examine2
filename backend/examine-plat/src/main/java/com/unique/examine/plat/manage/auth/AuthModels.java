package com.unique.examine.plat.manage.auth;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Authentication API request and response models.
 */
public final class AuthModels {

    private AuthModels() {
    }

    public record LoginRequest(String loginName, String password, String mfaCode, String loginTarget,
                               String systemCode, String tenantCode) {
    }

    public record LoginResponse(String accessToken, String refreshToken, AccountProfile profile,
                                DefaultLanding defaultLanding, SsoBindingSummary ssoBindingSummary,
                                String requestId, String traceId) {
    }

    public record AccountProfile(String accountId, String accountName, String mobile, String email,
                                 List<String> platformRoles, List<String> systemRoles) {
    }

    public record DefaultLanding(String landingType, String systemId, String tenantId, String route) {
    }

    public record SsoBindingSummary(boolean bound, List<String> providers, String lastProvider) {
    }

    public record TokenRefreshRequest(String refreshToken) {
    }

    public record RegisterWithSystemRequest(String accountName, String mobile, String email, String password,
                                            String systemName, String systemCode, Integer tenantMode,
                                            String templateCode) {
    }

    public record RegisterWithSystemResponse(String accessToken, String refreshToken, String accountId, String systemId,
                                             String systemMemberId,
                                             String systemSuperAdminRoleId, List<String> initGuideSteps,
                                             String auditLogId) {
    }

    public record PasswordResetRequest(String loginName, String verifyChannel) {
    }

    public record PasswordResetResponse(String resetTicket, String verifyCode, String expiresAt, String traceId) {
    }

    public record PasswordResetConfirmRequest(String resetTicket, String verifyCode, String newPassword) {
    }

    public record AuthActionResult(String result, String traceId, String auditLogId, LocalDateTime operatedAt) {
    }
}
