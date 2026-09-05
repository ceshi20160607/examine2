package com.unique.unexamine.application.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
public class ApplicationManagementController {
    private final ApplicationManagementService service;

    public ApplicationManagementController(ApplicationManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<ApplicationModels.ApplicationView>> list() {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require()));
    }

    @GetMapping("/resources")
    public ApiResult<List<ApplicationModels.ResourceOption>> resources() {
        return ApiResult.ok(service.resources(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<ApplicationModels.CreateResult> create(
            @Valid @RequestBody ApplicationModels.CreateRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.create(AuthenticationContextHolder.require(), body, TraceIdFilter.current(request)));
    }

    @PostMapping("/import")
    public ApiResult<ApplicationModels.CreateResult> importConfiguration(
            @Valid @RequestBody ApplicationModels.ImportRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.importConfiguration(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{applicationId}")
    public ApiResult<ApplicationModels.ApplicationView> detail(@PathVariable Long applicationId) {
        return ApiResult.ok(service.detail(AuthenticationContextHolder.require(), applicationId));
    }

    @GetMapping("/{applicationId}/calls")
    public ApiResult<List<ApplicationBridgeModels.CallLogView>> calls(@PathVariable Long applicationId) {
        return ApiResult.ok(service.calls(AuthenticationContextHolder.require(), applicationId));
    }

    @GetMapping("/{applicationId}/export")
    public ApiResult<ApplicationModels.ConfigurationExport> exportConfiguration(@PathVariable Long applicationId) {
        return ApiResult.ok(service.exportConfiguration(AuthenticationContextHolder.require(), applicationId));
    }

    @PutMapping("/{applicationId}/draft")
    public ApiResult<ApplicationModels.ApplicationView> saveDraft(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationModels.SaveDraftRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveDraft(AuthenticationContextHolder.require(), applicationId, body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{applicationId}/publication-check")
    public ApiResult<ApplicationModels.PublicationCheck> publicationCheck(@PathVariable Long applicationId) {
        return ApiResult.ok(service.publicationCheck(AuthenticationContextHolder.require(), applicationId));
    }

    @PostMapping("/{applicationId}/publish")
    public ApiResult<ApplicationModels.PublishResult> publish(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationModels.PublishRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.publish(AuthenticationContextHolder.require(), applicationId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{applicationId}/credentials")
    public ApiResult<ApplicationModels.RotateResult> rotateCredential(
            @PathVariable Long applicationId, HttpServletRequest request) {
        return ApiResult.ok(service.rotateCredential(AuthenticationContextHolder.require(), applicationId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{applicationId}/disable")
    public ApiResult<ApplicationModels.ApplicationView> disable(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationModels.DisableRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.disable(AuthenticationContextHolder.require(), applicationId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{applicationId}/copy")
    public ApiResult<ApplicationModels.CreateResult> copy(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationModels.CopyRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.copy(AuthenticationContextHolder.require(), applicationId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{applicationId}/enable")
    public ApiResult<ApplicationModels.RotateResult> enable(
            @PathVariable Long applicationId, HttpServletRequest request) {
        return ApiResult.ok(service.enable(AuthenticationContextHolder.require(), applicationId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{applicationId}/rollback")
    public ApiResult<ApplicationModels.PublishResult> rollback(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationModels.RollbackRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.rollback(AuthenticationContextHolder.require(), applicationId, body,
                TraceIdFilter.current(request)));
    }

    @DeleteMapping("/{applicationId}")
    public ApiResult<Void> delete(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationModels.DeleteRequest body,
            HttpServletRequest request) {
        service.delete(AuthenticationContextHolder.require(), applicationId, body, TraceIdFilter.current(request));
        return ApiResult.ok(null);
    }
}
