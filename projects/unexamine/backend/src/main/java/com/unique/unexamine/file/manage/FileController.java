package com.unique.unexamine.file.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final FileStorageService service;

    public FileController(FileStorageService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<List<FileModels.FileView>> list(HttpServletRequest request) {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require(), TraceIdFilter.current(request)));
    }

    @GetMapping("/policy")
    public ApiResult<FileModels.StoragePolicyView> policy(HttpServletRequest request) {
        return ApiResult.ok(service.policy(AuthenticationContextHolder.require(), TraceIdFilter.current(request)));
    }

    @GetMapping("/storage")
    public ApiResult<List<FileModels.StorageBackendView>> storage(HttpServletRequest request) {
        return ApiResult.ok(service.storageBackends(AuthenticationContextHolder.require(), TraceIdFilter.current(request)));
    }

    @PutMapping("/storage/object")
    public ApiResult<FileModels.StorageBackendView> configureObjectStorage(
            @Valid @RequestBody FileModels.ConfigureObjectStorageRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.configureObjectStorage(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/storage/object/deactivate")
    public ApiResult<FileModels.StorageBackendView> deactivateObjectStorage(HttpServletRequest request) {
        return ApiResult.ok(service.deactivateObjectStorage(AuthenticationContextHolder.require(),
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{fileId}")
    public ApiResult<FileModels.FileView> detail(@PathVariable Long fileId, HttpServletRequest request) {
        return ApiResult.ok(service.detail(AuthenticationContextHolder.require(), fileId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/uploads")
    public ApiResult<FileModels.UploadSessionView> start(
            @Valid @RequestBody FileModels.StartUploadRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.start(AuthenticationContextHolder.require(), input, TraceIdFilter.current(request)));
    }

    @GetMapping("/uploads/{sessionId}")
    public ApiResult<FileModels.UploadSessionView> uploadStatus(
            @PathVariable Long sessionId,
            @RequestHeader("X-Upload-Token") String uploadToken,
            HttpServletRequest request) {
        return ApiResult.ok(service.uploadStatus(AuthenticationContextHolder.require(), sessionId, uploadToken,
                TraceIdFilter.current(request)));
    }

    @PutMapping(value = "/uploads/{sessionId}/chunks", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResult<FileModels.UploadChunkView> uploadChunk(
            @PathVariable Long sessionId,
            @RequestParam Long offset,
            @RequestHeader("X-Upload-Token") String uploadToken,
            @RequestHeader("X-Chunk-Sha256") String chunkSha256,
            @RequestBody byte[] content,
            HttpServletRequest request) {
        return ApiResult.ok(service.uploadChunk(AuthenticationContextHolder.require(), sessionId, uploadToken,
                offset, chunkSha256, content, TraceIdFilter.current(request)));
    }

    @PostMapping("/uploads/{sessionId}/complete")
    public ApiResult<FileModels.FileView> completeUpload(
            @PathVariable Long sessionId,
            @RequestHeader("X-Upload-Token") String uploadToken,
            HttpServletRequest request) {
        return ApiResult.ok(service.completeUpload(AuthenticationContextHolder.require(), sessionId, uploadToken,
                TraceIdFilter.current(request)));
    }

    @DeleteMapping("/uploads/{sessionId}")
    public ApiResult<FileModels.UploadSessionView> cancelUpload(
            @PathVariable Long sessionId,
            @RequestHeader("X-Upload-Token") String uploadToken,
            HttpServletRequest request) {
        return ApiResult.ok(service.cancelUpload(AuthenticationContextHolder.require(), sessionId, uploadToken,
                TraceIdFilter.current(request)));
    }

    @PutMapping(value = "/uploads/{sessionId}/content", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ApiResult<FileModels.FileView> upload(
            @PathVariable Long sessionId,
            @RequestHeader("X-Upload-Token") String uploadToken,
            @RequestBody byte[] content,
            HttpServletRequest request) {
        return ApiResult.ok(service.upload(AuthenticationContextHolder.require(), sessionId, uploadToken, content,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/{fileId}/references")
    public ApiResult<FileModels.FileView> reference(
            @PathVariable Long fileId,
            @Valid @RequestBody FileModels.AddReferenceRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.addReference(AuthenticationContextHolder.require(), fileId, input,
                TraceIdFilter.current(request)));
    }

    @DeleteMapping("/{fileId}/references/{referenceId}")
    public ApiResult<FileModels.FileView> removeReference(
            @PathVariable Long fileId, @PathVariable Long referenceId,
            HttpServletRequest request) {
        return ApiResult.ok(service.removeReference(AuthenticationContextHolder.require(), fileId, referenceId,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{fileId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long fileId, HttpServletRequest request) {
        return binary(service.preview(AuthenticationContextHolder.require(), fileId, TraceIdFilter.current(request)));
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long fileId, HttpServletRequest request) {
        return binary(service.download(AuthenticationContextHolder.require(), fileId, TraceIdFilter.current(request)));
    }

    @DeleteMapping("/{fileId}")
    public ApiResult<FileModels.DeleteResult> delete(@PathVariable Long fileId, HttpServletRequest request) {
        return ApiResult.ok(service.delete(AuthenticationContextHolder.require(), fileId, TraceIdFilter.current(request)));
    }

    private ResponseEntity<byte[]> binary(FileModels.BinaryContent content) {
        MediaType contentType;
        try { contentType = MediaType.parseMediaType(content.contentType()); }
        catch (RuntimeException ignored) { contentType = MediaType.APPLICATION_OCTET_STREAM; }
        ContentDisposition disposition = content.inline()
                ? ContentDisposition.inline().filename(content.fileName(), StandardCharsets.UTF_8).build()
                : ContentDisposition.attachment().filename(content.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentLength(content.bytes().length).body(content.bytes());
    }
}
