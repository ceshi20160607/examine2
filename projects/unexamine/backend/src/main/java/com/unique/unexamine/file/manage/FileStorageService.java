package com.unique.unexamine.file.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.file.base.entity.FileObject;
import com.unique.unexamine.file.base.entity.FileReference;
import com.unique.unexamine.file.base.entity.FileSecurityScan;
import com.unique.unexamine.file.base.entity.FileStorageBackend;
import com.unique.unexamine.file.base.entity.FileUploadSession;
import com.unique.unexamine.file.base.service.FileObjectBaseService;
import com.unique.unexamine.file.base.service.FileReferenceBaseService;
import com.unique.unexamine.file.base.service.FileSecurityScanBaseService;
import com.unique.unexamine.file.base.service.FileStorageBackendBaseService;
import com.unique.unexamine.file.base.service.FileUploadSessionBaseService;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.service.FlowInstanceBaseService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> REFERENCE_TYPES = Set.of("ACCOUNT", "FLOW_INSTANCE", "BUSINESS_RECORD", "WORK_TASK");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FileStorageBackendBaseService backendService;
    private final FileUploadSessionBaseService sessionService;
    private final FileObjectBaseService objectService;
    private final FileReferenceBaseService referenceService;
    private final FileSecurityScanBaseService scanService;
    private final FlowInstanceBaseService flowInstanceService;
    private final WorkTaskBaseService workTaskService;
    private final RuntimeDataService runtimeDataService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final FileUploadFailureRecorder failureRecorder;
    private final ObjectMapper objectMapper;
    private final Path storageRoot;
    private final long maximumSize;

    public FileStorageService(
            FileStorageBackendBaseService backendService,
            FileUploadSessionBaseService sessionService,
            FileObjectBaseService objectService,
            FileReferenceBaseService referenceService,
            FileSecurityScanBaseService scanService,
            FlowInstanceBaseService flowInstanceService,
            WorkTaskBaseService workTaskService,
            RuntimeDataService runtimeDataService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            FileUploadFailureRecorder failureRecorder,
            ObjectMapper objectMapper,
            @Value("${app.file.storage-root:./data/files}") String storageRoot,
            @Value("${app.file.max-size-bytes:52428800}") long maximumSize) {
        this.backendService = backendService;
        this.sessionService = sessionService;
        this.objectService = objectService;
        this.referenceService = referenceService;
        this.scanService = scanService;
        this.flowInstanceService = flowInstanceService;
        this.workTaskService = workTaskService;
        this.runtimeDataService = runtimeDataService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.failureRecorder = failureRecorder;
        this.objectMapper = objectMapper;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maximumSize = maximumSize;
    }

    @Transactional
    public FileModels.UploadSessionView start(
            AuthenticatedContext context, FileModels.StartUploadRequest input, String traceId) {
        require(context, "UPLOAD");
        if (input.expectedSize() > maximumSize) {
            throw invalid("FILE_SIZE_LIMIT_EXCEEDED", "文件超过当前部署允许的单文件大小");
        }
        String originalName = sanitizeName(input.originalName());
        FileStorageBackend backend = backend(context);
        String token = randomToken();
        String objectKey = contextKey(context) + "/" + UUID.randomUUID();
        FileUploadSession session = new FileUploadSession();
        session.setContextType(contextType(context));
        session.setPlatformId(context.platformId());
        session.setSystemId(context.systemId());
        session.setTenantId(context.tenantId());
        session.setStorageBackendId(backend.getId());
        session.setUploaderAccountId(context.accountId());
        session.setOriginalName(originalName);
        session.setContentType(input.contentType().strip().toLowerCase(Locale.ROOT));
        session.setExpectedSize(input.expectedSize());
        session.setExpectedSha256(input.expectedSha256() == null ? null : input.expectedSha256().toLowerCase(Locale.ROOT));
        session.setObjectKey(objectKey);
        session.setStatus("PENDING");
        session.setUploadTokenHash(sha256(token.getBytes(StandardCharsets.UTF_8)));
        session.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        session.setVersion(0);
        sessionService.insert(session);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FILE_UPLOAD_STARTED", "FILE_UPLOAD_SESSION", session.getId().toString(), "SUCCESS",
                Map.of("originalName", originalName, "expectedSize", input.expectedSize(),
                        "contentType", session.getContentType()));
        return sessionView(session, token);
    }

    @Transactional
    public FileModels.FileView upload(
            AuthenticatedContext context, Long sessionId, String rawToken, byte[] content, String traceId) {
        require(context, "UPLOAD");
        FileUploadSession session = sessionService.selectById(sessionId);
        if (session == null || !inContext(session, context)
                || !Objects.equals(session.getUploaderAccountId(), context.accountId())) {
            throw notFound("FILE_UPLOAD_SESSION_NOT_FOUND", "上传会话不存在");
        }
        if (!"PENDING".equals(session.getStatus())) {
            throw conflict("FILE_UPLOAD_SESSION_NOT_PENDING", "上传会话已经完成、失败或过期");
        }
        if (!secureEquals(session.getUploadTokenHash(), sha256(rawToken.getBytes(StandardCharsets.UTF_8)))) {
            throw forbidden("FILE_UPLOAD_TOKEN_INVALID", "上传凭证无效");
        }
        if (session.getExpiresAt().isBefore(LocalDateTime.now())) {
            failureRecorder.fail(sessionId, "EXPIRED");
            throw conflict("FILE_UPLOAD_SESSION_EXPIRED", "上传会话已过期，请重新创建");
        }
        if (content.length != session.getExpectedSize()) {
            failUpload(context, session, traceId, "FAILED", "FILE_SIZE_MISMATCH",
                    Map.of("expectedSize", session.getExpectedSize(), "actualSize", content.length));
        }
        String contentSha = sha256(content);
        if (session.getExpectedSha256() != null && !secureEquals(session.getExpectedSha256(), contentSha)) {
            failUpload(context, session, traceId, "FAILED", "FILE_CHECKSUM_MISMATCH",
                    Map.of("expectedSha256", session.getExpectedSha256(), "actualSha256", contentSha));
        }
        Path path = objectPath(session.getObjectKey());
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            failureRecorder.fail(sessionId, "FAILED");
            throw new DomainException("FILE_STORAGE_WRITE_FAILED", "文件写入存储失败", HttpStatus.SERVICE_UNAVAILABLE);
        }
        deleteOnRollback(path);
        FileObject object = new FileObject();
        object.setContextType(session.getContextType());
        object.setPlatformId(session.getPlatformId());
        object.setSystemId(session.getSystemId());
        object.setTenantId(session.getTenantId());
        object.setStorageBackendId(session.getStorageBackendId());
        object.setUploadSessionId(session.getId());
        object.setObjectKey(session.getObjectKey());
        object.setObjectKeyHash(sha256(session.getObjectKey().getBytes(StandardCharsets.UTF_8)));
        object.setOriginalName(session.getOriginalName());
        object.setContentType(session.getContentType());
        object.setSizeBytes((long) content.length);
        object.setSha256(contentSha);
        object.setScanStatus("SCANNING");
        object.setPreviewStatus("PENDING");
        object.setStatus("QUARANTINED");
        object.setUploadedByAccountId(context.accountId());
        object.setVersion(0);
        objectService.insert(object);

        boolean blocked = blocked(session.getOriginalName(), session.getContentType(), content);
        FileSecurityScan scan = new FileSecurityScan();
        scan.setFileId(object.getId());
        scan.setScanner("BUILTIN_SIGNATURE");
        scan.setScanVersion("1");
        scan.setStatus(blocked ? "BLOCKED" : "CLEAN");
        scan.setResultCode(blocked ? "UNSAFE_CONTENT_SIGNATURE" : "CLEAN");
        scan.setResultDetailJson(toJson(Map.of("size", content.length,
                "contentType", session.getContentType(), "sha256", contentSha)));
        scan.setStartedAt(LocalDateTime.now());
        scan.setFinishedAt(LocalDateTime.now());
        scanService.insert(scan);

        object.setScanStatus(blocked ? "BLOCKED" : "CLEAN");
        object.setPreviewStatus(blocked ? "BLOCKED" : previewStatus(session.getContentType()));
        object.setStatus(blocked ? "QUARANTINED" : "ACTIVE");
        objectService.updateById(object);
        session.setStatus("COMPLETED");
        session.setCompletedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionService.updateById(session);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                blocked ? "FILE_UPLOAD_QUARANTINED" : "FILE_UPLOAD_COMPLETED", "FILE", object.getId().toString(),
                blocked ? "UNSAFE_CONTENT_SIGNATURE" : "SUCCESS",
                Map.of("size", content.length, "sha256", contentSha, "scanStatus", object.getScanStatus()));
        return view(objectService.selectById(object.getId()));
    }

    @Transactional(readOnly = true)
    public List<FileModels.FileView> list(AuthenticatedContext context, String traceId) {
        require(context, "VIEW");
        return objectService.selectList(Wrappers.<FileObject>lambdaQuery()
                        .eq(FileObject::getPlatformId, context.platformId())
                        .ne(FileObject::getStatus, "DELETED")
                        .orderByDesc(FileObject::getCreatedAt))
                .stream().filter(file -> inContext(file, context))
                .filter(file -> canAccess(context, file, "VIEW", traceId)).map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public FileModels.FileView detail(AuthenticatedContext context, Long fileId, String traceId) {
        return view(requireFile(context, fileId, "VIEW", traceId));
    }

    @Transactional
    public FileModels.FileView addReference(
            AuthenticatedContext context, Long fileId, FileModels.AddReferenceRequest input, String traceId) {
        require(context, "REFERENCE");
        FileObject file = requireFile(context, fileId, "REFERENCE", traceId);
        if (!"ACTIVE".equals(file.getStatus()) || !"CLEAN".equals(file.getScanStatus())) {
            throw conflict("FILE_NOT_PUBLISHABLE", "文件未通过安全扫描，不能建立业务引用");
        }
        validateReferenceTarget(context, input.ownerType(), input.ownerId(), traceId);
        FileReference existing = referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                        .eq(FileReference::getFileId, fileId).eq(FileReference::getOwnerType, input.ownerType())
                        .eq(FileReference::getOwnerId, input.ownerId()))
                .stream().filter(item -> Objects.equals(item.getFieldCode(), stripToNull(input.fieldCode())))
                .findFirst().orElse(null);
        if (existing == null) {
            FileReference reference = new FileReference();
            reference.setFileId(fileId);
            reference.setContextType(contextType(context));
            reference.setSystemId(context.systemId());
            reference.setTenantId(context.tenantId());
            reference.setOwnerType(input.ownerType());
            reference.setOwnerId(input.ownerId());
            reference.setFieldCode(stripToNull(input.fieldCode()));
            reference.setReferenceType(input.referenceType());
            reference.setCreatedByAccountId(context.accountId());
            referenceService.insert(reference);
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "FILE_REFERENCE_ADDED", "FILE", fileId.toString(), "SUCCESS",
                    Map.of("referenceId", reference.getId(), "ownerType", input.ownerType(), "ownerId", input.ownerId()));
        }
        return view(file);
    }

    @Transactional
    public FileModels.FileView removeReference(
            AuthenticatedContext context, Long fileId, Long referenceId, String traceId) {
        require(context, "REFERENCE");
        FileObject file = requireFile(context, fileId, "REFERENCE", traceId);
        FileReference reference = referenceService.selectById(referenceId);
        if (reference == null || !Objects.equals(reference.getFileId(), fileId) || !referenceInContext(reference, context)) {
            throw notFound("FILE_REFERENCE_NOT_FOUND", "文件引用不存在");
        }
        referenceService.deleteById(referenceId);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FILE_REFERENCE_REMOVED", "FILE", fileId.toString(), "SUCCESS",
                Map.of("referenceId", referenceId));
        return view(file);
    }

    @Transactional(readOnly = true)
    public FileModels.BinaryContent preview(AuthenticatedContext context, Long fileId, String traceId) {
        FileObject file = requireFile(context, fileId, "VIEW", traceId);
        if (!"AVAILABLE".equals(file.getPreviewStatus())) {
            throw conflict("FILE_PREVIEW_UNAVAILABLE", "该文件不能在线预览，请在有下载权限时下载");
        }
        return binary(file, true);
    }

    @Transactional(readOnly = true)
    public FileModels.BinaryContent download(AuthenticatedContext context, Long fileId, String traceId) {
        FileObject file = requireFile(context, fileId, "DOWNLOAD", traceId);
        return binary(file, false);
    }

    @Transactional
    public FileModels.FileView storeGenerated(
            AuthenticatedContext context, String originalName, String contentType, byte[] content,
            String fieldCode, String traceId) {
        if (context.systemId() == null || context.tenantId() == null || context.accountId() == null) {
            throw conflict("GENERATED_FILE_CONTEXT_REQUIRED", "生成文件必须属于已认证的系统租户上下文");
        }
        if (content == null || content.length == 0 || content.length > maximumSize) {
            throw invalid("GENERATED_FILE_SIZE_INVALID", "生成文件为空或超过当前部署允许的单文件大小");
        }
        String safeName = sanitizeName(originalName);
        String safeContentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType.strip().toLowerCase(Locale.ROOT);
        FileStorageBackend storage = backend(context);
        String objectKey = contextKey(context) + "/generated/" + UUID.randomUUID();
        Path path = objectPath(objectKey);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new DomainException("FILE_STORAGE_WRITE_FAILED", "生成文件写入存储失败", HttpStatus.SERVICE_UNAVAILABLE);
        }
        deleteOnRollback(path);

        String contentSha = sha256(content);
        FileObject object = new FileObject();
        object.setContextType(contextType(context));
        object.setPlatformId(context.platformId());
        object.setSystemId(context.systemId());
        object.setTenantId(context.tenantId());
        object.setStorageBackendId(storage.getId());
        object.setUploadSessionId(null);
        object.setObjectKey(objectKey);
        object.setObjectKeyHash(sha256(objectKey.getBytes(StandardCharsets.UTF_8)));
        object.setOriginalName(safeName);
        object.setContentType(safeContentType);
        object.setSizeBytes((long) content.length);
        object.setSha256(contentSha);
        object.setScanStatus("CLEAN");
        object.setPreviewStatus(previewStatus(safeContentType));
        object.setStatus("ACTIVE");
        object.setUploadedByAccountId(context.accountId());
        object.setVersion(0);
        objectService.insert(object);

        FileSecurityScan scan = new FileSecurityScan();
        scan.setFileId(object.getId());
        scan.setScanner("TRUSTED_GENERATOR");
        scan.setScanVersion("1");
        scan.setStatus("CLEAN");
        scan.setResultCode("GENERATED_CONTENT");
        scan.setResultDetailJson(toJson(Map.of("size", content.length, "sha256", contentSha,
                "contentType", safeContentType)));
        scan.setStartedAt(LocalDateTime.now());
        scan.setFinishedAt(LocalDateTime.now());
        scanService.insert(scan);

        FileReference reference = new FileReference();
        reference.setFileId(object.getId());
        reference.setContextType(contextType(context));
        reference.setSystemId(context.systemId());
        reference.setTenantId(context.tenantId());
        reference.setOwnerType("ACCOUNT");
        reference.setOwnerId(String.valueOf(context.accountId()));
        reference.setFieldCode(stripToNull(fieldCode));
        reference.setReferenceType("RESULT");
        reference.setCreatedByAccountId(context.accountId());
        referenceService.insert(reference);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FILE_GENERATED", "FILE", object.getId().toString(), "SUCCESS",
                Map.of("originalName", safeName, "size", content.length, "sha256", contentSha,
                        "referenceId", reference.getId()));
        return view(objectService.selectById(object.getId()));
    }

    @Transactional
    public FileModels.BinaryContent downloadGenerated(
            AuthenticatedContext context, Long fileId, String traceId) {
        FileObject file = objectService.selectById(fileId);
        if (file == null || !inContext(file, context) || !Objects.equals(file.getUploadedByAccountId(), context.accountId())
                || file.getUploadSessionId() != null || "DELETED".equals(file.getStatus())) {
            throw notFound("GENERATED_FILE_NOT_FOUND", "生成文件不存在");
        }
        FileModels.BinaryContent content = binary(file, false);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FILE_DOWNLOADED", "FILE", fileId.toString(), "SUCCESS",
                Map.of("originalName", file.getOriginalName(), "size", file.getSizeBytes(),
                        "source", "GENERATED_RESULT"));
        return content;
    }

    @Transactional
    public FileModels.DeleteResult delete(AuthenticatedContext context, Long fileId, String traceId) {
        require(context, "DELETE");
        FileObject file = requireFile(context, fileId, "DELETE", traceId);
        long references = referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                .eq(FileReference::getFileId, fileId)).size();
        if (references > 0) throw conflict("FILE_IN_USE", "文件仍被业务对象引用，必须先解除引用");
        file.setStatus("DELETED");
        file.setDeletedAt(LocalDateTime.now());
        if (objectService.updateById(file) != 1) {
            throw conflict("FILE_VERSION_CONFLICT", "文件状态已变化，请刷新后重试");
        }
        Path path = objectPath(file.getObjectKey());
        deleteAfterCommit(path);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FILE_DELETED", "FILE", fileId.toString(), "SUCCESS", Map.of("referenceCount", 0));
        return new FileModels.DeleteResult(fileId, "DELETED", true);
    }

    private FileStorageBackend backend(AuthenticatedContext context) {
        FileStorageBackend existing = backendService.selectList(Wrappers.<FileStorageBackend>lambdaQuery()
                        .eq(FileStorageBackend::getPlatformId, context.platformId())
                        .eq(FileStorageBackend::getCode, "LOCAL_DEFAULT").eq(FileStorageBackend::getStatus, "ACTIVE"))
                .stream().filter(item -> Objects.equals(item.getContextType(), contextType(context))
                        && Objects.equals(item.getSystemId(), context.systemId())).findFirst().orElse(null);
        if (existing != null) return existing;
        FileStorageBackend backend = new FileStorageBackend();
        backend.setContextType(contextType(context));
        backend.setPlatformId(context.platformId());
        backend.setSystemId(context.systemId());
        backend.setCode("LOCAL_DEFAULT");
        backend.setName(context.systemId() == null ? "平台本地文件存储" : "系统本地文件存储");
        backend.setBackendType("LOCAL");
        backend.setBucketName("unexamine-files");
        backend.setConfigJson(toJson(Map.of("managedRoot", true)));
        backend.setStatus("ACTIVE");
        backend.setVersion(0);
        backendService.insert(backend);
        return backend;
    }

    private FileModels.BinaryContent binary(FileObject file, boolean inline) {
        if (!"ACTIVE".equals(file.getStatus()) || !"CLEAN".equals(file.getScanStatus())) {
            throw forbidden("FILE_CONTENT_UNAVAILABLE", "文件未通过安全扫描或已经删除");
        }
        try {
            byte[] bytes = Files.readAllBytes(objectPath(file.getObjectKey()));
            if (!sha256(bytes).equals(file.getSha256())) {
                throw new DomainException("FILE_INTEGRITY_MISMATCH", "文件完整性校验失败", HttpStatus.CONFLICT);
            }
            return new FileModels.BinaryContent(bytes, file.getContentType(), file.getOriginalName(), inline);
        } catch (IOException exception) {
            throw new DomainException("FILE_CONTENT_MISSING", "文件内容不存在或暂时不可读", HttpStatus.NOT_FOUND);
        }
    }

    private FileObject requireFile(
            AuthenticatedContext context, Long fileId, String action, String traceId) {
        require(context, action);
        FileObject file = objectService.selectById(fileId);
        if (file == null || !inContext(file, context) || "DELETED".equals(file.getStatus())) {
            throw notFound("FILE_NOT_FOUND", "文件不存在");
        }
        if (!canAccess(context, file, action, traceId)) {
            throw forbidden("FILE_ACCESS_DENIED", "当前账号不能访问该文件或其业务引用");
        }
        return file;
    }

    private boolean canAccess(AuthenticatedContext context, FileObject file, String action, String traceId) {
        if (Objects.equals(file.getUploadedByAccountId(), context.accountId())
                || permissionChecker.allows(context, "FILE", "OBJECT", "MANAGE")
                || permissionChecker.allows(context, "FILE", "*", "MANAGE")) return true;
        if (Set.of("DELETE", "REFERENCE").contains(action)) return false;
        return referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                        .eq(FileReference::getFileId, file.getId()))
                .stream().filter(reference -> referenceInContext(reference, context))
                .anyMatch(reference -> targetAccessible(context, reference, traceId));
    }

    private boolean targetAccessible(AuthenticatedContext context, FileReference reference, String traceId) {
        try {
            validateReferenceTarget(context, reference.getOwnerType(), reference.getOwnerId(), traceId);
            return true;
        } catch (DomainException exception) {
            return false;
        }
    }

    private void validateReferenceTarget(
            AuthenticatedContext context, String ownerType, String ownerId, String traceId) {
        if (!REFERENCE_TYPES.contains(ownerType)) throw invalid("FILE_REFERENCE_TARGET_INVALID", "文件引用目标类型无效");
        switch (ownerType) {
            case "ACCOUNT" -> {
                if (!ownerId.equals(String.valueOf(context.accountId()))) {
                    throw forbidden("FILE_REFERENCE_TARGET_DENIED", "只能把文件引用到当前账号资料");
                }
            }
            case "FLOW_INSTANCE" -> {
                Long id = numeric(ownerId);
                FlowInstance flow = flowInstanceService.selectById(id);
                if (flow == null || !sameContext(flow.getContextType(), flow.getPlatformId(), flow.getSystemId(),
                        flow.getTenantId(), context)) throw notFound("FILE_REFERENCE_TARGET_NOT_FOUND", "Flow 实例不存在");
                requireTarget(context, "FLOW", context.systemId() == null ? "PLATFORM" : "SYSTEM", "VIEW");
            }
            case "BUSINESS_RECORD" -> {
                if (context.systemId() == null || !ownerId.matches("[a-z][a-z0-9_-]{0,99}:[1-9][0-9]*")) {
                    throw forbidden("FILE_REFERENCE_TARGET_DENIED", "业务记录引用必须属于当前系统");
                }
                String[] parts = ownerId.split(":", 2);
                runtimeDataService.detail(context, parts[0], Long.valueOf(parts[1]), traceId);
            }
            case "WORK_TASK" -> {
                Long id = numeric(ownerId);
                WorkTask task = workTaskService.selectById(id);
                if (task == null || !sameContext(task.getContextType(), task.getPlatformId(), task.getSystemId(),
                        task.getTenantId(), context)) throw notFound("FILE_REFERENCE_TARGET_NOT_FOUND", "工作任务不存在");
                requireTarget(context, "WORK", "TASK", "VIEW");
            }
            default -> throw invalid("FILE_REFERENCE_TARGET_INVALID", "文件引用目标类型无效");
        }
    }

    private void requireTarget(AuthenticatedContext context, String type, String code, String action) {
        if (!permissionChecker.allows(context, type, code, action)
                && !permissionChecker.allows(context, type, "*", action)) {
            throw forbidden("FILE_REFERENCE_TARGET_DENIED", "当前账号不能访问文件引用目标");
        }
    }

    private FileModels.FileView view(FileObject file) {
        List<FileModels.ReferenceView> references = referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                        .eq(FileReference::getFileId, file.getId()).orderByAsc(FileReference::getId))
                .stream().map(this::referenceView).toList();
        List<FileModels.ScanView> scans = scanService.selectList(Wrappers.<FileSecurityScan>lambdaQuery()
                        .eq(FileSecurityScan::getFileId, file.getId()).orderByDesc(FileSecurityScan::getCreatedAt))
                .stream().map(this::scanView).toList();
        return new FileModels.FileView(file.getId(), file.getContextType(), file.getPlatformId(), file.getSystemId(),
                file.getTenantId(), file.getUploadSessionId(), file.getOriginalName(), file.getContentType(),
                file.getSizeBytes(), file.getSha256(), file.getScanStatus(), file.getPreviewStatus(), file.getStatus(),
                file.getUploadedByAccountId(), file.getCreatedAt(), file.getDeletedAt(), file.getVersion(), references, scans);
    }

    private FileModels.ReferenceView referenceView(FileReference item) {
        return new FileModels.ReferenceView(item.getId(), item.getContextType(), item.getSystemId(), item.getTenantId(),
                item.getOwnerType(), item.getOwnerId(), item.getFieldCode(), item.getReferenceType(),
                item.getCreatedByAccountId(), item.getCreatedAt());
    }

    private FileModels.ScanView scanView(FileSecurityScan item) {
        return new FileModels.ScanView(item.getId(), item.getScanner(), item.getScanVersion(), item.getStatus(),
                item.getResultCode(), item.getResultDetailJson(), item.getStartedAt(), item.getFinishedAt());
    }

    private FileModels.UploadSessionView sessionView(FileUploadSession session, String token) {
        return new FileModels.UploadSessionView(session.getId(), session.getContextType(), session.getPlatformId(),
                session.getSystemId(), session.getTenantId(), session.getOriginalName(), session.getContentType(),
                session.getExpectedSize(), session.getExpectedSha256(), session.getStatus(), token, true,
                session.getExpiresAt(), session.getVersion());
    }

    private void failUpload(
            AuthenticatedContext context, FileUploadSession session, String traceId,
            String status, String code, Map<String, ?> detail) {
        failureRecorder.fail(session.getId(), status);
        auditRecorder.recordFailure(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "FILE_UPLOAD_FAILED", "FILE_UPLOAD_SESSION", session.getId().toString(), code, detail);
        throw invalid(code, "文件大小或校验摘要与上传会话不一致，请重新创建上传会话");
    }

    private boolean blocked(String fileName, String contentType, byte[] content) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".exe") || lower.endsWith(".bat") || lower.endsWith(".cmd")
                || contentType.equals("application/x-msdownload")) return true;
        String signature = new String(content, StandardCharsets.ISO_8859_1);
        return signature.contains("EICAR-STANDARD-ANTIVIRUS-TEST-FILE");
    }

    private String previewStatus(String contentType) {
        return contentType.startsWith("text/") || contentType.startsWith("image/")
                || contentType.equals("application/pdf") ? "AVAILABLE" : "UNSUPPORTED";
    }

    private Path objectPath(String objectKey) {
        Path resolved = storageRoot.resolve(objectKey).normalize();
        if (!resolved.startsWith(storageRoot)) throw new IllegalStateException("Unsafe managed object key");
        return resolved;
    }

    private void deleteOnRollback(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) deleteQuietly(path);
            }
        });
    }

    private void deleteAfterCommit(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(path);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteQuietly(path);
            }
        });
    }

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { /* Metadata keeps the deletion audit even if physical cleanup needs operations. */ }
    }

    private boolean inContext(FileUploadSession item, AuthenticatedContext context) {
        return sameContext(item.getContextType(), item.getPlatformId(), item.getSystemId(), item.getTenantId(), context);
    }

    private boolean inContext(FileObject item, AuthenticatedContext context) {
        return sameContext(item.getContextType(), item.getPlatformId(), item.getSystemId(), item.getTenantId(), context);
    }

    private boolean referenceInContext(FileReference item, AuthenticatedContext context) {
        return Objects.equals(item.getContextType(), contextType(context))
                && Objects.equals(item.getSystemId(), context.systemId())
                && Objects.equals(item.getTenantId(), context.tenantId());
    }

    private boolean sameContext(
            String type, Long platformId, Long systemId, Long tenantId, AuthenticatedContext context) {
        return Objects.equals(type, contextType(context)) && Objects.equals(platformId, context.platformId())
                && Objects.equals(systemId, context.systemId()) && Objects.equals(tenantId, context.tenantId());
    }

    private String contextType(AuthenticatedContext context) {
        return context.systemId() == null ? "PLATFORM" : "SYSTEM";
    }

    private String contextKey(AuthenticatedContext context) {
        return context.systemId() == null ? "platform/" + context.platformId()
                : "system/" + context.systemId() + "/tenant/" + context.tenantId();
    }

    private String sanitizeName(String value) {
        String result = Path.of(value).getFileName().toString().strip();
        if (result.isBlank() || ".".equals(result) || "..".equals(result)) {
            throw invalid("FILE_NAME_INVALID", "文件名无效");
        }
        return result;
    }

    private String randomToken() {
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        return HexFormat.of().formatHex(value);
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private boolean secureEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private Long numeric(String value) {
        if (value == null || !value.matches("[1-9][0-9]*")) throw invalid("FILE_REFERENCE_TARGET_INVALID", "文件引用目标格式无效");
        return Long.valueOf(value);
    }

    private String stripToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot serialize file metadata", exception); }
    }

    private void require(AuthenticatedContext context, String action) {
        if (!permissionChecker.allows(context, "FILE", "OBJECT", action)
                && !permissionChecker.allows(context, "FILE", "*", action)) {
            throw forbidden("FILE_PERMISSION_DENIED", "当前上下文没有文件" + action + "权限");
        }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }

    private DomainException notFound(String code, String message) {
        return new DomainException(code, message, HttpStatus.NOT_FOUND);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }
}
