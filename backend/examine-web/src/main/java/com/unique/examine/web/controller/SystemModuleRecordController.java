package com.unique.examine.web.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.web.ApiResult;
import com.unique.examine.module.entity.dto.ModuleRecordDslQuery;
import com.unique.examine.module.manage.ModuleRecordFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "自建系统态-业务数据（record/record_data）")
@RestController
@RequestMapping("/v1/system/records")
public class SystemModuleRecordController {

    @Autowired
    private ModuleRecordFacadeService moduleRecordFacadeService;

    public record CreateRecordBody(Long appId, Long modelId, JsonNode data) {}
    public record UpdateRecordBody(JsonNode data) {}

    @Operation(summary = "创建记录（EAV：写 un_module_record + 多行 un_module_record_data，每行 field_code + value_text）")
    @PostMapping("")
    public ApiResult<Map<String, Object>> create(@RequestBody CreateRecordBody body) {
        if (body == null) {
            return ApiResult.fail(400, "body 不能为空");
        }
        var r = moduleRecordFacadeService.createWithData(body.appId(), body.modelId(), body.data());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("recordId", r.getId());
        m.put("record", r);
        return ApiResult.ok(m);
    }

    @Operation(summary = "记录详情（主表 + EAV 聚合为 data 对象）")
    @GetMapping("/{recordId}")
    public ApiResult<Map<String, Object>> detail(@PathVariable("recordId") Long recordId) {
        return ApiResult.ok(moduleRecordFacadeService.detailWithData(recordId));
    }

    @Operation(summary = "更新记录（EAV：先清空旧字段行，再写入新字段行）")
    @PostMapping("/{recordId}/update")
    public ApiResult<Map<String, Object>> update(@PathVariable("recordId") Long recordId,
                                                 @RequestBody UpdateRecordBody body) {
        return ApiResult.ok(moduleRecordFacadeService.updateWithData(recordId, body == null ? null : body.data()));
    }

    @Operation(summary = "删除记录（软删主表 status=2，并删除对应 EAV 字段行）")
    @DeleteMapping("/{recordId}")
    public ApiResult<Void> delete(@PathVariable("recordId") Long recordId) {
        moduleRecordFacadeService.deleteRecord(recordId);
        return ApiResult.ok();
    }

    @Operation(summary = "DSL 白名单查询（分页/排序/过滤；禁止任意 SQL）")
    @PostMapping("/query")
    public ApiResult<Map<String, Object>> query(@RequestBody ModuleRecordDslQuery body) {
        return ApiResult.ok(moduleRecordFacadeService.queryDsl(body));
    }
}

