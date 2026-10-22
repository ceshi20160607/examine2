package com.unique.unexamine.sys.module.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.field.FieldValidationService;
import com.unique.unexamine.sys.manage.SystemManageService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.module.base.entity.UnModuleDict;
import com.unique.unexamine.sys.module.base.service.IUnModuleDictService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 字典草稿与发布管理；模块和工作表单使用同一份发布选项。 */
@Service
public class DictionaryService {
    private static final Set<String> TYPES = Set.of("LIST", "TREE", "CASCADE", "STATUS", "TAG", "OPTIONS");
    private final SystemManageService systems;
    private final IUnSysSystemService systemRows;
    private final IUnModuleDictService dictionaries;
    private final ConfigurationCache cache;
    private final ObjectMapper json;
    private final FieldValidationService validation;

    /** 选项标识在发布后不得删除；停用只阻止新选择，历史值继续展示。 */
    public record SaveRequest(Long id, @NotBlank @Size(max=80) String code,
                              @NotBlank @Size(max=100) String name, @NotBlank String type,
                              @NotNull List<Map<String, Object>> options) {}
    /** 可缓存的发布结果，名称、层级、颜色与顺序均来自已发布配置。 */
    public record PublishedDictionary(Long id, String name, String type, int version,
                                      List<Map<String, Object>> options) {}

    /** 注入贴表服务及真正共用的配置缓存。 */
    public DictionaryService(SystemManageService systems, IUnSysSystemService systemRows,
            IUnModuleDictService dictionaries, ConfigurationCache cache, ObjectMapper json, FieldValidationService validation) {
        this.systems=systems; this.systemRows=systemRows; this.dictionaries=dictionaries;
        this.cache=cache; this.json=json;
        this.validation=validation;
    }

    /** 管理员保存草稿，已发布版本保持可用。 */
    @Transactional(rollbackFor=Exception.class)
    public Long save(String token, Long systemId, Long tenantId, SaveRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        String type=request.type().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type) || !request.code().matches("[a-z][a-z0-9_]{1,79}"))
            throw new IllegalArgumentException("字典类型或编码无效");
        validateOptions(type,request.options());
        UnModuleDict row=request.id()==null?new UnModuleDict():locked(request.id());
        if (request.id()!=null && (!systemId.equals(row.getSystemId()) || !context.tenantId().equals(row.getTenantId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"不能修改其他系统或租户的字典");
        if (row.getId()!=null && !ids(request.options()).containsAll(ids(readOptions(row.getOptionsJson()))))
            throw new IllegalArgumentException("字典基础项不能删除，请通过字段可选范围控制使用");
        if (row.getPublishedOptionsJson()!=null) {
            Set<String> ids=ids(request.options());
            if (!ids.containsAll(ids(snapshot(row).options())))
                throw new IllegalArgumentException("已发布选项不能删除，请设置 enabled=false");
            if (!row.getCode().equals(request.code())) throw new IllegalArgumentException("已发布字典编码不能修改");
        }
        if (dictionaries.count(new LambdaQueryWrapper<UnModuleDict>().eq(UnModuleDict::getSystemId,systemId)
                .eq(UnModuleDict::getTenantId,context.tenantId()).eq(UnModuleDict::getCode,request.code())
                .ne(row.getId()!=null,UnModuleDict::getId,row.getId()))>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"字典编码已存在");
        row.setSystemId(systemId); row.setTenantId(context.tenantId()); row.setCode(request.code());
        row.setName(request.name().trim()); row.setDictType(type); row.setOptionsJson(write(request.options()));
        if (row.getStatus()==null) row.setStatus("DRAFT");
        dictionaries.addOrUpdate(row);
        return row.getId();
    }

    /** 发布时递增数据库版本，缓存失效失败或进程重启也不会返回旧快照。 */
    @Transactional(rollbackFor=Exception.class)
    public PublishedDictionary publish(String token,Long systemId,Long tenantId,Long id) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        UnModuleDict row=locked(id);
        if (!systemId.equals(row.getSystemId()) || !context.tenantId().equals(row.getTenantId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"不能发布其他系统或租户的字典");
        List<Map<String,Object>> options=readOptions(row.getOptionsJson());
        validateOptions(row.getDictType(),options);
        row.setPublishedVersion((row.getPublishedVersion()==null?0:row.getPublishedVersion())+1);
        row.setPublishedOptionsJson(write(Map.of("name",row.getName(),"type",row.getDictType(),"options",options)));
        row.setStatus("PUBLISHED"); dictionaries.addOrUpdate(row);
        cache.invalidateAfterCommit(namespace(systemId,row.getTenantId()));
        return snapshot(row);
    }

    /** 对外读取先验证实时身份，不能仅凭字典 ID 获得跨租户配置。 */
    public PublishedDictionary published(String token,Long systemId,Long tenantId,Long id) {
        var context=systems.context(token,systemId,tenantId);
        return published(systemId,context.tenantId(),id);
    }

    /** 业务服务已建立上下文后使用；子租户可继承主租户发布字典。 */
    public PublishedDictionary published(Long systemId,Long tenantId,Long id) {
        UnModuleDict row=dictionaries.queryById(id);
        Long root=systemRows.queryById(systemId).getRootTenantId();
        if (!systemId.equals(row.getSystemId()) || (!tenantId.equals(row.getTenantId()) && !root.equals(row.getTenantId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"字典不属于当前系统租户");
        if (!"PUBLISHED".equals(row.getStatus()) || row.getPublishedOptionsJson()==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"字典尚未发布");
        return cache.get(namespace(systemId,row.getTenantId()),id+":"+row.getPublishedVersion(),
                PublishedDictionary.class,()->snapshot(row));
    }

    /** 校验稳定标识及完整父链，保留设计器顺序与额外展示配置。 */
    private void validateOptions(String type,List<Map<String,Object>> options) {
        validation.validateDefinition(new FieldDefinition("dictionary","字典",FieldType.SINGLE_SELECT,false,Map.of("options",options)));
        Map<String,Map<String,Object>> byId=new LinkedHashMap<>();
        for (var option:options) {
            Object id=option.get("id"); Object label=option.get("label");
            if (id==null || String.valueOf(id).isBlank() || !(label instanceof String s) || s.isBlank()
                    || !(id instanceof String || id instanceof Number) || byId.put(String.valueOf(id),option)!=null
                    || option.containsKey("enabled") && !(option.get("enabled") instanceof Boolean))
                throw new IllegalArgumentException("字典选项标识名称或状态无效");
        }
        for (var option:options) {
            Set<String> visited=new HashSet<>(); String current=String.valueOf(option.get("id"));
            while (current!=null) {
                if (!visited.add(current) || !byId.containsKey(current)) throw new IllegalArgumentException("字典父级不存在或形成循环");
                Object parent=byId.get(current).get("parentId");
                if (parent!=null && !Set.of("TREE","CASCADE").contains(type)) throw new IllegalArgumentException("平面字典不能设置父级");
                current=parent==null?null:String.valueOf(parent);
            }
        }
    }

    private Set<String> ids(List<Map<String,Object>> options) {
        Set<String> result=new HashSet<>(); options.forEach(o->result.add(String.valueOf(o.get("id")))); return result;
    }
    /** 同一字典的保存与发布互斥，防止并发发布复用相同版本号。 */
    private UnModuleDict locked(Long id) {
        var row=dictionaries.getOne(new LambdaQueryWrapper<UnModuleDict>().eq(UnModuleDict::getId,id).last("FOR UPDATE"));
        if (row==null) throw new java.util.NoSuchElementException("字典不存在"); return row;
    }
    private String namespace(Long systemId,Long tenantId) { return "dict:"+systemId+":"+tenantId; }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("字典配置无法保存",ex); }
    }
    private List<Map<String,Object>> readOptions(String value) {
        try { return json.readValue(value,new TypeReference<List<Map<String,Object>>>(){}); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("字典选项损坏",ex); }
    }
    private PublishedDictionary snapshot(UnModuleDict row) {
        try {
            var tree=json.readTree(row.getPublishedOptionsJson());
            return new PublishedDictionary(row.getId(),tree.path("name").asText(),tree.path("type").asText(),
                    row.getPublishedVersion(),json.convertValue(tree.get("options"),new TypeReference<List<Map<String,Object>>>(){}));
        } catch (JsonProcessingException ex) { throw new IllegalStateException("字典发布内容损坏",ex); }
    }
}
