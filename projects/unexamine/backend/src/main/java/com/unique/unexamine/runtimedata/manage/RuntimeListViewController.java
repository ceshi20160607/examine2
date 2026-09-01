package com.unique.unexamine.runtimedata.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/runtime/modules/{moduleCode}/list-views")
public class RuntimeListViewController {
    private final RuntimeListViewService listViewService;

    public RuntimeListViewController(RuntimeListViewService listViewService) {
        this.listViewService = listViewService;
    }

    @GetMapping
    public ApiResult<List<RuntimeListView>> list(
            @PathVariable String moduleCode, HttpServletRequest request) {
        return ApiResult.ok(listViewService.list(AuthenticationContextHolder.require(), moduleCode,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/{viewCode}")
    public ApiResult<RuntimeListView> save(
            @PathVariable String moduleCode,
            @PathVariable String viewCode,
            @Valid @RequestBody SaveRuntimeListViewRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(listViewService.save(AuthenticationContextHolder.require(), moduleCode, viewCode, body,
                TraceIdFilter.current(request)));
    }

    @DeleteMapping("/{viewCode}")
    public ApiResult<Void> delete(
            @PathVariable String moduleCode,
            @PathVariable String viewCode,
            @Valid @RequestBody DeleteRuntimeListViewRequest body,
            HttpServletRequest request) {
        listViewService.delete(AuthenticationContextHolder.require(), moduleCode, viewCode, body,
                TraceIdFilter.current(request));
        return ApiResult.ok(null);
    }
}
