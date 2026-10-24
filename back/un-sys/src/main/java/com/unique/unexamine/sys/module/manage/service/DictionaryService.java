package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleDict;
import com.unique.unexamine.sys.module.base.service.IUnModuleDictService;
import com.unique.unexamine.sys.module.manage.bo.DictionarySearchBO;
import com.unique.unexamine.sys.module.manage.dto.DictionaryDto.CreateRequest;
import com.unique.unexamine.sys.module.manage.dto.DictionaryDto.Item;
import com.unique.unexamine.sys.module.manage.dto.DictionaryDto.UpdateRequest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 字典基础项逐条保存值与名称；字段选用范围和展示设置归字段 JSON 配置。 */
@Service
public class DictionaryService {
    private final SystemManageService systems;
    private final IUnSysSystemService systemRows;
    private final IUnModuleDictService dictionaries;
    private final IUnSysLogOptService logs;
    private final ConfigurationCache cache;
    private final ObjectMapper json;

    /** 基础表 CRUD 由生成服务提供，作用域与不可删除规则由本服务负责。 */
    public DictionaryService(SystemManageService systems,IUnSysSystemService systemRows,
            IUnModuleDictService dictionaries,IUnSysLogOptService logs,ConfigurationCache cache,ObjectMapper json) {
        this.systems=systems; this.systemRows=systemRows; this.dictionaries=dictionaries;
        this.logs=logs; this.cache=cache; this.json=json;
    }

    /** 新建基础项时确定不可改变的分类、值和父级；保存成功后立即生效。 */
    @Transactional(rollbackFor=Exception.class)
    public Item create(String token,Long systemId,Long tenantId,CreateRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        String code=request.dictCode().trim(),value=request.value().trim(),name=request.name().trim();
        UnModuleDict parent=null;
        if (request.parentId()!=null) {
            items(systemId,context.tenantId(),List.of(request.parentId()));
            parent=dictionaries.queryById(request.parentId());
            if (!code.equals(parent.getDictCode()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"级联父项必须属于同一字典分类");
        }
        var row=new UnModuleDict();
        row.setSystemId(systemId); row.setTenantId(context.tenantId()); row.setDictCode(code); row.setDictValue(value);
        row.setParentId(request.parentId()); row.setRevision(0L); row.setName(name);
        row.setDepthLevel(parent==null?0:parent.getDepthLevel()+1);
        row.setDepthPath(parent==null?null:parent.getDepthPath());
        row.setSortNo(request.sortNo()==null?0:request.sortNo());
        row.setColor(request.color()); row.setIcon(request.icon());
        row.setDefaultFlag(Boolean.TRUE.equals(request.defaultFlag()));
        return saveAndLog(token,systemId,row,null);
    }

    /** 现有基础项只修改展示资料，不接受基础值、分类或父级作为更新参数。 */
    @Transactional(rollbackFor=Exception.class)
    public Item update(String token,Long systemId,Long tenantId,Long id,UpdateRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        var row=dictionaries.getOne(new LambdaQueryWrapper<UnModuleDict>()
                .eq(UnModuleDict::getId,id).last("FOR UPDATE"));
        if (row==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"字典基础项不存在");
        if (!systemId.equals(row.getSystemId()) || !context.tenantId().equals(row.getTenantId())) forbidden();
        Item before=view(row);
        row.setName(request.name().trim());
        if (request.sortNo()!=null) row.setSortNo(request.sortNo());
        if (request.color()!=null) row.setColor(request.color());
        if (request.icon()!=null) row.setIcon(request.icon());
        if (request.defaultFlag()!=null) row.setDefaultFlag(request.defaultFlag());
        return saveAndLog(token,systemId,row,before);
    }

    /** 基础项和审计日志在同一事务中保存，缓存只在提交后失效。 */
    private Item saveAndLog(String token,Long systemId,UnModuleDict row,Item before) {
        row.setRevision(row.getRevision()+1); dictionaries.addOrUpdate(row);
        if (before==null) {
            row.setDepthPath(row.getDepthPath()==null?row.getId().toString():row.getDepthPath()+","+row.getId());
            dictionaries.addOrUpdate(row);
        }
        var log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(row.getTenantId());
        log.setAccountId(systems.accountId(token,systemId)); log.setActionCode("DICTIONARY_ITEM_SAVE");
        log.setObjectType("DICTIONARY_ITEM"); log.setObjectId(row.getId());
        Map<String,Object> detail=new LinkedHashMap<>(); detail.put("before",before); detail.put("after",view(row));
        try { log.setDetailJson(json.writeValueAsString(detail)); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("字典资料无法记录", ex); }
        logs.addOrUpdate(log); cache.invalidateAfterCommit(namespace(systemId,row.getTenantId())); return view(row);
    }

    /** 普通成员可查询作用域内基础项，角色字段授权仍在业务表单入口检查。 */
    public Page<Item> list(String token,Long systemId,Long tenantId,DictionarySearchBO search) {
        var context=systems.context(token,systemId,tenantId);
        Long root=systemRows.queryById(systemId).getRootTenantId();
        var query=new LambdaQueryWrapper<UnModuleDict>().eq(UnModuleDict::getSystemId,systemId)
                .in(UnModuleDict::getTenantId,new HashSet<>(List.of(root,context.tenantId())))
                .eq(search.getDictCode()!=null && !search.getDictCode().isBlank(),UnModuleDict::getDictCode,search.getDictCode())
                .like(search.getName()!=null && !search.getName().isBlank(),UnModuleDict::getName,search.getName())
                .eq(search.getValue()!=null,UnModuleDict::getDictValue,search.getValue())
                .eq(search.getParentId()!=null,UnModuleDict::getParentId,search.getParentId())
                .orderByAsc(UnModuleDict::getSortNo).orderByAsc(UnModuleDict::getId);
        var result=dictionaries.page(search.parse(),query);
        return new Page<Item>(result.getCurrent(), result.getSize(), result.getTotal())
                .setRecords(result.getRecords().stream().map(this::view).toList());
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

    private Item view(UnModuleDict row) { return new Item(row.getId(),row.getDictCode(),row.getDictValue(),row.getName(),
            row.getParentId(),row.getDepthLevel(),row.getSortNo(),row.getColor(),row.getIcon(),
            Boolean.TRUE.equals(row.getDefaultFlag())); }
    private String namespace(Long systemId,Long tenantId) { return "dictionary-item:"+systemId+":"+tenantId; }
    private void forbidden() { throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"字典基础项不属于当前系统租户"); }
}
