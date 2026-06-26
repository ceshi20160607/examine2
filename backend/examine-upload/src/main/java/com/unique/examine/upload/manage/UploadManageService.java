package com.unique.examine.upload.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.upload.base.entity.UploadFile;
import com.unique.examine.upload.base.entity.UploadFileAccessLog;
import com.unique.examine.upload.base.entity.UploadFileVersion;
import com.unique.examine.upload.base.entity.UploadStoragePolicy;
import com.unique.examine.upload.base.service.UploadFileAccessLogBaseService;
import com.unique.examine.upload.base.service.UploadFileBaseService;
import com.unique.examine.upload.base.service.UploadFileVersionBaseService;
import com.unique.examine.upload.base.service.UploadStoragePolicyBaseService;
import com.unique.examine.upload.manage.UploadManageModels.FileAccessRequest;
import com.unique.examine.upload.manage.UploadManageModels.FileAccessResultVO;
import com.unique.examine.upload.manage.UploadManageModels.UploadFileVO;
import com.unique.examine.upload.manage.UploadManageModels.UploadResultRequest;
import com.unique.examine.upload.manage.UploadManageModels.UploadResultVO;
import com.unique.examine.upload.manage.UploadManageModels.UploadStoragePolicyVO;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload management service backed by persisted upload tables.
 */
@Service
public class UploadManageService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final long SYSTEM_OPERATOR_ID = 0L;
    private static final String DEFAULT_POLICY_CODE = "LOCAL_DEFAULT";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final UploadFileBaseService fileBaseService;
    private final UploadFileVersionBaseService fileVersionBaseService;
    private final UploadFileAccessLogBaseService accessLogBaseService;
    private final UploadStoragePolicyBaseService storagePolicyBaseService;
    private final Path localRoot;
    private final ObjectMapper objectMapper;

    public UploadManageService(UploadFileBaseService fileBaseService,
                               UploadFileVersionBaseService fileVersionBaseService,
                               UploadFileAccessLogBaseService accessLogBaseService,
                               UploadStoragePolicyBaseService storagePolicyBaseService,
                               @Value("${unexamine.upload.local-root:./data/uploads}") String localRoot,
                               ObjectMapper objectMapper) {
        this.fileBaseService = fileBaseService;
        this.fileVersionBaseService = fileVersionBaseService;
        this.accessLogBaseService = accessLogBaseService;
        this.storagePolicyBaseService = storagePolicyBaseService;
        this.localRoot = Path.of(localRoot).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    /**
     * Register upload result.
     *
     * @param request upload result request
     * @return upload result
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResultVO saveResult(UploadResultRequest request) {
        String fileName = safeText(Objects.isNull(request) ? null : request.fileName(), "upload-file.bin");
        String fileId = uniqueFileId("file_" + shortTrace(RequestContext.current().traceId()));
        UploadFile file = createOrReuseFile(fileId, fileName,
                safeText(Objects.isNull(request) ? null : request.fileType(), mimeFromName(fileName)),
                Objects.isNull(request) || Objects.isNull(request.sizeBytes()) ? 0L : request.sizeBytes(),
                Objects.isNull(request) ? null : request.checksum(),
                safeText(Objects.isNull(request) ? null : request.policyCode(), DEFAULT_POLICY_CODE),
                safeText(Objects.isNull(request) ? null : request.sourceType(), "SYSTEM"),
                null, null, null);
        return new UploadResultVO(toVO(file), true, RequestContext.current().traceId(),
                auditLogId(RequestContext.current()));
    }

    /**
     * Store a real multipart file using the local upload root.
     *
     * @param multipartFile uploaded file
     * @param policyCode storage policy code
     * @param sourceType source type
     * @return upload result
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResultVO upload(MultipartFile multipartFile, String policyCode, String sourceType) {
        if (Objects.isNull(multipartFile) || multipartFile.isEmpty()) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "上传文件不能为空");
        }
        String fileName = sanitizeFileName(safeText(multipartFile.getOriginalFilename(), "upload-file.bin"));
        String fileId = uniqueFileId("file_" + shortTrace(RequestContext.current().traceId()));
        String objectKey = "uploads/" + fileId + "/" + fileName;
        Path target = resolveLocalPath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            String checksum = writeAndChecksum(multipartFile, target);
            UploadFile file = createOrReuseFile(fileId, fileName,
                    safeText(multipartFile.getContentType(), mimeFromName(fileName)), multipartFile.getSize(),
                    checksum, safeText(policyCode, DEFAULT_POLICY_CODE), safeText(sourceType, "WEB_UPLOAD"),
                    null, null, null, objectKey);
            return new UploadResultVO(toVO(file), true, RequestContext.current().traceId(),
                    auditLogId(RequestContext.current()));
        } catch (IOException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "上传文件写入失败");
        }
    }

    /**
     * Register a generated file for import/export or other backend tasks.
     *
     * @param fileId file id
     * @param fileName file name
     * @param fileType file type or extension
     * @param checksum checksum
     * @param systemId system id
     * @param tenantId tenant id
     * @param ownerMemberId owner member id
     * @return file reference
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadFileVO registerGeneratedFile(String fileId, String fileName, String fileType, String checksum,
                                              Long systemId, Long tenantId, Long ownerMemberId) {
        byte[] content = ("fileId,fileName\n" + fileId + "," + fileName + "\n").getBytes(StandardCharsets.UTF_8);
        return registerGeneratedFile(fileId, fileName, fileType, checksum, systemId, tenantId, ownerMemberId,
                content);
    }

    /**
     * Register and write a generated file.
     *
     * @param fileId file id
     * @param fileName file name
     * @param fileType file type
     * @param checksum optional checksum
     * @param systemId system id
     * @param tenantId tenant id
     * @param ownerMemberId owner member id
     * @param content file content
     * @return file reference
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadFileVO registerGeneratedFile(String fileId, String fileName, String fileType, String checksum,
                                              Long systemId, Long tenantId, Long ownerMemberId, byte[] content) {
        String resolvedFileName = sanitizeFileName(safeText(fileName, "generated-file.csv"));
        String objectKey = "generated/" + fileId + "/" + resolvedFileName;
        Path target = resolveLocalPath(objectKey);
        byte[] resolvedContent = Objects.isNull(content) ? new byte[0] : content;
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, resolvedContent);
        } catch (IOException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "生成文件写入失败");
        }
        UploadFile file = createOrReuseFile(fileId, resolvedFileName, fileType, (long) resolvedContent.length,
                safeText(checksum, "sha256:" + hex(sha256Digest().digest(resolvedContent))), DEFAULT_POLICY_CODE,
                "SYSTEM", systemId, tenantId, ownerMemberId, objectKey);
        return toVO(file);
    }

    /**
     * Read stored file bytes for server-side import processing.
     *
     * @param fileId file id
     * @param maxBytes max bytes
     * @return file content
     */
    public byte[] readFileBytes(String fileId, long maxBytes) {
        UploadFile file = requireFile(fileId);
        if (Objects.nonNull(file.getSizeBytes()) && file.getSizeBytes() > maxBytes) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "导入文件超过允许大小");
        }
        Path path = resolveLocalPath(file.getObjectKey());
        try {
            return Files.readAllBytes(path);
        } catch (IOException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "文件内容不可读取");
        }
    }

    /**
     * Get persisted file detail.
     *
     * @param fileId file id
     * @return file detail
     */
    public UploadFileVO getFile(String fileId) {
        return toVO(requireFile(fileId));
    }

    /**
     * Query upload storage policies, creating a local default policy when empty.
     *
     * @return storage policies
     */
    @Transactional(rollbackFor = Exception.class)
    public List<UploadStoragePolicyVO> policies() {
        ensureDefaultPolicies();
        return storagePolicyBaseService.list(new LambdaQueryWrapper<UploadStoragePolicy>()
                        .orderByAsc(UploadStoragePolicy::getScope)
                        .orderByAsc(UploadStoragePolicy::getPolicyCode))
                .stream()
                .map(this::toPolicyVO)
                .toList();
    }

    /**
     * Generate a file access result and persist access audit log.
     *
     * @param fileId file id
     * @param request access request
     * @return access result
     */
    @Transactional(rollbackFor = Exception.class)
    public FileAccessResultVO accessResult(String fileId, FileAccessRequest request) {
        UploadFile file = requireFile(fileId);
        RequestContext context = RequestContext.current();
        String accessType = safeText(Objects.isNull(request) ? null : request.accessType(), "DOWNLOAD");
        boolean allowed = !"DENIED_SAMPLE".equals(Objects.isNull(request) ? null : request.permissionSnapshotVersion());
        String disabledReason = allowed ? null : "当前权限快照不允许访问该文件";
        logAccess(file, accessType, allowed, disabledReason);
        return new FileAccessResultVO(fileId, allowed, accessType,
                allowed ? "/api/v1/uploads/files/" + fileId + "/" + accessType.toLowerCase() : null,
                allowed ? LocalDateTime.now().plusMinutes(15) : null,
                disabledReason, context.traceId(), auditLogId(context));
    }

    /**
     * Build a Spring resource response for download or inline preview.
     *
     * @param fileId file id
     * @param inline inline preview flag
     * @return response entity
     */
    public ResponseEntity<Resource> fileResponse(String fileId, boolean inline) {
        UploadFile file = requireFile(fileId);
        Path path = resolveLocalPath(file.getObjectKey());
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "文件内容不存在");
        }
        FileSystemResource resource = new FileSystemResource(path);
        logAccess(file, inline ? "PREVIEW" : "DOWNLOAD", true, null);
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(URLEncoder.encode(file.getFileName(), StandardCharsets.UTF_8), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(safeText(file.getMimeType(), MediaType.APPLICATION_OCTET_STREAM_VALUE)))
                .contentLength(file.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource);
    }

    private UploadFile createOrReuseFile(String fileId, String fileName, String fileType, Long sizeBytes,
                                         String checksum, String policyCode, String scope, Long systemId,
                                         Long tenantId, Long ownerMemberId) {
        return createOrReuseFile(fileId, fileName, fileType, sizeBytes, checksum, policyCode, scope,
                systemId, tenantId, ownerMemberId, null);
    }

    private UploadFile createOrReuseFile(String fileId, String fileName, String fileType, Long sizeBytes,
                                         String checksum, String policyCode, String scope, Long systemId,
                                         Long tenantId, Long ownerMemberId, String objectKey) {
        ensureDefaultPolicies();
        UploadFile existing = fileBaseService.getOne(new LambdaQueryWrapper<UploadFile>()
                .eq(UploadFile::getFileId, fileId)
                .eq(UploadFile::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.nonNull(existing)) {
            return existing;
        }
        LocalDateTime now = LocalDateTime.now();
        UploadStoragePolicy policy = resolvePolicy(policyCode);
        UploadFile file = new UploadFile();
        file.setFileId(fileId);
        file.setScope(safeText(scope, "SYSTEM"));
        file.setSystemId(systemId);
        file.setTenantId(tenantId);
        file.setFileName(safeText(fileName, fileId));
        file.setExtension(extension(file.getFileName(), fileType));
        file.setMimeType(mimeType(fileType, file.getFileName()));
        file.setSizeBytes(Objects.isNull(sizeBytes) ? 0L : sizeBytes);
        file.setSha256(StringUtils.hasText(checksum) ? checksum : "sha256:" + fileId);
        file.setStorageProvider(policy.getStorageProvider());
        file.setStorageBucket(policy.getStorageBucket());
        file.setObjectKey(safeText(objectKey, "uploads/" + fileId + "/" + file.getFileName()));
        file.setStorageStatus("READY");
        file.setPreviewStatus(Objects.equals(policy.getPreviewEnabled(), ENABLED) ? "READY" : "DISABLED");
        file.setOwnerAccountId(RequestContext.current().accountId());
        file.setOwnerMemberId(ownerMemberId);
        file.setPermissionSnapshotId("upload_perm_" + fileId);
        file.setCreatedAt(now);
        file.setUpdatedAt(now);
        file.setDeleted(DELETED_NO);
        fileBaseService.saveEntity(file);
        saveVersion(file, now);
        return file;
    }

    private UploadFile requireFile(String fileId) {
        UploadFile file = fileBaseService.getOne(new LambdaQueryWrapper<UploadFile>()
                .eq(UploadFile::getFileId, fileId)
                .eq(UploadFile::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(file)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "文件不存在");
        }
        return file;
    }

    private Path resolveLocalPath(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "文件存储路径为空");
        }
        Path path = localRoot.resolve(objectKey).normalize();
        if (!path.startsWith(localRoot)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "文件路径不合法");
        }
        return path;
    }

    private String writeAndChecksum(MultipartFile multipartFile, Path target) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream inputStream = new DigestInputStream(multipartFile.getInputStream(), digest)) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return "sha256:" + hex(digest.digest());
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "当前运行环境不支持 SHA-256");
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    private String sanitizeFileName(String fileName) {
        String value = safeText(fileName, "upload-file.bin").replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1);
        return value.replaceAll("[\\r\\n]", "").trim();
    }

    private void logAccess(UploadFile file, String action, boolean allowed, String disabledReason) {
        RequestContext context = RequestContext.current();
        UploadFileAccessLog log = new UploadFileAccessLog();
        log.setFileId(file.getFileId());
        log.setAccessAction(action);
        log.setActorAccountId(context.accountId());
        log.setActorMemberId(file.getOwnerMemberId());
        log.setResult(allowed ? "SUCCESS" : "DENIED");
        log.setFailureReason(disabledReason);
        log.setRequestId(context.requestId());
        log.setTraceId(context.traceId());
        log.setCreatedAt(LocalDateTime.now());
        accessLogBaseService.saveEntity(log);
    }

    private void saveVersion(UploadFile file, LocalDateTime now) {
        UploadFileVersion version = new UploadFileVersion();
        version.setFileId(file.getFileId());
        version.setVersionNo(1);
        version.setObjectKey(file.getObjectKey());
        version.setSizeBytes(file.getSizeBytes());
        version.setSha256(file.getSha256());
        version.setCreatedBy(SYSTEM_OPERATOR_ID);
        version.setCreatedAt(now);
        fileVersionBaseService.saveEntity(version);
    }

    private UploadStoragePolicy resolvePolicy(String policyCode) {
        return storagePolicyBaseService.getOne(new LambdaQueryWrapper<UploadStoragePolicy>()
                .eq(UploadStoragePolicy::getPolicyCode, safeText(policyCode, DEFAULT_POLICY_CODE))
                .orderByAsc(UploadStoragePolicy::getId)
                .last("LIMIT 1"), false);
    }

    private void ensureDefaultPolicies() {
        if (storagePolicyBaseService.count(new LambdaQueryWrapper<UploadStoragePolicy>()
                .eq(UploadStoragePolicy::getPolicyCode, DEFAULT_POLICY_CODE)) == 0) {
            UploadStoragePolicy local = new UploadStoragePolicy();
            local.setScope("SYSTEM");
            local.setPolicyCode(DEFAULT_POLICY_CODE);
            local.setStorageProvider("LOCAL");
            local.setStorageBucket("local-uploads");
            local.setMaxFileSize(50L * 1024 * 1024);
            local.setAllowedExtensions(toJson(List.of("xlsx", "csv", "jpg", "jpeg", "png", "pdf", "docx")));
            local.setPreviewEnabled(ENABLED);
            local.setQuotaLimitBytes(10L * 1024 * 1024 * 1024);
            local.setStatus(ENABLED);
            local.setUpdatedAt(LocalDateTime.now());
            storagePolicyBaseService.saveEntity(local);
        }
        if (storagePolicyBaseService.count(new LambdaQueryWrapper<UploadStoragePolicy>()
                .eq(UploadStoragePolicy::getPolicyCode, "OBJECT_STORAGE_PROD")) == 0) {
            UploadStoragePolicy object = new UploadStoragePolicy();
            object.setScope("SYSTEM");
            object.setPolicyCode("OBJECT_STORAGE_PROD");
            object.setStorageProvider("OBJECT_STORAGE");
            object.setStorageBucket("prod-bucket");
            object.setSecretRefId(null);
            object.setMaxFileSize(200L * 1024 * 1024);
            object.setAllowedExtensions(toJson(List.of("xlsx", "csv", "jpg", "jpeg", "png", "pdf", "docx")));
            object.setPreviewEnabled(ENABLED);
            object.setQuotaLimitBytes(100L * 1024 * 1024 * 1024);
            object.setStatus(0);
            object.setUpdatedAt(LocalDateTime.now());
            storagePolicyBaseService.saveEntity(object);
        }
    }

    private UploadFileVO toVO(UploadFile file) {
        return new UploadFileVO(file.getFileId(), file.getFileName(), file.getMimeType(), file.getSizeBytes(),
                file.getSha256(), file.getStorageStatus(), DEFAULT_POLICY_CODE, file.getStorageProvider(),
                !"LOCAL".equals(file.getStorageProvider()), "/api/v1/uploads/files/" + file.getFileId() + "/preview",
                "/api/v1/uploads/files/" + file.getFileId() + "/download",
                Objects.isNull(file.getOwnerMemberId()) ? null : String.valueOf(file.getOwnerMemberId()),
                file.getCreatedAt());
    }

    private UploadStoragePolicyVO toPolicyVO(UploadStoragePolicy policy) {
        int maxSizeMb = Objects.isNull(policy.getMaxFileSize())
                ? 0 : Math.toIntExact(policy.getMaxFileSize() / 1024 / 1024);
        boolean objectStorageEnabled = "OBJECT_STORAGE".equals(policy.getStorageProvider())
                && Objects.equals(policy.getStatus(), ENABLED)
                && StringUtils.hasText(policy.getSecretRefId());
        String disabledReason = Objects.equals(policy.getStatus(), ENABLED)
                ? null : "策略未启用或对象存储密钥未配置";
        return new UploadStoragePolicyVO(policy.getPolicyCode(), policy.getPolicyCode(),
                policy.getStorageProvider(), maxSizeMb, readAllowedExtensions(policy.getAllowedExtensions()),
                objectStorageEnabled, disabledReason);
    }

    private List<String> readAllowedExtensions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String uniqueFileId(String prefix) {
        String candidate = prefix + "_" + System.currentTimeMillis();
        if (fileBaseService.count(new LambdaQueryWrapper<UploadFile>()
                .eq(UploadFile::getFileId, candidate)) == 0) {
            return candidate;
        }
        return candidate + "_" + System.nanoTime();
    }

    private String extension(String fileName, String fileType) {
        if (StringUtils.hasText(fileName) && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        }
        if (StringUtils.hasText(fileType) && !fileType.contains("/")) {
            return fileType.toLowerCase();
        }
        return "bin";
    }

    private String mimeType(String fileType, String fileName) {
        if (StringUtils.hasText(fileType) && fileType.contains("/")) {
            return fileType;
        }
        return mimeFromName(fileName);
    }

    private String mimeFromName(String fileName) {
        String extension = extension(fileName, null);
        return switch (extension) {
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "csv" -> "text/csv";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String shortTrace(String traceId) {
        return Objects.isNull(traceId) || traceId.length() <= 8 ? "trace" : traceId.substring(traceId.length() - 8);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "上传策略序列化失败");
        }
    }
}
