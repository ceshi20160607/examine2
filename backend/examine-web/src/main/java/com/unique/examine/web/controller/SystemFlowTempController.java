package com.unique.examine.web.controller;

import com.unique.examine.core.entity.BasePage;
import com.unique.examine.core.entity.PageEntity;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.flow.entity.po.FlowTemp;
import com.unique.examine.flow.service.IFlowTempService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

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
}
