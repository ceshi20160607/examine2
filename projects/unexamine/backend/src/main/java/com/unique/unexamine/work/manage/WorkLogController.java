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
@RequestMapping("/api/work/logs")
public class WorkLogController {
    private final WorkLogService service;

    public WorkLogController(WorkLogService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<WorkLogModels.LogView>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam(required = false) Long authorAccountId) {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require(), workDate, authorAccountId));
    }

    @PostMapping
    public ApiResult<WorkLogModels.LogView> create(
            @Valid @RequestBody WorkLogModels.CreateLogRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.create(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{logId}")
    public ApiResult<WorkLogModels.LogView> detail(@PathVariable Long logId) {
        return ApiResult.ok(service.detail(AuthenticationContextHolder.require(), logId));
    }

    @PutMapping("/{logId}")
    public ApiResult<WorkLogModels.LogView> update(
            @PathVariable Long logId, @Valid @RequestBody WorkLogModels.UpdateLogRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.update(AuthenticationContextHolder.require(), logId, body,
                TraceIdFilter.current(request)));
    }
}
