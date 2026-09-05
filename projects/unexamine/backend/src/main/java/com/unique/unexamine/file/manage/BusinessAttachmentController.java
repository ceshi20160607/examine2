package com.unique.unexamine.file.manage;

import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/business-attachments")
public class BusinessAttachmentController {
    private static final String PERSONAL_FIELD = "personal_documents";
    private final FileStorageService service;

    public BusinessAttachmentController(FileStorageService service) {
        this.service = service;
    }

    @GetMapping("/personal")
    public ApiResult<List<FileModels.BusinessAttachmentView>> personal(HttpServletRequest request) {
        AuthenticatedContext context = AuthenticationContextHolder.require();
        return ApiResult.ok(service.listBusinessAttachments(context, "ACCOUNT", context.accountId().toString(),
                PERSONAL_FIELD, TraceIdFilter.current(request)));
    }

    @PostMapping(value = "/personal", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<FileModels.BusinessAttachmentView> uploadPersonal(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "DOCUMENT") String purpose,
            HttpServletRequest request) {
        AuthenticatedContext context = AuthenticationContextHolder.require();
        return ApiResult.ok(upload(context, file, "ACCOUNT", context.accountId().toString(), PERSONAL_FIELD,
                purpose, TraceIdFilter.current(request)));
    }

    @GetMapping("/records/{moduleCode}/{recordId}/fields/{fieldCode}")
    public ApiResult<List<FileModels.BusinessAttachmentView>> recordAttachments(
            @PathVariable String moduleCode, @PathVariable Long recordId, @PathVariable String fieldCode,
            HttpServletRequest request) {
        AuthenticatedContext context = AuthenticationContextHolder.require();
        return ApiResult.ok(service.listBusinessAttachments(context, "BUSINESS_RECORD",
                moduleCode + ":" + recordId, fieldCode, TraceIdFilter.current(request)));
    }

    @PostMapping(value = "/records/{moduleCode}/{recordId}/fields/{fieldCode}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<FileModels.BusinessAttachmentView> uploadRecordAttachment(
            @PathVariable String moduleCode, @PathVariable Long recordId, @PathVariable String fieldCode,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "ATTACHMENT") String purpose,
            HttpServletRequest request) {
        return ApiResult.ok(upload(AuthenticationContextHolder.require(), file, "BUSINESS_RECORD",
                moduleCode + ":" + recordId, fieldCode, purpose, TraceIdFilter.current(request)));
    }

    @GetMapping("/flow-instances/{instanceId}")
    public ApiResult<List<FileModels.BusinessAttachmentView>> flowAttachments(
            @PathVariable Long instanceId, HttpServletRequest request) {
        return ApiResult.ok(service.listBusinessAttachments(AuthenticationContextHolder.require(), "FLOW_INSTANCE",
                instanceId.toString(), "attachments", TraceIdFilter.current(request)));
    }

    @PostMapping(value = "/flow-instances/{instanceId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<FileModels.BusinessAttachmentView> uploadFlowAttachment(
            @PathVariable Long instanceId, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "ATTACHMENT") String purpose, HttpServletRequest request) {
        return ApiResult.ok(upload(AuthenticationContextHolder.require(), file, "FLOW_INSTANCE",
                instanceId.toString(), "attachments", purpose, TraceIdFilter.current(request)));
    }

    @GetMapping("/work-tasks/{taskId}")
    public ApiResult<List<FileModels.BusinessAttachmentView>> workTaskAttachments(
            @PathVariable Long taskId, HttpServletRequest request) {
        return ApiResult.ok(service.listBusinessAttachments(AuthenticationContextHolder.require(), "WORK_TASK",
                taskId.toString(), "attachments", TraceIdFilter.current(request)));
    }

    @PostMapping(value = "/work-tasks/{taskId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<FileModels.BusinessAttachmentView> uploadWorkTaskAttachment(
            @PathVariable Long taskId, @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "ATTACHMENT") String purpose, HttpServletRequest request) {
        return ApiResult.ok(upload(AuthenticationContextHolder.require(), file, "WORK_TASK",
                taskId.toString(), "attachments", purpose, TraceIdFilter.current(request)));
    }

    @GetMapping("/{attachmentId}/preview")
    public ResponseEntity<byte[]> preview(@PathVariable Long attachmentId, HttpServletRequest request) {
        return binary(service.previewBusinessAttachment(AuthenticationContextHolder.require(), attachmentId,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/{attachmentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long attachmentId, HttpServletRequest request) {
        return binary(service.downloadBusinessAttachment(AuthenticationContextHolder.require(), attachmentId,
                TraceIdFilter.current(request)));
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResult<FileModels.DeleteResult> remove(@PathVariable Long attachmentId, HttpServletRequest request) {
        return ApiResult.ok(service.removeBusinessAttachment(AuthenticationContextHolder.require(), attachmentId,
                TraceIdFilter.current(request)));
    }

    private FileModels.BusinessAttachmentView upload(
            AuthenticatedContext context, MultipartFile file, String ownerType, String ownerId,
            String fieldCode, String purpose, String traceId) {
        try {
            String originalName = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
            return service.uploadBusinessAttachment(context, file.getBytes(), originalName,
                    file.getContentType(), ownerType, ownerId, fieldCode, purpose, traceId);
        } catch (IOException exception) {
            throw new DomainException("FILE_UPLOAD_READ_FAILED", "无法读取上传的附件", HttpStatus.BAD_REQUEST);
        }
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
