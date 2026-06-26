package com.unique.examine.module.manage.importexport;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 导入导出接口请求与响应模型。
 */
public final class ImportExportModels {

    private ImportExportModels() {
    }

    /**
     * 文件引用。
     *
     * @param fileId 文件编号
     * @param fileName 文件名称
     * @param fileType 文件类型
     * @param downloadUrl 样例下载地址，不暴露真实对象存储
     * @param checksum 文件校验摘要
     * @param createdAt 文件创建时间
     */
    public record FileRef(String fileId, String fileName, String fileType, String downloadUrl,
                          String checksum, LocalDateTime createdAt) {
    }

    /**
     * 统一后台任务形态。
     *
     * @param taskId 任务编号
     * @param bizType 业务类型
     * @param idempotencyKey 幂等键
     * @param status 任务状态
     * @param progress 任务进度
     * @param retryable 是否允许重试
     * @param cancelable 是否允许取消
     * @param resultFile 结果文件
     * @param errorFile 错误文件
     * @param failureReason 失败原因
     * @param partialSuccessCount 部分成功数量
     * @param partialFailureCount 部分失败数量
     * @param rollbackSupported 是否支持回滚
     * @param traceId 链路追踪编号
     * @param auditLogId 审计日志编号
     * @param createdBy 创建人
     * @param createdAt 创建时间
     */
    public record AsyncTask(String taskId, String bizType, String idempotencyKey, String status, int progress,
                            boolean retryable, boolean cancelable, FileRef resultFile, FileRef errorFile,
                            String failureReason, Integer partialSuccessCount, Integer partialFailureCount,
                            boolean rollbackSupported, String traceId, String auditLogId, String createdBy,
                            LocalDateTime createdAt) {
    }

    /**
     * 导入预检请求。
     *
     * @param fileId 上传文件编号
     * @param templateCode 导入模板编码
     * @param fieldMapping 源列到字段编码的映射
     * @param duplicateStrategy 重复数据处理策略
     */
    public record ImportPrecheckRequest(String fileId, String templateCode, Map<String, String> fieldMapping,
                                        String duplicateStrategy) {
    }

    /**
     * 导入问题明细。
     *
     * @param rowNo 行号
     * @param fieldCode 字段编码
     * @param level 问题级别
     * @param message 问题说明
     * @param suggestion 处理建议
     */
    public record ImportIssue(Integer rowNo, String fieldCode, String level, String message, String suggestion) {
    }

    /**
     * 导入预检结果。
     *
     * @param precheckId 预检编号
     * @param passed 是否通过
     * @param totalRows 总行数
     * @param validRows 可导入行数
     * @param invalidRows 不可导入行数
     * @param duplicateRows 重复行数
     * @param issues 问题列表
     * @param resultFile 预检结果文件
     * @param errorFile 预检错误文件
     * @param task 预检任务快照
     * @param traceId 链路追踪编号
     * @param auditLogId 审计日志编号
     */
    public record ImportPrecheckResult(String precheckId, boolean passed, int totalRows, int validRows,
                                       int invalidRows, int duplicateRows, List<ImportIssue> issues,
                                       FileRef resultFile, FileRef errorFile, AsyncTask task, String traceId,
                                       String auditLogId) {
    }

    /**
     * 导入确认请求。
     *
     * @param precheckId 预检编号
     * @param duplicateStrategy 确认执行时的重复处理策略
     * @param rollbackSupported 是否要求保留回滚边界
     */
    public record ImportConfirmRequest(String precheckId, String duplicateStrategy, Boolean rollbackSupported) {
    }

    /**
     * 导入确认结果。
     *
     * @param precheckId 预检编号
     * @param task 导入执行任务
     * @param precheckWarnings 执行前仍需展示的预检警告
     */
    public record ImportConfirmResult(String precheckId, AsyncTask task, List<ImportIssue> precheckWarnings) {
    }

    /**
     * 导出请求。
     *
     * @param scope 导出范围
     * @param selectedRecordIds 已选记录编号
     * @param fields 导出字段
     * @param fileFormat 文件格式
     * @param desensitizeMode 脱敏模式
     * @param filters 导出筛选条件
     */
    public record ExportRequest(String scope, List<String> selectedRecordIds, List<String> fields,
                                String fileFormat, String desensitizeMode, Map<String, Object> filters) {
    }

    /**
     * 导出结果。
     *
     * @param task 导出后台任务
     * @param scope 实际导出范围
     * @param fields 实际导出字段
     * @param desensitizeMode 实际脱敏模式
     * @param expectedResultFile 预计结果文件
     * @param expectedErrorFile 预计错误文件
     */
    public record ExportResult(AsyncTask task, String scope, List<String> fields, String desensitizeMode,
                               FileRef expectedResultFile, FileRef expectedErrorFile) {
    }
}
