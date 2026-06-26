package com.unique.examine.plat.manage.sso;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderPublishResult;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderSaveRequest;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderTestRequest;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderTestResultVO;
import com.unique.examine.plat.manage.sso.SsoModels.IdentityProviderVO;
import com.unique.examine.plat.manage.sso.SsoModels.MemberBindingConfirmRequest;
import com.unique.examine.plat.manage.sso.SsoModels.MemberBindingConfirmVO;
import com.unique.examine.plat.manage.sso.SsoModels.OrgSyncPrecheckRequest;
import com.unique.examine.plat.manage.sso.SsoModels.OrgSyncPrecheckResultVO;
import com.unique.examine.plat.manage.sso.SsoModels.SystemSsoPolicyUpdateRequest;
import com.unique.examine.plat.manage.sso.SsoModels.SystemSsoPolicyVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * SSO policy API controller.
 */
@RestController
public class SsoController {

    private final SsoService ssoService;

    public SsoController(SsoService ssoService) {
        this.ssoService = ssoService;
    }

    @GetMapping("/api/v1/platform/identity-providers")
    public ApiResponse<List<IdentityProviderVO>> identityProviders() {
        return ApiResponse.success(ssoService.identityProviders());
    }

    @PostMapping("/api/v1/platform/identity-providers")
    public ApiResponse<IdentityProviderVO> createIdentityProvider(@RequestBody IdentityProviderSaveRequest request) {
        return ApiResponse.success(ssoService.createIdentityProvider(request));
    }

    @PatchMapping("/api/v1/platform/identity-providers/{providerId}")
    public ApiResponse<IdentityProviderVO> updateIdentityProvider(@PathVariable String providerId,
                                                                  @RequestBody IdentityProviderSaveRequest request) {
        return ApiResponse.success(ssoService.updateIdentityProvider(providerId, request));
    }

    @PostMapping("/api/v1/platform/identity-providers/{providerId}/test")
    public ApiResponse<IdentityProviderTestResultVO> testIdentityProvider(@PathVariable String providerId,
                                                                         @RequestBody IdentityProviderTestRequest request) {
        return ApiResponse.success(ssoService.testIdentityProvider(providerId, request));
    }

    @PostMapping("/api/v1/platform/identity-providers/{providerId}/publish")
    public ApiResponse<IdentityProviderPublishResult> publishIdentityProvider(@PathVariable String providerId) {
        return ApiResponse.success(ssoService.publishIdentityProvider(providerId));
    }

    @GetMapping("/api/v1/systems/{systemId}/sso/policies")
    public ApiResponse<SystemSsoPolicyVO> policy(@PathVariable String systemId) {
        return ApiResponse.success(ssoService.policy(systemId));
    }

    @PatchMapping("/api/v1/systems/{systemId}/sso/policies")
    public ApiResponse<SystemSsoPolicyVO> updatePolicy(@PathVariable String systemId,
                                                       @RequestBody SystemSsoPolicyUpdateRequest request) {
        return ApiResponse.success(ssoService.updatePolicy(systemId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/sso/org-sync/precheck")
    public ApiResponse<OrgSyncPrecheckResultVO> precheckOrgSync(@PathVariable String systemId,
                                                                @RequestBody OrgSyncPrecheckRequest request) {
        return ApiResponse.success(ssoService.precheckOrgSync(systemId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/sso/member-bindings/confirm")
    public ApiResponse<MemberBindingConfirmVO> confirmMemberBinding(@PathVariable String systemId,
                                                                    @RequestBody MemberBindingConfirmRequest request) {
        return ApiResponse.success(ssoService.confirmMemberBinding(systemId, request));
    }
}
