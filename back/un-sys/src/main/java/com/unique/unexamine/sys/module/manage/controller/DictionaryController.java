package com.unique.unexamine.sys.module.manage.controller;

import com.unique.unexamine.sys.module.manage.service.DictionaryService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** 字典基础项接口只调用 Service，不暴露删除或整组选项发布入口。 */
@RestController
@RequestMapping("/api/systems/{systemId}/dictionaries")
@Tag(name="字典基础项")
public class DictionaryController {
    private final DictionaryService service;
    /** 注入基础项业务服务。 */
    public DictionaryController(DictionaryService service) { this.service=service; }

    /** 每次新增一条基础项，已有项只允许修改名称。 */
    @PostMapping
    @Operation(summary="保存字典基础项")
    public DictionaryService.Item save(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @Valid @RequestBody DictionaryService.SaveRequest request) { return service.save(token,systemId,tenantId,request); }

    /** 基础值与名称用于字段配置时选择，新增项不会自动加入已有字段。 */
    @GetMapping
    @Operation(summary="查询字典基础项列表")
    public DictionaryService.ItemPage list(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @RequestParam(defaultValue="1") long page,@RequestParam(defaultValue="20") long size,
            @RequestParam(required=false) String name,@RequestParam(required=false) String value,@RequestParam(required=false) Long parentId) {
        return service.list(token,systemId,tenantId,page,size,name,value,parentId);
    }

    /** 按稳定 ID 查询当前基础值和名称。 */
    @GetMapping("/{id}")
    @Operation(summary="查询字典基础项")
    public DictionaryService.Item get(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long id) {
        return service.get(token,systemId,tenantId,id);
    }
}
