package com.unique.unexamine.sys.file.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.file.manage.dto.FileManageDto.FileView;
import com.unique.unexamine.sys.file.manage.service.FileManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 系统文件 HTTP 入口，所有归属和引用规则由业务 Service 执行。 */
@RestController
@RequestMapping("/api/systems/{systemId}/files")
@Tag(name = "系统文件")
public class FileController {
    private final FileManageService service;
    /** 注入文件业务服务。 */
    public FileController(FileManageService service) { this.service = service; }

    /** 上传字节并登记文件元数据。 */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件")
    public Result<FileView> upload(@RequestHeader(value="Authorization", required=false) String token,
            @RequestHeader(value="X-Tenant-Id", required=false) Long tenantId,
            @PathVariable Long systemId, @RequestParam(required=false) String batchId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return Result.ok(service.upload(token, systemId, tenantId, batchId, file));
    }

    /** 查看本租户本人上传的可用文件。 */
    @GetMapping
    @Operation(summary = "文件列表")
    public Result<Page<FileView>> list(@RequestHeader(value="Authorization", required=false) String token,
            @RequestHeader(value="X-Tenant-Id", required=false) Long tenantId,
            @PathVariable Long systemId, @ModelAttribute SearchBO search) {
        return Result.ok(service.queryPageList(token, systemId, tenantId, search));
    }

    /** 按详情表单中的批次 UUID 查询当前有权读取的同批文件。 */
    @GetMapping("/batches/{batchId}")
    @Operation(summary = "查询附件字段文件批次")
    public Result<java.util.List<FileView>> batch(
            @RequestHeader(value="Authorization", required=false) String token,
            @RequestHeader(value="X-Tenant-Id", required=false) Long tenantId,
            @PathVariable Long systemId, @PathVariable String batchId) {
        return Result.ok(service.batch(token, systemId, tenantId, batchId));
    }

    /** 查询文件元数据，不公开对象存储键。 */
    @GetMapping("/{fileId}")
    @Operation(summary = "文件详情")
    public Result<FileView> detail(@RequestHeader(value="Authorization", required=false) String token,
            @RequestHeader(value="X-Tenant-Id", required=false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long fileId) {
        return Result.ok(service.queryById(token, systemId, tenantId, fileId));
    }

    /** 内容由已授权的私有存储对象流式返回。 */
    @GetMapping("/{fileId}/download")
    @Operation(summary = "下载文件")
    public ResponseEntity<InputStreamResource> download(@RequestHeader(value="Authorization", required=false) String token,
            @RequestHeader(value="X-Tenant-Id", required=false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long fileId) throws IOException {
        var content = service.content(token, systemId, tenantId, fileId);
        var disposition = ContentDisposition.attachment().filename(content.file().originalName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(content.file().sizeBytes())
                .body(new InputStreamResource(content.stream()));
    }

    /** 常见图片和 PDF 内联展示，其余格式明确提示下载。 */
    @GetMapping("/{fileId}/preview")
    @Operation(summary = "预览文件")
    public ResponseEntity<InputStreamResource> preview(@RequestHeader(value="Authorization", required=false) String token,
            @RequestHeader(value="X-Tenant-Id", required=false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long fileId) throws IOException {
        var content = service.preview(token, systemId, tenantId, fileId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .contentType(MediaType.parseMediaType(content.file().contentType()))
                .contentLength(content.file().sizeBytes())
                .body(new InputStreamResource(content.stream()));
    }

    /** 引用中的文件会由业务层拒绝删除。 */
    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除未引用文件")
    public Result<Void> delete(@RequestHeader(value="Authorization", required=false) String token,
            @RequestHeader(value="X-Tenant-Id", required=false) Long tenantId,
            @PathVariable Long systemId, @PathVariable Long fileId) throws IOException {
        service.delete(token, systemId, tenantId, fileId);
        return Result.ok();
    }
}
