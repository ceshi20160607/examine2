package com.unique.unexamine.work.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/work")
public class WorkConfigurationController {
    private final WorkConfigurationService service;

    public WorkConfigurationController(WorkConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/configuration")
    public ApiResult<WorkConfigurationModels.ConfigurationView> configuration() {
        return ApiResult.ok(service.configuration(AuthenticationContextHolder.require()));
    }

    @GetMapping("/configuration/effective/{targetType}")
    public ApiResult<List<WorkConfigurationModels.FieldView>> effectiveFields(@PathVariable String targetType) {
        return ApiResult.ok(service.effectiveFields(AuthenticationContextHolder.require(), targetType));
    }

    @PutMapping("/configuration/fields/{targetType}/{fieldCode}")
    public ApiResult<WorkConfigurationModels.FieldView> saveField(
            @PathVariable String targetType, @PathVariable String fieldCode,
            @Valid @RequestBody WorkConfigurationModels.SaveFieldRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveField(AuthenticationContextHolder.require(), targetType, fieldCode,
                body, TraceIdFilter.current(request)));
    }

    @PostMapping("/configuration/fields/{targetType}/{fieldCode}/publish")
    public ApiResult<WorkConfigurationModels.FieldView> publishField(
            @PathVariable String targetType, @PathVariable String fieldCode,
            @Valid @RequestBody WorkConfigurationModels.PublishFieldRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.publishField(AuthenticationContextHolder.require(), targetType, fieldCode,
                body, TraceIdFilter.current(request)));
    }

    @PostMapping("/configuration/fields/{targetType}/{fieldCode}/rollback")
    public ApiResult<WorkConfigurationModels.FieldView> rollbackField(
            @PathVariable String targetType, @PathVariable String fieldCode,
            @Valid @RequestBody WorkConfigurationModels.RollbackFieldRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.rollbackField(AuthenticationContextHolder.require(), targetType, fieldCode,
                body, TraceIdFilter.current(request)));
    }

    @GetMapping("/calendar")
    public ApiResult<WorkConfigurationModels.CalendarView> calendar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long tenantMemberId,
            @RequestParam(required = false) Long accountId) {
        return ApiResult.ok(service.calendar(AuthenticationContextHolder.require(), from, to, projectId,
                tenantMemberId, accountId));
    }
}
