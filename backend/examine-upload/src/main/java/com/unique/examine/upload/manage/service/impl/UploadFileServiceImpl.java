package com.unique.examine.upload.manage.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.upload.base.entity.File;
import com.unique.examine.upload.base.entity.FileReference;
import com.unique.examine.upload.base.entity.StorageConfig;
import com.unique.examine.upload.base.service.IFileReferenceService;
import com.unique.examine.upload.base.service.IFileService;
import com.unique.examine.upload.base.service.IStorageConfigService;
import com.unique.examine.upload.manage.bo.FileBindDTO;
import com.unique.examine.upload.manage.bo.FileQueryBO;
import com.unique.examine.upload.manage.bo.FileUnbindDTO;
import com.unique.examine.upload.manage.enums.UploadErrorCode;
import com.unique.examine.upload.manage.service.UploadFileService;
import com.unique.examine.upload.manage.vo.FileAccessVO;
import com.unique.examine.upload.manage.vo.FileInfoVO;
import com.unique.examine.upload.manage.vo.FileListItemVO;
import com.unique.examine.upload.manage.vo.FileReferenceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传文件管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class UploadFileServiceImpl implements UploadFileService {

    private static final String ENABLED = "ENABLED";

    private static final String TEMP = "TEMP";

    private static final String REFERENCED = "REFERENCED";

    private static final String DELETED = "DELETED";

    private static final String EXPIRED = "EXPIRED";

    private static final String ACTIVE = "ACTIVE";

    private static final String UNBOUND = "UNBOUND";

    private static final byte YES = 1;

    private static final byte NO = 0;

    private static final long MAX_FILE_SIZE = 50L * 1024L * 1024L;

    private static final Set<String> PREVIEW_EXTENSIONS = Set.of("txt", "md", "json", "csv", "png", "jpg", "jpeg",
            "gif", "webp", "pdf");

    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of("exe", "bat", "cmd", "sh", "msi", "jar");

    private final IFileService fileService;

    private final IFileReferenceService fileReferenceService;

    private final IStorageConfigService storageConfigService;

    private final PermissionService permissionService;

    /**
     * 上传文件并保存临时文件元数据。
     */
    @Override
    @Transactional
    public FileInfoVO upload(Long systemId, MultipartFile multipartFile, String bizType, Long moduleId, Long recordId,
            String fieldCode) {
        permissionService.requireOperation("FILE_UPLOAD");
        validateUploadFile(multipartFile);
        StorageConfig storageConfig = defaultStorage(systemId);
        StoredFile storedFile = storeFile(systemId, storageConfig, multipartFile);
        LocalDateTime now = LocalDateTime.now();
        File file = new File()
                .setSystemId(systemId)
                .setTenantId(currentTenantId())
                .setStorageConfigId(storageConfig.getId())
                .setFileName(multipartFile.getOriginalFilename())
                .setExtension(extension(multipartFile.getOriginalFilename()))
                .setContentType(multipartFile.getContentType())
                .setFileSize(multipartFile.getSize())
                .setSha256(storedFile.sha256())
                .setStorageKey(storedFile.storageKey())
                .setStatus(TEMP)
                .setPreviewable(previewable(extension(multipartFile.getOriginalFilename())) ? YES : NO)
                .setOwnerMemberId(currentMemberId())
                .setRefCount(0)
                .setTempExpiresAt(now.plusDays(1))
                .setRequestId(currentRequestId())
                .setDeleted(NO)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        fileService.save(file);
        if (Objects.nonNull(recordId)) {
            FileBindDTO bindDTO = new FileBindDTO();
            bindDTO.setFileId(file.getId());
            bindDTO.setBizType(StringUtils.hasText(bizType) ? bizType : "MODULE_RECORD_FIELD");
            bindDTO.setBizId(recordId);
            bindDTO.setModuleId(moduleId);
            bindDTO.setRecordId(recordId);
            bindDTO.setFieldCode(fieldCode);
            bindDTO.setDisplayName(file.getFileName());
            bindFiles(systemId, List.of(bindDTO));
        }
        return fileInfo(fileService.getById(file.getId()));
    }

    /**
     * 查询文件列表。
     */
    @Override
    public PageResult<FileListItemVO> queryFiles(Long systemId, FileQueryBO queryBO) {
        permissionService.requireOperation("FILE_VIEW");
        FileQueryBO query = Objects.isNull(queryBO) ? new FileQueryBO() : queryBO;
        List<File> files = fileService.lambdaQuery()
                .eq(File::getSystemId, systemId)
                .eq(File::getDeleted, NO)
                .list()
                .stream()
                .filter(file -> !StringUtils.hasText(query.getStatus()) || Objects.equals(file.getStatus(),
                        query.getStatus()))
                .filter(file -> !StringUtils.hasText(query.getKeyword()) || contains(file.getFileName(),
                        query.getKeyword()) || contains(file.getExtension(), query.getKeyword()))
                .filter(file -> matchesReferenceFilter(file, query))
                .sorted(Comparator.comparing(File::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        long pageNo = normalizePageNo(query.getPageNo());
        long pageSize = normalizePageSize(query.getPageSize());
        int from = Math.toIntExact(Math.min((pageNo - 1) * pageSize, files.size()));
        int to = Math.toIntExact(Math.min(from + pageSize, files.size()));
        List<FileListItemVO> records = files.subList(from, to).stream().map(this::fileListItem).toList();
        return PageResult.<FileListItemVO>builder()
                .records(records)
                .total(files.size())
                .pageNo(pageNo)
                .pageSize(pageSize)
                .hasNext(to < files.size())
                .build();
    }

    /**
     * 查询文件详情。
     */
    @Override
    public FileInfoVO fileDetail(Long systemId, Long fileId) {
        File file = requireActiveFile(systemId, fileId);
        requireFileAccess(file);
        return fileInfo(file);
    }

    /**
     * 读取预览文件资源。
     */
    @Override
    public FileAccessVO preview(Long systemId, Long fileId) {
        File file = requireActiveFile(systemId, fileId);
        requireFileAccess(file);
        if (!Objects.equals(file.getPreviewable(), YES)) {
            throw new BusinessException(UploadErrorCode.FILE_NOT_PREVIEWABLE);
        }
        return access(file, true);
    }

    /**
     * 读取下载文件资源。
     */
    @Override
    public FileAccessVO download(Long systemId, Long fileId) {
        File file = requireActiveFile(systemId, fileId);
        requireFileAccess(file);
        return access(file, false);
    }

    /**
     * 删除未引用文件。
     */
    @Override
    @Transactional
    public FileInfoVO deleteFile(Long systemId, Long fileId) {
        permissionService.requireOperation("FILE_DELETE");
        File file = requireExistingFile(systemId, fileId);
        if (Objects.equals(file.getStatus(), DELETED) || Objects.equals(file.getStatus(), EXPIRED)) {
            throw new BusinessException(UploadErrorCode.FILE_DELETED);
        }
        if (Objects.nonNull(file.getRefCount()) && file.getRefCount() > 0 || hasActiveReference(file.getId())) {
            throw new BusinessException(UploadErrorCode.FILE_REFERENCED);
        }
        file.setStatus(DELETED)
                .setDeleted(YES)
                .setDeletedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        fileService.updateById(file);
        return fileInfo(file);
    }

    /**
     * 绑定文件引用。
     */
    @Override
    @Transactional
    public List<FileReferenceVO> bindFiles(Long systemId, List<FileBindDTO> bindDTOList) {
        if (CollectionUtils.isEmpty(bindDTOList)) {
            return List.of();
        }
        List<FileReferenceVO> result = new ArrayList<>();
        for (FileBindDTO bindDTO : bindDTOList) {
            File file = requireActiveFile(systemId, bindDTO.getFileId());
            if (Objects.nonNull(bindDTO.getRecordId())) {
                permissionService.requireDataScope("RECORD", toId(bindDTO.getRecordId()), toId(file.getOwnerMemberId()));
            }
            FileReference reference = activeReference(systemId, bindDTO);
            if (Objects.isNull(reference)) {
                reference = new FileReference()
                        .setSystemId(systemId)
                        .setTenantId(file.getTenantId())
                        .setFileId(file.getId())
                        .setBizType(defaultBizType(bindDTO.getBizType()))
                        .setBizId(defaultBizId(bindDTO))
                        .setModuleId(bindDTO.getModuleId())
                        .setRecordId(bindDTO.getRecordId())
                        .setFieldCode(bindDTO.getFieldCode())
                        .setDisplayName(StringUtils.hasText(bindDTO.getDisplayName()) ? bindDTO.getDisplayName()
                                : file.getFileName())
                        .setSortOrder(Objects.isNull(bindDTO.getSortOrder()) ? 0 : bindDTO.getSortOrder())
                        .setStatus(ACTIVE)
                        .setBoundBy(currentMemberId())
                        .setBoundAt(LocalDateTime.now())
                        .setRequestId(currentRequestId())
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now());
                fileReferenceService.save(reference);
                file.setRefCount(activeReferenceCount(file.getId()))
                        .setStatus(REFERENCED)
                        .setUpdatedAt(LocalDateTime.now());
                fileService.updateById(file);
            }
            result.add(referenceVO(reference));
        }
        return result;
    }

    /**
     * 解绑文件引用。
     */
    @Override
    @Transactional
    public List<FileReferenceVO> unbindFiles(Long systemId, List<FileUnbindDTO> unbindDTOList) {
        if (CollectionUtils.isEmpty(unbindDTOList)) {
            return List.of();
        }
        List<FileReferenceVO> result = new ArrayList<>();
        for (FileUnbindDTO unbindDTO : unbindDTOList) {
            File file = requireActiveFile(systemId, unbindDTO.getFileId());
            FileReference reference = fileReferenceService.lambdaQuery()
                    .eq(FileReference::getSystemId, systemId)
                    .eq(FileReference::getFileId, unbindDTO.getFileId())
                    .eq(FileReference::getBizType, defaultBizType(unbindDTO.getBizType()))
                    .eq(FileReference::getBizId, unbindDTO.getBizId())
                    .eq(StringUtils.hasText(unbindDTO.getFieldCode()), FileReference::getFieldCode,
                            unbindDTO.getFieldCode())
                    .eq(FileReference::getStatus, ACTIVE)
                    .one();
            if (Objects.nonNull(reference)) {
                reference.setStatus(UNBOUND)
                        .setUnboundAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now());
                fileReferenceService.updateById(reference);
                int refCount = activeReferenceCount(file.getId());
                file.setRefCount(refCount)
                        .setStatus(refCount > 0 ? REFERENCED : TEMP)
                        .setUpdatedAt(LocalDateTime.now());
                fileService.updateById(file);
                result.add(referenceVO(reference));
            }
        }
        return result;
    }

    private void validateUploadFile(MultipartFile multipartFile) {
        if (Objects.isNull(multipartFile) || multipartFile.isEmpty()) {
            throw new BusinessException(CommonErrorCode.PARAM_INVALID, "上传文件不能为空");
        }
        if (multipartFile.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(UploadErrorCode.SIZE_EXCEEDED);
        }
        String extension = extension(multipartFile.getOriginalFilename());
        if (FORBIDDEN_EXTENSIONS.contains(extension)) {
            throw new BusinessException(UploadErrorCode.TYPE_FORBIDDEN);
        }
    }

    private StorageConfig defaultStorage(Long systemId) {
        List<StorageConfig> configs = storageConfigService.lambdaQuery()
                .eq(StorageConfig::getDefaultFlag, YES)
                .eq(StorageConfig::getStatus, ENABLED)
                .eq(StorageConfig::getDeleted, NO)
                .list();
        return configs.stream()
                .filter(config -> Objects.equals(config.getSystemId(), systemId))
                .findFirst()
                .or(() -> configs.stream().filter(config -> Objects.isNull(config.getSystemId())).findFirst())
                .orElseThrow(() -> new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE));
    }

    private StoredFile storeFile(Long systemId, StorageConfig storageConfig, MultipartFile multipartFile) {
        if (!"LOCAL".equals(storageConfig.getStorageType())) {
            throw new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE, "MVP 仅支持 LOCAL 存储");
        }
        String extension = extension(multipartFile.getOriginalFilename());
        String fileName = UUID.randomUUID() + (StringUtils.hasText(extension) ? "." + extension : "");
        String datePath = LocalDate.now().toString().replace("-", "/");
        String storageKey = systemId + "/" + datePath + "/" + fileName;
        Path rootPath = Path.of(StringUtils.hasText(storageConfig.getRootPath()) ? storageConfig.getRootPath()
                : "uploads").toAbsolutePath().normalize();
        Path targetPath = rootPath.resolve(storageKey).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE, "文件存储路径不合法");
        }
        try {
            Files.createDirectories(targetPath.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = multipartFile.getInputStream();
                    DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                Files.copy(digestInputStream, targetPath);
            }
            return new StoredFile(storageKey, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE, "文件存储失败");
        }
    }

    private FileAccessVO access(File file, boolean inline) {
        StorageConfig storageConfig = storageConfigService.getById(file.getStorageConfigId());
        if (Objects.isNull(storageConfig)) {
            throw new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE);
        }
        Path rootPath = Path.of(StringUtils.hasText(storageConfig.getRootPath()) ? storageConfig.getRootPath()
                : "uploads").toAbsolutePath().normalize();
        Path filePath = rootPath.resolve(file.getStorageKey()).normalize();
        if (!filePath.startsWith(rootPath) || !Files.exists(filePath)) {
            throw new BusinessException(UploadErrorCode.STORAGE_UNAVAILABLE, "文件对象不存在");
        }
        return FileAccessVO.builder()
                .fileName(file.getFileName())
                .contentType(file.getContentType())
                .size(file.getFileSize())
                .inline(inline)
                .resource(new FileSystemResource(filePath))
                .build();
    }

    private File requireExistingFile(Long systemId, Long fileId) {
        File file = fileService.getById(fileId);
        if (Objects.isNull(file) || !Objects.equals(file.getSystemId(), systemId)) {
            throw new BusinessException(UploadErrorCode.FILE_NOT_FOUND);
        }
        return file;
    }

    private File requireActiveFile(Long systemId, Long fileId) {
        File file = requireExistingFile(systemId, fileId);
        if (Objects.equals(file.getDeleted(), YES) || Objects.equals(file.getStatus(), DELETED)
                || Objects.equals(file.getStatus(), EXPIRED)) {
            throw new BusinessException(UploadErrorCode.FILE_DELETED);
        }
        return file;
    }

    private void requireFileAccess(File file) {
        permissionService.requireOperation("FILE_VIEW");
        List<FileReference> references = activeReferences(file.getId());
        if (references.isEmpty()) {
            if (!Objects.equals(file.getOwnerMemberId(), currentMemberId())) {
                permissionService.requireDataScope("FILE", toId(file.getId()), toId(file.getOwnerMemberId()));
            }
            return;
        }
        boolean checkedRecord = false;
        for (FileReference reference : references) {
            if (Objects.nonNull(reference.getRecordId())) {
                permissionService.requireDataScope("RECORD", toId(reference.getRecordId()), toId(file.getOwnerMemberId()));
                checkedRecord = true;
            }
        }
        if (!checkedRecord && !Objects.equals(file.getOwnerMemberId(), currentMemberId())) {
            permissionService.requireDataScope("FILE", toId(file.getId()), toId(file.getOwnerMemberId()));
        }
    }

    private boolean matchesReferenceFilter(File file, FileQueryBO query) {
        if (!StringUtils.hasText(query.getBizType()) && Objects.isNull(query.getBizId())
                && Objects.isNull(query.getModuleId()) && Objects.isNull(query.getRecordId())
                && !StringUtils.hasText(query.getFieldCode())) {
            return true;
        }
        return activeReferences(file.getId()).stream()
                .anyMatch(reference -> (!StringUtils.hasText(query.getBizType())
                        || Objects.equals(reference.getBizType(), query.getBizType()))
                        && (Objects.isNull(query.getBizId()) || Objects.equals(reference.getBizId(), query.getBizId()))
                        && (Objects.isNull(query.getModuleId())
                                || Objects.equals(reference.getModuleId(), query.getModuleId()))
                        && (Objects.isNull(query.getRecordId())
                                || Objects.equals(reference.getRecordId(), query.getRecordId()))
                        && (!StringUtils.hasText(query.getFieldCode())
                                || Objects.equals(reference.getFieldCode(), query.getFieldCode())));
    }

    private FileReference activeReference(Long systemId, FileBindDTO bindDTO) {
        return fileReferenceService.lambdaQuery()
                .eq(FileReference::getSystemId, systemId)
                .eq(FileReference::getFileId, bindDTO.getFileId())
                .eq(FileReference::getBizType, defaultBizType(bindDTO.getBizType()))
                .eq(FileReference::getBizId, defaultBizId(bindDTO))
                .eq(StringUtils.hasText(bindDTO.getFieldCode()), FileReference::getFieldCode, bindDTO.getFieldCode())
                .eq(FileReference::getStatus, ACTIVE)
                .one();
    }

    private List<FileReference> activeReferences(Long fileId) {
        return fileReferenceService.lambdaQuery()
                .eq(FileReference::getFileId, fileId)
                .eq(FileReference::getStatus, ACTIVE)
                .list();
    }

    private boolean hasActiveReference(Long fileId) {
        return activeReferenceCount(fileId) > 0;
    }

    private int activeReferenceCount(Long fileId) {
        return Math.toIntExact(fileReferenceService.lambdaQuery()
                .eq(FileReference::getFileId, fileId)
                .eq(FileReference::getStatus, ACTIVE)
                .count());
    }

    private FileInfoVO fileInfo(File file) {
        List<FileReferenceVO> references = activeReferences(file.getId()).stream().map(this::referenceVO).toList();
        boolean downloadable = !Objects.equals(file.getStatus(), DELETED) && !Objects.equals(file.getStatus(), EXPIRED);
        boolean previewable = downloadable && Objects.equals(file.getPreviewable(), YES);
        return FileInfoVO.builder()
                .fileId(toId(file.getId()))
                .fileName(file.getFileName())
                .extension(file.getExtension())
                .contentType(file.getContentType())
                .size(file.getFileSize())
                .status(file.getStatus())
                .previewable(previewable)
                .previewableReason(previewable ? null : "当前文件类型不支持预览")
                .downloadable(downloadable)
                .downloadableReason(downloadable ? null : "文件已删除或已过期")
                .downloadUrl("/api/v1/systems/" + file.getSystemId() + "/files/" + file.getId() + "/download")
                .ownerMemberId(toId(file.getOwnerMemberId()))
                .refCount(file.getRefCount())
                .tempExpiresAt(file.getTempExpiresAt())
                .createdAt(file.getCreatedAt())
                .references(references)
                .build();
    }

    private FileListItemVO fileListItem(File file) {
        return FileListItemVO.builder()
                .fileId(toId(file.getId()))
                .fileName(file.getFileName())
                .extension(file.getExtension())
                .contentType(file.getContentType())
                .size(file.getFileSize())
                .status(file.getStatus())
                .previewable(Objects.equals(file.getPreviewable(), YES))
                .downloadUrl("/api/v1/systems/" + file.getSystemId() + "/files/" + file.getId() + "/download")
                .ownerMemberId(toId(file.getOwnerMemberId()))
                .refCount(file.getRefCount())
                .createdAt(file.getCreatedAt())
                .build();
    }

    private FileReferenceVO referenceVO(FileReference reference) {
        return FileReferenceVO.builder()
                .referenceId(toId(reference.getId()))
                .fileId(toId(reference.getFileId()))
                .bizType(reference.getBizType())
                .bizId(toId(reference.getBizId()))
                .moduleId(toId(reference.getModuleId()))
                .recordId(toId(reference.getRecordId()))
                .fieldCode(reference.getFieldCode())
                .displayName(reference.getDisplayName())
                .sortOrder(reference.getSortOrder())
                .status(reference.getStatus())
                .boundAt(reference.getBoundAt())
                .build();
    }

    private String defaultBizType(String bizType) {
        return StringUtils.hasText(bizType) ? bizType : "MODULE_RECORD_FIELD";
    }

    private Long defaultBizId(FileBindDTO bindDTO) {
        if (Objects.nonNull(bindDTO.getBizId())) {
            return bindDTO.getBizId();
        }
        if (Objects.nonNull(bindDTO.getRecordId())) {
            return bindDTO.getRecordId();
        }
        throw new BusinessException(CommonErrorCode.PARAM_INVALID, "业务对象 ID 不能为空");
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private boolean previewable(String extension) {
        return PREVIEW_EXTENSIONS.contains(extension);
    }

    private String extension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
    }

    private Long currentTenantId() {
        RequestContext context = RequestContextHolder.get();
        return context == null || !StringUtils.hasText(context.getTenantId()) ? null : Long.valueOf(context.getTenantId());
    }

    private Long currentMemberId() {
        RequestContext context = RequestContextHolder.get();
        return context == null || !StringUtils.hasText(context.getMemberId()) ? null : Long.valueOf(context.getMemberId());
    }

    private String currentRequestId() {
        RequestContext context = RequestContextHolder.get();
        return context == null || !StringUtils.hasText(context.getRequestId()) ? "NO_REQUEST_ID" : context.getRequestId();
    }

    private long normalizePageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 200);
    }

    private String toId(Long value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private record StoredFile(String storageKey, String sha256) {
    }
}
