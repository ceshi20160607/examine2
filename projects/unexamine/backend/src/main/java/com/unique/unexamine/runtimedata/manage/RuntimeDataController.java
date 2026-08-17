package com.unique.unexamine.runtimedata.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/runtime/modules/{moduleCode}/records")
public class RuntimeDataController {
    private final RuntimeDataService runtimeDataService;

    public RuntimeDataController(RuntimeDataService runtimeDataService) {
        this.runtimeDataService = runtimeDataService;
    }

    @GetMapping
    public ApiResult<RuntimeRecordList> list(
            @PathVariable String moduleCode,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.list(
                AuthenticationContextHolder.require(), moduleCode, page, pageSize, TraceIdFilter.current(request)));
    }

    @GetMapping("/{recordId}")
    public ApiResult<RuntimeRecordView> detail(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.detail(
                AuthenticationContextHolder.require(), moduleCode, recordId, TraceIdFilter.current(request)));
    }

    @PostMapping
    public ApiResult<RuntimeRecordView> create(
            @PathVariable String moduleCode,
            @Valid @RequestBody CreateRuntimeRecordRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.create(
                AuthenticationContextHolder.require(), moduleCode, body, TraceIdFilter.current(request)));
    }

    @PutMapping("/{recordId}")
    public ApiResult<RuntimeRecordView> update(
            @PathVariable String moduleCode,
            @PathVariable Long recordId,
            @Valid @RequestBody UpdateRuntimeRecordRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(runtimeDataService.update(
                AuthenticationContextHolder.require(), moduleCode, recordId, body, TraceIdFilter.current(request)));
    }
}
