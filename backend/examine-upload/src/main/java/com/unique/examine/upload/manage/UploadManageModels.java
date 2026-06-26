package com.unique.examine.upload.manage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 上传管理接口模型。
 */
public final class UploadManageModels {

    private UploadManageModels() {
    }

    /**
     * 上传结果登记请求。
     *
     * @param fileName 文件名
     * @param fileType 文件类型
     * @param sizeBytes 文件大小
     * @param checksum 文件摘要
     * @param policyCode 存储策略编码
     * @param sourceType 来源类型
     */
    public record UploadResultRequest(String fileName, String fileType, Long sizeBytes, String checksum,
                                      String policyCode, String sourceType) {
    }

    /**
     * 文件视图。
     *
     * @param fileId 文件编号
     * @param fileName 文件名
     * @param fileType 文件类型
     * @param sizeBytes 文件大小
     * @param checksum 文件摘要
     * @param status 文件状态
     * @param storagePolicyCode 存储策略编码
     * @param storageMode 存储模式
     * @param objectStorageConnected 是否已连接生产对象存储
     * @param previewUrl 样例预览地址
     * @param downloadUrl 样例下载地址
     * @param uploadedBy 上传人
     * @param uploadedAt 上传时间
     */
    public record UploadFileVO(String fileId, String fileName, String fileType, Long sizeBytes, String checksum,
                               String status, String storagePolicyCode, String storageMode,
                               boolean objectStorageConnected, String previewUrl, String downloadUrl,
                               String uploadedBy, LocalDateTime uploadedAt) {
    }

    /**
     * 上传结果。
     *
     * @param file 文件详情
     * @param retryable 是否可重试
     * @param traceId 链路追踪编号
     * @param auditLogId 审计日志编号
     */
    public record UploadResultVO(UploadFileVO file, boolean retryable, String traceId, String auditLogId) {
    }

    /**
     * 存储策略。
     *
     * @param policyCode 策略编码
     * @param policyName 策略名称
     * @param storageMode 存储模式
     * @param maxSizeMb 最大文件大小
     * @param allowedTypes 允许的文件类型
     * @param productionObjectStorageEnabled 是否启用生产对象存储
     * @param disabledReason 禁用原因
     */
    public record UploadStoragePolicyVO(String policyCode, String policyName, String storageMode,
                                        int maxSizeMb, List<String> allowedTypes,
                                        boolean productionObjectStorageEnabled, String disabledReason) {
    }

    /**
     * 文件访问请求。
     *
     * @param accessType 访问类型
     * @param businessObjectId 业务对象编号
     * @param permissionSnapshotVersion 权限快照版本
     */
    public record FileAccessRequest(String accessType, String businessObjectId, String permissionSnapshotVersion) {
    }

    /**
     * 文件访问结果。
     *
     * @param fileId 文件编号
     * @param allowed 是否允许访问
     * @param accessType 访问类型
     * @param accessUrl 样例访问地址
     * @param expiresAt 访问地址过期时间
     * @param disabledReason 禁用原因
     * @param traceId 链路追踪编号
     * @param auditLogId 审计日志编号
     */
    public record FileAccessResultVO(String fileId, boolean allowed, String accessType, String accessUrl,
                                     LocalDateTime expiresAt, String disabledReason, String traceId,
                                     String auditLogId) {
    }
}
