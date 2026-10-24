package com.unique.unexamine.sys.module.manage.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.common.Result;
import com.unique.unexamine.sys.module.manage.bo.DictionarySearchBO;
import com.unique.unexamine.sys.module.manage.dto.DictionaryDto.CreateRequest;
import com.unique.unexamine.sys.module.manage.dto.DictionaryDto.Item;
import com.unique.unexamine.sys.module.manage.dto.DictionaryDto.UpdateRequest;
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

    /** 每次新增一条基础项，确定稳定分类、基础值与父级。 */
    @PostMapping
    @Operation(summary="保存字典基础项")
    public Result<Item> create(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @Valid @RequestBody CreateRequest request) { return Result.ok(service.create(token,systemId,tenantId,request)); }

    /** 修改已有基础项的名称和展示资料。 */
    @PutMapping("/{id}")
    @Operation(summary="修改字典基础项")
    public Result<Item> update(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @PathVariable Long id,@Valid @RequestBody UpdateRequest request) {
        return Result.ok(service.update(token,systemId,tenantId,id,request));
    }

    /** 基础值与名称用于字段配置时选择，新增项不会自动加入已有字段。 */
    @GetMapping
    @Operation(summary="查询字典基础项列表")
    public Result<Page<Item>> list(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,
            @ModelAttribute DictionarySearchBO search) {
        return Result.ok(service.list(token,systemId,tenantId,search));
    }

    /** 按稳定 ID 查询当前基础值和名称。 */
    @GetMapping("/{id}")
    @Operation(summary="查询字典基础项")
    public Result<Item> get(@RequestHeader(value="Authorization",required=false) String token,
            @RequestHeader(value="X-Tenant-Id",required=false) Long tenantId,@PathVariable Long systemId,@PathVariable Long id) {
        return Result.ok(service.get(token,systemId,tenantId,id));
    }
}
