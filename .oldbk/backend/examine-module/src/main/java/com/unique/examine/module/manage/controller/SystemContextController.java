package com.unique.examine.module.manage.controller;

import java.util.List;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.module.manage.bo.StatusChangeBO;
import com.unique.examine.module.manage.bo.SystemEnterBO;
import com.unique.examine.module.manage.bo.SystemProfileUpdateBO;
import com.unique.examine.module.manage.bo.TenantSaveBO;
import com.unique.examine.module.manage.bo.TenantSwitchBO;
import com.unique.examine.module.manage.service.SystemRbacService;
import com.unique.examine.module.manage.vo.SystemContextVO;
import com.unique.examine.module.manage.vo.TenantVO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统上下文和租户接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}")
public class SystemContextController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SystemRbacService systemRbacService;

    private final AuthSessionService authSessionService;

    /**
     * 进入系统。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param enterBO 进入入参
     * @return 系统上下文
     */
    @Operation(summary = "进入系统")
    @PostMapping(value = "/enter", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SystemContextVO enter(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @RequestBody(required = false) SystemEnterBO enterBO) {
        return enterWithBody(authorization, systemId, enterBO == null ? new SystemEnterBO() : enterBO);
    }

    /**
     * 兼容无请求体或表单 Content-Type 的系统进入请求。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @return 系统上下文
     */
    @Operation(summary = "进入系统")
    @PostMapping(value = "/enter", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public SystemContextVO enterForm(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @RequestParam(required = false) String tenantId) {
        SystemEnterBO enterBO = new SystemEnterBO();
        enterBO.setTenantId(tenantId);
        return enterWithBody(authorization, systemId, enterBO);
    }

    /**
     * 兼容未声明 Content-Type 的空请求体系统进入请求。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @return 系统上下文
     */
    @Operation(summary = "进入系统")
    @PostMapping(value = "/enter", consumes = MediaType.ALL_VALUE)
    public SystemContextVO enterEmpty(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        return enterWithBody(authorization, systemId, new SystemEnterBO());
    }

    private SystemContextVO enterWithBody(String authorization, Long systemId, SystemEnterBO enterBO) {
        return systemRbacService.enterSystem(currentAccountId(authorization), systemId, enterBO);
    }

    /**
     * 查询系统资料。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @return 系统资料
     */
    @Operation(summary = "查询系统资料")
    @GetMapping("/profile")
    public SystemContextVO profile(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return systemRbacService.profile(systemId);
    }

    /**
     * 更新系统资料。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param updateBO 更新入参
     * @return 系统资料
     */
    @Operation(summary = "更新系统资料")
    @PutMapping("/profile")
    public SystemContextVO updateProfile(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody SystemProfileUpdateBO updateBO) {
        validateLogin(authorization);
        return systemRbacService.updateProfile(systemId, updateBO);
    }

    /**
     * 查询系统租户。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @return 租户列表
     */
    @Operation(summary = "查询系统租户")
    @GetMapping("/tenants")
    public List<TenantVO> tenants(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return systemRbacService.listTenants(systemId);
    }

    /**
     * 创建系统租户。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 租户
     */
    @Operation(summary = "创建系统租户")
    @PostMapping("/tenants")
    public TenantVO createTenant(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody TenantSaveBO saveBO) {
        validateLogin(authorization);
        return systemRbacService.createTenant(systemId, saveBO);
    }

    /**
     * 变更租户状态。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param statusBO 状态入参
     * @return 租户
     */
    @Operation(summary = "变更租户状态")
    @PatchMapping("/tenants/{tenantId}/status")
    public TenantVO changeTenantStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long tenantId, @Valid @RequestBody StatusChangeBO statusBO) {
        validateLogin(authorization);
        return systemRbacService.changeTenantStatus(systemId, tenantId, statusBO);
    }

    /**
     * 切换租户上下文。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param switchBO 切换入参
     * @return 系统上下文
     */
    @Operation(summary = "切换租户上下文")
    @PostMapping("/tenant-context/switch")
    public SystemContextVO switchTenant(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody TenantSwitchBO switchBO) {
        return systemRbacService.switchTenant(currentAccountId(authorization), systemId, switchBO);
    }

    private void validateLogin(String authorization) {
        currentAccountId(authorization);
    }

    private Long currentAccountId(String authorization) {
        CurrentUserVO currentUser = authSessionService.me(resolveBearer(authorization));
        return Long.valueOf(currentUser.getAccount().getAccountId());
    }

    private static String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).strip();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
