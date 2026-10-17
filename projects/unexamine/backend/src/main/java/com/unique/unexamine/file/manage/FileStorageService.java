package com.unique.unexamine.file.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.unique.unexamine.foundation.base.entity.CoreQuotaPolicy;
import com.unique.unexamine.foundation.base.service.CoreQuotaPolicyBaseService;
import com.unique.unexamine.foundation.manage.event.LocalBusinessEvent;
import com.unique.unexamine.foundation.manage.event.LocalBusinessEventPublisher;
import com.unique.unexamine.flow.manage.FlowRuntimeService;
import com.unique.unexamine.flow.base.entity.FlowHistoryEvent;
import com.unique.unexamine.flow.base.service.FlowHistoryEventBaseService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.platform.base.entity.PlatformDefinition;
import com.unique.unexamine.platform.base.service.PlatformDefinitionBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.work.manage.WorkManagementService;
import com.unique.unexamine.work.manage.WorkLogService;
import com.unique.unexamine.work.base.entity.WorkTaskHistory;
import com.unique.unexamine.work.base.service.WorkTaskHistoryBaseService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {
    private static final Set<String> REFERENCE_TYPES = Set.of(
            "ACCOUNT", "FLOW_INSTANCE", "BUSINESS_RECORD", "WORK_TASK", "WORK_LOG");
    private static final int MAXIMUM_CHUNK_SIZE = 5 * 1024 * 1024;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final FileStorageBackendBaseService backendService;
    private final FileUploadSessionBaseService sessionService;
    private final CoreQuotaPolicyBaseService quotaPolicyService;
    private final PlatformDefinitionBaseService platformService;
    private final SystemTenantBaseService tenantService;
    private final FileObjectBaseService objectService;
    private final FileReferenceBaseService referenceService;
    private final FileSecurityScanBaseService scanService;
    private final FlowRuntimeService flowRuntimeService;
    private final WorkManagementService workManagementService;
    private final WorkLogService workLogService;
    private final FlowHistoryEventBaseService flowHistoryService;
    private final WorkTaskHistoryBaseService workTaskHistoryService;
    private final RuntimeDataService runtimeDataService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final FileUploadFailureRecorder failureRecorder;
    private final LocalBusinessEventPublisher localEvents;
    private final ObjectMapper objectMapper;
    private final ManagedObjectStore objectStore;
    private final Path storageRoot;
    private final long maximumSize;
    private final long contextQuota;

    public FileStorageService(
            FileStorageBackendBaseService backendService,
            FileUploadSessionBaseService sessionService,
            CoreQuotaPolicyBaseService quotaPolicyService,
            PlatformDefinitionBaseService platformService,
            SystemTenantBaseService tenantService,
            FileObjectBaseService objectService,
            FileReferenceBaseService referenceService,
            FileSecurityScanBaseService scanService,
            FlowRuntimeService flowRuntimeService,
            WorkManagementService workManagementService,
            WorkLogService workLogService,
            FlowHistoryEventBaseService flowHistoryService,
            WorkTaskHistoryBaseService workTaskHistoryService,
            RuntimeDataService runtimeDataService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            FileUploadFailureRecorder failureRecorder,
            LocalBusinessEventPublisher localEvents,
            ObjectMapper objectMapper,
            ManagedObjectStore objectStore,
            @Value("${app.file.storage-root:./data/files}") String storageRoot,
            @Value("${app.file.max-size-bytes:52428800}") long maximumSize,
            @Value("${app.file.context-quota-bytes:5368709120}") long contextQuota) {
        this.backendService = backendService;
        this.sessionService = sessionService;
        this.quotaPolicyService = quotaPolicyService;
        this.platformService = platformService;
        this.tenantService = tenantService;
        this.objectService = objectService;
        this.referenceService = referenceService;
        this.scanService = scanService;
        this.flowRuntimeService = flowRuntimeService;
        this.workManagementService = workManagementService;
        this.workLogService = workLogService;
        this.flowHistoryService = flowHistoryService;
        this.workTaskHistoryService = workTaskHistoryService;
        this.runtimeDataService = runtimeDataService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.failureRecorder = failureRecorder;
        this.localEvents = localEvents;
        this.objectMapper = objectMapper;
        this.objectStore = objectStore;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
        this.maximumSize = maximumSize;
        this.contextQuota = contextQuota;
    }

    @Transactional
    public FileModels.UploadSessionView start(
            AuthenticatedContext context, FileModels.StartUploadRequest input, String traceId) {
        require(context, "UPLOAD");
        if (input.expectedSize() > maximumSize) {
            throw invalid("FILE_SIZE_LIMIT_EXCEEDED", "文件超过当前部署允许的单文件大小");
        }
        cleanupExpiredUploads(context);
        requireQuota(context, input.expectedSize());
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
        session.setExpireTime(LocalDateTime.now().plusMinutes(30));
        session.setVersion(0);
        sessionService.insert(session);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FILE_UPLOAD_STARTED", "FILE_UPLOAD_SESSION", session.getId().toString(), "SUCCESS",
                Map.of("originalName", originalName, "expectedSize", input.expectedSize(),
                        "contentType", session.getContentType()));
        return sessionView(session, token);
    }

    @Transactional
    public FileModels.StoragePolicyView policy(AuthenticatedContext context, String traceId) {
        require(context, "VIEW");
        cleanupExpiredUploads(context);
        long used = usedBytes(context);
        long reserved = reservedBytes(context);
        FileStorageBackend active = backend(context);
        long limit = quotaLimit(context, false);
        return new FileModels.StoragePolicyView(maximumSize, limit, used, reserved,
                Math.max(0L, limit - used - reserved), active.getBackendType(), active.getName());
    }

    @Transactional(readOnly = true)
    public List<FileModels.StorageBackendView> storageBackends(AuthenticatedContext context, String traceId) {
        require(context, "MANAGE");
        return contextBackends(context).stream()
                .sorted(Comparator.comparing(FileStorageBackend::getBackendType)
                        .thenComparing(FileStorageBackend::getCode))
                .map(this::storageView).toList();
    }

    /** 保存对象存储时可同时做真实探测并启用；密钥只接收一次，后续留空即可沿用。 */
    @Transactional
    public FileModels.StorageBackendView configureObjectStorage(
            AuthenticatedContext context, FileModels.ConfigureObjectStorageRequest input, String traceId) {
        require(context, "MANAGE");
        URI endpoint;
        try {
            endpoint = URI.create(input.endpoint().strip());
        } catch (IllegalArgumentException exception) {
            throw invalid("OBJECT_STORAGE_ENDPOINT_INVALID", "对象存储地址格式无效");
        }
        if (!Set.of("http", "https").contains(endpoint.getScheme()) || endpoint.getHost() == null
                || endpoint.getUserInfo() != null || endpoint.getQuery() != null || endpoint.getFragment() != null) {
            throw invalid("OBJECT_STORAGE_ENDPOINT_INVALID", "对象存储地址必须是完整的 HTTP 或 HTTPS 服务地址");
        }
        FileStorageBackend storage = contextBackends(context).stream()
                .filter(item -> "OBJECT_DEFAULT".equals(item.getCode())).findFirst().orElse(null);
        boolean created = storage == null;
        if (created) {
            storage = new FileStorageBackend();
            storage.setContextType(contextType(context));
            storage.setPlatformId(context.platformId());
            storage.setSystemId(context.systemId());
            storage.setScopeOwnerId(context.systemId() == null ? context.platformId() : context.systemId());
            storage.setCode("OBJECT_DEFAULT");
            storage.setBackendType("S3");
            storage.setVersion(0);
        }
        String accessKeyId = stripToNull(input.accessKeyId());
        String secretAccessKey = stripToNull(input.secretAccessKey());
        if ((accessKeyId == null) != (secretAccessKey == null)) {
            throw invalid("OBJECT_STORAGE_CREDENTIAL_INCOMPLETE", "访问密钥 ID 和访问密钥必须同时填写");
        }
        if (storage.getCredentialRefId() == null && accessKeyId == null) {
            throw invalid("OBJECT_STORAGE_CREDENTIAL_REQUIRED", "首次配置对象存储必须填写访问密钥");
        }
        storage.setName(input.name().strip());
        storage.setEndpoint(endpoint.toString().replaceAll("/$", ""));
        storage.setBucketName(input.bucketName().strip());
        storage.setConfigJson(toJson(Map.of("region", input.region().strip(), "pathStyle", input.pathStyle())));
        storage.setStatus("INACTIVE");
        if (accessKeyId != null) {
            storage.setCredentialRefId(objectStore.saveCredentials(storage,
                    toJson(Map.of("accessKeyId", accessKeyId, "secretAccessKey", secretAccessKey)),
                    context.accountId()));
        }
        if (created) backendService.insert(storage);
        else if (backendService.updateById(storage) != 1) {
            throw conflict("OBJECT_STORAGE_VERSION_CONFLICT", "对象存储配置已经变化，请刷新后重试");
        }
        if (input.activate()) {
            objectStore.probe(storage);
            storage.setStatus("ACTIVE");
            if (backendService.updateById(storage) != 1) {
                throw conflict("OBJECT_STORAGE_VERSION_CONFLICT", "对象存储配置已经变化，请刷新后重试");
            }
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                input.activate() ? "OBJECT_STORAGE_ACTIVATED" : "OBJECT_STORAGE_CONFIGURED",
                "FILE_STORAGE_BACKEND", storage.getId().toString(), "SUCCESS",
                Map.of("endpoint", storage.getEndpoint(), "bucketName", storage.getBucketName(),
                        "region", input.region().strip(), "credentialRotated", accessKeyId != null));
        return storageView(backendService.selectById(storage.getId()));
    }

    @Transactional
    public FileModels.StorageBackendView deactivateObjectStorage(AuthenticatedContext context, String traceId) {
        require(context, "MANAGE");
        FileStorageBackend storage = contextBackends(context).stream()
                .filter(item -> "OBJECT_DEFAULT".equals(item.getCode())).findFirst()
                .orElseThrow(() -> notFound("OBJECT_STORAGE_NOT_CONFIGURED", "当前空间尚未配置对象存储"));
        storage.setStatus("INACTIVE");
        if (backendService.updateById(storage) != 1) {
            throw conflict("OBJECT_STORAGE_VERSION_CONFLICT", "对象存储配置已经变化，请刷新后重试");
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "OBJECT_STORAGE_DEACTIVATED", "FILE_STORAGE_BACKEND", storage.getId().toString(), "SUCCESS", Map.of());
        return storageView(backendService.selectById(storage.getId()));
    }

    /** 查询服务端已经持久化的字节数，客户端据此从断点继续上传。 */
    @Transactional(readOnly = true)
    public FileModels.UploadSessionView uploadStatus(
            AuthenticatedContext context, Long sessionId, String rawToken, String traceId) {
        require(context, "UPLOAD");
        FileUploadSession session = requireUploadSession(context, sessionId, rawToken);
        return sessionView(session, null);
    }

    @Transactional
    public FileModels.UploadSessionView cancelUpload(
            AuthenticatedContext context, Long sessionId, String rawToken, String traceId) {
        require(context, "UPLOAD");
        FileUploadSession session = requireUploadSession(context, sessionId, rawToken);
        if ("COMPLETED".equals(session.getStatus())) {
            throw conflict("FILE_UPLOAD_ALREADY_COMPLETED", "上传已经完成，不能取消");
        }
        if (!Set.of("CANCELLED", "EXPIRED", "FAILED").contains(session.getStatus())) {
            session.setStatus("CANCELLED");
            session.setUpdateTime(LocalDateTime.now());
            sessionService.updateById(session);
        }
        deleteAfterCommit(partPath(session));
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FILE_UPLOAD_CANCELLED", "FILE_UPLOAD_SESSION", sessionId.toString(), "SUCCESS", Map.of());
        return sessionView(session, null);
    }

    /**
     * 按确定偏移写入分片。相同偏移、相同内容的重试视为成功；内容不同则拒绝，防止静默损坏。
     */
    @Transactional
    public FileModels.UploadChunkView uploadChunk(
            AuthenticatedContext context, Long sessionId, String rawToken, Long offset,
            String chunkSha256, byte[] content, String traceId) {
        require(context, "UPLOAD");
        FileUploadSession session = requireUploadSession(context, sessionId, rawToken);
        requirePending(session);
        if (offset == null || offset < 0) throw invalid("FILE_CHUNK_OFFSET_INVALID", "分片偏移必须从 0 开始");
        if (content.length == 0 || content.length > MAXIMUM_CHUNK_SIZE) {
            throw invalid("FILE_CHUNK_SIZE_INVALID", "单个分片必须大于 0 且不超过 5 MB");
        }
        if (chunkSha256 == null || !chunkSha256.matches("[a-fA-F0-9]{64}")
                || !secureEquals(chunkSha256.toLowerCase(Locale.ROOT), sha256(content))) {
            throw invalid("FILE_CHUNK_CHECKSUM_MISMATCH", "分片校验摘要不一致，请重传当前分片");
        }
        if (offset + content.length > session.getExpectedSize()) {
            throw invalid("FILE_CHUNK_RANGE_INVALID", "分片超过上传会话声明的文件大小");
        }
        Path partPath = partPath(session);
        try {
            Files.createDirectories(partPath.getParent());
            try (FileChannel channel = FileChannel.open(partPath, StandardOpenOption.CREATE,
                    StandardOpenOption.READ, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) {
                long current = channel.size();
                if (offset > current) {
                    throw conflict("FILE_CHUNK_OFFSET_CONFLICT", "上传偏移不连续，服务端续传位置为 " + current);
                }
                if (offset < current) {
                    if (offset + content.length > current || !sameBytes(channel, offset, content)) {
                        throw conflict("FILE_CHUNK_REPLAY_MISMATCH", "该偏移已经写入不同内容，请重新创建上传会话");
                    }
                    return new FileModels.UploadChunkView(sessionId, current, session.getExpectedSize(),
                            session.getStatus(), true);
                }
                channel.position(offset);
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
                long uploaded = channel.size();
                auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                        "FILE_UPLOAD_CHUNK_SAVED", "FILE_UPLOAD_SESSION", sessionId.toString(), "SUCCESS",
                        Map.of("offset", offset, "chunkSize", content.length, "uploadedSize", uploaded));
                return new FileModels.UploadChunkView(sessionId, uploaded, session.getExpectedSize(),
                        session.getStatus(), false);
            }
        } catch (DomainException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DomainException("FILE_STORAGE_WRITE_FAILED", "上传分片写入存储失败", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /** 完成操作可以安全重试；只有字节数和整文件摘要均通过后才生成可引用文件。 */
    @Transactional
    public FileModels.FileView completeUpload(
            AuthenticatedContext context, Long sessionId, String rawToken, String traceId) {
        require(context, "UPLOAD");
        FileUploadSession session = requireUploadSession(context, sessionId, rawToken);
        FileModels.FileView completed = completedFile(session);
        if (completed != null) return completed;
        requirePending(session);
        Path partPath = partPath(session);
        try {
            long uploaded = Files.exists(partPath) ? Files.size(partPath) : 0L;
            if (uploaded != session.getExpectedSize()) {
                throw conflict("FILE_UPLOAD_INCOMPLETE", "文件尚未上传完成，服务端续传位置为 " + uploaded);
            }
            byte[] content = Files.readAllBytes(partPath);
            FileModels.FileView result = upload(context, sessionId, rawToken, content, traceId);
            deleteAfterCommit(partPath);
            return result;
        } catch (DomainException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new DomainException("FILE_CONTENT_MISSING", "上传分片不存在或暂时不可读", HttpStatus.NOT_FOUND);
        }
    }

    @Transactional
    public FileModels.FileView upload(
            AuthenticatedContext context, Long sessionId, String rawToken, byte[] content, String traceId) {
        require(context, "UPLOAD");
        FileUploadSession session = requireUploadSession(context, sessionId, rawToken);
        FileModels.FileView completed = completedFile(session);
        if (completed != null) return completed;
        requirePending(session);
        if (content.length != session.getExpectedSize()) {
            failUpload(context, session, traceId, "FAILED", "FILE_SIZE_MISMATCH",
                    Map.of("expectedSize", session.getExpectedSize(), "actualSize", content.length));
        }
        String contentSha = sha256(content);
        if (session.getExpectedSha256() != null && !secureEquals(session.getExpectedSha256(), contentSha)) {
            failUpload(context, session, traceId, "FAILED", "FILE_CHECKSUM_MISMATCH",
                    Map.of("expectedSha256", session.getExpectedSha256(), "actualSha256", contentSha));
        }
        FileStorageBackend storage = requireBackend(session.getStorageBackendId());
        try {
            objectStore.put(storage, session.getObjectKey(), content, session.getContentType());
        } catch (DomainException exception) {
            failureRecorder.fail(sessionId, "FAILED");
            deleteQuietly(partPath(session));
            throw exception;
        }
        deleteOnRollback(storage, session.getObjectKey());
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
        scan.setStartTime(LocalDateTime.now());
        scan.setFinishTime(LocalDateTime.now());
        scanService.insert(scan);

        object.setScanStatus(blocked ? "BLOCKED" : "CLEAN");
        object.setPreviewStatus(blocked ? "BLOCKED" : previewStatus(session.getContentType()));
        object.setStatus(blocked ? "QUARANTINED" : "ACTIVE");
        objectService.updateById(object);
        session.setStatus("COMPLETED");
        session.setCompleteTime(LocalDateTime.now());
        session.setUpdateTime(LocalDateTime.now());
        sessionService.updateById(session);
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                blocked ? "FILE_UPLOAD_QUARANTINED" : "FILE_UPLOAD_COMPLETED", "FILE", object.getId().toString(),
                blocked ? "UNSAFE_CONTENT_SIGNATURE" : "SUCCESS",
                Map.of("size", content.length, "sha256", contentSha, "scanStatus", object.getScanStatus()));
        // 仅安全扫描通过的上传进入可用文件扩展点；隔离文件不向后续自动化暴露。
        if (!blocked) {
            localEvents.publish(context, object.getTenantId(), LocalBusinessEvent.Type.FILE_UPLOADED,
                    "FILE_OBJECT", object.getId().toString(), null);
        }
        return view(objectService.selectById(object.getId()));
    }

    @Transactional(readOnly = true)
    public List<FileModels.FileView> list(AuthenticatedContext context, String traceId) {
        require(context, "VIEW");
        return objectService.selectList(Wrappers.<FileObject>lambdaQuery()
                        .eq(FileObject::getPlatformId, context.platformId())
                        .ne(FileObject::getStatus, "DELETED")
                        .orderByDesc(FileObject::getCreateTime))
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
            reference.setPlatformId(context.platformId());
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

    @Transactional(readOnly = true)
    public List<FileModels.BusinessAttachmentView> listBusinessAttachments(
            AuthenticatedContext context, String ownerType, String ownerId, String fieldCode, String traceId) {
        require(context, "VIEW");
        validateReferenceTarget(context, ownerType, ownerId, traceId);
        validateBusinessAttachmentField(context, ownerType, ownerId, fieldCode, false, traceId);
        String normalizedField = stripToNull(fieldCode);
        return referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                        .eq(FileReference::getOwnerType, ownerType).eq(FileReference::getOwnerId, ownerId)
                        .orderByDesc(FileReference::getCreateTime))
                .stream().filter(reference -> readableReferenceInContext(reference, context))
                .filter(reference -> Objects.equals(reference.getFieldCode(), normalizedField))
                .map(reference -> Map.entry(reference, objectService.selectById(reference.getFileId())))
                .filter(entry -> entry.getValue() != null && fileMatchesReference(entry.getValue(), entry.getKey())
                        && !"DELETED".equals(entry.getValue().getStatus()))
                .map(entry -> businessAttachmentView(entry.getValue(), entry.getKey(), context, traceId))
                .toList();
    }

    @Transactional
    public FileModels.BusinessAttachmentView uploadBusinessAttachment(
            AuthenticatedContext context, byte[] content, String originalName, String contentType,
            String ownerType, String ownerId, String fieldCode, String purpose, String traceId) {
        require(context, "UPLOAD");
        require(context, "REFERENCE");
        validateReferenceTarget(context, ownerType, ownerId, traceId);
        validateBusinessAttachmentField(context, ownerType, ownerId, fieldCode, true, traceId);
        if (content == null || content.length == 0 || content.length > maximumSize) {
            throw invalid("FILE_SIZE_INVALID", "附件为空或超过当前部署允许的单文件大小");
        }
        cleanupExpiredUploads(context);
        requireQuota(context, content.length);
        String safeName = sanitizeName(originalName);
        String safeContentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType.strip().toLowerCase(Locale.ROOT);
        String safePurpose = purpose == null ? "ATTACHMENT" : purpose.strip().toUpperCase(Locale.ROOT);
        if (!Set.of("ATTACHMENT", "IMAGE", "DOCUMENT", "RESULT").contains(safePurpose)) {
            throw invalid("FILE_REFERENCE_TYPE_INVALID", "附件用途无效");
        }
        FileStorageBackend storage = backend(context);
        String objectKey = contextKey(context) + "/attachments/" + UUID.randomUUID();
        objectStore.put(storage, objectKey, content, safeContentType);
        deleteOnRollback(storage, objectKey);

        String contentSha = sha256(content);
        boolean unsafe = blocked(safeName, safeContentType, content);
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
        object.setScanStatus(unsafe ? "BLOCKED" : "CLEAN");
        object.setPreviewStatus(unsafe ? "BLOCKED" : previewStatus(safeContentType));
        object.setStatus(unsafe ? "QUARANTINED" : "ACTIVE");
        object.setUploadedByAccountId(context.accountId());
        object.setVersion(0);
        objectService.insert(object);

        FileSecurityScan scan = new FileSecurityScan();
        scan.setFileId(object.getId());
        scan.setScanner("BUILTIN_SIGNATURE");
        scan.setScanVersion("1");
        scan.setStatus(unsafe ? "BLOCKED" : "CLEAN");
        scan.setResultCode(unsafe ? "UNSAFE_CONTENT_SIGNATURE" : "CLEAN");
        scan.setResultDetailJson(toJson(Map.of("size", content.length, "contentType", safeContentType,
                "sha256", contentSha)));
        scan.setStartTime(LocalDateTime.now());
        scan.setFinishTime(LocalDateTime.now());
        scanService.insert(scan);

        FileReference reference = null;
        if (!unsafe) {
            reference = new FileReference();
            reference.setPlatformId(context.platformId());
            reference.setFileId(object.getId());
            reference.setContextType(contextType(context));
            reference.setSystemId(context.systemId());
            reference.setTenantId(context.tenantId());
            reference.setOwnerType(ownerType);
            reference.setOwnerId(ownerId);
            reference.setFieldCode(stripToNull(fieldCode));
            reference.setReferenceType(safePurpose);
            reference.setCreatedByAccountId(context.accountId());
            referenceService.insert(reference);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                unsafe ? "BUSINESS_ATTACHMENT_QUARANTINED" : "BUSINESS_ATTACHMENT_ADDED",
                "FILE", object.getId().toString(), unsafe ? "UNSAFE_CONTENT_SIGNATURE" : "SUCCESS",
                Map.of("originalName", safeName, "size", content.length, "sha256", contentSha,
                        "businessTargetType", ownerType, "purpose", safePurpose));
        if (!unsafe) recordBusinessAttachmentTimeline(context, traceId, "BUSINESS_RECORD_ATTACHMENT_ADDED",
                ownerType, ownerId, fieldCode, object, safePurpose);
        return businessAttachmentView(object, reference, context, traceId);
    }

    /** 把已完成的受控上传绑定到业务对象，分片上传与业务权限在这里汇合。 */
    @Transactional
    public FileModels.BusinessAttachmentView attachBusinessFile(
            AuthenticatedContext context, Long fileId, String ownerType, String ownerId,
            String fieldCode, String purpose, String traceId) {
        require(context, "REFERENCE");
        validateReferenceTarget(context, ownerType, ownerId, traceId);
        validateBusinessAttachmentField(context, ownerType, ownerId, fieldCode, true, traceId);
        FileObject file = requireFile(context, fileId, "REFERENCE", traceId);
        if (!"ACTIVE".equals(file.getStatus()) || !"CLEAN".equals(file.getScanStatus())) {
            throw conflict("FILE_NOT_PUBLISHABLE", "文件未通过安全扫描，不能加入业务附件");
        }
        String safePurpose = purpose == null ? "ATTACHMENT" : purpose.strip().toUpperCase(Locale.ROOT);
        if (!Set.of("ATTACHMENT", "IMAGE", "DOCUMENT", "RESULT").contains(safePurpose)) {
            throw invalid("FILE_REFERENCE_TYPE_INVALID", "附件用途无效");
        }
        String normalizedField = stripToNull(fieldCode);
        FileReference reference = referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                        .eq(FileReference::getFileId, fileId).eq(FileReference::getOwnerType, ownerType)
                        .eq(FileReference::getOwnerId, ownerId))
                .stream().filter(item -> Objects.equals(item.getFieldCode(), normalizedField))
                .findFirst().orElse(null);
        if (reference == null) {
            reference = new FileReference();
            reference.setPlatformId(context.platformId());
            reference.setFileId(fileId);
            reference.setContextType(contextType(context));
            reference.setSystemId(context.systemId());
            reference.setTenantId(context.tenantId());
            reference.setOwnerType(ownerType);
            reference.setOwnerId(ownerId);
            reference.setFieldCode(normalizedField);
            reference.setReferenceType(safePurpose);
            reference.setCreatedByAccountId(context.accountId());
            referenceService.insert(reference);
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    "BUSINESS_ATTACHMENT_ADDED", "FILE", fileId.toString(), "SUCCESS",
                    Map.of("attachmentId", reference.getId(), "businessTargetType", ownerType,
                            "businessTargetId", ownerId, "purpose", safePurpose));
            recordBusinessAttachmentTimeline(context, traceId, "BUSINESS_RECORD_ATTACHMENT_ADDED",
                    ownerType, ownerId, fieldCode, file, safePurpose);
        }
        return businessAttachmentView(file, reference, context, traceId);
    }

    @Transactional(readOnly = true)
    public FileModels.BinaryContent previewBusinessAttachment(
            AuthenticatedContext context, Long attachmentId, String traceId) {
        Attachment attachment = requireBusinessAttachment(context, attachmentId, "VIEW", traceId);
        if (!"AVAILABLE".equals(attachment.file().getPreviewStatus())) {
            throw conflict("FILE_PREVIEW_UNAVAILABLE", "该附件不能在线预览，请在有下载权限时下载");
        }
        return binary(attachment.file(), true);
    }

    @Transactional(readOnly = true)
    public FileModels.BinaryContent downloadBusinessAttachment(
            AuthenticatedContext context, Long attachmentId, String traceId) {
        return binary(requireBusinessAttachment(context, attachmentId, "DOWNLOAD", traceId).file(), false);
    }

    @Transactional
    public FileModels.DeleteResult removeBusinessAttachment(
            AuthenticatedContext context, Long attachmentId, String traceId) {
        require(context, "DELETE");
        FileReference reference = referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                        .eq(FileReference::getId, attachmentId).last("FOR UPDATE"))
                .stream().findFirst().orElseThrow(() -> notFound("BUSINESS_ATTACHMENT_NOT_FOUND", "业务附件不存在"));
        if (!referenceInContext(reference, context)) throw notFound("BUSINESS_ATTACHMENT_NOT_FOUND", "业务附件不存在");
        validateReferenceTarget(context, reference.getOwnerType(), reference.getOwnerId(), traceId);
        validateBusinessAttachmentField(context, reference.getOwnerType(), reference.getOwnerId(),
                reference.getFieldCode(), true, traceId);
        FileObject file = objectService.selectById(reference.getFileId());
        if (file == null || !inContext(file, context)) throw notFound("BUSINESS_ATTACHMENT_NOT_FOUND", "业务附件不存在");
        if (managedRecordField(reference)) {
            throw conflict("FILE_FIELD_MANAGED_BY_RECORD", "该文件是记录字段当前值，请在编辑记录时替换或清空");
        }
        referenceService.deleteById(reference.getId());
        long remaining = referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                .eq(FileReference::getFileId, file.getId())).size();
        boolean bytesRemoved = false;
        if (remaining == 0) {
            file.setStatus("DELETED");
            file.setDeleteTime(LocalDateTime.now());
            objectService.updateById(file);
            deleteAfterCommit(requireBackend(file.getStorageBackendId()), file.getObjectKey());
            bytesRemoved = true;
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "BUSINESS_ATTACHMENT_REMOVED", "FILE", file.getId().toString(), "SUCCESS",
                Map.of("attachmentId", attachmentId, "remainingReferences", remaining));
        recordBusinessAttachmentTimeline(context, traceId, "BUSINESS_RECORD_ATTACHMENT_REMOVED",
                reference.getOwnerType(), reference.getOwnerId(), reference.getFieldCode(), file,
                reference.getReferenceType());
        return new FileModels.DeleteResult(file.getId(), remaining == 0 ? "DELETED" : "ACTIVE", bytesRemoved);
    }

    private Attachment requireBusinessAttachment(
            AuthenticatedContext context, Long attachmentId, String action, String traceId) {
        require(context, action);
        FileReference reference = referenceService.selectById(attachmentId);
        if (reference == null || !readableReferenceInContext(reference, context)) {
            throw notFound("BUSINESS_ATTACHMENT_NOT_FOUND", "业务附件不存在");
        }
        validateReferenceTarget(context, reference.getOwnerType(), reference.getOwnerId(), traceId);
        validateBusinessAttachmentField(context, reference.getOwnerType(), reference.getOwnerId(),
                reference.getFieldCode(), false, traceId);
        FileObject file = objectService.selectById(reference.getFileId());
        if (file == null || !fileMatchesReference(file, reference) || "DELETED".equals(file.getStatus())) {
            throw notFound("BUSINESS_ATTACHMENT_NOT_FOUND", "业务附件不存在");
        }
        return new Attachment(reference, file);
    }

    private FileModels.BusinessAttachmentView businessAttachmentView(
            FileObject file, FileReference reference, AuthenticatedContext context, String traceId) {
        boolean accessible = reference != null && targetAccessible(context, reference, traceId);
        return new FileModels.BusinessAttachmentView(reference == null ? null : reference.getId(), file.getId(),
                file.getOriginalName(), file.getContentType(), file.getSizeBytes(), file.getScanStatus(),
                file.getPreviewStatus(), file.getStatus(), reference == null ? "QUARANTINE" : reference.getReferenceType(),
                file.getCreateTime(), accessible && "AVAILABLE".equals(file.getPreviewStatus()),
                accessible && "CLEAN".equals(file.getScanStatus()),
                accessible && referenceInContext(reference, context) && !managedRecordField(reference));
    }

    private boolean managedRecordField(FileReference reference) {
        if (reference == null || !"BUSINESS_RECORD".equals(reference.getOwnerType())
                || reference.getOwnerId() == null || reference.getFieldCode() == null) return false;
        String[] owner = reference.getOwnerId().split(":", 2);
        if (owner.length != 2 || !owner[1].matches("[1-9][0-9]*")) return false;
        return runtimeDataService.isManagedAttachmentValue(owner[0], Long.valueOf(owner[1]),
                reference.getFieldCode(), reference.getFileId());
    }

    private void recordBusinessAttachmentTimeline(
            AuthenticatedContext context, String traceId, String eventCode,
            String ownerType, String ownerId, String fieldCode, FileObject file, String purpose) {
        Map<String, Object> detail = Map.of("fieldCode", Objects.toString(fieldCode, "attachments"),
                "fileId", file.getId(), "fileName", file.getOriginalName(), "purpose", purpose);
        if ("BUSINESS_RECORD".equals(ownerType)) {
            String[] target = ownerId.split(":", 2);
            LinkedHashMap<String, Object> recordDetail = new LinkedHashMap<>(detail);
            recordDetail.put("moduleCode", target[0]);
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    eventCode, "BUSINESS_RECORD", target[1], "SUCCESS", recordDetail);
            return;
        }
        if ("WORK_TASK".equals(ownerType)) {
            WorkTaskHistory history = new WorkTaskHistory();
            history.setTaskId(Long.valueOf(ownerId));
            history.setActionCode(eventCode.endsWith("ADDED") ? "ATTACHMENT_ADDED" : "ATTACHMENT_REMOVED");
            history.setBeforeJson(null);
            history.setAfterJson(toJson(detail));
            history.setCommentText(file.getOriginalName());
            history.setChangedByAccountId(context.accountId());
            history.setChangeTime(LocalDateTime.now());
            workTaskHistoryService.insert(history);
            return;
        }
        if ("WORK_LOG".equals(ownerType)) {
            auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                    eventCode, "WORK_LOG", ownerId, "SUCCESS", detail);
            return;
        }
        if ("FLOW_INSTANCE".equals(ownerType)) {
            FlowHistoryEvent history = new FlowHistoryEvent();
            history.setInstanceId(Long.valueOf(ownerId));
            history.setEventType(eventCode.endsWith("ADDED") ? "ATTACHMENT_ADDED" : "ATTACHMENT_REMOVED");
            history.setEventName(eventCode.endsWith("ADDED") ? "添加流程附件" : "移除流程附件");
            history.setActorAccountId(context.accountId());
            history.setActorTenantMemberId(context.tenantMemberId());
            history.setDetailJson(toJson(detail));
            history.setOccurTime(LocalDateTime.now());
            flowHistoryService.insert(history);
        }
    }

    private record Attachment(FileReference reference, FileObject file) {
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
        cleanupExpiredUploads(context);
        requireQuota(context, content.length);
        String safeName = sanitizeName(originalName);
        String safeContentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream" : contentType.strip().toLowerCase(Locale.ROOT);
        FileStorageBackend storage = backend(context);
        String objectKey = contextKey(context) + "/generated/" + UUID.randomUUID();
        objectStore.put(storage, objectKey, content, safeContentType);
        deleteOnRollback(storage, objectKey);

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
        scan.setStartTime(LocalDateTime.now());
        scan.setFinishTime(LocalDateTime.now());
        scanService.insert(scan);

        FileReference reference = new FileReference();
        reference.setPlatformId(context.platformId());
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
        FileObject file = requireFile(context, fileId, "DELETE", traceId);
        long references = referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                .eq(FileReference::getFileId, fileId)).size();
        if (references > 0) throw conflict("FILE_IN_USE", "文件仍被业务对象引用，必须先解除引用");
        file.setStatus("DELETED");
        file.setDeleteTime(LocalDateTime.now());
        if (objectService.updateById(file) != 1) {
            throw conflict("FILE_VERSION_CONFLICT", "文件状态已变化，请刷新后重试");
        }
        FileStorageBackend storage = requireBackend(file.getStorageBackendId());
        deleteAfterCommit(storage, file.getObjectKey());
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FILE_DELETED", "FILE", fileId.toString(), "SUCCESS", Map.of("referenceCount", 0));
        return new FileModels.DeleteResult(fileId, "DELETED", true);
    }

    private FileStorageBackend backend(AuthenticatedContext context) {
        FileStorageBackend object = contextBackends(context).stream()
                .filter(item -> "OBJECT_DEFAULT".equals(item.getCode()) && "ACTIVE".equals(item.getStatus()))
                .findFirst().orElse(null);
        if (object != null) return object;
        FileStorageBackend existing = contextBackends(context).stream()
                .filter(item -> "LOCAL_DEFAULT".equals(item.getCode()) && "ACTIVE".equals(item.getStatus()))
                .findFirst().orElse(null);
        if (existing != null) return existing;
        FileStorageBackend backend = new FileStorageBackend();
        backend.setContextType(contextType(context));
        backend.setPlatformId(context.platformId());
        backend.setSystemId(context.systemId());
        backend.setScopeOwnerId(context.systemId() == null ? context.platformId() : context.systemId());
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

    private List<FileStorageBackend> contextBackends(AuthenticatedContext context) {
        return backendService.selectList(Wrappers.<FileStorageBackend>lambdaQuery()
                        .eq(FileStorageBackend::getPlatformId, context.platformId()))
                .stream().filter(item -> Objects.equals(item.getContextType(), contextType(context))
                        && Objects.equals(item.getSystemId(), context.systemId())).toList();
    }

    private FileStorageBackend requireBackend(Long backendId) {
        FileStorageBackend backend = backendService.selectById(backendId);
        if (backend == null) {
            throw new DomainException("FILE_STORAGE_BACKEND_MISSING", "文件所属存储配置不存在", HttpStatus.CONFLICT);
        }
        return backend;
    }

    private FileModels.StorageBackendView storageView(FileStorageBackend backend) {
        try {
            var configuration = objectMapper.readTree(backend.getConfigJson());
            return new FileModels.StorageBackendView(backend.getId(), backend.getCode(), backend.getName(),
                    backend.getBackendType(), backend.getEndpoint(), backend.getBucketName(),
                    configuration.path("region").asText(null), configuration.path("pathStyle").asBoolean(false),
                    backend.getCredentialRefId() != null, backend.getStatus(), backend.getVersion());
        } catch (JsonProcessingException exception) {
            throw new DomainException("FILE_STORAGE_CONFIGURATION_INVALID", "文件存储配置损坏", HttpStatus.CONFLICT);
        }
    }

    /** 上传会话先预留完整大小，直传文件按新增字节校验；同一租户的并发写入在租户行上串行化。 */
    private void requireQuota(AuthenticatedContext context, long requestedBytes) {
        lockQuotaScope(context);
        long limit = quotaLimit(context, true);
        long used = usedBytes(context, true);
        long reserved = reservedBytes(context, true);
        if (requestedBytes > limit - used - reserved) {
            throw invalid("FILE_CONTEXT_QUOTA_EXCEEDED", "当前业务空间的文件容量不足，请删除无用文件或调整容量");
        }
    }

    private long usedBytes(AuthenticatedContext context) {
        return usedBytes(context, false);
    }

    private long usedBytes(AuthenticatedContext context, boolean currentRead) {
        LambdaQueryWrapper<FileObject> query = Wrappers.<FileObject>lambdaQuery()
                .eq(FileObject::getContextType, contextType(context))
                .eq(FileObject::getPlatformId, context.platformId())
                .ne(FileObject::getStatus, "DELETED");
        if (context.systemId() == null) query.isNull(FileObject::getSystemId).isNull(FileObject::getTenantId);
        else query.eq(FileObject::getSystemId, context.systemId()).eq(FileObject::getTenantId, context.tenantId());
        if (currentRead) query.last("FOR UPDATE");
        return objectService.selectList(query).stream().mapToLong(FileObject::getSizeBytes).sum();
    }

    private long reservedBytes(AuthenticatedContext context) {
        return reservedBytes(context, false);
    }

    private long reservedBytes(AuthenticatedContext context, boolean currentRead) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<FileUploadSession> query = Wrappers.<FileUploadSession>lambdaQuery()
                .eq(FileUploadSession::getContextType, contextType(context))
                .eq(FileUploadSession::getPlatformId, context.platformId())
                .eq(FileUploadSession::getStatus, "PENDING")
                .gt(FileUploadSession::getExpireTime, now);
        if (context.systemId() == null) query.isNull(FileUploadSession::getSystemId).isNull(FileUploadSession::getTenantId);
        else query.eq(FileUploadSession::getSystemId, context.systemId())
                .eq(FileUploadSession::getTenantId, context.tenantId());
        if (currentRead) query.last("FOR UPDATE");
        return sessionService.selectList(query).stream().mapToLong(FileUploadSession::getExpectedSize).sum();
    }

    /** 锁住稳定存在的归属行，未配置租户策略时默认容量也不会被并发请求同时穿透。 */
    private void lockQuotaScope(AuthenticatedContext context) {
        if (context.systemId() == null) {
            PlatformDefinition platform = platformService.selectList(Wrappers.<PlatformDefinition>lambdaQuery()
                    .eq(PlatformDefinition::getId, context.platformId()).last("FOR UPDATE"))
                    .stream().findFirst().orElse(null);
            if (platform == null) throw conflict("FILE_PLATFORM_CONTEXT_INVALID", "平台文件空间不存在");
        } else {
            SystemTenant tenant = tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                    .eq(SystemTenant::getId, context.tenantId())
                    .eq(SystemTenant::getSystemId, context.systemId()).last("FOR UPDATE"))
                    .stream().findFirst().orElse(null);
            if (tenant == null) throw conflict("FILE_TENANT_CONTEXT_INVALID", "租户文件空间不存在");
        }
    }

    /** 仅系统租户的总量策略覆盖部署默认容量；平台文件继续使用部署容量。 */
    private long quotaLimit(AuthenticatedContext context, boolean currentRead) {
        if (context.systemId() == null || context.tenantId() == null) return contextQuota;
        LambdaQueryWrapper<CoreQuotaPolicy> query = Wrappers.<CoreQuotaPolicy>lambdaQuery()
                .eq(CoreQuotaPolicy::getContextType, "SYSTEM")
                .eq(CoreQuotaPolicy::getSystemId, context.systemId())
                .eq(CoreQuotaPolicy::getTenantId, context.tenantId())
                .eq(CoreQuotaPolicy::getQuotaCode, "STORAGE_BYTES")
                .eq(CoreQuotaPolicy::getPeriodType, "TOTAL")
                .eq(CoreQuotaPolicy::getStatus, "ACTIVE");
        if (currentRead) query.last("FOR UPDATE");
        CoreQuotaPolicy policy = quotaPolicyService.selectOne(query);
        return policy == null ? contextQuota : policy.getHardLimit();
    }

    /** 过期分片不可恢复为文件对象；进入文件入口时顺带回收，避免依赖额外服务或软定时器。 */
    private void cleanupExpiredUploads(AuthenticatedContext context) {
        LocalDateTime now = LocalDateTime.now();
        sessionService.selectList(Wrappers.<FileUploadSession>lambdaQuery()
                        .eq(FileUploadSession::getPlatformId, context.platformId())
                        .eq(FileUploadSession::getStatus, "PENDING").le(FileUploadSession::getExpireTime, now))
                .stream().filter(item -> inContext(item, context)).forEach(session -> {
                    failureRecorder.fail(session.getId(), "EXPIRED");
                    deleteQuietly(partPath(session));
                });
    }

    private FileModels.BinaryContent binary(FileObject file, boolean inline) {
        if (!"ACTIVE".equals(file.getStatus()) || !"CLEAN".equals(file.getScanStatus())) {
            throw forbidden("FILE_CONTENT_UNAVAILABLE", "文件未通过安全扫描或已经删除");
        }
        byte[] bytes = objectStore.read(requireBackend(file.getStorageBackendId()), file.getObjectKey());
        if (!sha256(bytes).equals(file.getSha256())) {
            throw new DomainException("FILE_INTEGRITY_MISMATCH", "文件完整性校验失败", HttpStatus.CONFLICT);
        }
        return new FileModels.BinaryContent(bytes, file.getContentType(), file.getOriginalName(), inline);
    }

    private FileObject requireFile(
            AuthenticatedContext context, Long fileId, String action, String traceId) {
        require(context, action);
        FileObject file = objectService.selectById(fileId);
        if (file == null || (!inContext(file, context) && !sharedFileCandidate(file, context))
                || "DELETED".equals(file.getStatus())) {
            throw notFound("FILE_NOT_FOUND", "文件不存在");
        }
        if (!canAccess(context, file, action, traceId)) {
            throw forbidden("FILE_ACCESS_DENIED", "当前账号不能访问该文件或其业务引用");
        }
        return file;
    }

    private boolean canAccess(AuthenticatedContext context, FileObject file, String action, String traceId) {
        if (inContext(file, context) && (permissionChecker.allows(context, "FILE", "OBJECT", "MANAGE")
                || permissionChecker.allows(context, "FILE", "*", "MANAGE"))) return true;
        List<FileReference> references = referenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                .eq(FileReference::getFileId, file.getId()));
        if (references.isEmpty()) return inContext(file, context)
                && Objects.equals(file.getUploadedByAccountId(), context.accountId());
        return references.stream().filter(reference -> readableReferenceInContext(reference, context))
                .filter(reference -> fileMatchesReference(file, reference))
                .anyMatch(reference -> targetAccessible(context, reference, traceId));
    }

    private boolean targetAccessible(AuthenticatedContext context, FileReference reference, String traceId) {
        try {
            validateReferenceTarget(context, reference.getOwnerType(), reference.getOwnerId(), traceId);
            validateBusinessAttachmentField(context, reference.getOwnerType(), reference.getOwnerId(),
                    reference.getFieldCode(), false, traceId);
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
                flowRuntimeService.instance(context, id);
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
                workManagementService.task(context, id);
            }
            case "WORK_LOG" -> workLogService.requireAttachmentAccess(context, numeric(ownerId), false);
            default -> throw invalid("FILE_REFERENCE_TARGET_INVALID", "文件引用目标类型无效");
        }
    }

    private void validateBusinessAttachmentField(
            AuthenticatedContext context, String ownerType, String ownerId,
            String fieldCode, boolean write, String traceId) {
        if ("WORK_LOG".equals(ownerType)) {
            workLogService.requireAttachmentAccess(context, numeric(ownerId), write);
            return;
        }
        if (!"BUSINESS_RECORD".equals(ownerType)) return;
        if (fieldCode == null || fieldCode.isBlank()) {
            throw invalid("ATTACHMENT_FIELD_REQUIRED", "业务记录附件必须绑定已发布的文件字段");
        }
        String[] parts = ownerId.split(":", 2);
        runtimeDataService.requireAttachmentField(context, parts[0], Long.valueOf(parts[1]), fieldCode, write, traceId);
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
                        .eq(FileSecurityScan::getFileId, file.getId()).orderByDesc(FileSecurityScan::getCreateTime))
                .stream().map(this::scanView).toList();
        return new FileModels.FileView(file.getId(), file.getContextType(), file.getPlatformId(), file.getSystemId(),
                file.getTenantId(), file.getUploadSessionId(), file.getOriginalName(), file.getContentType(),
                file.getSizeBytes(), file.getSha256(), file.getScanStatus(), file.getPreviewStatus(), file.getStatus(),
                file.getUploadedByAccountId(), file.getCreateTime(), file.getDeleteTime(), file.getVersion(), references, scans);
    }

    private FileModels.ReferenceView referenceView(FileReference item) {
        return new FileModels.ReferenceView(item.getId(), item.getContextType(), item.getSystemId(), item.getTenantId(),
                item.getOwnerType(), item.getOwnerId(), item.getFieldCode(), item.getReferenceType(),
                item.getCreatedByAccountId(), item.getCreateTime());
    }

    private FileModels.ScanView scanView(FileSecurityScan item) {
        return new FileModels.ScanView(item.getId(), item.getScanner(), item.getScanVersion(), item.getStatus(),
                item.getResultCode(), item.getResultDetailJson(), item.getStartTime(), item.getFinishTime());
    }

    private FileModels.UploadSessionView sessionView(FileUploadSession session, String token) {
        return new FileModels.UploadSessionView(session.getId(), session.getContextType(), session.getPlatformId(),
                session.getSystemId(), session.getTenantId(), session.getOriginalName(), session.getContentType(),
                session.getExpectedSize(), session.getExpectedSha256(), session.getStatus(), token, true,
                session.getExpireTime(), uploadedSize(session), session.getVersion());
    }

    private FileUploadSession requireUploadSession(
            AuthenticatedContext context, Long sessionId, String rawToken) {
        FileUploadSession session = sessionService.selectById(sessionId);
        if (session == null || !inContext(session, context)
                || !Objects.equals(session.getUploaderAccountId(), context.accountId())) {
            throw notFound("FILE_UPLOAD_SESSION_NOT_FOUND", "上传会话不存在");
        }
        if (rawToken == null || !secureEquals(session.getUploadTokenHash(),
                sha256(rawToken.getBytes(StandardCharsets.UTF_8)))) {
            throw forbidden("FILE_UPLOAD_TOKEN_INVALID", "上传凭证无效");
        }
        return session;
    }

    private void requirePending(FileUploadSession session) {
        if (!"PENDING".equals(session.getStatus())) {
            throw conflict("FILE_UPLOAD_SESSION_NOT_PENDING", "上传会话已经完成、失败或过期");
        }
        if (session.getExpireTime().isBefore(LocalDateTime.now())) {
            failureRecorder.fail(session.getId(), "EXPIRED");
            deleteQuietly(partPath(session));
            throw conflict("FILE_UPLOAD_SESSION_EXPIRED", "上传会话已过期，请重新创建");
        }
    }

    private FileModels.FileView completedFile(FileUploadSession session) {
        if (!"COMPLETED".equals(session.getStatus())) return null;
        FileObject object = objectService.selectList(Wrappers.<FileObject>lambdaQuery()
                        .eq(FileObject::getUploadSessionId, session.getId()).orderByDesc(FileObject::getId))
                .stream().findFirst().orElse(null);
        if (object == null) throw conflict("FILE_UPLOAD_RESULT_MISSING", "上传已完成但文件记录不存在");
        return view(object);
    }

    private Path partPath(FileUploadSession session) {
        return objectPath(session.getObjectKey() + ".part");
    }

    private long uploadedSize(FileUploadSession session) {
        if ("COMPLETED".equals(session.getStatus())) return session.getExpectedSize();
        try { return Files.exists(partPath(session)) ? Files.size(partPath(session)) : 0L; }
        catch (IOException exception) { return 0L; }
    }

    private boolean sameBytes(FileChannel channel, long offset, byte[] expected) throws IOException {
        ByteBuffer current = ByteBuffer.allocate(expected.length);
        channel.position(offset);
        while (current.hasRemaining() && channel.read(current) >= 0) { }
        if (current.position() != expected.length) return false;
        current.flip();
        for (byte value : expected) if (current.get() != value) return false;
        return true;
    }

    private void failUpload(
            AuthenticatedContext context, FileUploadSession session, String traceId,
            String status, String code, Map<String, ?> detail) {
        failureRecorder.fail(session.getId(), status);
        deleteQuietly(partPath(session));
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

    private void deleteOnRollback(FileStorageBackend backend, String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) objectStore.delete(backend, objectKey);
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

    private void deleteAfterCommit(FileStorageBackend backend, String objectKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            objectStore.delete(backend, objectKey);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                objectStore.delete(backend, objectKey);
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
                && Objects.equals(item.getPlatformId(), context.platformId())
                && Objects.equals(item.getSystemId(), context.systemId())
                && Objects.equals(item.getTenantId(), context.tenantId());
    }

    /** 跨租户只读取同系统共享业务记录的附件，实际共享、角色和字段权限仍由目标校验执行。 */
    private boolean readableReferenceInContext(FileReference reference, AuthenticatedContext context) {
        return referenceInContext(reference, context)
                || "BUSINESS_RECORD".equals(reference.getOwnerType())
                && "SYSTEM".equals(reference.getContextType())
                && context.systemId() != null
                && Objects.equals(reference.getPlatformId(), context.platformId())
                && Objects.equals(reference.getSystemId(), context.systemId());
    }

    /** 文件字节必须仍属于该引用的来源租户，不能借用另一个租户的同名业务标识。 */
    private boolean fileMatchesReference(FileObject file, FileReference reference) {
        return Objects.equals(file.getContextType(), reference.getContextType())
                && Objects.equals(file.getPlatformId(), reference.getPlatformId())
                && Objects.equals(file.getSystemId(), reference.getSystemId())
                && Objects.equals(file.getTenantId(), reference.getTenantId());
    }

    /** 对跨租户文件先限定同平台、同系统，再逐条检查有效共享引用。 */
    private boolean sharedFileCandidate(FileObject file, AuthenticatedContext context) {
        return "SYSTEM".equals(file.getContextType()) && context.systemId() != null
                && Objects.equals(file.getPlatformId(), context.platformId())
                && Objects.equals(file.getSystemId(), context.systemId());
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
