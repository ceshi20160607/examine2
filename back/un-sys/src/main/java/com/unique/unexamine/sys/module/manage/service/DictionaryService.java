package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleDict;
import com.unique.unexamine.sys.module.base.service.IUnModuleDictService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 字典基础项逐条保存值与名称；字段选用范围和展示设置归字段 JSON 配置。 */
@Service
public class DictionaryService {
    private final SystemManageService systems;
    private final IUnSysSystemService systemRows;
    private final IUnModuleDictService dictionaries;
    private final IUnSysLogOptService logs;
    private final ConfigurationCache cache;
    private final ObjectMapper json;

    /** 新增基础项或修改名称，已有基础值和父级不能被改作其他含义。 */
    public record SaveRequest(Long id,@NotBlank @Size(max=100) String value,
                              @NotBlank @Size(max=100) String name,Long parentId) {}
    /** 字段引用 id，value 是例如 1、0 的基础值，name 为当前显示名称。 */
    public record Item(Long id,String value,String name,Long parentId,long revision) {}
    /** 基础项列表只包含当前租户和可继承的主租户资料。 */
    public record ItemPage(long total,long current,long size,List<Item> records) {}

    /** 基础表 CRUD 由生成服务提供，作用域与不可删除规则由本服务负责。 */
    public DictionaryService(SystemManageService systems,IUnSysSystemService systemRows,
            IUnModuleDictService dictionaries,IUnSysLogOptService logs,ConfigurationCache cache,ObjectMapper json) {
        this.systems=systems; this.systemRows=systemRows; this.dictionaries=dictionaries;
        this.logs=logs; this.cache=cache; this.json=json;
    }

    /** 保存即生效；字典不存在整组选项的草稿或发布操作。 */
    @Transactional(rollbackFor=Exception.class)
    public Item save(String token,Long systemId,Long tenantId,SaveRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        var row=request.id()==null?new UnModuleDict():dictionaries.getOne(
                new LambdaQueryWrapper<UnModuleDict>().eq(UnModuleDict::getId,request.id()).last("FOR UPDATE"));
        if (row==null) throw new NoSuchElementException("字典基础项不存在");
        String value=request.value().trim(),name=request.name().trim();
        if (row.getId()!=null) {
            if (!systemId.equals(row.getSystemId()) || !context.tenantId().equals(row.getTenantId())) forbidden();
            if (!value.equals(row.getDictValue()) || !Objects.equals(request.parentId(),row.getParentId()))
                throw new IllegalArgumentException("已有字典基础项只能修改名称，基础值和父级不可变更");
        } else {
            if (request.parentId()!=null) items(systemId,context.tenantId(),List.of(request.parentId()));
            row.setSystemId(systemId); row.setTenantId(context.tenantId()); row.setDictValue(value);
            row.setParentId(request.parentId()); row.setRevision(0L);
        }
        Item before=row.getId()==null?null:view(row);
        row.setName(name); row.setRevision(row.getRevision()+1); dictionaries.addOrUpdate(row);
        var log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(context.tenantId());
        log.setAccountId(systems.accountId(token,systemId)); log.setActionCode("DICTIONARY_ITEM_SAVE");
        log.setObjectType("DICTIONARY_ITEM"); log.setObjectId(row.getId());
        Map<String,Object> detail=new LinkedHashMap<>(); detail.put("before",before); detail.put("after",view(row));
        try { log.setDetailJson(json.writeValueAsString(detail)); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("字典资料无法记录",ex); }
        logs.addOrUpdate(log); cache.invalidateAfterCommit(namespace(systemId,row.getTenantId())); return view(row);
    }

    /** 普通成员可查询作用域内基础项，角色字段授权仍在业务表单入口检查。 */
    public ItemPage list(String token,Long systemId,Long tenantId,long page,long size,String name,String value,Long parentId) {
        var context=systems.context(token,systemId,tenantId);
        if (page<1 || size<1 || size>100) throw new IllegalArgumentException("分页参数无效");
        Long root=systemRows.queryById(systemId).getRootTenantId();
        var query=new LambdaQueryWrapper<UnModuleDict>().eq(UnModuleDict::getSystemId,systemId)
                .in(UnModuleDict::getTenantId,new HashSet<>(List.of(root,context.tenantId())))
                .like(name!=null && !name.isBlank(),UnModuleDict::getName,name)
                .eq(value!=null,UnModuleDict::getDictValue,value).eq(parentId!=null,UnModuleDict::getParentId,parentId)
                .orderByAsc(UnModuleDict::getId);
        var result=dictionaries.page(new Page<>(page,size),query);
        return new ItemPage(result.getTotal(),page,size,result.getRecords().stream().map(this::view).toList());
    }

    /** 查询基础项不依赖发布状态，名称修改后所有引用读取当前名称。 */
    public Item get(String token,Long systemId,Long tenantId,Long id) {
        var context=systems.context(token,systemId,tenantId);
        return items(systemId,context.tenantId(),List.of(id)).get(id);
    }

    /** 业务服务已建立上下文后，批量解析选中的稳定 ID；新基础项不会自动加入字段。 */
    public Map<Long,Item> items(Long systemId,Long tenantId,Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        if (ids.stream().anyMatch(id->id==null || id<1)) throw new IllegalArgumentException("字典基础项 ID 无效");
        Long root=systemRows.queryById(systemId).getRootTenantId(); Map<Long,Item> result=new LinkedHashMap<>();
        for (var row:dictionaries.listByIds(ids)) {
            if (!systemId.equals(row.getSystemId()) || !List.of(root,tenantId).contains(row.getTenantId())) forbidden();
            // 每次核对数据库提交版本，失效失败和重启不能复用旧名称。
            result.put(row.getId(),cache.get(namespace(systemId,row.getTenantId()),row.getId()+":"+row.getRevision(),
                    Item.class,()->view(row)));
        }
        if (result.size()!=new HashSet<>(ids).size()) throw new IllegalArgumentException("选用的字典基础项不存在");
        return result;
    }

    private Item view(UnModuleDict row) { return new Item(row.getId(),row.getDictValue(),row.getName(),row.getParentId(),row.getRevision()); }
    private String namespace(Long systemId,Long tenantId) { return "dictionary-item:"+systemId+":"+tenantId; }
    private void forbidden() { throw new ResponseStatusException(HttpStatus.FORBIDDEN,"字典基础项不属于当前系统租户"); }
}
