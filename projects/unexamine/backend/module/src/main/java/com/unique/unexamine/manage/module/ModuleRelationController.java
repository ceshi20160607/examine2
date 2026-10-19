package com.unique.unexamine.manage.module;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.base.entity.*;
import com.unique.unexamine.base.mapper.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.regex.Pattern;

/** 模块关联配置与记录之间的关联。 */
@RestController
@RequestMapping("/api/modules/{moduleId}")
public class ModuleRelationController {
    private static final Pattern CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");
    private final ModuleSupport support;
    private final ModuleRecordAccess access;
    private final UnModuleRelationConfigMapper relationConfigs;
    private final UnModuleRelationMapper relations;
    private final UnModuleRecordMapper records;
    private final UnModuleMapper modules;
    private final ObjectMapper json;

    public ModuleRelationController(ModuleSupport support, ModuleRecordAccess access,
                                    UnModuleRelationConfigMapper relationConfigs,
                                    UnModuleRelationMapper relations, UnModuleRecordMapper records,
                                    UnModuleMapper modules, ObjectMapper json) {
        this.support = support;
        this.access = access;
        this.relationConfigs = relationConfigs;
        this.relations = relations;
        this.records = records;
        this.modules = modules;
        this.json = json;
    }

    @GetMapping("/relation-configs")
    public List<UnModuleRelationConfig> relationConfigs(@PathVariable long moduleId, @RequestParam long tenantId,
                                                          HttpServletRequest request) {
        read(request, tenantId, moduleId);
        return relationConfigs.selectList(new LambdaQueryWrapper<UnModuleRelationConfig>()
                .eq(UnModuleRelationConfig::getSourceModuleId, moduleId).eq(UnModuleRelationConfig::getStatus, 1));
    }

    @PostMapping("/relation-configs")
    public UnModuleRelationConfig addRelationConfig(@PathVariable long moduleId, @RequestParam long tenantId,
                                                     @Valid @RequestBody RelationConfigForm body, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        code(body.code());
        if (body.direction() != null && !Set.of("forward", "both").contains(body.direction())) bad("关联方向无效");
        UnModule target = modules.selectById(body.targetModuleId());
        if (target == null || !target.getSystemId().equals(support.tenant(tenantId).getSystemId())) bad("关联目标模块不属于当前系统");
        if (relationConfigs.selectCount(new LambdaQueryWrapper<UnModuleRelationConfig>()
                .eq(UnModuleRelationConfig::getSourceModuleId, moduleId)
                .eq(UnModuleRelationConfig::getRelationCode, body.code())) > 0) conflict("关联编码已存在");
        UnModuleRelationConfig row = new UnModuleRelationConfig();
        row.setSourceModuleId(moduleId);
        row.setTargetModuleId(body.targetModuleId());
        row.setRelationCode(body.code());
        row.setName(body.name());
        row.setDirection(body.direction() == null ? "forward" : body.direction());
        row.setMappingJson(body.mapping() == null ? null : encode(body.mapping()));
        row.setStatus((byte) 1);
        relationConfigs.insert(row);
        return relationConfigs.selectById(row.getId());
    }

    @PutMapping("/relation-configs/{id}")
    public UnModuleRelationConfig updateRelationConfig(@PathVariable long moduleId, @PathVariable long id,
                                                        @RequestParam long tenantId, @Valid @RequestBody RelationConfigForm body,
                                                        HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleRelationConfig row = relationConfigs.selectById(id);
        if (row == null || !row.getSourceModuleId().equals(moduleId)) missing("关联配置不存在");
        if (!row.getRelationCode().equals(body.code()) || !row.getTargetModuleId().equals(body.targetModuleId()))
            conflict("关联编码和目标模块不可直接修改");
        if (body.direction() != null && !Set.of("forward", "both").contains(body.direction())) bad("关联方向无效");
        row.setName(body.name());
        row.setDirection(body.direction() == null ? "forward" : body.direction());
        row.setMappingJson(body.mapping() == null ? null : encode(body.mapping()));
        relationConfigs.updateById(row);
        return relationConfigs.selectById(id);
    }

    @DeleteMapping("/relation-configs/{id}")
    public void stopRelationConfig(@PathVariable long moduleId, @PathVariable long id,
                                   @RequestParam long tenantId, HttpServletRequest request) {
        write(request, tenantId, moduleId);
        UnModuleRelationConfig row = relationConfigs.selectById(id);
        if (row == null || !row.getSourceModuleId().equals(moduleId)) missing("关联配置不存在");
        row.setStatus((byte) 0);
        relationConfigs.updateById(row);
    }

    @GetMapping("/records/{recordId}/relations")
    public List<UnModuleRelation> relations(@PathVariable long moduleId, @PathVariable long recordId,
                                             @RequestParam long tenantId, HttpServletRequest request) {
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        access.requireRead(scope, record(tenantId, moduleId, recordId));
        return relations.selectList(new LambdaQueryWrapper<UnModuleRelation>()
                .eq(UnModuleRelation::getTenantId, tenantId)
                .and(q -> q.eq(UnModuleRelation::getSourceRecordId, recordId)
                        .or().eq(UnModuleRelation::getTargetRecordId, recordId)))
                .stream().filter(link -> {
                    if (!link.getSourceRecordId().equals(recordId)) {
                        UnModuleRelationConfig configuration = relationConfigs.selectById(link.getRelationConfigId());
                        if (configuration == null || !"both".equals(configuration.getDirection())) return false;
                    }
                    Long otherId = link.getSourceRecordId().equals(recordId)
                            ? link.getTargetRecordId() : link.getSourceRecordId();
                    UnModuleRecord other = records.selectById(otherId);
                    if (other == null || !other.getTenantId().equals(tenantId) || other.getRecordStatus() == 2) return false;
                    try { access.requireRead(scope, other); return true; }
                    catch (ResponseStatusException e) { return false; }
                }).toList();
    }

    @PostMapping("/records/{recordId}/relations")
    public UnModuleRelation addRelation(@PathVariable long moduleId, @PathVariable long recordId, @RequestParam long tenantId,
                                         @RequestBody RelationForm body, HttpServletRequest request) {
        UnSysTenantMember actor = support.member(request, tenantId);
        ModuleRecordAccess.Scope scope = access.scope(request, tenantId);
        support.module(tenantId, moduleId);
        access.requireWrite(scope, record(tenantId, moduleId, recordId));
        UnModuleRelationConfig config = relationConfigs.selectById(body.configId());
        if (config == null || !config.getSourceModuleId().equals(moduleId) || !ModuleSupport.enabled(config.getStatus())) bad("关联配置无效");
        access.requireRead(scope, record(tenantId, config.getTargetModuleId(), body.targetRecordId()));
        if (relations.selectCount(new LambdaQueryWrapper<UnModuleRelation>().eq(UnModuleRelation::getTenantId, tenantId)
                .eq(UnModuleRelation::getRelationConfigId, body.configId()).eq(UnModuleRelation::getSourceRecordId, recordId)
                .eq(UnModuleRelation::getTargetRecordId, body.targetRecordId())) > 0) conflict("记录关联已存在");
        UnModuleRelation row = new UnModuleRelation();
        row.setTenantId(tenantId);
        row.setRelationConfigId(body.configId());
        row.setSourceRecordId(recordId);
        row.setTargetRecordId(body.targetRecordId());
        row.setCreateMemberId(actor.getMemberId());
        relations.insert(row);
        return relations.selectById(row.getId());
    }

    @DeleteMapping("/records/{recordId}/relations/{id}")
    public void deleteRelation(@PathVariable long moduleId, @PathVariable long recordId, @PathVariable long id,
                               @RequestParam long tenantId, HttpServletRequest request) {
        access.requireWrite(access.scope(request, tenantId), record(tenantId, moduleId, recordId));
        UnModuleRelation row = relations.selectById(id);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getSourceRecordId().equals(recordId)) missing("关联不存在");
        relations.deleteById(id);
    }


    private UnModuleRecord record(long tenantId, long moduleId, long recordId) {
        support.module(tenantId, moduleId);
        UnModuleRecord row = records.selectById(recordId);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)
                || row.getRecordStatus() == 2) missing("记录不存在");
        return row;
    }

    private void read(HttpServletRequest request, long tenantId, long moduleId) {
        support.member(request, tenantId);
        support.module(tenantId, moduleId);
    }

    private void write(HttpServletRequest request, long tenantId, long moduleId) {
        support.admin(request, tenantId);
        support.module(tenantId, moduleId);
    }

    private String encode(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (Exception e) { bad("配置 JSON 无效"); return null; }
    }

    private static void code(String code) { if (code == null || !CODE.matcher(code).matches()) bad("编码格式无效"); }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static void missing(String message) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    public record RelationConfigForm(@NotBlank String code, @NotBlank String name, @NotNull Long targetModuleId,
                                     String direction, JsonNode mapping) { }
    public record RelationForm(@NotNull Long configId, @NotNull Long targetRecordId) { }
}
