package com.unique.examine.web.controller;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowTempVer;
import com.unique.examine.flow.entity.po.FlowTempVerLine;
import com.unique.examine.flow.entity.po.FlowTempVerLineCond;
import com.unique.examine.flow.service.IFlowTempVerLineCondService;
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

@Tag(name = "自建系统态-flow版本连线条件（CRUD封装）")
@RestController
@RequestMapping("/v1/system/flow/temp-ver-line-conds")
public class SystemFlowTempVerLineCondController {

    @Autowired
    private IFlowTempVerLineCondService flowTempVerLineCondService;
    @Autowired
    private IFlowTempVerLineService flowTempVerLineService;
    @Autowired
    private IFlowTempVerService flowTempVerService;

    @Operation(summary = "连线条件分页（按 lineId；system/tenant 隔离）")
    @GetMapping("/page")
    public ApiResult<Map<String, Object>> page(@RequestParam("lineId") Long lineId,
                                               @RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "size", required = false) Integer size) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        FlowTempVerLine line = requireLineInScope(lineId, systemId, tenantId);
        requireVerInScope(line.getTempVerId(), systemId, tenantId);

        int p = (page == null || page <= 0) ? 1 : page;
        int s = (size == null || size <= 0) ? 20 : Math.min(size, 200);
        long offset = (long) (p - 1) * s;

        var q = flowTempVerLineCondService.lambdaQuery()
                .eq(FlowTempVerLineCond::getLineId, lineId);
        long total = q.count();
        List<FlowTempVerLineCond> records = total == 0 ? List.of() : q.orderByAsc(FlowTempVerLineCond::getGroupNo)
                .orderByAsc(FlowTempVerLineCond::getId)
                .last("limit " + s + " offset " + offset)
                .list();
        return ApiResult.ok(Map.of("page", p, "size", s, "total", total, "records", records));
    }

    @Operation(summary = "连线条件详情（按 id）")
    @GetMapping("/{id}")
    public ApiResult<FlowTempVerLineCond> detail(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        FlowTempVerLineCond c = flowTempVerLineCondService.getById(id);
        if (c == null) throw new BusinessException(404, "条件不存在");
        FlowTempVerLine line = requireLineInScope(c.getLineId(), systemId, tenantId);
        requireVerInScope(line.getTempVerId(), systemId, tenantId);
        return ApiResult.ok(c);
    }

    public record UpsertBody(Long id, Long lineId, Integer groupNo, String logicOp, String leftVar, String cmpOp,
                             String rightType, String rightValue, Integer status) {}

    @Operation(summary = "新增/更新连线条件（按 lineId；作用域跟随 line->ver）")
    @PostMapping("/upsert")
    public ApiResult<FlowTempVerLineCond> upsert(@RequestBody UpsertBody body) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) throw new BusinessException(400, "body 不能为空");
        if (body.lineId() == null || body.lineId() <= 0) throw new BusinessException(400, "lineId 不能为空");

        FlowTempVerLine line = requireLineInScope(body.lineId(), systemId, tenantId);
        requireVerInScope(line.getTempVerId(), systemId, tenantId);

        FlowTempVerLineCond c;
        if (body.id() != null) {
            c = flowTempVerLineCondService.getById(body.id());
            if (c == null) throw new BusinessException(404, "条件不存在");
            if (!Objects.equals(c.getLineId(), body.lineId())) throw new BusinessException(403, "无权操作该条件");
        } else {
            c = new FlowTempVerLineCond();
            c.setLineId(body.lineId());
            c.setCreateUserId(platId);
        }
        c.setGroupNo(body.groupNo());
        c.setLogicOp(body.logicOp());
        c.setLeftVar(body.leftVar());
        c.setCmpOp(body.cmpOp());
        c.setRightType(body.rightType());
        c.setRightValue(body.rightValue());
        c.setStatus(body.status() == null ? 1 : body.status());
        c.setUpdateUserId(platId);

        flowTempVerLineCondService.addOrUpdate(c);
        return ApiResult.ok(c);
    }

    public record DeleteIdsBody(List<Serializable> ids) {}

    @Operation(summary = "删除连线条件（按 id 列表）")
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
            FlowTempVerLineCond c = flowTempVerLineCondService.getById(sid);
            if (c == null) continue;
            FlowTempVerLine line = requireLineInScope(c.getLineId(), systemId, tenantId);
            requireVerInScope(line.getTempVerId(), systemId, tenantId);
        }
        flowTempVerLineCondService.deleteByIds(ids);
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

    private FlowTempVerLine requireLineInScope(Long lineId, long systemId, long tenantId) {
        if (lineId == null) throw new BusinessException(400, "lineId 不能为空");
        FlowTempVerLine line = flowTempVerLineService.getById(lineId);
        if (line == null) throw new BusinessException(404, "连线不存在");
        if (!Objects.equals(line.getSystemId(), systemId) || !Objects.equals(line.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该连线");
        }
        return line;
    }
}

