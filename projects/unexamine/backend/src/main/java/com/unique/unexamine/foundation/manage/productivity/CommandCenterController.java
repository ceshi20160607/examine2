package com.unique.unexamine.foundation.manage.productivity;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/command-center")
public class CommandCenterController {
    private final CommandCenterService service;

    public CommandCenterController(CommandCenterService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<CommandCenterModels.View> discover(
            @RequestParam(defaultValue = "") String query, HttpServletRequest request) {
        return ApiResult.ok(service.discover(AuthenticationContextHolder.require(), query,
                TraceIdFilter.current(request)));
    }

    @PutMapping("/state")
    public ApiResult<CommandCenterModels.View> updateState(
            @Valid @RequestBody CommandCenterModels.UpdateStateRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.updateState(AuthenticationContextHolder.require(), body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/execute")
    public ApiResult<CommandCenterModels.Execution> execute(
            @Valid @RequestBody CommandCenterModels.ExecuteRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.execute(AuthenticationContextHolder.require(), body.commandId(),
                TraceIdFilter.current(request)));
    }
}
