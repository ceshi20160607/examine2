package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.module.base.entity.UnModuleRule;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.module.base.service.IUnModuleService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRuleService;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordStatusDto.SaveRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordStatusDto.View;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 模块业务状态使用现有规则表保存；编码是动作配置和记录值的稳定引用。 */
@Service
public class ModuleRecordStatusService {
    public static final String DRAFT="DRAFT", NORMAL="NORMAL", DELETED="DELETED";
    private static final String TYPE="RECORD_STATUS";
    private static final Set<String> BUILTIN=Set.of(DRAFT,NORMAL,DELETED);
    private final SystemManageService systems;
    private final IUnModuleRuleService rules;
    private final IUnModuleService moduleRows;
    private final IUnSysSystemService systemRows;
    private final IUnSysLogOptService logs;
    private final ObjectMapper json;

    public ModuleRecordStatusService(SystemManageService systems,IUnModuleRuleService rules,
                                     IUnModuleService moduleRows,IUnSysSystemService systemRows,
                                     IUnSysLogOptService logs,ObjectMapper json) {
        this.systems=systems; this.rules=rules; this.moduleRows=moduleRows; this.systemRows=systemRows;
        this.logs=logs; this.json=json;
    }

    /** 预置编码固定，自定义状态按模块和租户读取。 */
    public List<View> configuration(String token,Long systemId,Long tenantId,Long moduleId) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        var module=module(systemId,context.tenantId(),moduleId,false);
        Map<String,View> result=new java.util.LinkedHashMap<>();
        result.put(DRAFT,new View(DRAFT,"草稿",true,true));
        result.put(NORMAL,new View(NORMAL,"正常",true,true));
        result.put(DELETED,new View(DELETED,"删除",true,true));
        for (var row:rules.list(new LambdaQueryWrapper<UnModuleRule>().eq(UnModuleRule::getModuleId,moduleId)
                .eq(UnModuleRule::getTenantId,module.getTenantId()).eq(UnModuleRule::getRuleType,TYPE)
                .orderByAsc(UnModuleRule::getId))) result.put(row.getRuleCode(),view(row));
        if (!module.getTenantId().equals(context.tenantId()))
            for (var row:rules.list(new LambdaQueryWrapper<UnModuleRule>().eq(UnModuleRule::getModuleId,moduleId)
                    .eq(UnModuleRule::getTenantId,context.tenantId()).eq(UnModuleRule::getRuleType,TYPE)
                    .orderByAsc(UnModuleRule::getId))) result.put(row.getRuleCode(),view(row));
        return List.copyOf(result.values());
    }

    /** 更新名称或启停立即生效；已有记录仍保留原状态编码。 */
    @Transactional(rollbackFor=Exception.class)
    public View save(String token,Long systemId,Long tenantId,Long moduleId,SaveRequest request) {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        module(systemId,context.tenantId(),moduleId,true);
        if (request==null || request.code()==null || request.name()==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"状态配置不能为空");
        String code=request.code().trim().toUpperCase(Locale.ROOT), name=request.name().trim();
        if (!code.matches("[A-Z][A-Z0-9_]{0,39}") || BUILTIN.contains(code) || name.isEmpty() || name.length()>100)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"系统预置状态不可修改，状态编码或名称无效");
        var row=rules.getOne(new LambdaQueryWrapper<UnModuleRule>().eq(UnModuleRule::getModuleId,moduleId)
                .eq(UnModuleRule::getTenantId,context.tenantId()).eq(UnModuleRule::getRuleCode,code),false);
        if (row!=null && !TYPE.equals(row.getRuleType()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"状态编码已被其他规则使用");
        if (row==null) {
            row=new UnModuleRule(); row.setModuleId(moduleId); row.setTenantId(context.tenantId());
            row.setRuleCode(code); row.setRuleType(TYPE);
        }
        try { row.setRuleJson(json.writeValueAsString(Map.of("name",name))); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"状态名称无法保存",ex); }
        row.setStatus(request.enabled()?"ACTIVE":"DISABLED"); rules.addOrUpdate(row);
        UnSysLogOpt log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(context.tenantId());
        log.setAccountId(systems.accountId(token,systemId)); log.setActionCode("MODULE_RECORD_STATUS_SAVE");
        log.setObjectType("MODULE_RULE"); log.setObjectId(row.getId());
        try { log.setDetailJson(json.writeValueAsString(Map.of("code",code,"name",name,"enabled",request.enabled()))); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"状态变更日志无法保存",ex); }
        logs.addOrUpdate(log);
        return view(row);
    }

    /** 动作保存和执行均核对当前有效状态，删除状态只由逻辑删除链写入。 */
    public void requireTarget(Long moduleId,Long tenantId,String code) {
        if (code==null) return;
        if (DRAFT.equals(code) || NORMAL.equals(code)) return;
        if (DELETED.equals(code)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"删除状态只能由删除动作设置");
        var module=moduleRows.getById(moduleId);
        if (module==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"模块不存在");
        var row=rules.getOne(new LambdaQueryWrapper<UnModuleRule>().eq(UnModuleRule::getModuleId,moduleId)
                .eq(UnModuleRule::getTenantId,tenantId).eq(UnModuleRule::getRuleCode,code)
                .eq(UnModuleRule::getRuleType,TYPE),false);
        if (row==null && !module.getTenantId().equals(tenantId))
            row=rules.getOne(new LambdaQueryWrapper<UnModuleRule>().eq(UnModuleRule::getModuleId,moduleId)
                    .eq(UnModuleRule::getTenantId,module.getTenantId()).eq(UnModuleRule::getRuleCode,code)
                    .eq(UnModuleRule::getRuleType,TYPE),false);
        if (row==null || !"ACTIVE".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"模块目标状态未配置或已停用");
    }

    private View view(UnModuleRule row) {
        try { return new View(row.getRuleCode(),json.readTree(row.getRuleJson()).path("name").asText(),false,
                "ACTIVE".equals(row.getStatus())); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"状态配置损坏",ex); }
    }

    /** 模块行锁串行化同一模块的状态编码配置；继承模块仍受系统与租户边界约束。 */
    private UnModule module(Long systemId,Long tenantId,Long moduleId,boolean lock) {
        var row=moduleRows.getOne(new LambdaQueryWrapper<UnModule>().eq(UnModule::getId,moduleId)
                .last(lock?"FOR UPDATE":""),false);
        var system=systemRows.getById(systemId);
        if (row==null || system==null || !systemId.equals(row.getSystemId())
                || !List.of(system.getRootTenantId(),tenantId).contains(row.getTenantId())
                || !"ACTIVE".equals(row.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"模块不存在或未启用");
        return row;
    }
}
