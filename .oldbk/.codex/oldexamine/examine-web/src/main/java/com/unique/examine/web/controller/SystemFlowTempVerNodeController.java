package com.unique.examine.web.controller;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowTempVer;
import com.unique.examine.flow.entity.po.FlowTempVerNode;
import com.unique.examine.flow.service.IFlowTempVerNodeService;
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

@Tag(name = "自建系统态-flow版本节点（CRUD封装）")
@RestController
@RequestMapping("/v1/system/flow/temp-ver-nodes")
public class SystemFlowTempVerNodeController {

    @Autowired
    private IFlowTempVerNodeService flowTempVerNodeService;
    @Autowired
    private IFlowTempVerService flowTempVerService;

    @Operation(summary = "节点分页（按 tempVerId；system/tenant 隔离）")
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

        var q = flowTempVerNodeService.lambdaQuery()
                .eq(FlowTempVerNode::getSystemId, systemId)
                .eq(FlowTempVerNode::getTenantId, tenantId)
                .eq(FlowTempVerNode::getTempVerId, ver.getId());
        long total = q.count();
        List<FlowTempVerNode> records = total == 0 ? List.of() : q.orderByAsc(FlowTempVerNode::getSortNo)
                .orderByAsc(FlowTempVerNode::getId)
                .last("limit " + s + " offset " + offset)
                .list();
        return ApiResult.ok(Map.of("page", p, "size", s, "total", total, "records", records));
    }

    @Operation(summary = "节点详情（按 id；system/tenant 隔离）")
    @GetMapping("/{id}")
    public ApiResult<FlowTempVerNode> detail(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();

        FlowTempVerNode n = flowTempVerNodeService.getById(id);
        if (n == null) throw new BusinessException(404, "节点不存在");
        if (!Objects.equals(n.getSystemId(), systemId) || !Objects.equals(n.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该节点");
        }
        return ApiResult.ok(n);
    }

    public record UpsertBody(Long id, Long tempVerId, String nodeKey, String parentNodeKey, String nodeType, String nodeName,
                             Integer sortNo, Integer status, String configJson) {}

    @Operation(summary = "新增/更新节点（system/tenant 强制注入）")
    @PostMapping("/upsert")
    public ApiResult<FlowTempVerNode> upsert(@RequestBody UpsertBody body) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) throw new BusinessException(400, "body 不能为空");
        if (body.tempVerId() == null || body.tempVerId() <= 0) throw new BusinessException(400, "tempVerId 不能为空");
        if (body.nodeKey() == null || body.nodeKey().isBlank()) throw new BusinessException(400, "nodeKey 不能为空");
        if (body.nodeType() == null || body.nodeType().isBlank()) throw new BusinessException(400, "nodeType 不能为空");

        requireVerInScope(body.tempVerId(), systemId, tenantId);

        FlowTempVerNode n;
        if (body.id() != null) {
            n = flowTempVerNodeService.getById(body.id());
            if (n == null) throw new BusinessException(404, "节点不存在");
            if (!Objects.equals(n.getSystemId(), systemId) || !Objects.equals(n.getTenantId(), tenantId) || !Objects.equals(n.getTempVerId(), body.tempVerId())) {
                throw new BusinessException(403, "无权操作该节点");
            }
        } else {
            n = new FlowTempVerNode();
            n.setSystemId(systemId);
            n.setTenantId(tenantId);
            n.setTempVerId(body.tempVerId());
            n.setCreateUserId(platId);
        }
        n.setNodeKey(body.nodeKey().trim());
        n.setParentNodeKey(body.parentNodeKey() == null ? null : body.parentNodeKey().trim());
        n.setNodeType(body.nodeType().trim());
        n.setNodeName(body.nodeName() == null ? null : body.nodeName().trim());
        n.setSortNo(body.sortNo());
        n.setStatus(body.status() == null ? 1 : body.status());
        n.setConfigJson(body.configJson());
        n.setUpdateUserId(platId);

        flowTempVerNodeService.addOrUpdate(n);
        return ApiResult.ok(n);
    }

    public record DeleteIdsBody(List<Serializable> ids) {}

    @Operation(summary = "删除节点（按 id 列表；system/tenant 隔离）")
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
            FlowTempVerNode n = flowTempVerNodeService.getById(sid);
            if (n == null) continue;
            if (!Objects.equals(n.getSystemId(), systemId) || !Objects.equals(n.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权删除该节点");
            }
        }
        flowTempVerNodeService.deleteByIds(ids);
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

