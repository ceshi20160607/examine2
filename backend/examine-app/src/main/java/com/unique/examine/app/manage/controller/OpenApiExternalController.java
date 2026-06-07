package com.unique.examine.app.manage.controller;

import com.unique.examine.app.manage.service.OpenApiExternalService;
import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.flow.manage.vo.FlowActionResultVO;
import com.unique.examine.module.manage.vo.RecordDetailVO;
import com.unique.examine.module.manage.vo.RecordListItemVO;
import com.unique.examine.module.manage.vo.RecordMutationResultVO;
import com.unique.examine.upload.manage.vo.FileAccessVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OpenAPI 外部调用接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/openapi/v1")
public class OpenApiExternalController {

    private final OpenApiExternalService openApiExternalService;

    /**
     * 查询运行记录。
     */
    @Operation(summary = "OpenAPI 查询运行记录")
    @PostMapping("/records/query")
    public PageResult<RecordListItemVO> queryRecords(HttpServletRequest request, @RequestBody String rawBody) {
        return openApiExternalService.queryRecords(request, rawBody);
    }

    /**
     * 查询运行记录详情。
     */
    @Operation(summary = "OpenAPI 查询运行记录详情")
    @GetMapping("/records/{recordId}")
    public RecordDetailVO recordDetail(HttpServletRequest request, @PathVariable Long recordId,
            @RequestParam String moduleCode) {
        return openApiExternalService.recordDetail(request, recordId, moduleCode);
    }

    /**
     * 创建运行记录。
     */
    @Operation(summary = "OpenAPI 创建运行记录")
    @PostMapping("/records")
    public RecordMutationResultVO createRecord(HttpServletRequest request, @RequestBody String rawBody) {
        return openApiExternalService.createRecord(request, rawBody);
    }

    /**
     * 更新运行记录。
     */
    @Operation(summary = "OpenAPI 更新运行记录")
    @PutMapping("/records/{recordId}")
    public RecordMutationResultVO updateRecord(HttpServletRequest request, @PathVariable Long recordId,
            @RequestBody String rawBody) {
        return openApiExternalService.updateRecord(request, recordId, rawBody);
    }

    /**
     * 提交运行记录。
     */
    @Operation(summary = "OpenAPI 提交运行记录")
    @PostMapping("/records/{recordId}/submit")
    public RecordMutationResultVO submitRecord(HttpServletRequest request, @PathVariable Long recordId,
            @RequestBody String rawBody) {
        return openApiExternalService.submitRecord(request, recordId, rawBody);
    }

    /**
     * 处理流程任务。
     */
    @Operation(summary = "OpenAPI 处理流程任务")
    @PostMapping("/flow/tasks/{taskId}/actions")
    public FlowActionResultVO handleTask(HttpServletRequest request, @PathVariable Long taskId,
            @RequestBody String rawBody) {
        return openApiExternalService.handleTask(request, taskId, rawBody);
    }

    /**
     * 下载文件。
     */
    @Operation(summary = "OpenAPI 下载文件")
    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<Resource> downloadFile(HttpServletRequest request, @PathVariable Long fileId) {
        FileAccessVO access = openApiExternalService.downloadFile(request, fileId);
        MediaType mediaType = MediaType.parseMediaType(access.getContentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(access.getSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(access.getFileName())
                        .build()
                        .toString())
                .body(access.getResource());
    }
}
