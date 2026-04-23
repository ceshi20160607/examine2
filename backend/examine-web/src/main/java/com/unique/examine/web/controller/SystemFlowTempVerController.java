package com.unique.examine.web.controller;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowTemp;
import com.unique.examine.flow.entity.po.FlowTempVer;
import com.unique.examine.flow.service.IFlowTempService;
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
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Tag(name = "自建系统态-flow模板版本（CRUD封装）")
@RestController
@RequestMapping("/v1/system/flow/temp-vers")
public class SystemFlowTempVerController {

    @Autowired
    private IFlowTempVerService flowTempVerService;
    @Autowired
    private IFlowTempService flowTempService;

    @Operation(summary = "版本分页（按 tempId；system/tenant 隔离）")
    @GetMapping("/page")
    public ApiResult<Map<String, Object>> page(@RequestParam("tempId") Long tempId,
                                               @RequestParam(value = "page", required = false) Integer page,
                                               @RequestParam(value = "size", required = false) Integer size) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (tempId == null || tempId <= 0) {
            throw new BusinessException(400, "tempId 不能为空");
        }
        FlowTemp temp = flowTempService.getById(tempId);
        if (temp == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (!Objects.equals(temp.getSystemId(), systemId) || !Objects.equals(temp.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该模板");
        }

        int p = (page == null || page <= 0) ? 1 : page;
        int s = (size == null || size <= 0) ? 20 : Math.min(size, 200);
        long offset = (long) (p - 1) * s;

        var q = flowTempVerService.lambdaQuery()
                .eq(FlowTempVer::getSystemId, systemId)
                .eq(FlowTempVer::getTenantId, tenantId)
                .eq(FlowTempVer::getTempId, tempId);
        long total = q.count();
        List<FlowTempVer> records = total == 0 ? List.of() : q.orderByDesc(FlowTempVer::getVerNo)
                .orderByDesc(FlowTempVer::getId)
                .last("limit " + s + " offset " + offset)
                .list();
        return ApiResult.ok(Map.of("page", p, "size", s, "total", total, "records", records));
    }

    @Operation(summary = "版本详情（按 id；system/tenant 隔离）")
    @GetMapping("/{id}")
    public ApiResult<FlowTempVer> detail(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        FlowTempVer v = flowTempVerService.getById(id);
        if (v == null) {
            throw new BusinessException(404, "版本不存在");
        }
        if (!Objects.equals(v.getSystemId(), systemId) || !Objects.equals(v.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该版本");
        }
        return ApiResult.ok(v);
    }

    public record UpsertBody(Long id, Long tempId, Integer verNo, Integer publishStatus, String graphJson, String formJson) {}

    @Operation(summary = "新增/更新版本（system/tenant 强制注入）")
    @PostMapping("/upsert")
    public ApiResult<FlowTempVer> upsert(@RequestBody UpsertBody body) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) {
            throw new BusinessException(400, "body 不能为空");
        }
        if (body.tempId() == null || body.tempId() <= 0) {
            throw new BusinessException(400, "tempId 不能为空");
        }
        FlowTemp temp = flowTempService.getById(body.tempId());
        if (temp == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (!Objects.equals(temp.getSystemId(), systemId) || !Objects.equals(temp.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权操作该模板");
        }

        int pub = body.publishStatus() == null ? 1 : body.publishStatus();
        if (pub != 1 && pub != 2 && pub != 3) {
            throw new BusinessException(400, "publishStatus 须为 1=草稿 2=已发布 3=已废弃");
        }

        FlowTempVer v;
        if (body.id() != null) {
            v = flowTempVerService.getById(body.id());
            if (v == null) {
                throw new BusinessException(404, "版本不存在");
            }
            if (!Objects.equals(v.getSystemId(), systemId) || !Objects.equals(v.getTenantId(), tenantId) || !Objects.equals(v.getTempId(), body.tempId())) {
                throw new BusinessException(403, "无权操作该版本");
            }
        } else {
            v = new FlowTempVer();
            v.setSystemId(systemId);
            v.setTenantId(tenantId);
            v.setTempId(body.tempId());
            v.setCreateUserId(platId);
        }
        if (body.verNo() != null) {
            v.setVerNo(body.verNo());
        }
        v.setPublishStatus(pub);
        v.setGraphJson(body.graphJson());
        v.setFormJson(body.formJson());
        v.setUpdateUserId(platId);

        flowTempVerService.addOrUpdate(v);
        return ApiResult.ok(v);
    }

    public record DeleteIdsBody(List<Serializable> ids) {}

    @Operation(summary = "删除版本（按 id 列表；system/tenant 隔离）")
    @PostMapping("/delete")
    public ApiResult<Void> delete(@RequestBody DeleteIdsBody body) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        List<Serializable> ids = body == null ? null : body.ids();
        if (ids == null || ids.isEmpty()) {
            return ApiResult.ok();
        }
        for (Serializable sid : ids) {
            if (sid == null) continue;
            FlowTempVer v = flowTempVerService.getById(sid);
            if (v == null) continue;
            if (!Objects.equals(v.getSystemId(), systemId) || !Objects.equals(v.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权删除该版本");
            }
        }
        flowTempVerService.deleteByIds(ids);
        return ApiResult.ok();
    }

    @Operation(summary = "发布版本（publishStatus=2，并写回 temp.latestVerNo）")
    @PostMapping("/{id}/publish")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<FlowTempVer> publish(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        FlowTempVer v = flowTempVerService.getById(id);
        if (v == null) {
            throw new BusinessException(404, "版本不存在");
        }
        if (!Objects.equals(v.getSystemId(), systemId) || !Objects.equals(v.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权操作该版本");
        }
        if (v.getGraphJson() == null || v.getGraphJson().isBlank()) {
            throw new BusinessException(400, "发布前必须填写 graphJson");
        }
        FlowTemp t = flowTempService.getById(v.getTempId());
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (!Objects.equals(t.getSystemId(), systemId) || !Objects.equals(t.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权操作该模板");
        }
        if (v.getVerNo() == null || v.getVerNo() <= 0) {
            // 自动分配 verNo：当前 temp 下最大 verNo + 1
            Integer max = flowTempVerService.lambdaQuery()
                    .eq(FlowTempVer::getSystemId, systemId)
                    .eq(FlowTempVer::getTenantId, tenantId)
                    .eq(FlowTempVer::getTempId, v.getTempId())
                    .select(FlowTempVer::getVerNo)
                    .orderByDesc(FlowTempVer::getVerNo)
                    .last("limit 1")
                    .oneOpt()
                    .map(FlowTempVer::getVerNo)
                    .orElse(0);
            v.setVerNo((max == null ? 0 : max) + 1);
        }

        // 旧发布版本设为废弃（同 tempId）
        flowTempVerService.lambdaUpdate()
                .eq(FlowTempVer::getSystemId, systemId)
                .eq(FlowTempVer::getTenantId, tenantId)
                .eq(FlowTempVer::getTempId, v.getTempId())
                .eq(FlowTempVer::getPublishStatus, 2)
                .ne(FlowTempVer::getId, v.getId())
                .set(FlowTempVer::getPublishStatus, 3)
                .update();

        v.setPublishStatus(2);
        v.setUpdateUserId(platId);
        flowTempVerService.updateById(v);

        t.setLatestVerNo(v.getVerNo());
        t.setUpdateUserId(platId);
        flowTempService.updateById(t);

        return ApiResult.ok(v);
    }
}

