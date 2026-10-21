package com.unique.unexamine.module.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.module.base.entity.UnModule;
import com.unique.unexamine.module.base.entity.UnModuleDict;
import com.unique.unexamine.module.base.service.IUnModuleDictService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.regex.Pattern;

/** 模块可选项字典配置。 */
@Tag(name = "模块字典", description = "模块字典项的查询和维护。")
@RestController
@RequestMapping("/api/modules")
public class ModuleDictionaryController {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final ModuleSupport support;
    private final IUnModuleDictService dictionaries;

    public ModuleDictionaryController(ModuleSupport support, IUnModuleDictService dictionaries) {
        this.support = support;
        this.dictionaries = dictionaries;
    }

    @Operation(summary = "查询字典（模块字典）")
    @GetMapping("/{moduleId}/dicts")
    public List<UnModuleDict> dicts(@RequestParam long tenantId, @PathVariable long moduleId, HttpServletRequest request) {
        support.member(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        return dictionaries.list(new LambdaQueryWrapper<UnModuleDict>().eq(UnModuleDict::getSystemId, module.getSystemId())
                .in(UnModuleDict::getModuleId, List.of(0L, moduleId)).eq(UnModuleDict::getStatus, 1)
                .orderByAsc(UnModuleDict::getDictCode, UnModuleDict::getSortNo));
    }

    @Operation(summary = "新建字典项（模块字典）")
    @PostMapping("/{moduleId}/dicts")
    public UnModuleDict createDictItem(@RequestParam long tenantId, @PathVariable long moduleId,
                                        @Valid @RequestBody DictForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        code(body.dictCode());
        code(body.itemCode());
        if (dictionaries.count(new LambdaQueryWrapper<UnModuleDict>().eq(UnModuleDict::getSystemId, module.getSystemId())
                .eq(UnModuleDict::getModuleId, moduleId).eq(UnModuleDict::getDictCode, body.dictCode())
                .eq(UnModuleDict::getItemCode, body.itemCode())) > 0) conflict("字典选项编码已存在");
        UnModuleDict row = new UnModuleDict();
        row.setSystemId(module.getSystemId());
        row.setModuleId(moduleId);
        row.setDictCode(body.dictCode());
        row.setItemCode(body.itemCode());
        row.setItemName(body.itemName());
        row.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        row.setStatus((byte) 1);
        dictionaries.save(row);
        return dictionaries.getById(row.getId());
    }

    @Operation(summary = "修改字典项（模块字典）")
    @PutMapping("/{moduleId}/dicts/{dictId}")
    public UnModuleDict updateDictItem(@RequestParam long tenantId, @PathVariable long moduleId,
                                        @PathVariable long dictId, @Valid @RequestBody DictForm body,
                                        HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        UnModuleDict row = dictionaries.getById(dictId);
        if (row == null || !row.getSystemId().equals(module.getSystemId()) || !row.getModuleId().equals(moduleId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字典选项不存在");
        if (!row.getDictCode().equals(body.dictCode()) || !row.getItemCode().equals(body.itemCode()))
            conflict("字典编码与选项编码不可修改");
        row.setItemName(body.itemName());
        row.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        dictionaries.updateById(row);
        return dictionaries.getById(dictId);
    }

    @Operation(summary = "停用字典项（模块字典）")
    @DeleteMapping("/{moduleId}/dicts/{dictId}")
    public void stopDictItem(@RequestParam long tenantId, @PathVariable long moduleId,
                             @PathVariable long dictId, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        UnModuleDict row = dictionaries.getById(dictId);
        if (row == null || !row.getSystemId().equals(module.getSystemId()) || !row.getModuleId().equals(moduleId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "字典选项不存在");
        row.setStatus((byte) 0);
        dictionaries.updateById(row);
    }


    private static void code(String code) { if (code == null || !CODE.matcher(code).matches()) bad("编码格式错误"); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public record DictForm(@NotBlank String dictCode, @NotBlank String itemCode, @NotBlank String itemName, Integer sortNo) { }
}
