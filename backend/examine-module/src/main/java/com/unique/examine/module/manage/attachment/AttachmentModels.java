package com.unique.examine.module.manage.attachment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 业务记录附件接口模型。
 */
public final class AttachmentModels {

    private AttachmentModels() {
    }

    /**
     * 附件绑定请求。
     *
     * @param fileIds 文件编号列表
     * @param bindMode 绑定模式
     * @param sourceType 来源类型
     * @param mobileCaptureMeta 移动端扫码、拍照等采集元数据
     */
    public record AttachmentBindRequest(List<String> fileIds, String bindMode, String sourceType,
                                        Map<String, Object> mobileCaptureMeta) {
    }

    /**
     * 附件视图。
     *
     * @param attachmentId 业务附件编号
     * @param fileId 文件编号
     * @param fileName 文件名
     * @param fileType 文件类型
     * @param status 附件状态
     * @param previewUrl 样例预览地址
     * @param downloadUrl 样例下载地址
     * @param permissionMode 权限模式
     * @param retryable 上传失败后是否可重试
     * @param capturedBy 采集人
     * @param capturedAt 采集时间
     */
    public record AttachmentVO(String attachmentId, String fileId, String fileName, String fileType, String status,
                               String previewUrl, String downloadUrl, String permissionMode, boolean retryable,
                               String capturedBy, LocalDateTime capturedAt) {
    }

    /**
     * 附件绑定结果。
     *
     * @param recordId 记录编号
     * @param moduleId 模块编号
     * @param attachments 附件列表
     * @param permissionSnapshotVersion 权限快照版本
     * @param traceId 链路追踪编号
     * @param auditLogId 审计日志编号
     * @param operatedAt 操作时间
     */
    public record AttachmentBindResult(String recordId, String moduleId, List<AttachmentVO> attachments,
                                       String permissionSnapshotVersion, String traceId, String auditLogId,
                                       LocalDateTime operatedAt) {
    }
}
