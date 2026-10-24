package com.unique.unexamine.sys.module.manage.dto;

import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/** ModuleShare 的请求与返回模型。 */
public final class ModuleShareDto {
    private ModuleShareDto() {}

    /** 单条记录与范围二选一；范围至少含负责人或当前保存字段筛选条件。 */
    public record SaveRequest(@NotNull Long targetTenantId,Long recordId,List<Long> ownerAccountIds,
                              List<ModuleSceneDto.Condition> filters,List<Long> actionMenuIds,
                              LocalDateTime effectiveTime,LocalDateTime expireTime) {}

    /** 范围配置使用稳定账号和字段主键，运行时按来源租户数据重新求值。 */
    public record RangeScope(List<Long> ownerAccountIds,List<ModuleSceneDto.Condition> filters) {}

    /** 共享配置和撤销结果保留原始记录所属租户。 */
    public record ShareView(Long id,Long sourceTenantId,Long targetTenantId,Long moduleId,
                            Long recordId,RangeScope scope,List<Long> actionMenuIds,
                            String status,LocalDateTime effectiveTime,LocalDateTime expireTime) {}
}
