package com.unique.examine.module.manage;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.flow.entity.po.FlowBinding;
import com.unique.examine.flow.entity.po.FlowTemp;
import com.unique.examine.flow.service.IFlowBindingService;
import com.unique.examine.flow.service.IFlowTempService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemModuleFlowBindingService {

    public record UpsertBindingCmd(
            Long id,
            Long appId,
            Long modelId,
            String triggerAction,
            Long tempId,
            Integer status
    ) {}

    @Autowired
    private IFlowBindingService flowBindingService;
    @Autowired
    private IFlowTempService flowTempService;

    public List<Map<String, Object>> listByModel(Long appId, Long modelId, Long operatorPlatId) {
        requireContext(operatorPlatId, appId, modelId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        String bizType = ModuleFlowTriggerService.moduleBizType(appId, modelId);
        List<FlowBinding> list = flowBindingService.lambdaQuery()
                .eq(FlowBinding::getSystemId, systemId)
                .eq(FlowBinding::getTenantId, tenantId)
                .eq(FlowBinding::getBizType, bizType)
                .orderByAsc(FlowBinding::getTriggerAction)
                .list();
        List<Map<String, Object>> out = new ArrayList<>();
        for (FlowBinding b : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("binding", b);
            row.put("id", b.getId() == null ? null : String.valueOf(b.getId()));
            row.put("triggerAction", b.getTriggerAction());
            row.put("tempId", b.getTempId() == null ? null : String.valueOf(b.getTempId()));
            row.put("status", b.getStatus());
            if (b.getTempId() != null) {
                FlowTemp t = flowTempService.getById(b.getTempId());
                row.put("tempCode", t != null ? t.getTempCode() : null);
                row.put("tempName", t != null ? t.getTempName() : null);
            }
            out.add(row);
        }
        return out;
    }

    public List<Map<String, Object>> listFlowTemps(Long operatorPlatId) {
        requireOperator(operatorPlatId);
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        List<FlowTemp> temps = flowTempService.lambdaQuery()
                .eq(FlowTemp::getSystemId, systemId)
                .eq(FlowTemp::getTenantId, tenantId)
                .eq(FlowTemp::getStatus, 1)
                .orderByAsc(FlowTemp::getTempCode)
                .list();
        List<Map<String, Object>> out = new ArrayList<>();
        for (FlowTemp t : temps) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId() == null ? null : String.valueOf(t.getId()));
            m.put("tempCode", t.getTempCode());
            m.put("tempName", t.getTempName());
            out.add(m);
        }
        return out;
    }

    @Transactional(rollbackFor = Exception.class)
    public FlowBinding upsert(Long operatorPlatId, UpsertBindingCmd body) {
        requireContext(operatorPlatId, body.appId(), body.modelId());
        if (body.triggerAction() == null || body.triggerAction().isBlank()) {
            throw new BusinessException(400, "triggerAction 不能为空");
        }
        if (body.tempId() == null || body.tempId() <= 0L) {
            throw new BusinessException(400, "tempId 不能为空");
        }
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        String bizType = ModuleFlowTriggerService.moduleBizType(body.appId(), body.modelId());
        String action = body.triggerAction().trim();
        int status = body.status() == null ? 1 : body.status();

        FlowTemp temp = flowTempService.getById(body.tempId());
        if (temp == null || !Objects.equals(temp.getSystemId(), systemId) || !Objects.equals(temp.getTenantId(), tenantId)) {
            throw new BusinessException(400, "流程模板不存在");
        }

        FlowBinding binding;
        if (body.id() != null) {
            binding = flowBindingService.getById(body.id());
            if (binding == null) {
                throw new BusinessException(404, "绑定不存在");
            }
        } else {
            binding = flowBindingService.lambdaQuery()
                    .eq(FlowBinding::getSystemId, systemId)
                    .eq(FlowBinding::getTenantId, tenantId)
                    .eq(FlowBinding::getBizType, bizType)
                    .eq(FlowBinding::getTriggerAction, action)
                    .last("limit 1")
                    .one();
            if (binding == null) {
                binding = new FlowBinding();
                binding.setSystemId(systemId);
                binding.setTenantId(tenantId);
                binding.setBizType(bizType);
                binding.setTriggerAction(action);
                binding.setCreateUserId(operatorPlatId);
            }
        }
        binding.setTempId(body.tempId());
        binding.setStatus(status);
        binding.setUpdateUserId(operatorPlatId);
        if (binding.getId() == null) {
            flowBindingService.save(binding);
        } else {
            flowBindingService.updateById(binding);
        }
        return binding;
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long operatorPlatId, Long bindingId) {
        requireOperator(operatorPlatId);
        if (bindingId == null) {
            return;
        }
        FlowBinding b = flowBindingService.getById(bindingId);
        if (b == null) {
            return;
        }
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (!Objects.equals(b.getSystemId(), systemId) || !Objects.equals(b.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权删除");
        }
        flowBindingService.removeById(bindingId);
    }

    private static void requireContext(Long operatorPlatId, Long appId, Long modelId) {
        requireOperator(operatorPlatId);
        if (appId == null || appId <= 0L || modelId == null || modelId <= 0L) {
            throw new BusinessException(400, "appId/modelId 不能为空");
        }
    }

    private static void requireOperator(Long platId) {
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
    }

    private static long requireSystem() {
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        return systemId;
    }
}
