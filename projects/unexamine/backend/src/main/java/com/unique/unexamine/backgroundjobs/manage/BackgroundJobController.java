package com.unique.unexamine.backgroundjobs.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system/background-jobs")
@RequirePermission(resourceType = "CONFIG", resourceCode = "SYSTEM", actionCode = "MANAGE")
public class BackgroundJobController {
    private final BackgroundJobService service;

    public BackgroundJobController(BackgroundJobService service) {
        this.service = service;
    }

    @GetMapping("/handlers")
    public ApiResult<List<BackgroundJobModels.HandlerView>> handlers() {
        return ApiResult.ok(service.handlers());
    }

    @GetMapping
    public ApiResult<List<BackgroundJobModels.JobView>> list() {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<BackgroundJobModels.JobDetail> submit(@Valid @RequestBody BackgroundJobModels.SubmitJobRequest input,
                                                           HttpServletRequest request) {
        return ApiResult.ok(service.submit(AuthenticationContextHolder.require(), input, TraceIdFilter.current(request)));
    }

    @GetMapping("/{jobId}")
    public ApiResult<BackgroundJobModels.JobDetail> detail(@PathVariable long jobId) {
        return ApiResult.ok(service.detail(AuthenticationContextHolder.require(), jobId));
    }

    @PostMapping("/{jobId}/retry")
    public ApiResult<BackgroundJobModels.JobDetail> retry(@PathVariable long jobId,
                                                          @Valid @RequestBody BackgroundJobModels.RetryJobRequest input,
                                                          HttpServletRequest request) {
        return ApiResult.ok(service.retryNow(AuthenticationContextHolder.require(), jobId, input,
                TraceIdFilter.current(request)));
    }
}
