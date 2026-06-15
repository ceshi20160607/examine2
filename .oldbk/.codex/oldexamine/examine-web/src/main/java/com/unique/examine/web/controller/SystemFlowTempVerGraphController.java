package com.unique.examine.web.controller;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.manage.FlowTempVerGraphService;
import com.unique.examine.flow.manage.FlowTempVerGraphService.SaveGraphBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "自建系统态-flow 可视化设计器")
@RestController
@RequestMapping("/v1/system/flow/temp-vers")
public class SystemFlowTempVerGraphController {

    @Autowired
    private FlowTempVerGraphService flowTempVerGraphService;

    @Operation(summary = "加载流程图设计器数据（节点/边 + graphJson）")
    @GetMapping("/{tempVerId}/graph-designer")
    public ApiResult<Map<String, Object>> load(@PathVariable("tempVerId") Long tempVerId) {
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        return ApiResult.ok(flowTempVerGraphService.loadDesignerGraph(tempVerId, systemId, tenantId));
    }

    @Operation(summary = "保存流程图设计器（写节点/边表并生成 graphJson）")
    @PostMapping("/{tempVerId}/graph-designer")
    public ApiResult<Map<String, Object>> save(@PathVariable("tempVerId") Long tempVerId,
                                               @RequestBody SaveGraphBody body) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = requireSystem();
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        return ApiResult.ok(flowTempVerGraphService.saveDesignerGraph(tempVerId, body, systemId, tenantId, platId));
    }

    private long requireSystem() {
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        return systemId;
    }
}
