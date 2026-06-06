package com.unique.examine.web.controller;

import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowTempVer;
import com.unique.examine.flow.entity.po.FlowTempVerSetting;
import com.unique.examine.flow.service.IFlowTempVerService;
import com.unique.examine.flow.service.IFlowTempVerSettingService;
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

@Tag(name = "自建系统态-flow版本全局设置（CRUD封装）")
@RestController
@RequestMapping("/v1/system/flow/temp-ver-settings")
public class SystemFlowTempVerSettingController {

    @Autowired
    private IFlowTempVerSettingService flowTempVerSettingService;
    @Autowired
    private IFlowTempVerService flowTempVerService;

    @Operation(summary = "全局设置分页（按 tempVerId；system/tenant 隔离）")
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

        var q = flowTempVerSettingService.lambdaQuery()
                .eq(FlowTempVerSetting::getSystemId, systemId)
                .eq(FlowTempVerSetting::getTenantId, tenantId)
                .eq(FlowTempVerSetting::getTempVerId, ver.getId());
        long total = q.count();
        List<FlowTempVerSetting> records = total == 0 ? List.of() : q.orderByAsc(FlowTempVerSetting::getId)
                .last("limit " + s + " offset " + offset)
                .list();
        return ApiResult.ok(Map.of("page", p, "size", s, "total", total, "records", records));
    }

    @Operation(summary = "全局设置详情（按 id）")
    @GetMapping("/{id}")
    public ApiResult<FlowTempVerSetting> detail(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        FlowTempVerSetting s = flowTempVerSettingService.getById(id);
        if (s == null) throw new BusinessException(404, "setting 不存在");
        if (!Objects.equals(s.getSystemId(), systemId) || !Objects.equals(s.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该 setting");
        }
        requireVerInScope(s.getTempVerId(), systemId, tenantId);
        return ApiResult.ok(s);
    }

    public record UpsertBody(Long id, Long tempVerId, String exceptionMode, Long exceptionAdminPlatId, String exceptionEndReason, Integer status) {}

    @Operation(summary = "新增/更新全局设置（system/tenant 强制注入）")
    @PostMapping("/upsert")
    public ApiResult<FlowTempVerSetting> upsert(@RequestBody UpsertBody body) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) throw new BusinessException(401, "未登录");
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) throw new BusinessException(403, "请先进入自建系统");
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        if (body == null) throw new BusinessException(400, "body 不能为空");
        if (body.tempVerId() == null || body.tempVerId() <= 0) throw new BusinessException(400, "tempVerId 不能为空");

        requireVerInScope(body.tempVerId(), systemId, tenantId);

        FlowTempVerSetting s;
        if (body.id() != null) {
            s = flowTempVerSettingService.getById(body.id());
            if (s == null) throw new BusinessException(404, "setting 不存在");
            if (!Objects.equals(s.getSystemId(), systemId) || !Objects.equals(s.getTenantId(), tenantId) || !Objects.equals(s.getTempVerId(), body.tempVerId())) {
                throw new BusinessException(403, "无权操作该 setting");
            }
        } else {
            s = new FlowTempVerSetting();
            s.setSystemId(systemId);
            s.setTenantId(tenantId);
            s.setTempVerId(body.tempVerId());
            s.setCreateUserId(platId);
        }
        s.setExceptionMode(body.exceptionMode());
        s.setExceptionAdminPlatId(body.exceptionAdminPlatId());
        s.setExceptionEndReason(body.exceptionEndReason());
        s.setStatus(body.status() == null ? 1 : body.status());
        s.setUpdateUserId(platId);

        flowTempVerSettingService.addOrUpdate(s);
        return ApiResult.ok(s);
    }

    public record DeleteIdsBody(List<Serializable> ids) {}

    @Operation(summary = "删除全局设置（按 id 列表）")
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
            FlowTempVerSetting s = flowTempVerSettingService.getById(sid);
            if (s == null) continue;
            if (!Objects.equals(s.getSystemId(), systemId) || !Objects.equals(s.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权删除该 setting");
            }
            requireVerInScope(s.getTempVerId(), systemId, tenantId);
        }
        flowTempVerSettingService.deleteByIds(ids);
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

