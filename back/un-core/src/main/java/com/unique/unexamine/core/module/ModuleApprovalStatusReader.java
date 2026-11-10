package com.unique.unexamine.core.module;

import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/** 审批域向模块页面提供指定记录的审批状态，模块不读取审批实例表。 */
public interface ModuleApprovalStatusReader {
    /** 返回记录主键到状态组主键、当前状态的映射；每组取最近一次发起的实例。 */
    Map<Long,Map<Long,String>> read(Long systemId,Long tenantId,Long moduleId,
                                    List<Long> recordIds,List<Long> statusGroupIds);

    /** 在业务记录分页之前添加指定状态组的最新审批实例条件。 */
    void applyFilter(QueryWrapper<?> where,Long systemId,Long moduleId,Long statusGroupId,String status);
}
