package com.unique.unexamine.sys.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 字典接口仅传递上下文和参数，发布与查询规则统一在 Service 中。 */
@RestController
@RequestMapping("/api/systems/{systemId}/dictionaries")
@Tag(name="系统数据字典")
public class DictionaryController {
    private final DictionaryService service;
    /** 注入字典业务服务。 */
    public DictionaryController(DictionaryService service) { this.service=service; }
    /** 保存不会改动运行态选项。 */
    @PostMapping
    @Operation(summary="保存字典草稿")
    public Long save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @Valid @RequestBody DictionaryService.SaveRequest request) { return service.save(token,systemId,tenantId,request); }
    /** 发布后使用新版本，历史记录仍保留原名称。 */
    @PostMapping("/{id}/publish")
    @Operation(summary="发布字典")
    public DictionaryService.PublishedDictionary publish(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long id) {
        return service.publish(token,systemId,tenantId,id);
    }
    /** 查询经过系统租户边界检查的发布选项。 */
    @GetMapping("/{id}")
    @Operation(summary="查询字典发布内容")
    public DictionaryService.PublishedDictionary get(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long id) {
        return service.published(token,systemId,tenantId,id);
    }
}
