package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleLog;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelation;
import com.unique.unexamine.sys.module.base.entity.UnModuleShare;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationService;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleConfigDto.FieldConfig;
import com.unique.unexamine.sys.module.manage.dto.ModuleMenuDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.ActionResult;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.AuditDetail;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.DeleteRecordsRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.ExecuteRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.FieldChange;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.FieldView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.OperationView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.RecordInformation;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.RecordView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.RuntimeAction;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.SaveRecordRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 有效模块的记录保存与读取；主记录、自定义字段和日志在同一事务中完成。 */
@Service
public class ModuleRecordService {
    private final SystemManageService systems;
    private final ModuleConfigService config;
    private final IUnSysSystemService systemRows;
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService data;
    private final IUnModuleLogService logs;
    private final ObjectMapper json;
    private final ModulePermissionService permissions;
    private final ModuleFieldService fieldRules;
    private final ModuleMenuService menus;
    private final IUnModuleRelationService relations;
    private final IUnPlatAccountService accounts;
    private final ModuleShareService shares;
    private final ModuleFileBatchService fileBatches;

    /** 注入已生成基础 Service 和系统成员关系服务。 */
    public ModuleRecordService(SystemManageService systems, ModuleConfigService config,
            IUnSysSystemService systemRows, IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers, IUnModuleRecordService records,
            IUnModuleRecordDataService data, IUnModuleLogService logs, ObjectMapper json,
            ModulePermissionService permissions, ModuleFieldService fieldRules, ModuleMenuService menus,
            IUnModuleRelationService relations, IUnPlatAccountService accounts,ModuleShareService shares,
            ModuleFileBatchService fileBatches) {
        this.systems = systems;
        this.config = config;
        this.systemRows = systemRows;
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.records = records;
        this.data = data;
        this.logs = logs;
        this.json = json;
        this.permissions = permissions;
        this.fieldRules = fieldRules;
        this.menus = menus;
        this.relations = relations;
        this.accounts = accounts;
        this.shares = shares;
        this.fileBatches = fileBatches;
    }

    /** 普通表单新增和编辑各自使用明确菜单，不允许通过编辑接口绕过负责人转移权限。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public RecordView save(String token,Long systemId,Long tenantId,Long moduleId,SaveRecordRequest request) {
        systems.context(token,systemId,tenantId); config.lockModule(systemId,moduleId);
        var access=permissions.require(token,systemId,tenantId,moduleId,request.id()==null?"CREATE":"EDIT");
        return saveWithAccess(access,moduleId,request,request.id()==null?"RECORD_CREATE":"RECORD_UPDATE",access.definition().menu().getName(),null,null);
    }

    /** 表单、自定义字段更新和转化创建都调用本保存链；不是复制一套 CRUD。 */
    private RecordView saveWithAccess(ModulePermissionService.Access access,Long moduleId,SaveRecordRequest request,
                                      String operation,String actionName,Long relatedModuleId,Long relatedRecordId) {
        var context=access.context(); Long systemId=context.systemId();
        if (request.values()==null) throw new IllegalArgumentException("字段值不能为空");
        access.policy().requireWritable(request.values().keySet());
        UnModule module=config.activeModule(systemId,moduleId,context.tenantId());
        List<UnModuleField> fields=config.currentFields(moduleId);
        Set<String> allowed=fields.stream().map(UnModuleField::getFieldCode).collect(java.util.stream.Collectors.toSet());
        if (!allowed.containsAll(request.values().keySet())) throw new IllegalArgumentException("提交包含未配置字段");
        ActionRecord selected=request.id()==null?null:actionRecord(access,request.id());
        UnModuleRecord record=selected==null?new UnModuleRecord():selected.record();
        UnModuleShare share=selected==null?null:selected.share();
        Long dataTenantId=selected==null?context.tenantId():record.getTenantId();
        if (request.id()!=null) {
            if (request.ownerAccountId()!=null && !request.ownerAccountId().equals(record.getOwnerUserId()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"负责人变更请使用转移动作");
        }
        Map<String,UnModuleRecordData> existing=request.id()==null?Map.of():dataByCode(record.getId());
        Map<String,Object> effective=new LinkedHashMap<>(); List<FieldChange> changes=new ArrayList<>();
        for (UnModuleField field:fields) {
            String code=field.getFieldCode();
            FieldDefinition definition=fieldRules.configured(code,config.fieldConfig(field).configuration());
            Object previous="name".equals(code)?record.getName():fieldRules.stored(existing.get(code),definition.type());
            Object value=request.values().containsKey(code)?request.values().get(code):previous;
            if (request.id()==null && !request.values().containsKey(code)) value=definition.rules().get("defaultValue");
            if ("name".equals(code) && value instanceof String text) value=text.trim();
            fieldRules.validate(systemId,dataTenantId,definition,value,previous);
            fieldRules.validateUnique(systemId,dataTenantId,moduleId,request.id(),definition,value);
            effective.put(code,value);
            if (!same(previous,value)) changes.add(new FieldChange(field.getId(),code,definition.label(),previous,value,
                    fieldRules.display(systemId,dataTenantId,definition,previous),
                    fieldRules.display(systemId,dataTenantId,definition,value)));
        }
        String name=(String)effective.get("name");
        if (name==null || name.isBlank()) throw new IllegalArgumentException("记录标题不能为空");
        fileBatches.validate(systemId,dataTenantId,request.id(),access.accountId(),fields,effective);
        Long ownerId=request.ownerAccountId()!=null?request.ownerAccountId():request.id()==null?
                systemRows.queryById(systemId).getOwnerAccountId():record.getOwnerUserId();
        var owner=ownerMembership(systemId,dataTenantId,ownerId);
        if (share==null) access.policy().requireOwner(ownerId);
        if (request.id()==null) {
            record.setSystemId(systemId); record.setTenantId(context.tenantId()); record.setModuleId(moduleId); record.setStatus("ACTIVE");
            record.setCreateUserId(systemRows.queryById(systemId).getOwnerAccountId());
        }
        record.setName(name); record.setOwnerUserId(ownerId); record.setOwnerDeptId(owner.getDepartmentId());
        record.setActualOperatorId(access.accountId()); record.setUpdateUserId(access.accountId());
        persistRecord(record);
        for (UnModuleField field:fields) {
            String code=field.getFieldCode(); if ("name".equals(code)) continue;
            var item=existing.getOrDefault(code,new UnModuleRecordData());
            FieldDefinition definition=fieldRules.configured(code,config.fieldConfig(field).configuration());
            if (item.getId()!=null && same(fieldRules.stored(item,definition.type()),effective.get(code))) continue;
            item.setRecordId(record.getId()); item.setFieldId(field.getId()); item.setFieldCode(code);
            fieldRules.store(item,definition,effective.get(code));
            persistDataValue(item);
        }
        fileBatches.sync(record,fields,effective);
        audit(module,record,access,share,operation,actionName,changes,relatedModuleId,relatedRecordId);
        return view(record,access.policy(),context.tenantId());
    }

    /** 候选动作逐个以真实菜单鉴权，配置中的停用项不会返回。 */
    public List<RuntimeAction> availableActions(String token,Long systemId,Long tenantId,Long moduleId) {
        var context=systems.context(token,systemId,tenantId); config.activeModule(systemId,moduleId,context.tenantId());
        List<RuntimeAction> result=new ArrayList<>();
        for (var definition:menus.definitions(context,moduleId)) {
            try {
                var access=permissions.requireMenu(token,systemId,tenantId,moduleId,definition.menu().getId());
                var settings=access.definition().settings();
                if ("CONVERT".equals(settings.type()) && (settings.targetModuleId()==null || settings.mappings().isEmpty())) continue;
                if ("UPDATE_FIELDS".equals(settings.type())) access.policy().requireWritable(actionFields(access.definition()).keySet());
                result.add(new RuntimeAction(definition.menu().getId(),access.definition().menu().getName(),settings.type(),settings.position()));
            } catch (ResponseStatusException ex) { if (ex.getStatusCode().value()!=403) throw ex; }
        }
        return result;
    }

    /** 更新字段的对话框复用当前字段布局、字典与业务值，不另行计算排版。 */
    public RecordInformation actionForm(String token,Long systemId,Long tenantId,Long moduleId,Long menuId,Long recordId) {
        var access=permissions.requireMenu(token,systemId,tenantId,moduleId,menuId);
        if (!"UPDATE_FIELDS".equals(access.definition().settings().type())) throw new IllegalArgumentException("该动作不使用字段更新表单");
        var record=readableRecord(access,recordId,false);
        var values=recordValues(record); conditions(access,values);
        var fields=actionFields(access.definition()); access.policy().requireWritable(fields.keySet());
        var form=fields.values().stream().map(field->config.runtimeFieldConfig(systemId,record.getTenantId(),field,true)).toList();
        for (UnModuleField field:fields.values()) if (values.containsKey(field.getFieldCode())) {
            FieldDefinition definition=fieldRules.configured(field.getFieldCode(),config.fieldConfig(field).configuration());
            values.put(field.getFieldCode(),fieldRules.present(systemId,record.getTenantId(),definition,values.get(field.getFieldCode())));
        }
        return new RecordInformation(recordId,record.getTenantId(),!record.getTenantId().equals(access.context().tenantId()),
                bind(form,values));
    }

    /** 动作执行统一检查菜单、条件、数据范围及字段许可，再进入同一业务保存事务。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public ActionResult execute(String token,Long systemId,Long tenantId,Long moduleId,Long menuId,Long recordId,ExecuteRequest request) {
        var initial=permissions.requireMenu(token,systemId,tenantId,moduleId,menuId);
        Set<Long> locks=new HashSet<>(); locks.add(moduleId);
        if ("CONVERT".equals(initial.definition().settings().type()) && initial.definition().settings().targetModuleId()!=null)
            locks.add(initial.definition().settings().targetModuleId());
        locks.stream().sorted().forEach(id->config.lockModule(systemId,id));
        var access=permissions.requireMenu(token,systemId,tenantId,moduleId,menuId);
        if (!Objects.equals(initial.definition().settings().targetModuleId(),access.definition().settings().targetModuleId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"动作配置已更新，请刷新后执行");
        ActionRecord selected=actionRecord(access,recordId);
        var record=selected.record();
        var values=recordValues(record); conditions(access,values);
        Map<String,Object> submitted=request.values()==null?Map.of():request.values();
        switch (access.definition().settings().type()) {
            case "UPDATE_FIELDS" -> {
                if (request.ownerAccountId()!=null) throw new IllegalArgumentException("字段动作不能变更负责人");
                var fields=actionFields(access.definition());
                if (submitted.isEmpty() || !fields.keySet().containsAll(submitted.keySet())) throw new IllegalArgumentException("只能提交动作指定字段");
                saveWithAccess(access,moduleId,new SaveRecordRequest(recordId,null,submitted),"ACTION_FIELD_UPDATE",access.definition().menu().getName(),null,null);
            }
            case "TRANSFER" -> {
                if (!submitted.isEmpty() || request.ownerAccountId()==null) throw new IllegalArgumentException("转移动作仅接受目标负责人");
                var owner=ownerMembership(systemId,record.getTenantId(),request.ownerAccountId());
                Long before=record.getOwnerUserId(); record.setOwnerUserId(request.ownerAccountId()); record.setOwnerDeptId(owner.getDepartmentId());
                record.setActualOperatorId(access.accountId()); record.setUpdateUserId(access.accountId());
                persistRecord(record);
                audit(access.definition().module(),record,access,selected.share(),"RECORD_TRANSFER",access.definition().menu().getName(),
                        List.of(new FieldChange(null,"ownerUserId","负责人",before,record.getOwnerUserId(),
                                Map.of(String.valueOf(before),accounts.queryById(before).getDisplayName()),
                                Map.of(String.valueOf(record.getOwnerUserId()),accounts.queryById(record.getOwnerUserId()).getDisplayName()))),null,null);
            }
            case "CONVERT" -> {
                if (selected.share()!=null) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"共享记录不能转化");
                if (!submitted.isEmpty() || request.ownerAccountId()!=null) throw new IllegalArgumentException("转化只使用当前保存的字段映射");
                return convert(token,access,record,values);
            }
            default -> throw new IllegalArgumentException("请使用该基础动作对应的业务入口");
        }
        return new ActionResult(recordId,null,null);
    }

    /** 同一来源到同一目标模块只允许一次成功转化，关系与两端日志共同提交。 */
    private ActionResult convert(String token,ModulePermissionService.Access access,UnModuleRecord source,Map<String,Object> values) {
        var settings=access.definition().settings(); Long targetModule=settings.targetModuleId();
        if (targetModule==null || settings.mappings().isEmpty()) throw new IllegalArgumentException("转化动作尚未配置目标与字段映射");
        if (relations.count(new LambdaQueryWrapper<UnModuleRelation>().eq(UnModuleRelation::getSourceRecordId,source.getId())
                .eq(UnModuleRelation::getTargetModuleId,targetModule).eq(UnModuleRelation::getRelationType,"CONVERSION"))>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"此记录已转化到该目标模块");
        var targetAccess=permissions.require(token,access.context().systemId(),access.context().tenantId(),targetModule,"CREATE");
        Map<Long,UnModuleField> sourceFields=new HashMap<>(),targetFields=new HashMap<>();
        config.currentFields(source.getModuleId()).forEach(f->sourceFields.put(f.getId(),f));
        config.currentFields(targetModule).forEach(f->targetFields.put(f.getId(),f));
        Map<String,Object> mapped=new LinkedHashMap<>();
        for (var mapping:settings.mappings()) {
            var from=sourceFields.get(mapping.sourceFieldId()); var to=targetFields.get(mapping.targetFieldId());
            if (from==null || to==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"字段映射已失效，请重新配置");
            if (!access.policy().readable(from.getFieldCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"没有来源字段读取权限");
            var fromDefinition=fieldRules.configured(from.getFieldCode(),config.fieldConfig(from).configuration());
            var toDefinition=fieldRules.configured(to.getFieldCode(),config.fieldConfig(to).configuration());
            fieldRules.validateMapping(fromDefinition,toDefinition);
            if (mapped.containsKey(to.getFieldCode())) throw new IllegalArgumentException("目标字段映射重复");
            mapped.put(to.getFieldCode(),values.get(from.getFieldCode()));
        }
        var created=saveWithAccess(targetAccess,targetModule,new SaveRecordRequest(null,null,mapped),"RECORD_CONVERT_CREATE",
                access.definition().menu().getName(),source.getModuleId(),source.getId());
        var relation=new UnModuleRelation(); relation.setSourceRecordId(source.getId()); relation.setTargetRecordId(created.id());
        relation.setTargetModuleId(targetModule); relation.setActionMenuId(access.definition().menu().getId()); relation.setRelationType("CONVERSION");
        relation.setStatus("ACTIVE"); relations.addOrUpdate(relation);
        audit(access.definition().module(),source,access,null,"RECORD_CONVERT",access.definition().menu().getName(),List.of(),targetModule,created.id());
        return new ActionResult(source.getId(),targetModule,created.id());
    }

    /** 已配置条件只使用有权读取的字段值，禁止执行任意表达式或 SQL。 */
    private void conditions(ModulePermissionService.Access access,Map<String,Object> values) {
        Map<Long,UnModuleField> definitions=new HashMap<>(); config.currentFields(access.definition().module().getId()).forEach(f->definitions.put(f.getId(),f));
        for (var condition:access.definition().settings().conditions()) {
            var field=definitions.get(condition.fieldId());
            if (field==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"动作条件字段已失效");
            if (!access.policy().readable(field.getFieldCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"没有条件字段读取权限");
            Object value=values.get(field.getFieldCode()); boolean matched;
            boolean empty=value==null || value instanceof String text && text.isBlank() || value instanceof java.util.Collection<?> list && list.isEmpty();
            switch (condition.operator()) {
                case "EMPTY" -> matched=empty;
                case "NOT_EMPTY" -> matched=!empty;
                case "EQ" -> matched=same(value,condition.value());
                case "NE" -> matched=!same(value,condition.value());
                case "IN" -> matched=condition.value() instanceof List<?> list && list.stream().anyMatch(item->same(item,value));
                case "GT","GE","LT","LE" -> {
                    if (value==null) { matched=false; break; }
                    if (!(value instanceof Number) || !(condition.value() instanceof Number)) throw new IllegalArgumentException("大小比较条件必须使用数值");
                    int comparison=new BigDecimal(value.toString()).compareTo(new BigDecimal(condition.value().toString()));
                    matched=switch (condition.operator()) { case "GT"->comparison>0; case "GE"->comparison>=0; case "LT"->comparison<0; default->comparison<=0; };
                }
                default -> throw new IllegalArgumentException("动作条件操作符无效");
            }
            if (!matched) throw new ResponseStatusException(HttpStatus.CONFLICT,"当前记录不满足动作执行条件");
        }
    }
    private Map<String,UnModuleField> actionFields(ModuleMenuDto.Definition definition) {
        Map<String,UnModuleField> result=new LinkedHashMap<>(); var ids=definition.settings().fieldIds();
        for (var field:config.currentFields(definition.module().getId())) if (ids.contains(field.getId())) result.put(field.getFieldCode(),field);
        if (result.size()!=ids.size()) throw new ResponseStatusException(HttpStatus.CONFLICT,"动作字段已失效"); return result;
    }
    private Map<String,Object> recordValues(UnModuleRecord record) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("name",record.getName());
        Map<String,FieldType> types=new HashMap<>();
        config.currentFields(record.getModuleId()).forEach(field->types.put(field.getFieldCode(),FieldType.parse(field.getFieldType())));
        dataByCode(record.getId()).forEach((code,row)->{
            if (types.containsKey(code)) result.put(code,fieldRules.stored(row,types.get(code)));
        }); return result;
    }
    private boolean same(Object left,Object right) {
        if (left instanceof Number && right instanceof Number) return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()))==0;
        return json.valueToTree(left).equals(json.valueToTree(right));
    }

    /** 基础记录查询，不混入扩展字段或详情布局。 */
    public RecordView queryById(String token, Long systemId, Long tenantId, Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DETAIL");
        var context = access.context();
        config.activeModule(systemId, moduleId, context.tenantId());
        var record = readableRecord(access,recordId,false);
        return view(record, access.policy(),context.tenantId());
    }

    /** 按当前保存的字段顺序和布局填充记录详情。 */
    public RecordInformation information(String token, Long systemId, Long tenantId,
                                         Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DETAIL");
        var context = access.context();
        config.activeModule(systemId, moduleId, context.tenantId());
        UnModuleRecord record = readableRecord(access,recordId,false);
        boolean shared=!record.getTenantId().equals(context.tenantId());
        ModulePermissionService.Access editAccess=null;
        if (shared) {
            try {
                editAccess=permissions.require(token,systemId,tenantId,moduleId,"EDIT");
                shares.requireShared(editAccess,record);
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode().value()!=403 && ex.getStatusCode().value()!=404) throw ex;
                editAccess=null;
            }
        }
        final ModulePermissionService.Access effectiveEdit=editAccess;
        Map<String, Object> values = new LinkedHashMap<>();
        var stored = dataByCode(record.getId());
        List<UnModuleField> currentFields=config.currentFields(moduleId);
        for (UnModuleField field:currentFields) if (access.policy().readable(field.getFieldCode()) && stored.containsKey(field.getFieldCode())) {
            FieldDefinition definition=fieldRules.configured(field.getFieldCode(),config.fieldConfig(field).configuration());
            Object raw=fieldRules.stored(stored.get(field.getFieldCode()),definition.type());
            values.put(field.getFieldCode(),fieldRules.present(systemId,record.getTenantId(),definition,raw));
        }
        List<FieldConfig> form = access.policy().filterReadable(currentFields, UnModuleField::getFieldCode).stream()
                .map(field -> {
                    boolean editable=shared?effectiveEdit!=null && effectiveEdit.policy().writable(field.getFieldCode())
                            :access.policy().writable(field.getFieldCode());
                    var item = config.runtimeFieldConfig(systemId, record.getTenantId(), field, editable);
                    if (stored.containsKey(field.getFieldCode())) item.configuration().put("valueDisplay", fieldRules.display(systemId, record.getTenantId(),
                            fieldRules.configured(field.getFieldCode(), config.fieldConfig(field).configuration()),
                            fieldRules.stored(stored.get(field.getFieldCode()),FieldType.parse(field.getFieldType()))));
                    return item;
                }).toList();
        return new RecordInformation(recordId,record.getTenantId(),!record.getTenantId().equals(context.tenantId()),
                bind(form,values));
    }

    /** 业务列表在授权后的记录范围内分页，逻辑删除由 MyBatis-Plus 处理。 */
    public Page<RecordView> queryPageList(String token, Long systemId, Long tenantId, Long moduleId,
                                    ModuleRecordSearchBO search) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "LIST");
        var context = access.context();
        config.activeModule(systemId, moduleId, context.tenantId());
        String scope=search.getScope();
        Page<UnModuleRecord> requestPage=search.parse();
        if (!Set.of("ALL","MINE","SHARED").contains(scope)) throw new IllegalArgumentException("列表范围无效");
        QueryWrapper<UnModuleRecord> where=new QueryWrapper<UnModuleRecord>()
                .eq("system_id",systemId).eq("module_id",moduleId);
        if (!shares.applyRecordScope(where,access,scope))
            return new Page<RecordView>(requestPage.getCurrent(),requestPage.getSize(),0).setRecords(List.of());
        Page<UnModuleRecord> result=records.page(requestPage,where.orderByDesc("id"));
        return new Page<RecordView>(result.getCurrent(), result.getSize(), result.getTotal()).setRecords(result.getRecords().stream().map(record -> view(record,access.policy(),context.tenantId())).toList());
    }

    /** 业务记录默认逻辑删除，保留字段值和操作历史。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String token, Long systemId, Long tenantId, Long moduleId, Long recordId) {
        softDeleteRecords(token,systemId,tenantId,moduleId,List.of(recordId));
    }

    /** 批量删除先检查整批授权，再逐条写入删除时间与操作历史；任一失败回滚整批。 */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByIds(String token,Long systemId,Long tenantId,Long moduleId,DeleteRecordsRequest request) {
        if (request==null || request.ids()==null || request.ids().isEmpty() || request.ids().stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("请选择要删除的业务记录");
        softDeleteRecords(token,systemId,tenantId,moduleId,new ArrayList<>(new java.util.LinkedHashSet<>(request.ids())));
    }

    private void softDeleteRecords(String token,Long systemId,Long tenantId,Long moduleId,List<Long> ids) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DELETE");
        var context = access.context();
        UnModule module = config.activeModule(systemId, moduleId, context.tenantId());
        List<ActionRecord> selected=new ArrayList<>();
        for (Long id:ids) selected.add(actionRecord(access,id));
        LocalDateTime deletedTime=LocalDateTime.now();
        for (ActionRecord item:selected) {
            UnModuleRecord record=item.record();
            record.setDeletedTime(deletedTime);
            record.setActualOperatorId(access.accountId()); record.setUpdateUserId(access.accountId());
            persistRecord(record);
            audit(module,record,access,item.share(),"RECORD_DELETE",access.definition().menu().getName(),List.of(),null,null);
        }
    }

    /** 当前租户数据范围与有效共享范围求并集；共享撤销即时影响详情和历史入口。 */
    private UnModuleRecord readableRecord(ModulePermissionService.Access access,Long id,boolean includeDeleted) {
        UnModuleRecord record=records.queryById(id);
        if (!access.context().systemId().equals(record.getSystemId())
                || !access.definition().module().getId().equals(record.getModuleId())
                || (!includeDeleted && record.getDeletedTime()!=null))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"业务记录不存在");
        if (access.context().tenantId().equals(record.getTenantId())) access.policy().requireOwner(record.getOwnerUserId());
        else {
            if (record.getDeletedTime()!=null) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"业务记录不存在");
            shares.requireShared(access,record);
        }
        return record;
    }

    /** 写动作同时检查本租户负责人范围或锁定有效共享授权。 */
    private record ActionRecord(UnModuleRecord record,UnModuleShare share) {}
    private ActionRecord actionRecord(ModulePermissionService.Access access,Long id) {
        UnModuleRecord record=records.queryById(id);
        if (!access.context().systemId().equals(record.getSystemId())
                || !access.definition().module().getId().equals(record.getModuleId())
                || record.getDeletedTime()!=null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"业务记录不存在");
        if (access.context().tenantId().equals(record.getTenantId())) {
            access.policy().requireOwner(record.getOwnerUserId());
            return new ActionRecord(record,null);
        }
        return new ActionRecord(record,shares.requireSharedForWrite(access,record));
    }

    /** 负责人必须是当前系统当前租户的有效成员，并以其部门计算数据范围。 */
    private UnSysTenantMember ownerMembership(Long systemId, Long tenantId, Long ownerAccountId) {
        if (!"ACTIVE".equals(accounts.queryById(ownerAccountId).getStatus())) throw new IllegalArgumentException("负责人账号已停用");
        UnSysMember member = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, systemId)
                .eq(UnSysMember::getAccountId, ownerAccountId)
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (member == null) {
            throw new IllegalArgumentException("负责人不是有效系统成员");
        }
        UnSysTenantMember link = tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, member.getId())
                .eq(UnSysTenantMember::getStatus, "ACTIVE"), false);
        if (link == null) {
            throw new IllegalArgumentException("负责人不是有效租户成员");
        }
        return link;
    }

    /** 读取已保存字段值并按字段编码组成详情数据。 */
    private Map<String, UnModuleRecordData> dataByCode(Long recordId) {
        Map<String, UnModuleRecordData> result = new LinkedHashMap<>();
        for (UnModuleRecordData item : data.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId, recordId))) {
            result.put(item.getFieldCode(), item);
        }
        return result;
    }

    /** 主记录的并发版本在业务 Service 中检查，负责人、部门及软删除允许显式清空旧值。 */
    private void persistRecord(UnModuleRecord record) {
        if (record.getId()==null) {
            record.setLockVersion(0L);
            records.addOrUpdate(record);
            return;
        }
        long before=record.getLockVersion();
        long next=before+1;
        boolean updated=records.update(new LambdaUpdateWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId,record.getId()).eq(UnModuleRecord::getLockVersion,before)
                .eq(UnModuleRecord::getSystemId,record.getSystemId())
                .eq(UnModuleRecord::getTenantId,record.getTenantId())
                .set(UnModuleRecord::getName,record.getName())
                .set(UnModuleRecord::getStatus,record.getStatus())
                .set(UnModuleRecord::getOwnerUserId,record.getOwnerUserId())
                .set(UnModuleRecord::getOwnerDeptId,record.getOwnerDeptId())
                .set(UnModuleRecord::getActualOperatorId,record.getActualOperatorId())
                .set(UnModuleRecord::getUpdateUserId,record.getUpdateUserId())
                .set(UnModuleRecord::getArchivedTime,record.getArchivedTime())
                .set(UnModuleRecord::getDeletedTime,record.getDeletedTime())
                .set(UnModuleRecord::getLockVersion,next));
        if (!updated) throw new ResponseStatusException(HttpStatus.CONFLICT,"业务记录已被他人修改，请刷新后重试");
        record.setLockVersion(next);
    }

    /** 明确写入所有值列，防止清除或变更字段类型后旧值残留。 */
    private void persistDataValue(UnModuleRecordData item) {
        if (item.getId()==null) { data.addOrUpdate(item); return; }
        data.update(new LambdaUpdateWrapper<UnModuleRecordData>().eq(UnModuleRecordData::getId,item.getId())
                .set(UnModuleRecordData::getValueVarchar,item.getValueVarchar())
                .set(UnModuleRecordData::getValueText,item.getValueText())
                .set(UnModuleRecordData::getValueNumber,item.getValueNumber())
                .set(UnModuleRecordData::getValueTime,item.getValueTime())
                .set(UnModuleRecordData::getValueJson,item.getValueJson()));
    }

    /** 字段值以 JSON 保存，保留数字布尔和空值的类型。 */
    private String writeValue(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("字段值无法保存", ex);
        }
    }

    /** 记录实际操作者和字段前后值；名称快照只用于业务操作历史。 */
    private void audit(UnModule module,UnModuleRecord record,ModulePermissionService.Access access,UnModuleShare share,
                       String operation,String actionName,
                        List<FieldChange> changes,Long relatedModuleId,Long relatedRecordId) {
        Long operatorId=access.accountId();
        var log=new UnModuleLog(); log.setSystemId(record.getSystemId()); log.setTenantId(record.getTenantId());
        log.setActorTenantId(access.context().tenantId());
        if (share!=null) log.setShareId(share.getId());
        log.setModuleId(module.getId()); log.setRecordId(record.getId()); log.setOperatorAccountId(operatorId); log.setActionCode(operation);
        Map<String,Object> snapshot=new LinkedHashMap<>();
        snapshot.put("menuId",access.definition().menu().getId());
        snapshot.put("actorTenantId",access.context().tenantId());
        snapshot.put("sourceTenantId",record.getTenantId());
        snapshot.put("shareId",share==null?null:share.getId());
        snapshot.put("allData",access.policy().allData());
        snapshot.put("ownerAccountIds",access.policy().ownerAccountIds());
        snapshot.put("allReadableFields",access.policy().allReadableFields());
        snapshot.put("readableFields",access.policy().readableFields());
        snapshot.put("allWritableFields",access.policy().allWritableFields());
        snapshot.put("writableFields",access.policy().writableFields());
        log.setPermissionJson(writeValue(snapshot));
        log.setDetailJson(writeValue(new AuditDetail(actionName,accounts.queryById(operatorId).getDisplayName(),changes,relatedModuleId,relatedRecordId,null)));
        logs.addOrUpdate(log);
    }

    /** 操作历史与详情使用相同记录授权，并在服务端过滤无权读取的字段变化。 */
    public Page<OperationView> operations(String token,Long systemId,Long tenantId,Long moduleId,Long recordId,SearchBO search) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"DETAIL");
        var record=readableRecord(access,recordId,true);
        var where=new LambdaQueryWrapper<UnModuleLog>().eq(UnModuleLog::getSystemId,systemId)
                .eq(UnModuleLog::getTenantId,record.getTenantId()).eq(UnModuleLog::getModuleId,moduleId)
                .eq(UnModuleLog::getRecordId,recordId).orderByDesc(UnModuleLog::getId);
        var result=logs.page(search.parse(),where);
        return new Page<OperationView>(result.getCurrent(), result.getSize(), result.getTotal()).setRecords(result.getRecords().stream().map(log->{
            AuditDetail detail;
            try { detail=log.getDetailJson()==null?new AuditDetail(log.getActionCode(),null,List.of(),null,null,null):json.readValue(log.getDetailJson(),AuditDetail.class); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("业务操作日志损坏",ex); }
            var visible=detail.changes()==null?List.<FieldChange>of():detail.changes().stream()
                    .filter(change->change.fieldId()==null || access.policy().readable(change.fieldCode())).toList();
            return new OperationView(log.getId(),log.getActionCode(),log.getOperatorAccountId(),log.getCreateTime(),
                    new AuditDetail(detail.actionName(),detail.operatorName(),visible,detail.relatedModuleId(),detail.relatedRecordId(),
                            visible.isEmpty() && detail.changes()!=null && !detail.changes().isEmpty()?null:detail.message()));
        }).toList());
    }

    /** 仅在有权读取的字段配置上绑定业务值，保持原位置和宽度。 */
    private List<FieldView> bind(List<FieldConfig> form,Map<String,Object> values) {
        return form.stream().map(field->new FieldView(field.fieldName(),
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(field.configuration())),
                values.get(field.fieldName()))).toList();
    }

    /** 基础记录响应不包含字段扩展和布局。 */
    private RecordView view(UnModuleRecord record, PermissionPolicy policy,Long viewerTenantId) {
        return new RecordView(record.getId(), policy.readable("name") ? record.getName() : null,
                policy.readable("status") ? record.getStatus() : null,
                policy.readable("ownerUserId") ? record.getOwnerUserId() : null,
                policy.readable("createUserId") ? record.getCreateUserId() : null,
                policy.readable("createTime") ? record.getCreateTime() : null,
                record.getTenantId(),!record.getTenantId().equals(viewerTenantId));
    }
}
