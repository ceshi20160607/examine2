package com.unique.examine.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.entity.po.ModuleRecord;
import com.unique.examine.module.entity.po.ModuleRecordHistory;
import com.unique.examine.module.manage.ModuleRecordFacadeService;
import com.unique.examine.module.manage.ModuleRelationRecordService;
import com.unique.examine.web.service.OpenApiIdempotencyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "对外开放-业务记录")
@RestController
@RequestMapping("/v1/open/records")
public class OpenApiModuleRecordController {

    @Autowired
    private ModuleRecordFacadeService moduleRecordFacadeService;
    @Autowired
    private ModuleRelationRecordService moduleRelationRecordService;
    @Autowired
    private OpenApiIdempotencyService openApiIdempotencyService;

    public record CreateRecordBody(Long appId, Long modelId, JsonNode data) {}

    public record UpdateRecordBody(JsonNode data) {}

    public record QueryByRelationBody(Long relationId, Long parentRecordId, ModuleRecordDslQuery query) {}

    public record RelationLinkBody(Long relationId, Long parentRecordId, Long childRecordId) {}

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
                    m.put("recordId", r.getId() == null ? null : String.valueOf(r.getId()));
                    m.put("record", r);
                    return ApiResult.ok(m);
                });
    }

    @Operation(summary = "记录详情（主表 + EAV data）")
    @GetMapping("/{recordId}")
    public ApiResult<Map<String, Object>> detail(@PathVariable("recordId") Long recordId) {
        return ApiResult.ok(moduleRecordFacadeService.detailWithData(recordId));
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

    @Operation(summary = "删除记录（软删）")
    @DeleteMapping("/{recordId}")
    public ApiResult<Void> delete(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable("recordId") Long recordId) {
        Long clientId = (Long) request.getAttribute("openApiClientId");
        return openApiIdempotencyService.execute(
                response,
                clientId,
                request.getMethod(),
                request.getRequestURI(),
                idempotencyKey,
                () -> {
                    moduleRecordFacadeService.deleteRecord(recordId);
                    return ApiResult.ok();
                });
    }

    @Operation(summary = "DSL 白名单查询（支持 includeFieldCodes）")
    @PostMapping("/query")
    public ApiResult<Map<String, Object>> query(@RequestBody ModuleRecordDslQuery body) {
        return ApiResult.ok(moduleRecordFacadeService.queryDsl(body));
    }

    @Operation(summary = "按模型关系查询子记录")
    @PostMapping("/query-by-relation")
    public ApiResult<Map<String, Object>> queryByRelation(@RequestBody QueryByRelationBody body) {
        if (body == null) {
            return ApiResult.fail(400, "body 不能为空");
        }
        return ApiResult.ok(moduleRelationRecordService.queryByRelation(
                body.relationId(), body.parentRecordId(), body.query()));
    }

    @Operation(summary = "创建 n-n 模型关系关联")
    @PostMapping("/relations/attach")
    public ApiResult<Map<String, Object>> attachRelation(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RelationLinkBody body) {
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
                    return ApiResult.ok(moduleRelationRecordService.attachNn(
                            body.relationId(), body.parentRecordId(), body.childRecordId()));
                });
    }

    @Operation(summary = "删除 n-n 模型关系关联")
    @PostMapping("/relations/detach")
    public ApiResult<Map<String, Object>> detachRelation(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody RelationLinkBody body) {
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
                    return ApiResult.ok(moduleRelationRecordService.detachNn(
                            body.relationId(), body.parentRecordId(), body.childRecordId()));
                });
    }

    @Operation(summary = "记录变更历史")
    @GetMapping("/{recordId}/history")
    public ApiResult<List<ModuleRecordHistory>> history(
            @PathVariable("recordId") Long recordId,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return ApiResult.ok(moduleRecordFacadeService.listHistoryForRecord(recordId, limit));
    }
}
