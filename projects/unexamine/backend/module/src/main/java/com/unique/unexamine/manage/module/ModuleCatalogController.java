package com.unique.unexamine.manage.module;

import com.unique.unexamine.module.base.entity.UnModule;
import com.unique.unexamine.module.base.entity.UnModuleGroup;
import com.unique.unexamine.module.base.entity.UnModuleTenant;
import com.unique.unexamine.module.base.mapper.UnModuleGroupMapper;
import com.unique.unexamine.module.base.mapper.UnModuleMapper;
import com.unique.unexamine.module.base.mapper.UnModuleTenantMapper;

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

/** 模块分组、模块和租户启用关系。 */
@RestController
@RequestMapping("/api/modules")
public class ModuleCatalogController {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final ModuleSupport support;
    private final UnModuleGroupMapper groups;
    private final UnModuleMapper modules;
    private final UnModuleTenantMapper tenantModules;

    public ModuleCatalogController(ModuleSupport support, UnModuleGroupMapper groups, UnModuleMapper modules,
                                  UnModuleTenantMapper tenantModules) {
        this.support = support;
        this.groups = groups;
        this.modules = modules;
        this.tenantModules = tenantModules;
    }

    @GetMapping("/groups")
    public List<UnModuleGroup> groups(@RequestParam long tenantId, HttpServletRequest request) {
        support.member(request, tenantId);
        long systemId = support.tenant(tenantId).getSystemId();
        return groups.selectList(new LambdaQueryWrapper<UnModuleGroup>()
                .eq(UnModuleGroup::getSystemId, systemId).orderByAsc(UnModuleGroup::getParentId, UnModuleGroup::getSortNo, UnModuleGroup::getId));
    }

    @PostMapping("/groups")
    public UnModuleGroup createGroup(@RequestParam long tenantId, @Valid @RequestBody GroupForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        long systemId = support.tenant(tenantId).getSystemId();
        code(body.code());
        if (groups.selectCount(new LambdaQueryWrapper<UnModuleGroup>().eq(UnModuleGroup::getSystemId, systemId)
                .eq(UnModuleGroup::getCode, body.code())) > 0) conflict("分组编码已存在");
        long parentId = body.parentId() == null ? 0 : body.parentId();
        checkParent(systemId, parentId);
        UnModuleGroup group = new UnModuleGroup();
        group.setSystemId(systemId);
        group.setParentId(parentId);
        group.setCode(body.code());
        group.setName(body.name());
        group.setIcon(body.icon());
        group.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        group.setStatus((byte) 1);
        groups.insert(group);
        return groups.selectById(group.getId());
    }

    @PutMapping("/groups/{groupId}")
    public UnModuleGroup updateGroup(@RequestParam long tenantId, @PathVariable long groupId,
                                     @Valid @RequestBody GroupForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModuleGroup group = group(tenantId, groupId);
        if (!group.getCode().equals(body.code())) conflict("分组编码不可修改");
        long parentId = body.parentId() == null ? 0 : body.parentId();
        if (parentId == groupId) bad("分组不能作为自己的上级");
        checkParent(group.getSystemId(), parentId);
        group.setParentId(parentId);
        group.setName(body.name());
        group.setIcon(body.icon());
        group.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        groups.updateById(group);
        return groups.selectById(groupId);
    }

    @DeleteMapping("/groups/{groupId}")
    public void stopGroup(@RequestParam long tenantId, @PathVariable long groupId, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModuleGroup group = group(tenantId, groupId);
        if (groups.selectCount(new LambdaQueryWrapper<UnModuleGroup>().eq(UnModuleGroup::getParentId, groupId).eq(UnModuleGroup::getStatus, 1)) > 0
                || modules.selectCount(new LambdaQueryWrapper<UnModule>().eq(UnModule::getGroupId, groupId).eq(UnModule::getStatus, 1)) > 0) {
            conflict("分组下有启用的分组或模块");
        }
        group.setStatus((byte) 0);
        groups.updateById(group);
    }

    /** 列出同一业务系统的模块，并返回租户实际是否启用。 */
    @GetMapping
    public List<Map<String, Object>> modules(@RequestParam long tenantId, HttpServletRequest request) {
        support.member(request, tenantId);
        long systemId = support.tenant(tenantId).getSystemId();
        List<UnModule> all = modules.selectList(new LambdaQueryWrapper<UnModule>().eq(UnModule::getSystemId, systemId)
                .orderByAsc(UnModule::getGroupId, UnModule::getSortNo, UnModule::getId));
        Map<Long, UnModuleTenant> enabled = new HashMap<>();
        tenantModules.selectList(new LambdaQueryWrapper<UnModuleTenant>().eq(UnModuleTenant::getTenantId, tenantId))
                .forEach(row -> enabled.put(row.getModuleId(), row));
        List<Map<String, Object>> result = new ArrayList<>();
        for (UnModule module : all) {
            result.add(Map.of("module", module, "tenantEnabled",
                    enabled.containsKey(module.getId()) && ModuleSupport.enabled(enabled.get(module.getId()).getStatus())));
        }
        return result;
    }

    @PostMapping
    @Transactional
    public UnModule createModule(@RequestParam long tenantId, @Valid @RequestBody ModuleForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        long systemId = support.tenant(tenantId).getSystemId();
        code(body.code());
        UnModuleGroup group = group(tenantId, body.groupId());
        if (!ModuleSupport.enabled(group.getStatus())) bad("分组已停用");
        if (modules.selectCount(new LambdaQueryWrapper<UnModule>().eq(UnModule::getSystemId, systemId).eq(UnModule::getCode, body.code())) > 0)
            conflict("模块编码已存在");
        UnModule module = new UnModule();
        module.setSystemId(systemId);
        module.setGroupId(body.groupId());
        module.setCode(body.code());
        module.setName(body.name());
        module.setIcon(body.icon());
        module.setDescription(body.description());
        module.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        module.setStatus((byte) 1);
        modules.insert(module);
        UnModuleTenant relation = new UnModuleTenant();
        relation.setTenantId(tenantId);
        relation.setModuleId(module.getId());
        relation.setSortNo(0);
        relation.setStatus((byte) 1);
        tenantModules.insert(relation);
        return modules.selectById(module.getId());
    }

    @PutMapping("/{moduleId}")
    public UnModule updateModule(@RequestParam long tenantId, @PathVariable long moduleId,
                                 @Valid @RequestBody ModuleForm body, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = support.module(tenantId, moduleId);
        if (!module.getCode().equals(body.code())) conflict("模块编码不可修改");
        group(tenantId, body.groupId());
        module.setGroupId(body.groupId());
        module.setName(body.name());
        module.setIcon(body.icon());
        module.setDescription(body.description());
        module.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
        modules.updateById(module);
        return modules.selectById(moduleId);
    }

    @PutMapping("/{moduleId}/tenant-status")
    public UnModuleTenant tenantStatus(@RequestParam long tenantId, @PathVariable long moduleId,
                                       @RequestBody TenantStatus body, HttpServletRequest request) {
        support.admin(request, tenantId);
        UnModule module = modules.selectById(moduleId);
        if (module == null || !module.getSystemId().equals(support.tenant(tenantId).getSystemId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模块不存在");
        UnModuleTenant relation = tenantModules.selectOne(new LambdaQueryWrapper<UnModuleTenant>()
                .eq(UnModuleTenant::getTenantId, tenantId).eq(UnModuleTenant::getModuleId, moduleId));
        if (relation == null) {
            relation = new UnModuleTenant();
            relation.setTenantId(tenantId);
            relation.setModuleId(moduleId);
            relation.setSortNo(body.sortNo() == null ? 0 : body.sortNo());
            relation.setStatus((byte) (body.enabled() ? 1 : 0));
            tenantModules.insert(relation);
        } else {
            relation.setStatus((byte) (body.enabled() ? 1 : 0));
            if (body.sortNo() != null) relation.setSortNo(body.sortNo());
            tenantModules.updateById(relation);
        }
        return tenantModules.selectById(relation.getId());
    }


    private UnModuleGroup group(long tenantId, long groupId) {
        UnModuleGroup row = groups.selectById(groupId);
        if (row == null || !row.getSystemId().equals(support.tenant(tenantId).getSystemId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分组不存在");
        return row;
    }

    private void checkParent(long systemId, long parentId) {
        if (parentId == 0) return;
        UnModuleGroup parent = groups.selectById(parentId);
        if (parent == null || !parent.getSystemId().equals(systemId) || !ModuleSupport.enabled(parent.getStatus())) bad("上级分组无效");
    }

    private static void code(String code) { if (code == null || !CODE.matcher(code).matches()) bad("编码格式错误"); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public record GroupForm(@NotBlank String code, @NotBlank String name, Long parentId, String icon, Integer sortNo) { }
    public record ModuleForm(@NotNull Long groupId, @NotBlank String code, @NotBlank String name,
                             String icon, String description, Integer sortNo) { }
    public record TenantStatus(boolean enabled, Integer sortNo) { }
}
