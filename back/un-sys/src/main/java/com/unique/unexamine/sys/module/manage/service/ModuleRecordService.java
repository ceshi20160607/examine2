package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleLog;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService.FieldView;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService.PageQuery;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.permission.PermissionPolicy;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.math.BigDecimal;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelation;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

/** 发布模块的记录保存与读取；主记录、自定义字段和日志在同一事务中完成。 */
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

    /** 表单提交主键为空时新增，传主键时更新同模块记录。 */
    public record SaveRecordRequest(Long id, Long ownerAccountId,
                                    @NotNull Map<String, Object> values) {}

    /** `queryById` 只展示基础记录，不拼接表单布局。 */
    public record RecordView(Long id, String name, String status, Long ownerAccountId,
                             Long createAccountId, LocalDateTime createTime) {}

    /** 模块列表的实际分页结果。 */
    public record RecordPage(long total, long current, long size, List<RecordView> records) {}

    /** `information` 复用发布的表单配置并填入基础及扩展值。 */
    public record RecordInformation(Long id, List<FieldView> fields) {}

    /** 注入已生成基础 Service 和系统成员关系服务。 */
    public ModuleRecordService(SystemManageService systems, ModuleConfigService config,
            IUnSysSystemService systemRows, IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers, IUnModuleRecordService records,
            IUnModuleRecordDataService data, IUnModuleLogService logs, ObjectMapper json,
            ModulePermissionService permissions, ModuleFieldService fieldRules, ModuleMenuService menus,
            IUnModuleRelationService relations, IUnPlatAccountService accounts) {
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
    }

    /** 输入字段只允许动作指定的业务字段；归属转移使用单独的负责人参数。 */
    public record ExecuteRequest(Map<String,Object> values,Long ownerAccountId) {}
    /** 成功结果仅返回关联标识，后续跳转仍校验目标记录读取权限。 */
    public record ActionResult(Long recordId,Long targetModuleId,Long targetRecordId) {}
    /** 运行态按钮与页面共用菜单身份。 */
    public record RuntimeAction(Long menuId,String name,String type,String position) {}
    /** 日志保留操作发生时的字段名称、业务值和字典名称。 */
    public record FieldChange(Long fieldId,String fieldCode,String fieldName,Object before,Object after,
                               Map<String,String> beforeDisplay,Map<String,String> afterDisplay) {}
    /** 配置迁移与普通字段更新使用不同 actionCode，此处保存执行上下文。 */
    public record AuditDetail(String actionName,String operatorName,List<FieldChange> changes,
                               Long relatedModuleId,Long relatedRecordId,String message) {}
    /** 返回的字段变化已经按当前字段读取许可过滤。 */
    public record OperationView(Long id,String operationType,Long operatorAccountId,LocalDateTime createTime,AuditDetail detail) {}
    /** 操作历史分页复用生成的基础日志服务。 */
    public record OperationPage(long total,long current,long size,List<OperationView> records) {}

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
        UnModule module=config.publishedModule(systemId,moduleId,context.tenantId());
        List<UnModuleField> fields=config.publishedFields(moduleId);
        Set<String> allowed=fields.stream().map(UnModuleField::getFieldCode).collect(java.util.stream.Collectors.toSet());
        if (!allowed.containsAll(request.values().keySet())) throw new IllegalArgumentException("提交包含未发布字段");
        UnModuleRecord record=request.id()==null?new UnModuleRecord():visibleRecord(systemId,context.tenantId(),moduleId,request.id());
        if (request.id()!=null) {
            access.policy().requireOwner(record.getOwnerUserId());
            if (request.ownerAccountId()!=null && !request.ownerAccountId().equals(record.getOwnerUserId()))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,"负责人变更请使用转移动作");
        }
        Map<String,UnModuleRecordData> existing=request.id()==null?Map.of():dataByCode(record.getId());
        Map<String,Object> effective=new LinkedHashMap<>(); List<FieldChange> changes=new ArrayList<>();
        for (UnModuleField field:fields) {
            String code=field.getFieldCode();
            Object previous="name".equals(code)?record.getName():existing.containsKey(code)?readValue(existing.get(code).getValueJson()):null;
            FieldDefinition definition=fieldRules.published(code,config.fieldConfig(field).configuration());
            Object value=request.values().containsKey(code)?request.values().get(code):previous;
            if (request.id()==null && !request.values().containsKey(code)) value=definition.rules().get("defaultValue");
            if ("name".equals(code) && value instanceof String text) value=text.trim();
            fieldRules.validate(systemId,context.tenantId(),definition,value,previous);
            fieldRules.validateUnique(systemId,context.tenantId(),moduleId,request.id(),definition,value);
            effective.put(code,value);
            if (!same(previous,value)) changes.add(new FieldChange(field.getId(),code,definition.label(),previous,value,
                    fieldRules.display(systemId,context.tenantId(),definition,previous),
                    fieldRules.display(systemId,context.tenantId(),definition,value)));
        }
        String name=(String)effective.get("name");
        if (name==null || name.isBlank()) throw new IllegalArgumentException("记录标题不能为空");
        Long ownerId=request.ownerAccountId()!=null?request.ownerAccountId():request.id()==null?
                systemRows.queryById(systemId).getOwnerAccountId():record.getOwnerUserId();
        var owner=ownerMembership(systemId,context.tenantId(),ownerId); access.policy().requireOwner(ownerId);
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
            if (item.getId()!=null && same(readValue(item.getValueJson()),effective.get(code))) continue;
            item.setRecordId(record.getId()); item.setFieldId(field.getId()); item.setFieldCode(code);
            item.setValueJson(writeValue(effective.get(code)));
            indexValue(item,fieldRules.published(code,config.fieldConfig(field).configuration()),effective.get(code));
            persistDataValue(item);
        }
        audit(module,record,access.accountId(),operation,actionName,changes,relatedModuleId,relatedRecordId);
        return view(record,access.policy());
    }

    /** 候选动作逐个以真实菜单鉴权，配置中的草稿和停用项不会返回。 */
    public List<RuntimeAction> availableActions(String token,Long systemId,Long tenantId,Long moduleId) {
        var context=systems.context(token,systemId,tenantId); config.publishedModule(systemId,moduleId,context.tenantId());
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
        var record=visibleRecord(systemId,access.context().tenantId(),moduleId,recordId); access.policy().requireOwner(record.getOwnerUserId());
        var values=recordValues(record); conditions(access,values);
        var fields=actionFields(access.definition()); access.policy().requireWritable(fields.keySet());
        var form=fields.values().stream().map(field->config.runtimeFieldConfig(systemId,access.context().tenantId(),field,true)).toList();
        return new RecordInformation(recordId,records.information(recordId,form,values));
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
        var record=visibleRecord(systemId,access.context().tenantId(),moduleId,recordId); access.policy().requireOwner(record.getOwnerUserId());
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
                var owner=ownerMembership(systemId,access.context().tenantId(),request.ownerAccountId());
                Long before=record.getOwnerUserId(); record.setOwnerUserId(request.ownerAccountId()); record.setOwnerDeptId(owner.getDepartmentId());
                record.setActualOperatorId(access.accountId()); record.setUpdateUserId(access.accountId());
                persistRecord(record);
                audit(access.definition().module(),record,access.accountId(),"RECORD_TRANSFER",access.definition().menu().getName(),
                        List.of(new FieldChange(null,"ownerUserId","负责人",before,record.getOwnerUserId(),
                                Map.of(String.valueOf(before),accounts.queryById(before).getDisplayName()),
                                Map.of(String.valueOf(record.getOwnerUserId()),accounts.queryById(record.getOwnerUserId()).getDisplayName()))),null,null);
            }
            case "CONVERT" -> {
                if (!submitted.isEmpty() || request.ownerAccountId()!=null) throw new IllegalArgumentException("转化只使用已发布的字段映射");
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
        config.publishedFields(source.getModuleId()).forEach(f->sourceFields.put(f.getId(),f));
        config.publishedFields(targetModule).forEach(f->targetFields.put(f.getId(),f));
        Map<String,Object> mapped=new LinkedHashMap<>();
        for (var mapping:settings.mappings()) {
            var from=sourceFields.get(mapping.sourceFieldId()); var to=targetFields.get(mapping.targetFieldId());
            if (from==null || to==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"字段映射已失效，请重新配置");
            if (!access.policy().readable(from.getFieldCode())) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"没有来源字段读取权限");
            var fromDefinition=fieldRules.published(from.getFieldCode(),config.fieldConfig(from).configuration());
            var toDefinition=fieldRules.published(to.getFieldCode(),config.fieldConfig(to).configuration());
            fieldRules.validateMapping(fromDefinition,toDefinition);
            if (mapped.containsKey(to.getFieldCode())) throw new IllegalArgumentException("目标字段映射重复");
            mapped.put(to.getFieldCode(),values.get(from.getFieldCode()));
        }
        var created=saveWithAccess(targetAccess,targetModule,new SaveRecordRequest(null,null,mapped),"RECORD_CONVERT_CREATE",
                access.definition().menu().getName(),source.getModuleId(),source.getId());
        var relation=new UnModuleRelation(); relation.setSourceRecordId(source.getId()); relation.setTargetRecordId(created.id());
        relation.setTargetModuleId(targetModule); relation.setActionMenuId(access.definition().menu().getId()); relation.setRelationType("CONVERSION");
        relation.setStatus("ACTIVE"); relations.addOrUpdate(relation);
        audit(access.definition().module(),source,access.accountId(),"RECORD_CONVERT",access.definition().menu().getName(),List.of(),targetModule,created.id());
        return new ActionResult(source.getId(),targetModule,created.id());
    }

    /** 已配置条件只使用有权读取的字段值，禁止执行任意表达式或 SQL。 */
    private void conditions(ModulePermissionService.Access access,Map<String,Object> values) {
        Map<Long,UnModuleField> definitions=new HashMap<>(); config.publishedFields(access.definition().module().getId()).forEach(f->definitions.put(f.getId(),f));
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
    private Map<String,UnModuleField> actionFields(ModuleMenuService.Definition definition) {
        Map<String,UnModuleField> result=new LinkedHashMap<>(); var ids=definition.settings().fieldIds();
        for (var field:config.publishedFields(definition.module().getId())) if (ids.contains(field.getId())) result.put(field.getFieldCode(),field);
        if (result.size()!=ids.size()) throw new ResponseStatusException(HttpStatus.CONFLICT,"动作字段已失效"); return result;
    }
    private Map<String,Object> recordValues(UnModuleRecord record) {
        Map<String,Object> result=new LinkedHashMap<>(); result.put("name",record.getName());
        dataByCode(record.getId()).forEach((code,row)->result.put(code,readValue(row.getValueJson()))); return result;
    }
    private boolean same(Object left,Object right) {
        if (left instanceof Number && right instanceof Number) return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()))==0;
        return json.valueToTree(left).equals(json.valueToTree(right));
    }

    /** 基础记录查询，不混入扩展字段或详情布局。 */
    public RecordView queryById(String token, Long systemId, Long tenantId, Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DETAIL");
        var context = access.context();
        config.publishedModule(systemId, moduleId, context.tenantId());
        var record = visibleRecord(systemId, context.tenantId(), moduleId, recordId);
        access.policy().requireOwner(record.getOwnerUserId());
        return view(record, access.policy());
    }

    /** 按发布时保存的字段顺序和布局填充记录详情。 */
    public RecordInformation information(String token, Long systemId, Long tenantId,
                                         Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DETAIL");
        var context = access.context();
        config.publishedModule(systemId, moduleId, context.tenantId());
        UnModuleRecord record = visibleRecord(systemId, context.tenantId(), moduleId, recordId);
        access.policy().requireOwner(record.getOwnerUserId());
        Map<String, Object> values = new LinkedHashMap<>();
        var stored = dataByCode(record.getId());
        stored.forEach((code, item) -> { if (access.policy().readable(code)) values.put(code, readValue(item.getValueJson())); });
        List<IUnModuleRecordService.FieldConfig> form = access.policy().filterReadable(config.publishedFields(moduleId), UnModuleField::getFieldCode).stream()
                .map(field -> {
                    var item = config.runtimeFieldConfig(systemId, context.tenantId(), field, access.policy().writable(field.getFieldCode()));
                    if (stored.containsKey(field.getFieldCode())) item.configuration().put("valueDisplay", fieldRules.display(systemId, context.tenantId(),
                            fieldRules.published(field.getFieldCode(), config.fieldConfig(field).configuration()),
                            readValue(stored.get(field.getFieldCode()).getValueJson())));
                    return item;
                }).toList();
        return new RecordInformation(recordId, records.information(recordId, form, values));
    }

    /** 列表分页委托生成的基础 Service，软删除字段由 IS NULL 筛选。 */
    public RecordPage queryPageList(String token, Long systemId, Long tenantId, Long moduleId,
                                    long page, long size) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "LIST");
        var context = access.context();
        config.publishedModule(systemId, moduleId, context.tenantId());
        Map<String, Object> filters = new HashMap<>();
        filters.put("systemId", systemId);
        filters.put("tenantId", context.tenantId());
        filters.put("moduleId", moduleId);
        filters.put("deletedTime", null);
        if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("分页范围无效");
        if (!access.policy().allData() && access.policy().ownerAccountIds().isEmpty()) return new RecordPage(0,page,size,List.of());
        Page<UnModuleRecord> result = access.policy().allData()
                ? records.queryPageList(new PageQuery(page, size, filters, "id", true))
                : records.page(Page.of(page, size), new LambdaQueryWrapper<UnModuleRecord>()
                    .eq(UnModuleRecord::getSystemId,systemId).eq(UnModuleRecord::getTenantId,context.tenantId())
                    .eq(UnModuleRecord::getModuleId,moduleId).isNull(UnModuleRecord::getDeletedTime)
                    .in(UnModuleRecord::getOwnerUserId,access.policy().ownerAccountIds()).orderByDesc(UnModuleRecord::getId));
        return new RecordPage(result.getTotal(), result.getCurrent(), result.getSize(),
                result.getRecords().stream().map(record -> view(record,access.policy())).toList());
    }

    /** 业务记录默认逻辑删除，保留字段值和操作历史。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String token, Long systemId, Long tenantId, Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DELETE");
        var context = access.context();
        UnModule module = config.publishedModule(systemId, moduleId, context.tenantId());
        UnModuleRecord record = visibleRecord(systemId, context.tenantId(), moduleId, recordId);
        access.policy().requireOwner(record.getOwnerUserId());
        record.setDeletedTime(LocalDateTime.now());
        record.setActualOperatorId(access.accountId()); record.setUpdateUserId(access.accountId());
        persistRecord(record);
        audit(module, record, access.accountId(), "RECORD_DELETE", access.definition().menu().getName(), List.of(), null, null);
    }

    /** 防止跨模块、跨租户读取和操作逻辑删除记录。 */
    private UnModuleRecord visibleRecord(Long systemId, Long tenantId, Long moduleId, Long id) {
        return recordInScope(systemId, tenantId, moduleId, id, false);
    }

    /** 已删除记录允许有权成员查看操作历史，业务编辑入口仍明确拒绝。 */
    private UnModuleRecord recordInScope(Long systemId, Long tenantId, Long moduleId, Long id, boolean includeDeleted) {
        UnModuleRecord record = records.queryById(id);
        if (!systemId.equals(record.getSystemId()) || !tenantId.equals(record.getTenantId())
                || !moduleId.equals(record.getModuleId()) || (!includeDeleted && record.getDeletedTime() != null)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "业务记录不存在");
        }
        return record;
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

    /** 字段筛选索引与原值在同一事务保存；复杂值仍只存原始 JSON。 */
    static void indexValue(UnModuleRecordData item,FieldDefinition definition,Object value) {
        item.setIndexText(null); item.setIndexDecimal(null); item.setIndexTime(null);
        if (value==null) return;
        FieldType type=definition.type();
        switch (type.kind()) {
            case NUMBER -> {
                BigDecimal decimal=new BigDecimal(value.toString()).stripTrailingZeros();
                // 索引列最多保留 22 位整数和 8 位小数；超出时使用原始 JSON 精确查询。
                if (decimal.precision()-decimal.scale()<=22 && decimal.scale()<=8)
                    item.setIndexDecimal(decimal);
            }
            case DATE -> item.setIndexTime(LocalDate.parse(value.toString()).atStartOfDay());
            case DATETIME -> item.setIndexTime(LocalDateTime.parse(value.toString()));
            case TEXT, OPTION, REFERENCE, BOOLEAN, TIME -> {
                if (type==FieldType.SECRET || type==FieldType.RICH_TEXT) return;
                if (value instanceof java.util.Collection<?>) return;
                String text=value.toString();
                if (text.length()<=255) item.setIndexText(text);
            }
            default -> { }
        }
    }

    /** 明确写入空索引值，防止字段值清除后旧筛选结果仍命中。 */
    private void persistDataValue(UnModuleRecordData item) {
        if (item.getId()==null) { data.addOrUpdate(item); return; }
        data.update(new LambdaUpdateWrapper<UnModuleRecordData>().eq(UnModuleRecordData::getId,item.getId())
                .set(UnModuleRecordData::getValueJson,item.getValueJson())
                .set(UnModuleRecordData::getIndexText,item.getIndexText())
                .set(UnModuleRecordData::getIndexDecimal,item.getIndexDecimal())
                .set(UnModuleRecordData::getIndexTime,item.getIndexTime()));
    }

    /** 字段值以 JSON 保存，保留数字布尔和空值的类型。 */
    private String writeValue(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("字段值无法保存", ex);
        }
    }

    /** 详情还原存储时的字段类型，不从文本猜测。 */
    private Object readValue(String value) {
        if (value == null) return null;
        try {
            return json.readValue(value, Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("业务字段值损坏", ex);
        }
    }

    /** 记录实际操作者和字段前后值；名称快照只用于业务操作历史。 */
    private void audit(UnModule module,UnModuleRecord record,Long operatorId,String operation,String actionName,
                        List<FieldChange> changes,Long relatedModuleId,Long relatedRecordId) {
        var log=new UnModuleLog(); log.setSystemId(record.getSystemId()); log.setTenantId(record.getTenantId());
        log.setModuleId(module.getId()); log.setRecordId(record.getId()); log.setOperatorAccountId(operatorId); log.setActionCode(operation);
        log.setDetailJson(writeValue(new AuditDetail(actionName,accounts.queryById(operatorId).getDisplayName(),changes,relatedModuleId,relatedRecordId,null))); logs.addOrUpdate(log);
    }

    /** 操作历史与详情使用相同记录授权，并在服务端过滤无权读取的字段变化。 */
    public OperationPage operations(String token,Long systemId,Long tenantId,Long moduleId,Long recordId,long page,long size) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"DETAIL");
        var record=recordInScope(systemId,access.context().tenantId(),moduleId,recordId,true); access.policy().requireOwner(record.getOwnerUserId());
        var filters=Map.<String,Object>of("systemId",systemId,"tenantId",access.context().tenantId(),"moduleId",moduleId,"recordId",recordId);
        var result=logs.queryPageList(new IUnModuleLogService.PageQuery(page,size,filters,"id",true));
        return new OperationPage(result.getTotal(),result.getCurrent(),result.getSize(),result.getRecords().stream().map(log->{
            AuditDetail detail;
            try { detail=log.getDetailJson()==null?new AuditDetail(log.getActionCode(),null,List.of(),null,null,null):json.readValue(log.getDetailJson(),AuditDetail.class); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("业务操作日志损坏",ex); }
            var visible=detail.changes()==null?List.<FieldChange>of():detail.changes().stream()
                    .filter(change->change.fieldId()==null || access.policy().readable(change.fieldCode())).toList();
            return new OperationView(log.getId(),log.getActionCode(),log.getOperatorAccountId(),log.getCreateTime(),
                    new AuditDetail(detail.actionName(),detail.operatorName(),visible,detail.relatedModuleId(),detail.relatedRecordId(),
                            visible.isEmpty()?null:detail.message()));
        }).toList());
    }

    /** 基础记录响应不包含字段扩展和布局。 */
    private RecordView view(UnModuleRecord record, PermissionPolicy policy) {
        return new RecordView(record.getId(), policy.readable("name") ? record.getName() : null,
                policy.readable("status") ? record.getStatus() : null,
                policy.readable("ownerUserId") ? record.getOwnerUserId() : null,
                policy.readable("createUserId") ? record.getCreateUserId() : null,
                policy.readable("createTime") ? record.getCreateTime() : null);
    }
}
