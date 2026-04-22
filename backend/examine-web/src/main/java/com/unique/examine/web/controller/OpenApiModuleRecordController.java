package com.unique.examine.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.po.ModuleRecord;
import com.unique.examine.module.manage.ModuleRecordFacadeService;
import com.unique.examine.web.service.OpenApiIdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "对外开放-业务记录")
@RestController
@RequestMapping("/v1/open/records")
public class OpenApiModuleRecordController {

    @Autowired
    private ModuleRecordFacadeService moduleRecordFacadeService;
    @Autowired
    private OpenApiIdempotencyService openApiIdempotencyService;

    public record CreateRecordBody(Long appId, Long modelId, JsonNode data) {}

    public record UpdateRecordBody(JsonNode data) {}

    @Operation(summary = "创建记录（EAV）")
    @PostMapping("")
    public ApiResult<Map<String, Object>> create(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateRecordBody body) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    if (body == null) {
                        return ApiResult.fail(400, "body 不能为空");
                    }
                    ModuleRecord r = moduleRecordFacadeService.createWithData(body.appId(), body.modelId(), body.data());
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("recordId", r.getId());
                    m.put("record", r);
                    return ApiResult.ok(m);
                });
    }

    @Operation(summary = "更新记录")
    @PostMapping("/{recordId}/update")
    public ApiResult<Map<String, Object>> update(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("recordId") Long recordId,
            @RequestBody UpdateRecordBody body) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> ApiResult.ok(moduleRecordFacadeService.updateWithData(recordId, body == null ? null : body.data())));
    }
}
