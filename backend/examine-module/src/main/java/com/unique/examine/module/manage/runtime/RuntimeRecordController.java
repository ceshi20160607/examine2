package com.unique.examine.module.manage.runtime;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.ActionExecutionRequest;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.ActionExecutionResult;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.BusinessDetailView;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordHistoryEntry;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordMutationResult;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordSaveRequest;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RuntimePrintRequest;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RecordSearchRequest;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RuntimeListSchemaVO;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RuntimeRecordSearchVO;
import com.unique.examine.module.manage.runtime.RuntimeRecordModels.RuntimeSceneOptionVO;
import com.unique.examine.module.manage.config.ModuleConfigModels.PrintTemplatePreviewVO;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runtime dynamic record API controller.
 */
@RestController
public class RuntimeRecordController {

    private final RuntimeRecordService runtimeRecordService;

    public RuntimeRecordController(RuntimeRecordService runtimeRecordService) {
        this.runtimeRecordService = runtimeRecordService;
    }

    @GetMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/list-schema")
    public ApiResponse<RuntimeListSchemaVO> listSchema(@PathVariable String systemId,
                                                       @PathVariable String moduleId,
                                                       @RequestParam(required = false) String sceneCode) {
        return ApiResponse.success(runtimeRecordService.listSchema(systemId, moduleId, sceneCode));
    }

    @GetMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/scenes")
    public ApiResponse<List<RuntimeSceneOptionVO>> scenes(@PathVariable String systemId,
                                                          @PathVariable String moduleId) {
        return ApiResponse.success(runtimeRecordService.scenes(systemId, moduleId));
    }

    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/search")
    public ApiResponse<RuntimeRecordSearchVO> search(@PathVariable String systemId,
                                                     @PathVariable String moduleId,
                                                     @RequestBody(required = false) RecordSearchRequest request) {
        return ApiResponse.success(runtimeRecordService.search(systemId, moduleId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}")
    public ApiResponse<BusinessDetailView> detail(@PathVariable String systemId,
                                                  @PathVariable String moduleId,
                                                  @PathVariable String recordId) {
        return ApiResponse.success(runtimeRecordService.detail(systemId, moduleId, recordId));
    }

    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records")
    public ApiResponse<RecordMutationResult> create(@PathVariable String systemId,
                                                    @PathVariable String moduleId,
                                                    @RequestBody(required = false) RecordSaveRequest request,
                                                    @RequestHeader(value = "Idempotency-Key",
                                                            required = false) String idempotencyKey) {
        return ApiResponse.success(runtimeRecordService.create(systemId, moduleId, request, idempotencyKey));
    }

    @PatchMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}")
    public ApiResponse<RecordMutationResult> update(@PathVariable String systemId,
                                                    @PathVariable String moduleId,
                                                    @PathVariable String recordId,
                                                    @RequestBody(required = false) RecordSaveRequest request,
                                                    @RequestHeader(value = "Idempotency-Key",
                                                            required = false) String idempotencyKey) {
        return ApiResponse.success(runtimeRecordService.update(systemId, moduleId, recordId, request,
                idempotencyKey));
    }

    @DeleteMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}")
    public ApiResponse<RecordMutationResult> delete(@PathVariable String systemId,
                                                    @PathVariable String moduleId,
                                                    @PathVariable String recordId,
                                                    @RequestHeader(value = "Idempotency-Key",
                                                            required = false) String idempotencyKey) {
        return ApiResponse.success(runtimeRecordService.delete(systemId, moduleId, recordId, idempotencyKey));
    }

    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/actions/{actionCode}")
    public ApiResponse<ActionExecutionResult> executeAction(@PathVariable String systemId,
                                                            @PathVariable String moduleId,
                                                            @PathVariable String recordId,
                                                            @PathVariable String actionCode,
                                                            @RequestBody(required = false)
                                                            ActionExecutionRequest request,
                                                            @RequestHeader(value = "Idempotency-Key",
                                                                    required = false) String idempotencyKey) {
        return ApiResponse.success(runtimeRecordService.executeAction(systemId, moduleId, recordId, actionCode,
                request, idempotencyKey));
    }

    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/print-preview")
    public ApiResponse<PrintTemplatePreviewVO> printPreview(@PathVariable String systemId,
                                                            @PathVariable String moduleId,
                                                            @PathVariable String recordId,
                                                            @RequestBody(required = false)
                                                            RuntimePrintRequest request) {
        return ApiResponse.success(runtimeRecordService.printPreview(systemId, moduleId, recordId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/print-export")
    public ApiResponse<PrintTemplatePreviewVO> printExport(@PathVariable String systemId,
                                                           @PathVariable String moduleId,
                                                           @PathVariable String recordId,
                                                           @RequestBody(required = false)
                                                           RuntimePrintRequest request) {
        return ApiResponse.success(runtimeRecordService.printExport(systemId, moduleId, recordId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/records/{recordId}/history")
    public ApiResponse<PageResult<RecordHistoryEntry>> history(@PathVariable String systemId,
                                                               @PathVariable String moduleId,
                                                               @PathVariable String recordId,
                                                               @RequestParam(defaultValue = "1") int pageNo,
                                                               @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.success(runtimeRecordService.history(systemId, moduleId, recordId, pageNo, pageSize));
    }
}
