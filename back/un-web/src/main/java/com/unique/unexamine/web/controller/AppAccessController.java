package com.unique.unexamine.web.controller;

import com.unique.unexamine.app.manage.dto.AppAccessDto.AccessRequest;
import com.unique.unexamine.app.manage.dto.AppAccessDto.Decision;
import com.unique.unexamine.app.manage.service.AppAccessService;
import com.unique.unexamine.core.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 应用受控访问入口；业务资源执行方先通过该服务取得发布授权结果。 */
@RestController
@RequestMapping("/api/app-access")
@Tag(name = "应用受控访问")
public class AppAccessController {
    private final AppAccessService access;

    /** 注入应用授权与幂等审计服务。 */
    public AppAccessController(AppAccessService access) {
        this.access = access;
    }

    /** 对外调用必须使用发布后的应用资源授权快照。 */
    @PostMapping("/external/check")
    @Operation(summary = "校验外部应用资源动作授权")
    public Result<Decision> external(@Valid @RequestBody AccessRequest request, HttpServletRequest http) {
        return Result.ok(access.authorizeExternal(request.withCallerIp(http.getRemoteAddr())));
    }

    /** 系统内部应用同样使用 appId/secret 和发布授权，不直接绕过应用配置。 */
    @PostMapping("/internal/check")
    @Operation(summary = "校验内部应用资源动作授权")
    public Result<Decision> internal(@Valid @RequestBody AccessRequest request, HttpServletRequest http) {
        return Result.ok(access.authorizeInternal(request.withCallerIp(http.getRemoteAddr())));
    }
}
