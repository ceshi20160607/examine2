package com.unique.examine.module.manage.importexport;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.module.manage.importexport.ImportExportModels.ExportRequest;
import com.unique.examine.module.manage.importexport.ImportExportModels.ExportResult;
import com.unique.examine.module.manage.importexport.ImportExportModels.ImportConfirmRequest;
import com.unique.examine.module.manage.importexport.ImportExportModels.ImportConfirmResult;
import com.unique.examine.module.manage.importexport.ImportExportModels.ImportPrecheckRequest;
import com.unique.examine.module.manage.importexport.ImportExportModels.ImportPrecheckResult;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行态导入导出接口。
 */
@RestController
public class ImportExportController {

    private final ImportExportService importExportService;

    public ImportExportController(ImportExportService importExportService) {
        this.importExportService = importExportService;
    }

    /**
     * 导入预检。
     *
     * @param systemId 系统编号
     * @param moduleId 模块编号
     * @param request 预检请求
     * @param idempotencyKey 幂等键
     * @return 预检结果
     */
    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/imports/precheck")
    public ApiResponse<ImportPrecheckResult> precheck(@PathVariable String systemId,
                                                      @PathVariable String moduleId,
                                                      @RequestBody(required = false)
                                                      ImportPrecheckRequest request,
                                                      @RequestHeader(value = "Idempotency-Key",
                                                              required = false) String idempotencyKey) {
        return ApiResponse.success(importExportService.precheck(systemId, moduleId, request, idempotencyKey));
    }

    /**
     * 导入确认执行。
     *
     * @param systemId 系统编号
     * @param moduleId 模块编号
     * @param request 确认请求
     * @param idempotencyKey 幂等键
     * @return 导入任务
     */
    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/imports/confirm")
    public ApiResponse<ImportConfirmResult> confirm(@PathVariable String systemId,
                                                    @PathVariable String moduleId,
                                                    @RequestBody(required = false)
                                                    ImportConfirmRequest request,
                                                    @RequestHeader(value = "Idempotency-Key",
                                                            required = false) String idempotencyKey) {
        return ApiResponse.success(importExportService.confirm(systemId, moduleId, request, idempotencyKey));
    }

    /**
     * 创建导出任务。
     *
     * @param systemId 系统编号
     * @param moduleId 模块编号
     * @param request 导出请求
     * @param idempotencyKey 幂等键
     * @return 导出任务和文件引用
     */
    @PostMapping("/api/v1/systems/{systemId}/runtime/modules/{moduleId}/exports")
    public ApiResponse<ExportResult> export(@PathVariable String systemId,
                                            @PathVariable String moduleId,
                                            @RequestBody(required = false) ExportRequest request,
                                            @RequestHeader(value = "Idempotency-Key",
                                                    required = false) String idempotencyKey) {
        return ApiResponse.success(importExportService.export(systemId, moduleId, request, idempotencyKey));
    }
}
