package com.unique.unexamine.notification.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
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
@RequestMapping("/api/message-templates")
public class MessageTemplateController {
    private final MessageService service;

    public MessageTemplateController(MessageService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<MessageModels.TemplateView>> list() {
        return ApiResult.ok(service.listTemplates(AuthenticationContextHolder.require()));
    }

    @PostMapping
    public ApiResult<MessageModels.TemplateView> save(
            @Valid @RequestBody MessageModels.SaveTemplateRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveTemplate(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{templateId}/publish")
    public ApiResult<MessageModels.TemplateView> publish(
            @PathVariable Long templateId,
            HttpServletRequest request) {
        return ApiResult.ok(service.publishTemplate(AuthenticationContextHolder.require(), templateId,
                TraceIdFilter.current(request)));
    }
}
