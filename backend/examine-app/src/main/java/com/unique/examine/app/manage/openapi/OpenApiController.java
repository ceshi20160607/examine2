package com.unique.examine.app.manage.openapi;

import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiAppQuery;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiAppSaveRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiAppVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiCallLogQuery;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiCallLogVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiDeleteResultVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.ExternalRecordMutationRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.ExternalRecordMutationResult;
import com.unique.examine.app.manage.openapi.OpenApiModels.ExternalRecordRow;
import com.unique.examine.app.manage.openapi.OpenApiModels.ExternalRecordSearchRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiRateLimitUpdateRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiScopeUpdateRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiScopeVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiSecretRotationJobVO;
import com.unique.examine.app.manage.openapi.OpenApiModels.OpenApiSecretRotationRequest;
import com.unique.examine.app.manage.openapi.OpenApiModels.RateLimitMetadata;
import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAPI 外部应用和调用日志 API 控制器。
 */
/**
 * OpenAPI external application and call log API controller.
 */
@RestController
public class OpenApiController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String OPENAPI_APP_CODE_HEADER = "X-OpenAPI-App-Code";
    private static final String OPENAPI_SECRET_REF_HEADER = "X-OpenAPI-Secret-Ref";

    private final OpenApiService openApiService;

    public OpenApiController(OpenApiService openApiService) {
        this.openApiService = openApiService;
    }

    @GetMapping("/api/v1/systems/{systemId}/openapi/apps")
    public ApiResponse<PageResult<OpenApiAppVO>> apps(@PathVariable String systemId,
                                                      @RequestParam(defaultValue = "1") int pageNo,
                                                      @RequestParam(defaultValue = "20") int pageSize,
                                                      OpenApiAppQuery query) {
        return ApiResponse.success(openApiService.apps(systemId,
                new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/openapi/apps")
    public ApiResponse<OpenApiAppVO> create(@PathVariable String systemId,
                                            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false)
                                            String idempotencyKey,
                                            @RequestBody OpenApiAppSaveRequest request) {
        return ApiResponse.success(openApiService.create(systemId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/openapi/apps/{externalAppId}")
    public ApiResponse<OpenApiAppVO> detail(@PathVariable String systemId,
                                            @PathVariable String externalAppId) {
        return ApiResponse.success(openApiService.detail(systemId, externalAppId));
    }

    @PatchMapping("/api/v1/systems/{systemId}/openapi/apps/{externalAppId}")
    public ApiResponse<OpenApiAppVO> update(@PathVariable String systemId,
                                            @PathVariable String externalAppId,
                                            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false)
                                            String idempotencyKey,
                                            @RequestBody OpenApiAppSaveRequest request) {
        return ApiResponse.success(openApiService.update(systemId, externalAppId, idempotencyKey, request));
    }

    @DeleteMapping("/api/v1/systems/{systemId}/openapi/apps/{externalAppId}")
    public ApiResponse<OpenApiDeleteResultVO> delete(@PathVariable String systemId,
                                                     @PathVariable String externalAppId,
                                                     @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false)
                                                     String idempotencyKey,
                                                     @RequestBody(required = false) OpenApiAppSaveRequest request) {
        return ApiResponse.success(openApiService.delete(systemId, externalAppId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/openapi/apps/{externalAppId}/scopes")
    public ApiResponse<List<OpenApiScopeVO>> scopes(@PathVariable String systemId,
                                                    @PathVariable String externalAppId) {
        return ApiResponse.success(openApiService.scopes(systemId, externalAppId));
    }

    @PutMapping("/api/v1/systems/{systemId}/openapi/apps/{externalAppId}/scopes")
    public ApiResponse<OpenApiAppVO> updateScopes(@PathVariable String systemId,
                                                  @PathVariable String externalAppId,
                                                  @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false)
                                                  String idempotencyKey,
                                                  @RequestBody OpenApiScopeUpdateRequest request) {
        return ApiResponse.success(openApiService.updateScopes(systemId, externalAppId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/openapi/apps/{externalAppId}/rate-limit")
    public ApiResponse<RateLimitMetadata> rateLimit(@PathVariable String systemId,
                                                    @PathVariable String externalAppId) {
        return ApiResponse.success(openApiService.rateLimit(systemId, externalAppId));
    }

    @PatchMapping("/api/v1/systems/{systemId}/openapi/apps/{externalAppId}/rate-limit")
    public ApiResponse<OpenApiAppVO> updateRateLimit(@PathVariable String systemId,
                                                     @PathVariable String externalAppId,
                                                     @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false)
                                                     String idempotencyKey,
                                                     @RequestBody OpenApiRateLimitUpdateRequest request) {
        return ApiResponse.success(openApiService.updateRateLimit(systemId, externalAppId,
                idempotencyKey, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/openapi/apps/{externalAppId}/rotate-secret")
    public ApiResponse<OpenApiSecretRotationJobVO> rotateSecret(@PathVariable String systemId,
                                                               @PathVariable String externalAppId,
                                                               @RequestHeader(name = IDEMPOTENCY_KEY_HEADER,
                                                                       required = false)
                                                               String idempotencyKey,
                                                               @RequestBody OpenApiSecretRotationRequest request) {
        return ApiResponse.success(openApiService.rotateSecret(systemId, externalAppId, idempotencyKey, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/openapi/call-logs")
    public ApiResponse<PageResult<OpenApiCallLogVO>> callLogs(@PathVariable String systemId,
                                                              @RequestParam(defaultValue = "1") int pageNo,
                                                              @RequestParam(defaultValue = "20") int pageSize,
                                                              OpenApiCallLogQuery query) {
        return ApiResponse.success(openApiService.callLogs(systemId,
                new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/openapi/call-logs/search")
    public ApiResponse<PageResult<OpenApiCallLogVO>> searchCallLogs(@PathVariable String systemId,
                                                                    @RequestParam(defaultValue = "1") int pageNo,
                                                                    @RequestParam(defaultValue = "20") int pageSize,
                                                                    @RequestBody(required = false)
                                                                    OpenApiCallLogQuery query) {
        return ApiResponse.success(openApiService.callLogs(systemId,
                new PageRequest(pageNo, pageSize, null, List.of(), List.of()), query));
    }

    @PostMapping("/openapi/v1/systems/{systemId}/modules/{moduleId}/records/search")
    public ApiResponse<PageResult<ExternalRecordRow>> externalRecordSearch(
            @PathVariable String systemId,
            @PathVariable String moduleId,
            @RequestHeader(name = OPENAPI_APP_CODE_HEADER) String appCode,
            @RequestHeader(name = OPENAPI_SECRET_REF_HEADER) String secretRefId,
            @RequestBody(required = false) ExternalRecordSearchRequest request) {
        return ApiResponse.success(openApiService.externalRecordSearch(systemId, moduleId, appCode,
                secretRefId, request));
    }

    @GetMapping("/openapi/v1/systems/{systemId}/modules/{moduleId}/records/{recordId}")
    public ApiResponse<ExternalRecordRow> externalRecordDetail(
            @PathVariable String systemId,
            @PathVariable String moduleId,
            @PathVariable String recordId,
            @RequestHeader(name = OPENAPI_APP_CODE_HEADER) String appCode,
            @RequestHeader(name = OPENAPI_SECRET_REF_HEADER) String secretRefId) {
        return ApiResponse.success(openApiService.externalRecordDetail(systemId, moduleId, recordId, appCode,
                secretRefId));
    }

    @PostMapping("/openapi/v1/systems/{systemId}/modules/{moduleId}/records")
    public ApiResponse<ExternalRecordMutationResult> externalRecordCreate(
            @PathVariable String systemId,
            @PathVariable String moduleId,
            @RequestHeader(name = OPENAPI_APP_CODE_HEADER) String appCode,
            @RequestHeader(name = OPENAPI_SECRET_REF_HEADER) String secretRefId,
            @RequestHeader(name = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
            @RequestBody ExternalRecordMutationRequest request) {
        return ApiResponse.success(openApiService.externalRecordCreate(systemId, moduleId, appCode, secretRefId,
                idempotencyKey, request));
    }
}
