package com.unique.examine.web.controller;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowTempVer;
import com.unique.examine.flow.entity.po.FlowTempVerLine;
import com.unique.examine.flow.service.IFlowTempVerLineService;
import com.unique.examine.flow.service.IFlowTempVerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Tag(name = "自建系统态-flow版本连线（CRUD封装）")
@RestController
@RequestMapping("/v1/system/flow/temp-ver-lines")
public class SystemFlowTempVerLineController {

    @Autowired
    private IFlowTempVerLineService flowTempVerLineService;
    @Autowired
    private IFlowTempVerService flowTempVerService;

    @Operation(summary = "连线分页（按 tempVerId；system/tenant 隔离）")
    @GetMapping("/page")
    public ApiResult<Map<String, Object>> page(@RequestParam("tempVerId") Long tempVerId,
                                               @RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "size", required = false) Integer size) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        FlowTempVer ver = requireVerInScope(tempVerId, systemId, tenantId);

        int p = (page == null || page <= 0) ? 1 : page;
        int s = (size == null || size <= 0) ? 20 : Math.min(size, 200);
        long offset = (long) (p - 1) * s;

        var q = flowTempVerLineService.lambdaQuery()
                .eq(FlowTempVerLine::getSystemId, systemId)
                .eq(FlowTempVerLine::getTenantId, tenantId)
                .eq(FlowTempVerLine::getTempVerId, ver.getId());
        long total = q.count();
        List<FlowTempVerLine> records = total == 0 ? List.of() : q.orderByAsc(FlowTempVerLine::getPriority)
                .orderByAsc(FlowTempVerLine::getId)
                .last("limit " + s + " offset " + offset)
                .list();
        return ApiResult.ok(Map.of("page", p, "size", s, "total", total, "records", records));
    }

    @Operation(summary = "连线详情（按 id；system/tenant 隔离）")
    @GetMapping("/{id}")
    public ApiResult<FlowTempVerLine> detail(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        FlowTempVerLine l = flowTempVerLineService.getById(id);
        if (l == null) throw new BusinessException(404, "连线不存在");
        if (!Objects.equals(l.getSystemId(), systemId) || !Objects.equals(l.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该连线");
        }
        return ApiResult.ok(l);
    }

    public record UpsertBody(Long id, Long tempVerId, String fromNodeKey, String toNodeKey, Integer priority, Integer isDefault, Integer status, String remark) {}

    @Operation(summary = "新增/更新连线（system/tenant 强制注入）")
    @PostMapping("/upsert")
    public ApiResult<FlowTempVerLine> upsert(@RequestBody UpsertBody body) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) throw new BusinessException(400, "body 不能为空");
        if (body.tempVerId() == null || body.tempVerId() <= 0) throw new BusinessException(400, "tempVerId 不能为空");
        if (body.fromNodeKey() == null || body.fromNodeKey().isBlank()) throw new BusinessException(400, "fromNodeKey 不能为空");
        if (body.toNodeKey() == null || body.toNodeKey().isBlank()) throw new BusinessException(400, "toNodeKey 不能为空");

        requireVerInScope(body.tempVerId(), systemId, tenantId);

        FlowTempVerLine l;
        if (body.id() != null) {
            l = flowTempVerLineService.getById(body.id());
            if (l == null) throw new BusinessException(404, "连线不存在");
            if (!Objects.equals(l.getSystemId(), systemId) || !Objects.equals(l.getTenantId(), tenantId) || !Objects.equals(l.getTempVerId(), body.tempVerId())) {
                throw new BusinessException(403, "无权操作该连线");
            }
        } else {
            l = new FlowTempVerLine();
            l.setSystemId(systemId);
            l.setTenantId(tenantId);
            l.setTempVerId(body.tempVerId());
            l.setCreateUserId(platId);
        }
        l.setFromNodeKey(body.fromNodeKey().trim());
        l.setToNodeKey(body.toNodeKey().trim());
        l.setPriority(body.priority());
        l.setIsDefault(body.isDefault());
        l.setStatus(body.status() == null ? 1 : body.status());
        l.setRemark(body.remark() == null ? null : body.remark().trim());
        l.setUpdateUserId(platId);

        flowTempVerLineService.addOrUpdate(l);
        return ApiResult.ok(l);
    }

    public record DeleteIdsBody(List<Serializable> ids) {}

    @Operation(summary = "删除连线（按 id 列表；system/tenant 隔离）")
    @PostMapping("/delete")
    public ApiResult<Void> delete(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        List<Serializable> ids = body == null ? null : body.ids();
        if (ids == null || ids.isEmpty()) return ApiResult.ok();
        for (Serializable sid : ids) {
            if (sid == null) continue;
            FlowTempVerLine l = flowTempVerLineService.getById(sid);
            if (l == null) continue;
            if (!Objects.equals(l.getSystemId(), systemId) || !Objects.equals(l.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权删除该连线");
            }
        }
        flowTempVerLineService.deleteByIds(ids);
        return ApiResult.ok();
    }

    private FlowTempVer requireVerInScope(Long tempVerId, long systemId, long tenantId) {
        FlowTempVer ver = flowTempVerService.getById(tempVerId);
        if (ver == null) throw new BusinessException(404, "版本不存在");
        if (!Objects.equals(ver.getSystemId(), systemId) || !Objects.equals(ver.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该版本");
        }
        return ver;
    }
}

