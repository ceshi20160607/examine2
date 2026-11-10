package com.unique.unexamine.sys.module.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.unique.unexamine.core.bo.SearchBO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 模块 CSV 导入的请求结果模型；文件内容不进入接口 JSON。 */
public final class ModuleImportDto {
    private ModuleImportDto() {}

    /** 导入模式：CREATE 只新增，UPDATE_BY_ID 按 id 更新，UPSERT_BY_ID 有 id 更新否则新增。 */
    public enum Mode { CREATE, UPDATE_BY_ID, UPSERT_BY_ID }

    /** 无副作用预检返回一次性确认凭据和字段映射。 */
    public record PreviewResult(String previewId, Mode mode, int totalCount,
            int failedCount, Map<String, String> fieldMapping, List<PreviewError> errors) {}

    /** 预检仅报告 CSV 与字段值错误；唯一和记录权限仍在确认时逐行检查。 */
    public record PreviewError(int rowNo, String message) {}

    /** 作业结果用于前端跳转作业明细，并保留逐行失败统计。 */
    public record ImportResult(@Schema(description = "导入交换作业主键") Long jobId,
                               @Schema(description = "作业状态") String status,
                               @Schema(description = "数据行总数") int totalCount,
                               @Schema(description = "成功行数") int successCount,
                               @Schema(description = "失败行数") int failureCount) {}

    /** 导入作业持久化后的读取结果。 */
    public record JobView(Long id, Long moduleId, String mode, String status,
            Integer totalCount, Integer successCount, Integer failureCount,
            LocalDateTime createTime, LocalDateTime finishTime) {}

    /** 导入行结果仅返回业务结果和失败说明。 */
    public record RowView(Integer rowNo, Long recordId, String operationCode,
            String status, String errorCode, String detailJson, LocalDateTime createTime) {}

    /** 导入行结果分页条件。 */
    public static class RowSearch extends SearchBO {
        @Schema(description = "SUCCESS 或 FAILED") private String status;
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}
