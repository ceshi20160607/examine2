package com.unique.examine.web.controller;

import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.security.AuthContextHolder;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowTemp;
import com.unique.examine.flow.service.IFlowTempService;
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

/**
 * 将生成器 CRUD 的 FlowTemp 能力以系统态（/v1/system）暴露给移动端/外部调用。
 *
 * <p>注意：不修改 examine-flow 下生成器产物（FlowTempController 等），仅新增薄封装。</p>
 */
@Tag(name = "自建系统态-flow模板（查询封装）")
@RestController
@RequestMapping("/v1/system/flow/temps")
public class SystemFlowTempController {

    @Autowired
    private IFlowTempService flowTempService;

    @Operation(summary = "流程模板分页（FlowTemp；最小封装）")
    @GetMapping("/page")
    public ApiResult<Map<String, Object>> page(@RequestParam(value = "page", required = false) Integer page,
                                                @RequestParam(value = "size", required = false) Integer size) {
        PageEntity pe = new PageEntity();
        long p = (page == null || page <= 0) ? 1L : page.longValue();
        long s = (size == null || size <= 0) ? 20L : Math.min(size.longValue(), 200L);
        pe.setPage(p);
        pe.setLimit(s);
        pe.setPageType(1);

        BasePage<FlowTemp> bp = flowTempService.queryPageList(pe);
        return ApiResult.ok(Map.of(
                "page", bp.getCurrent(),
                "size", bp.getSize(),
                "total", bp.getTotal(),
                "records", bp.getRecords()
        ));
    }

    @Operation(summary = "流程模板详情（按 id；system/tenant 隔离）")
    @GetMapping("/{id}")
    public ApiResult<FlowTemp> detail(@PathVariable("id") Long id) {
        Long platId = AuthContextHolder.getPlatId();
        if (platId == null) {
            throw new BusinessException(401, "未登录");
        }
        long systemId = AuthContextHolder.getSystemIdOrDefault();
        if (systemId == 0L) {
            throw new BusinessException(403, "请先进入自建系统");
        }
        long tenantId = AuthContextHolder.getTenantIdOrDefault();
        FlowTemp t = flowTempService.getById(id);
        if (t == null) {
            throw new BusinessException(404, "模板不存在");
        }
        if (!Objects.equals(t.getSystemId(), systemId) || !Objects.equals(t.getTenantId(), tenantId)) {
            throw new BusinessException(403, "无权访问该模板");
        }
        return ApiResult.ok(t);
    }

    public record UpsertBody(Long id, String tempCode, String tempName, String categoryCode, Integer status, String remark) {}

    @Operation(summary = "新增/更新流程模板（system/tenant 强制注入）")
    @PostMapping("/upsert")
    public ApiResult<FlowTemp> upsert(@RequestBody UpsertBody body) {
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
        if (body.tempCode() == null || body.tempCode().isBlank()) {
            throw new BusinessException(400, "tempCode 不能为空");
        }
        if (body.tempName() == null || body.tempName().isBlank()) {
            throw new BusinessException(400, "tempName 不能为空");
        }
        int st = body.status() == null ? 1 : body.status();
        if (st != 1 && st != 2) {
            throw new BusinessException(400, "status 须为 1=启用 或 2=停用");
        }

        FlowTemp t;
        if (body.id() != null) {
            t = flowTempService.getById(body.id());
            if (t == null) {
                throw new BusinessException(404, "模板不存在");
            }
            if (!Objects.equals(t.getSystemId(), systemId) || !Objects.equals(t.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权操作该模板");
            }
        } else {
            t = new FlowTemp();
            t.setSystemId(systemId);
            t.setTenantId(tenantId);
            t.setCreateUserId(platId);
        }
        t.setTempCode(body.tempCode().trim());
        t.setTempName(body.tempName().trim());
        t.setCategoryCode(body.categoryCode() == null ? null : body.categoryCode().trim());
        t.setStatus(st);
        t.setRemark(body.remark() == null ? null : body.remark().trim());
        t.setUpdateUserId(platId);

        flowTempService.addOrUpdate(t);
        return ApiResult.ok(t);
    }

    public record DeleteIdsBody(List<Serializable> ids) {}

    @Operation(summary = "删除流程模板（按 id 列表；system/tenant 隔离）")
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
            FlowTemp t = flowTempService.getById(sid);
            if (t == null) continue;
            if (!Objects.equals(t.getSystemId(), systemId) || !Objects.equals(t.getTenantId(), tenantId)) {
                throw new BusinessException(403, "无权删除该模板");
            }
        }
        flowTempService.deleteByIds(ids);
        return ApiResult.ok();
    }
}
