package com.unique.unexamine.notification.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
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

import java.util.List;

/** 工作配置页使用的今日联系规则接口。 */
@RestController
@RequestMapping("/api/todo-contact-rules")
public class TodoContactRuleController {
    private final TodoContactRuleService service;

    public TodoContactRuleController(TodoContactRuleService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<TodoContactRuleModels.RuleView>> list() {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<TodoContactRuleModels.RuleView> create(
            @Valid @RequestBody TodoContactRuleModels.SaveRuleRequest body, HttpServletRequest request) {
        return ApiResult.ok(service.create(AuthenticationContextHolder.require(), body, TraceIdFilter.current(request)));
    }

    @PutMapping("/{ruleId}")
    public ApiResult<TodoContactRuleModels.RuleView> update(
            @PathVariable Long ruleId, @Valid @RequestBody TodoContactRuleModels.SaveRuleRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.update(AuthenticationContextHolder.require(), ruleId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{ruleId}/publish")
    public ApiResult<TodoContactRuleModels.RuleView> publish(
            @PathVariable Long ruleId, @Valid @RequestBody TodoContactRuleModels.ChangeStatusRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.publish(AuthenticationContextHolder.require(), ruleId, body,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{ruleId}/disable")
    public ApiResult<TodoContactRuleModels.RuleView> disable(
            @PathVariable Long ruleId, @Valid @RequestBody TodoContactRuleModels.ChangeStatusRequest body,
            HttpServletRequest request) {
        return ApiResult.ok(service.disable(AuthenticationContextHolder.require(), ruleId, body,
                TraceIdFilter.current(request)));
    }
}
