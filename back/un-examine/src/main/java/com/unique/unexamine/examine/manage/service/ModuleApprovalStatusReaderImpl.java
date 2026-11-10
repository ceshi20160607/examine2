package com.unique.unexamine.examine.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.unique.unexamine.core.module.ModuleApprovalStatusReader;
import com.unique.unexamine.examine.base.entity.UnExamineExample;
import com.unique.unexamine.examine.base.entity.UnModuleExamine;
import com.unique.unexamine.examine.base.service.IUnExamineExampleService;
import com.unique.unexamine.examine.base.service.IUnModuleExamineService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 审批实例的状态仍由 examine 维护，页面状态字段只读取绑定后的最新实例。 */
@Service
public class ModuleApprovalStatusReaderImpl implements ModuleApprovalStatusReader {
    private final IUnModuleExamineService bindings;
    private final IUnExamineExampleService examples;

    public ModuleApprovalStatusReaderImpl(IUnModuleExamineService bindings,IUnExamineExampleService examples) {
        this.bindings=bindings; this.examples=examples;
    }

    @Override
    public Map<Long,Map<Long,String>> read(Long systemId,Long tenantId,Long moduleId,
            List<Long> recordIds,List<Long> statusGroupIds) {
        if (recordIds==null || recordIds.isEmpty() || statusGroupIds==null || statusGroupIds.isEmpty()) return Map.of();
        List<UnModuleExamine> configured=bindings.list(new LambdaQueryWrapper<UnModuleExamine>()
                .eq(UnModuleExamine::getSystemId,systemId).eq(UnModuleExamine::getModuleId,moduleId)
                .and(w->w.isNull(UnModuleExamine::getTenantId).or().eq(UnModuleExamine::getTenantId,tenantId))
                .in(UnModuleExamine::getStatusGroupId,statusGroupIds));
        if (configured.isEmpty()) return Map.of();
        Map<Long,Long> groupByBinding=new HashMap<>();
        configured.forEach(binding->groupByBinding.put(binding.getId(),binding.getStatusGroupId()));
        List<UnExamineExample> instances=examples.list(new LambdaQueryWrapper<UnExamineExample>()
                .eq(UnExamineExample::getSystemId,systemId).eq(UnExamineExample::getTenantId,tenantId)
                .eq(UnExamineExample::getModuleId,moduleId).in(UnExamineExample::getRecordId,recordIds)
                .in(UnExamineExample::getModuleExamineId,groupByBinding.keySet())
                .orderByDesc(UnExamineExample::getStartTime).orderByDesc(UnExamineExample::getId));
        Map<Long,Map<Long,String>> result=new LinkedHashMap<>();
        for (UnExamineExample instance:instances)
            result.computeIfAbsent(instance.getRecordId(),id->new LinkedHashMap<>())
                    .putIfAbsent(groupByBinding.get(instance.getModuleExamineId()),instance.getStatus());
        return result;
    }

    /** 相关子查询限定同一记录和来源租户，只比较该状态组最新发起的有效审批实例。 */
    @Override
    public void applyFilter(QueryWrapper<?> where,Long systemId,Long moduleId,Long statusGroupId,String status) {
        String latest="(SELECT e.status FROM un_examine_example e JOIN un_module_examine b "
                +"ON b.id=e.module_examine_id AND b.deleted_time IS NULL "
                +"WHERE e.deleted_time IS NULL AND e.system_id="+systemId
                +" AND e.module_id="+moduleId+" AND e.tenant_id=un_module_record.tenant_id"
                +" AND e.record_id=un_module_record.id AND b.status_group_id="+statusGroupId
                +" ORDER BY e.start_time DESC,e.id DESC LIMIT 1)";
        where.apply(latest+" = {0}",status);
    }
}
