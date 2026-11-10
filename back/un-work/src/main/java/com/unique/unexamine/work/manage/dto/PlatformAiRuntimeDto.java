package com.unique.unexamine.work.manage.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.plat.manage.dto.PlatformLogDto.LogView;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemView;
import com.unique.unexamine.work.manage.dto.WorkTaskDto.TaskView;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 平台 Agent 只处理平台任务，不携带系统业务写入数据。 */
public final class PlatformAiRuntimeDto {
    private PlatformAiRuntimeDto() {}

    /** 当前账号可使用的已发布本地模型。 */
    public record AvailableView(Long configId, String name, List<String> capabilities, Integer modelVersion) {}

    /** 平台管理员查看模型当前用量。 */
    public record QuotaView(Long configId, String month, int monthlyQuota, int maxConcurrency,
            long usedCalls, long activeCalls) {}

    /** 对话指定平台模型，由当前账号权限限定结果。 */
    public record DialogRequest(@NotNull Long configId, @NotBlank @Size(max = 1000) String message) {}

    /** 平台任务查询同时展示关键词与数据口径。 */
    public record TaskQueryView(String intent, String scope, String keyword, Integer modelVersion,
            Page<TaskView> result) {}

    /** 平台日志查询沿用管理员权限和原日志筛选口径。 */
    public record LogQueryView(String intent, String scope, String keyword, Integer modelVersion,
            Page<LogView> result) {}

    /** 平台只提示当前账号可切换的系统，不包含业务模块内容。 */
    public record SystemQueryView(String intent, String keyword, Integer modelVersion,
            List<SystemView> systems) {}

    /** 任务创建识别结果必须先交给用户核对。 */
    public record TaskPreviewView(String intent, String title, String description, LocalDateTime dueTime,
            double confidence, boolean ready, String confirmationToken, String conversationId,
            Integer modelVersion) {}

    /** 确认请求只接受一次性凭证，不接受新的任务字段。 */
    public record ConfirmRequest(@NotBlank String confirmationToken) {}

    /** 写入后的平台任务与 Agent 审计使用同一追踪标识。 */
    public record TaskResult(Long taskId, String confirmationToken, String conversationId) {}
}
