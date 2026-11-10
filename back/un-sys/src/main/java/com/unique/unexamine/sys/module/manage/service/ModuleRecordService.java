package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.ComputedFieldService;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.core.module.ModuleRecordProcessContext;
import com.unique.unexamine.core.module.ModuleRecordProcessHandler;
import com.unique.unexamine.core.module.ModuleDeleteApprovalStarter;
import com.unique.unexamine.core.module.ExamineCompletionContext;
import com.unique.unexamine.core.module.ExamineBusinessUpdateContext;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventPublisher;
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
import com.unique.unexamine.sys.module.base.entity.UnModulePageField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.entity.UnModuleRelation;
import com.unique.unexamine.sys.module.base.entity.UnModuleShare;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRelationService;
import com.unique.unexamine.sys.module.base.service.IUnModuleSequenceService;
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
import com.unique.unexamine.sys.module.manage.util.ModuleRecordStorage;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
    private final ModuleRecordStatusService recordStatuses;
    private final IUnModuleRelationService relations;
    private final IUnPlatAccountService accounts;
    private final ModuleShareService shares;
    private final ModuleFileBatchService fileBatches;
    private final ModuleRelationFieldService relationFields;
    private final IUnModuleSequenceService sequences;
    private final List<ModuleRecordProcessHandler> processHandlers;
    private final BusinessEventPublisher eventPublisher;

    /** 注入已生成基础 Service 和系统成员关系服务。 */
    public ModuleRecordService(SystemManageService systems, ModuleConfigService config,
            IUnSysSystemService systemRows, IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers, IUnModuleRecordService records,
            IUnModuleRecordDataService data, IUnModuleLogService logs, ObjectMapper json,
            ModulePermissionService permissions, ModuleFieldService fieldRules, ModuleMenuService menus,
            IUnModuleRelationService relations, IUnPlatAccountService accounts,ModuleShareService shares,
            ModuleFileBatchService fileBatches,ModuleRelationFieldService relationFields,
            IUnModuleSequenceService sequences) {
        this(systems, config, systemRows, members, tenantMembers, records, data, logs, json,
                permissions, fieldRules, menus, relations, accounts, shares, fileBatches,
                relationFields, sequences, List.of(), null, null);
    }

    /**
     * 完整构造函数由 Spring 注入 Flow、审批等记录保存后处理器；旧的单元测试构造函数继续可用。
     */
    @Autowired
    public ModuleRecordService(SystemManageService systems, ModuleConfigService config,
            IUnSysSystemService systemRows, IUnSysMemberService members,
            IUnSysTenantMemberService tenantMembers, IUnModuleRecordService records,
            IUnModuleRecordDataService data, IUnModuleLogService logs, ObjectMapper json,
            ModulePermissionService permissions, ModuleFieldService fieldRules, ModuleMenuService menus,
            IUnModuleRelationService relations, IUnPlatAccountService accounts, ModuleShareService shares,
            ModuleFileBatchService fileBatches, ModuleRelationFieldService relationFields,
            IUnModuleSequenceService sequences, @Lazy List<ModuleRecordProcessHandler> processHandlers,
            BusinessEventPublisher eventPublisher,ModuleRecordStatusService recordStatuses) {
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
        this.recordStatuses = recordStatuses;
        this.relations = relations;
        this.accounts = accounts;
        this.shares = shares;
        this.fileBatches = fileBatches;
        this.relationFields = relationFields;
        this.sequences = sequences;
        // Flow/审批处理器会回调记录服务；保留延迟代理，到保存成功后才解析处理器。
        this.processHandlers = processHandlers == null ? List.of() : processHandlers;
        this.eventPublisher = eventPublisher;
    }

    /** 普通表单新增和编辑各自使用明确菜单，不允许通过编辑接口绕过负责人转移权限。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public RecordView save(String token,Long systemId,Long tenantId,Long moduleId,SaveRecordRequest request) {
        if (request == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"记录内容不能为空");
        systems.context(token,systemId,tenantId); config.lockModule(systemId,moduleId);
        var access=permissions.require(token,systemId,tenantId,moduleId,request.id()==null?"CREATE":"EDIT");
        return saveWithAccess(token,access,moduleId,request,request.id()==null?"RECORD_CREATE":"RECORD_UPDATE",access.definition().menu().getName(),null,null);
    }

    /**
     * 使用已授权的模块动作保存一行记录。导入等批处理动作先由调用方取得菜单权限，
     * 每行仍复用字段校验、数据范围、流程触发和日志保存链。
     */
    public RecordView saveByAuthorizedAction(String token, ModulePermissionService.Access access,
            Long moduleId, SaveRecordRequest request, String operation) {
        if (access == null || access.definition() == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"模块动作授权不能为空");
        return saveWithAccess(token, access, moduleId, request, operation,
                access.definition().menu().getName(), null, null);
    }

    /**
     * 审批终态提交运行快照字段的受控入口。调用方已经由已发布审批配置授权，仍复用字段类型、必填、唯一、
     * 公式和主表槽位保存规则；提交只允许当前记录，不改变负责人和租户归属。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveByProcess(ExamineBusinessUpdateContext process) {
        if (process == null || process.values() == null || process.values().isEmpty()) return Map.of();
        UnModuleRecord record = records.getById(process.recordId());
        if (record == null || !Objects.equals(record.getSystemId(), process.systemId())
                || !Objects.equals(record.getTenantId(), process.tenantId())
                || !Objects.equals(record.getModuleId(), process.moduleId()) || record.getDeletedTime() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批自动节点关联的业务记录不存在");
        Long operatorId = process.accountId() != null ? process.accountId() : record.getActualOperatorId();
        if (operatorId == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"审批自动节点缺少可记录的操作账号");
        UnModule module = config.activeModule(process.systemId(), process.moduleId(), process.tenantId());
        List<UnModuleField> fields = config.currentFields(process.moduleId());
        Map<String, UnModuleRecordData> existing = dataByCode(record.getId());
        Map<String, Object> changed = new LinkedHashMap<>();
        Map<String, FieldDefinition> definitions = new LinkedHashMap<>();
        Map<String, Object> previousValues = new LinkedHashMap<>();
        Map<String, Object> effective = new LinkedHashMap<>();
        for (UnModuleField field : fields) {
            FieldDefinition definition = fieldRules.configured(field.getFieldKey(), config.fieldConfig(field).configuration());
            Object previous = storedValue(record, field, definition, existing);
            definitions.put(field.getFieldKey(), definition);
            previousValues.put(field.getFieldKey(), previous);
            // 自动编号属于系统计算字段，但它在业务记录生命周期内保持原值；公式重算时必须能引用该值。
            if (!definition.type().computed() || definition.type() == FieldType.AUTO_NUMBER)
                effective.put(field.getFieldKey(), previous);
        }
        for (Map.Entry<String, Object> entry : process.values().entrySet()) {
            UnModuleField field = fields.stream().filter(item -> entry.getKey().equals(item.getFieldKey())).findFirst()
                    .orElseThrow(() -> new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"审批自动节点写入了未配置字段: " + entry.getKey()));
            FieldDefinition definition = definitions.get(field.getFieldKey());
            if (definition.type().computed()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,definition.label() + "由系统计算，不能由审批自动节点写入");
            Object previous = previousValues.get(field.getFieldKey());
            Object value = fieldRules.normalize(definition, entry.getValue());
            fieldRules.validate(process.systemId(), process.tenantId(), definition, value, previous);
            relationFields.validateByProcess(process.systemId(), process.tenantId(), definition, value);
            fieldRules.validateUnique(process.systemId(), process.tenantId(), process.moduleId(), record.getId(), definition, value);
            effective.put(field.getFieldKey(), value);
            if (!same(previous, value)) changed.put(field.getFieldKey(), value);
            if ("name".equals(field.getFieldKey())) {
                if (value == null || value.toString().isBlank()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"记录标题不能为空");
                record.setName(value.toString().trim());
            }
            else ModuleRecordStorage.write(record, definition, value);
        }
        // 审批节点修改普通字段后，依赖这些字段的公式也必须在终态重新计算并持久化。
        Set<String> pendingFormula = fields.stream()
                .filter(field -> definitions.get(field.getFieldKey()).type() == FieldType.FORMULA)
                .map(UnModuleField::getFieldKey).collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        while (!pendingFormula.isEmpty()) {
            boolean progressed = false;
            for (String code : new ArrayList<>(pendingFormula)) {
                FieldDefinition definition = definitions.get(code);
                ComputedFieldService.FormulaRule rule = ComputedFieldService.formula(definition);
                if (ComputedFieldService.references(rule).stream()
                        .anyMatch(reference -> definitions.containsKey(reference) && !effective.containsKey(reference))) continue;
                Object value = ComputedFieldService.evaluate(rule, effective);
                fieldRules.validateComputedValue(definition, value);
                fieldRules.validateUnique(process.systemId(), process.tenantId(), process.moduleId(), record.getId(), definition, value);
                Object previous = previousValues.get(code);
                effective.put(code, value);
                if (!same(previous, value)) changed.put(code, value);
                if (!same(previous, value) && ModuleRecordStorage.isMain(definition))
                    ModuleRecordStorage.write(record, definition, value);
                pendingFormula.remove(code);
                progressed = true;
            }
            if (!progressed) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "公式字段存在循环依赖或引用了无法计算的字段");
        }
        // 审批自动节点也可能写入附件字段，先复用普通保存的批次归属校验，避免把他人或其他租户的文件写入业务记录。
        fileBatches.validate(process.systemId(), process.tenantId(), record.getId(), operatorId, fields, effective);
        record.setActualOperatorId(operatorId);
        record.setUpdateUserId(operatorId);
        persistRecord(record, fields);
        for (Map.Entry<String, Object> entry : changed.entrySet()) {
            UnModuleField field = fields.stream().filter(item -> entry.getKey().equals(item.getFieldKey())).findFirst().orElseThrow();
            FieldDefinition definition = definitions.get(field.getFieldKey());
            if (ModuleRecordStorage.isMain(definition)) continue;
            UnModuleRecordData item = existing.getOrDefault(field.getFieldKey(), new UnModuleRecordData());
            item.setRecordId(record.getId()); item.setFieldId(field.getId()); item.setFieldKey(field.getFieldKey());
            fieldRules.store(item, definition, entry.getValue());
            persistDataValue(item);
        }
        fileBatches.sync(record, fields, effective);
        UnModuleLog log = new UnModuleLog();
        log.setSystemId(record.getSystemId()); log.setTenantId(record.getTenantId()); log.setActorTenantId(process.tenantId());
        log.setModuleId(record.getModuleId());
        log.setRecordId(record.getId()); log.setOperatorAccountId(operatorId);
        log.setActionCode(process.operation() == null ? "EXAMINE_API_UPDATE" : process.operation());
        log.setPermissionJson(writeValue(Map.of("source", "EXAMINE_AUTO_NODE")));
        Map<String, Object> logDetail = new LinkedHashMap<>();
        logDetail.put("fieldValues", changed);
        log.setDetailJson(writeValue(logDetail));
        log.setCreateTime(LocalDateTime.now());
        logs.addOrUpdate(log);
        // 审批终态的业务写回不是普通用户编辑；Flow/examine 的后续线路由由审批完成回调负责，
        // 这里不再次触发模块 UPDATE 绑定，避免同一审批完成后重新创建审批实例。
        return new LinkedHashMap<>(effective);
    }

    /** 审批节点提交字段时提前执行类型、关联、附件和唯一性校验，运行快照不保存无效值。 */
    @Transactional(rollbackFor = Exception.class)
    public void validateByProcess(ExamineBusinessUpdateContext process) {
        if (process == null || process.values() == null || process.values().isEmpty()) return;
        UnModuleRecord record = records.getById(process.recordId());
        if (record == null || !Objects.equals(record.getSystemId(), process.systemId())
                || !Objects.equals(record.getTenantId(), process.tenantId())
                || !Objects.equals(record.getModuleId(), process.moduleId()) || record.getDeletedTime() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批节点关联的业务记录不存在");
        Long operatorId = process.accountId() != null ? process.accountId() : record.getActualOperatorId();
        if (operatorId == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点缺少可记录的操作账号");
        config.activeModule(process.systemId(), process.moduleId(), process.tenantId());
        List<UnModuleField> fields = config.currentFields(process.moduleId());
        Map<String, UnModuleRecordData> existing = dataByCode(record.getId());
        Map<String, Object> effective = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : process.values().entrySet()) {
            UnModuleField field = fields.stream().filter(item -> entry.getKey().equals(item.getFieldKey())).findFirst()
                    .orElseThrow(() -> new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,
                            "审批节点写入了未配置字段: " + entry.getKey()));
            FieldDefinition definition = fieldRules.configured(field.getFieldKey(),
                    config.fieldConfig(field).configuration());
            if (definition.type().computed())
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, definition.label() + "由系统计算，不能由审批节点直接写入");
            Object previous = storedValue(record, field, definition, existing);
            Object value = fieldRules.normalize(definition, entry.getValue());
            fieldRules.validate(process.systemId(), process.tenantId(), definition, value, previous);
            relationFields.validateByProcess(process.systemId(), process.tenantId(), definition, value);
            fieldRules.validateUnique(process.systemId(), process.tenantId(), process.moduleId(), record.getId(), definition, value);
            if ("name".equals(field.getFieldKey()) && (value == null || value.toString().isBlank()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "记录标题不能为空");
            effective.put(field.getFieldKey(), value);
        }
        fileBatches.validate(process.systemId(), process.tenantId(), record.getId(), operatorId, fields, effective);
    }

    /** 表单、自定义字段更新和转化创建都调用本保存链；不是复制一套 CRUD。 */
    private RecordView saveWithAccess(String token,ModulePermissionService.Access access,Long moduleId,SaveRecordRequest request,
                                      String operation,String actionName,Long relatedModuleId,Long relatedRecordId) {
        if (request == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"记录内容不能为空");
        if (!"DRAFT".equals(request.mode()) && !"SUBMIT".equals(request.mode()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"保存方式只能是草稿或提交");
        boolean draft="DRAFT".equals(request.mode());
        var context=access.context(); Long systemId=context.systemId();
        if (request.values()==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段值不能为空");
        UnModule module=config.activeModule(systemId,moduleId,context.tenantId());
        List<UnModuleField> fields=config.currentFields(moduleId);
        Set<String> allowed=fields.stream().map(UnModuleField::getFieldKey).collect(java.util.stream.Collectors.toSet());
        if (!allowed.containsAll(request.values().keySet())) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"提交包含未配置字段");
        for (UnModuleField field : fields) {
            if (request.values().containsKey(field.getFieldKey())) fieldRules.requireEditable(field);
        }
        ActionRecord selected=request.id()==null?null:actionRecord(access,request.id());
        UnModuleRecord record=selected==null?new UnModuleRecord():selected.record();
        PermissionPolicy fieldPolicy=selected==null?access.policy():permissions.recordPolicy(access,record);
        fieldPolicy.requireWritable(request.values().keySet());
        UnModuleShare share=selected==null?null:selected.share();
        Long dataTenantId=selected==null?context.tenantId():record.getTenantId();
        if (request.id()!=null) {
            if (request.ownerAccountId()!=null && !request.ownerAccountId().equals(record.getOwnerUserId()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"负责人变更请使用转移动作");
        }
        Map<String,UnModuleRecordData> existing=request.id()==null?Map.of():dataByCode(record.getId());
        Map<String,FieldDefinition> definitions=new LinkedHashMap<>();
        Map<String,Object> previousValues=new LinkedHashMap<>();
        Map<String,Object> effective=new LinkedHashMap<>(); List<FieldChange> changes=new ArrayList<>();
        List<UnModuleField> computedFields=new ArrayList<>();
        for (UnModuleField field:fields) {
            String code=field.getFieldKey();
            FieldDefinition definition=fieldRules.configured(code,config.fieldConfig(field).configuration());
            Object previous=storedValue(record,field,definition,existing);
            definitions.put(code,definition); previousValues.put(code,previous);
            if (definition.type().computed()) {
                if (request.values().containsKey(code)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,definition.label()+"由系统计算，不能直接提交");
                computedFields.add(field);
                if (previous!=null) effective.put(code,previous);
                continue;
            }
            Object value=request.values().containsKey(code)?request.values().get(code):previous;
            if (request.id()==null && !request.values().containsKey(code)) value=fieldRules.defaultValue(definition);
            if ("name".equals(code) && value instanceof String text) value=text.trim();
            value=fieldRules.normalize(definition,value);
            fieldRules.validate(systemId,dataTenantId,definition,value,previous,draft);
            relationFields.validate(token,systemId,context.tenantId(),definition,value);
            fieldRules.validateUnique(systemId,dataTenantId,moduleId,request.id(),definition,value);
            effective.put(code,value);
            if (!same(previous,value)) changes.add(new FieldChange(field.getId(),code,definition.label(),previous,value,
                    fieldRules.display(systemId,dataTenantId,definition,previous),
                    fieldRules.display(systemId,dataTenantId,definition,value)));
        }
        // 自动编号只在新增记录或旧记录缺少该值时分配，编辑已有编号不会重新编号。
        for (UnModuleField field:computedFields) {
            FieldDefinition definition=definitions.get(field.getFieldKey());
            if (definition.type()!=FieldType.AUTO_NUMBER) continue;
            Object previous=previousValues.get(field.getFieldKey());
            if (previous!=null) continue;
            Object value=generateAutoNumber(moduleId,dataTenantId,definition);
            fieldRules.validateComputedValue(definition,value);
            fieldRules.validateUnique(systemId,dataTenantId,moduleId,request.id(),definition,value);
            effective.put(field.getFieldKey(),value);
            changes.add(new FieldChange(field.getId(),field.getFieldKey(),definition.label(),null,value,Map.of(),Map.of()));
        }
        // 公式按依赖逐轮计算，允许公式字段依赖已经计算出的其他公式字段，禁止循环依赖。
        Set<String> pendingFormula=new java.util.LinkedHashSet<>();
        for (UnModuleField field:computedFields) if (definitions.get(field.getFieldKey()).type()==FieldType.FORMULA)
            pendingFormula.add(field.getFieldKey());
        while (!pendingFormula.isEmpty()) {
            boolean progressed=false;
            for (String code:new ArrayList<>(pendingFormula)) {
                FieldDefinition definition=definitions.get(code);
                ComputedFieldService.FormulaRule rule=ComputedFieldService.formula(definition);
                if (ComputedFieldService.references(rule).stream().anyMatch(reference ->
                        definitions.containsKey(reference) && !effective.containsKey(reference))) continue;
                Object value=ComputedFieldService.evaluate(rule,effective);
                fieldRules.validateComputedValue(definition,value);
                fieldRules.validateUnique(systemId,dataTenantId,moduleId,request.id(),definition,value);
                Object previous=previousValues.get(code); effective.put(code,value); pendingFormula.remove(code); progressed=true;
                if (!same(previous,value)) changes.add(new FieldChange(findField(fields,code).getId(),code,definition.label(),previous,value,Map.of(),Map.of()));
            }
            if (!progressed) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"公式字段存在循环依赖或引用了无法计算的字段");
        }
        String name=(String)effective.get("name");
        if (!draft && (name==null || name.isBlank())) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"记录标题不能为空");
        fileBatches.validate(systemId,dataTenantId,request.id(),access.accountId(),fields,effective);
        Long ownerId=request.ownerAccountId();
        if (ownerId==null) {
            // 新记录优先归属当前登录成员；无登录身份的接入才回退到系统所属人。
            ownerId=request.id()==null
                    ? access.accountId()!=null?access.accountId():systemRows.queryById(systemId).getOwnerAccountId()
                    : record.getOwnerUserId();
        }
        var owner=ownerMembership(systemId,dataTenantId,ownerId);
        // 团队编辑只授权原记录；负责人变更仍由独立转移动作处理。
        if (share==null && !(request.id()!=null && permissions.teamAllows(access,record)))
            access.policy().requireOwner(ownerId);
        if (request.id()==null) {
            record.setSystemId(systemId); record.setTenantId(context.tenantId()); record.setModuleId(moduleId);
            record.setCheckStatus(0);
            // 创建人使用实际登录成员；无登录身份的接入才回退系统所属人。
            record.setCreateUserId(access.accountId()!=null
                    ? access.accountId():systemRows.queryById(systemId).getOwnerAccountId());
        }
        // 模块动作决定保存后的业务状态；草稿不启动 Flow 或审批。
        String targetStatus=draft?access.definition().settings().draftStatusCode():access.definition().settings().submitStatusCode();
        if (targetStatus==null) targetStatus=draft?ModuleRecordStatusService.DRAFT:
                Set.of("CREATE","EDIT").contains(access.definition().settings().type())?ModuleRecordStatusService.NORMAL:null;
        if (targetStatus!=null) {
            if (recordStatuses!=null) recordStatuses.requireTarget(moduleId,context.tenantId(),targetStatus);
            record.setStatus(targetStatus);
        }
        record.setName(name==null?"":name); record.setOwnerUserId(ownerId); record.setOwnerDeptId(owner.getDepartmentId());
        record.setActualOperatorId(access.accountId()); record.setUpdateUserId(access.accountId());
        for (UnModuleField field:fields) {
            if ("name".equals(field.getFieldKey())) continue;
            FieldDefinition definition=fieldRules.configured(field.getFieldKey(),config.fieldConfig(field).configuration());
            ModuleRecordStorage.write(record,definition,effective.get(field.getFieldKey()));
        }
        persistRecord(record,fields);
        for (UnModuleField field:fields) {
            String code=field.getFieldKey(); if ("name".equals(code)) continue;
            var item=existing.getOrDefault(code,new UnModuleRecordData());
            FieldDefinition definition=fieldRules.configured(code,config.fieldConfig(field).configuration());
            if (ModuleRecordStorage.isMain(definition)) continue;
            if (item.getId()!=null && same(fieldRules.stored(item,definition),effective.get(code))) continue;
            item.setRecordId(record.getId()); item.setFieldId(field.getId()); item.setFieldKey(code);
            fieldRules.store(item,definition,effective.get(code));
            persistDataValue(item);
        }
        fileBatches.sync(record,fields,effective);
        audit(module,record,access,share,operation,actionName,changes,relatedModuleId,relatedRecordId);
        if (draft) return view(record,fieldPolicy,context.tenantId());
        ModuleRecordProcessContext processContext = new ModuleRecordProcessContext(
                systemId, record.getTenantId(), moduleId, record.getId(), access.accountId(),
                processEvent(operation), access.definition().menu().getId(), operation, effective);
        publishEvent(processContext, request.id() == null ? "RECORD_CREATE" : "RECORD_UPDATE",
                Map.of("operation", operation == null ? "" : operation));
        for (ModuleRecordProcessHandler handler : processHandlers) handler.afterSave(processContext);
        UnModuleRecord current=records.getById(record.getId());
        return view(current==null?record:current,fieldPolicy,context.tenantId());
    }

    /** 将保存业务动作归一为 Flow/审批绑定使用的事件编码。 */
    private String processEvent(String operation) {
        if (operation == null) return "UPDATE";
        if (operation.contains("CREATE")) return "CREATE";
        if (operation.contains("UPDATE")) return "UPDATE";
        if (operation.contains("TRANSFER")) return "TRANSFER";
        return operation;
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
            } catch (BaseException ex) { if (ex.getCode()!=SystemCodeEnum.SYSTEM_NO_AUTH.getCode()) throw ex; }
        }
        return result;
    }

    /** 更新字段的对话框复用当前字段布局、字典与业务值，不另行计算排版。 */
    public RecordInformation actionForm(String token,Long systemId,Long tenantId,Long moduleId,Long menuId,Long recordId) {
        var access=permissions.requireMenu(token,systemId,tenantId,moduleId,menuId);
        if (!"UPDATE_FIELDS".equals(access.definition().settings().type())) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"该动作不使用字段更新表单");
        var record=readableRecord(access,recordId,false);
        var values=recordValues(record); conditions(access,values);
        var fields=actionFields(access.definition()); access.policy().requireWritable(fields.keySet());
        var form=fields.values().stream().map(field->config.runtimeFieldConfig(systemId,record.getTenantId(),field,true)).toList();
        for (UnModuleField field:fields.values()) if (values.containsKey(field.getFieldKey())) {
            FieldDefinition definition=fieldRules.configured(field.getFieldKey(),config.fieldConfig(field).configuration());
            values.put(field.getFieldKey(),fieldRules.present(systemId,record.getTenantId(),definition,values.get(field.getFieldKey())));
        }
        return new RecordInformation(recordId,record.getTenantId(),!record.getTenantId().equals(access.context().tenantId()),
                access.policy().readable("status")?record.getStatus():null,
                access.policy().readable("status")?record.getCheckStatus():null,
                bind(form,values),Map.of());
    }

    /** 动作执行统一检查菜单、条件、数据范围及字段许可，再进入同一业务保存事务。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public ActionResult execute(String token,Long systemId,Long tenantId,Long moduleId,Long menuId,Long recordId,ExecuteRequest request) {
        if (request == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"动作执行参数不能为空");
        var initial=permissions.requireMenu(token,systemId,tenantId,moduleId,menuId);
        Set<Long> locks=new HashSet<>(); locks.add(moduleId);
        if ("CONVERT".equals(initial.definition().settings().type()) && initial.definition().settings().targetModuleId()!=null)
            locks.add(initial.definition().settings().targetModuleId());
        locks.stream().sorted().forEach(id->config.lockModule(systemId,id));
        var access=permissions.requireMenu(token,systemId,tenantId,moduleId,menuId);
        if (!Objects.equals(initial.definition().settings().targetModuleId(),access.definition().settings().targetModuleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"动作配置已更新，请刷新后执行");
        ActionRecord selected=actionRecord(access,recordId);
        var record=selected.record();
        var values=recordValues(record); conditions(access,values);
        Map<String,Object> submitted=request.values()==null?Map.of():request.values();
        switch (access.definition().settings().type()) {
            case "UPDATE_FIELDS" -> {
                if (request.ownerAccountId()!=null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段动作不能变更负责人");
                var fields=actionFields(access.definition());
                if (submitted.isEmpty() || !fields.keySet().containsAll(submitted.keySet())) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"只能提交动作指定字段");
                saveWithAccess(token,access,moduleId,new SaveRecordRequest(recordId,null,submitted),"ACTION_FIELD_UPDATE",access.definition().menu().getName(),null,null);
            }
            case "UPDATE_STATUS" -> {
                if (!submitted.isEmpty() || request.ownerAccountId()!=null)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"状态动作不接受字段或负责人输入");
                String target=access.definition().settings().submitStatusCode();
                if (target==null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"状态动作尚未配置目标状态");
                recordStatuses.requireTarget(moduleId,record.getTenantId(),target);
                String before=record.getStatus();
                record.setStatus(target); record.setActualOperatorId(access.accountId()); record.setUpdateUserId(access.accountId());
                persistRecord(record);
                audit(access.definition().module(),record,access,selected.share(),"ACTION_STATUS_UPDATE",
                        access.definition().menu().getName(),List.of(new FieldChange(null,"status","业务状态",before,target,
                                Map.of(),Map.of())),null,null);
                ModuleRecordProcessContext process=new ModuleRecordProcessContext(systemId,record.getTenantId(),moduleId,
                        recordId,access.accountId(),"UPDATE_STATUS",menuId,"ACTION_STATUS_UPDATE",recordValues(record));
                publishEvent(process,"ACTION_STATUS_UPDATE",Map.of("beforeStatus",before,"status",target));
                for (ModuleRecordProcessHandler handler:processHandlers) handler.afterAction(process);
            }
            case "TRANSFER" -> {
                if (!submitted.isEmpty() || request.ownerAccountId()==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"转移动作仅接受目标负责人");
                var owner=ownerMembership(systemId,record.getTenantId(),request.ownerAccountId());
                Long before=record.getOwnerUserId(); record.setOwnerUserId(request.ownerAccountId()); record.setOwnerDeptId(owner.getDepartmentId());
                String targetStatus=access.definition().settings().submitStatusCode();
                if (targetStatus!=null) {
                    recordStatuses.requireTarget(moduleId,record.getTenantId(),targetStatus);
                    record.setStatus(targetStatus);
                }
                record.setActualOperatorId(access.accountId()); record.setUpdateUserId(access.accountId());
                persistRecord(record);
                audit(access.definition().module(),record,access,selected.share(),"RECORD_TRANSFER",access.definition().menu().getName(),
                        List.of(new FieldChange(null,"ownerUserId","负责人",before,record.getOwnerUserId(),
                                Map.of(String.valueOf(before),accounts.queryById(before).getDisplayName()),
                                Map.of(String.valueOf(record.getOwnerUserId()),accounts.queryById(record.getOwnerUserId()).getDisplayName()))),null,null);
                // 转移动作已经完成记录写入和审计；沿用同一处理器链，使按菜单动作配置的 Flow/examine 生效。
                Map<String,Object> transferValues=new LinkedHashMap<>(recordValues(record));
                transferValues.put("ownerUserId",record.getOwnerUserId());
                transferValues.put("ownerDeptId",record.getOwnerDeptId());
                ModuleRecordProcessContext processContext=new ModuleRecordProcessContext(
                        systemId,record.getTenantId(),moduleId,record.getId(),access.accountId(),
                        "TRANSFER",access.definition().menu().getId(),"RECORD_TRANSFER",transferValues);
                Map<String,Object> transferMetadata=new LinkedHashMap<>();
                transferMetadata.put("beforeOwnerUserId",before);
                transferMetadata.put("ownerUserId",record.getOwnerUserId());
                transferMetadata.put("ownerDeptId",record.getOwnerDeptId());
                publishEvent(processContext,"RECORD_TRANSFER",transferMetadata);
                for (ModuleRecordProcessHandler handler:processHandlers) handler.afterAction(processContext);
            }
            case "CONVERT" -> {
                if (selected.share()!=null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"共享记录不能转化");
                if (!submitted.isEmpty() || request.ownerAccountId()!=null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"转化只使用当前保存的字段映射");
                return convert(token,access,record,values);
            }
            default -> throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"请使用该基础动作对应的业务入口");
        }
        return new ActionResult(recordId,null,null);
    }

    /** 同一来源到同一目标模块只允许一次成功转化，关系与两端日志共同提交。 */
    private ActionResult convert(String token,ModulePermissionService.Access access,UnModuleRecord source,Map<String,Object> values) {
        var settings=access.definition().settings(); Long targetModule=settings.targetModuleId();
        if (targetModule==null || settings.mappings().isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"转化动作尚未配置目标与字段映射");
        if (relations.count(new LambdaQueryWrapper<UnModuleRelation>().eq(UnModuleRelation::getSourceRecordId,source.getId())
                .eq(UnModuleRelation::getTargetModuleId,targetModule).eq(UnModuleRelation::getRelationType,"CONVERSION"))>0)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"此记录已转化到该目标模块");
        var targetAccess=permissions.require(token,access.context().systemId(),access.context().tenantId(),targetModule,"CREATE");
        Map<Long,UnModuleField> sourceFields=new HashMap<>(),targetFields=new HashMap<>();
        config.currentFields(source.getModuleId()).forEach(f->sourceFields.put(f.getId(),f));
        config.currentFields(targetModule).forEach(f->targetFields.put(f.getId(),f));
        Map<String,Object> mapped=new LinkedHashMap<>();
        for (var mapping:settings.mappings()) {
            var from=sourceFields.get(mapping.sourceFieldId()); var to=targetFields.get(mapping.targetFieldId());
            if (from==null || to==null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"字段映射已失效，请重新配置");
            if (!access.policy().readable(from.getFieldKey())) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"没有来源字段读取权限");
            var fromDefinition=fieldRules.configured(from.getFieldKey(),config.fieldConfig(from).configuration());
            var toDefinition=fieldRules.configured(to.getFieldKey(),config.fieldConfig(to).configuration());
            fieldRules.validateMapping(fromDefinition,toDefinition);
            if (mapped.containsKey(to.getFieldKey())) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"目标字段映射重复");
            mapped.put(to.getFieldKey(),values.get(from.getFieldKey()));
        }
        var created=saveWithAccess(token,targetAccess,targetModule,new SaveRecordRequest(null,null,mapped),"RECORD_CONVERT_CREATE",
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
            String code=condition.fieldId()==0L?"status":condition.fieldId()==-1L?"checkStatus":
                    field==null?null:field.getFieldKey();
            if (code==null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"动作条件字段已失效");
            if (!access.policy().readable(field==null?"status":code)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"没有条件字段读取权限");
            Object value=values.get(code); boolean matched;
            boolean empty=value==null || value instanceof String text && text.isBlank() || value instanceof java.util.Collection<?> list && list.isEmpty();
            switch (condition.operator()) {
                case "EMPTY" -> matched=empty;
                case "NOT_EMPTY" -> matched=!empty;
                case "EQ" -> matched=same(value,condition.value());
                case "NE" -> matched=!same(value,condition.value());
                case "IN" -> matched=condition.value() instanceof List<?> list && list.stream().anyMatch(item->same(item,value));
                case "GT","GE","LT","LE" -> {
                    if (value==null) { matched=false; break; }
                    if (!(value instanceof Number) || !(condition.value() instanceof Number)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"大小比较条件必须使用数值");
                    int comparison=new BigDecimal(value.toString()).compareTo(new BigDecimal(condition.value().toString()));
                    matched=switch (condition.operator()) { case "GT"->comparison>0; case "GE"->comparison>=0; case "LT"->comparison<0; default->comparison<=0; };
                }
                default -> throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"动作条件操作符无效");
            }
            if (!matched) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"当前记录不满足动作执行条件");
        }
    }
    private Map<String,UnModuleField> actionFields(ModuleMenuDto.Definition definition) {
        Map<String,UnModuleField> result=new LinkedHashMap<>(); var ids=definition.settings().fieldIds();
        for (var field:config.currentFields(definition.module().getId())) if (ids.contains(field.getId())) result.put(field.getFieldKey(),field);
        if (result.size()!=ids.size()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"动作字段已失效"); return result;
    }
    private Map<String,Object> recordValues(UnModuleRecord record) {
        List<UnModuleField> fields=config.currentFields(record.getModuleId());
        Map<String,UnModuleRecordData> rows=dataByCode(record.getId());
        Map<String,Object> result=new LinkedHashMap<>(); result.put("name",record.getName());
        result.put("status",record.getStatus()); result.put("checkStatus",record.getCheckStatus());
        for (UnModuleField field:fields) {
            if ("name".equals(field.getFieldKey())) continue;
            FieldDefinition definition=fieldRules.configured(field.getFieldKey(),config.fieldConfig(field).configuration());
            Object value=ModuleRecordStorage.isMain(definition)?ModuleRecordStorage.read(record,definition)
                    :fieldRules.stored(rows.get(field.getFieldKey()),definition);
            if (value!=null) result.put(field.getFieldKey(),value);
        }
        return result;
    }
    private boolean same(Object left,Object right) {
        if (left instanceof Number && right instanceof Number) return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()))==0;
        return json.valueToTree(left).equals(json.valueToTree(right));
    }

    /** 使用独立序列表分配并发安全的自动编号，不通过查询业务记录最大值推算。 */
    private String generateAutoNumber(Long moduleId,Long tenantId,FieldDefinition definition) {
        ComputedFieldService.AutoNumberRule rule=ComputedFieldService.autoNumber(definition);
        LocalDateTime now=LocalDateTime.now();
        long number=sequences.nextValue(moduleId,tenantId,rule.ruleCode(),rule.periodKey(now),rule.start());
        return rule.render(now,number);
    }

    private UnModuleField findField(List<UnModuleField> fields,String fieldKey) {
        return fields.stream().filter(field->fieldKey.equals(field.getFieldKey())).findFirst()
                .orElseThrow(()->new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"公式字段不存在: "+fieldKey));
    }

    /** 基础记录查询，不混入扩展字段或详情布局。 */
    public RecordView queryById(String token, Long systemId, Long tenantId, Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DETAIL");
        var context = access.context();
        config.activeModule(systemId, moduleId, context.tenantId());
        var record = readableRecord(access,recordId,false);
        return view(record, permissions.recordPolicy(access,record),context.tenantId());
    }

    /**
     * 应用外部运行入口只读取授权所需的记录边界，不把主表实体直接暴露给控制器。
     * 记录必须属于请求的系统、租户和模块，且不能是逻辑删除数据。
     */
    public ExternalRecordBoundary requireExternalBoundary(Long systemId, Long tenantId,
                                                           Long moduleId, Long recordId) {
        UnModuleRecord record = records.getOne(new LambdaQueryWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId, recordId)
                .eq(UnModuleRecord::getSystemId, systemId)
                .eq(UnModuleRecord::getTenantId, tenantId)
                .eq(UnModuleRecord::getModuleId, moduleId)
                .isNull(UnModuleRecord::getDeletedTime), false);
        if (record == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "业务记录不存在");
        return new ExternalRecordBoundary(record.getId(), record.getOwnerUserId(), record.getOwnerDeptId());
    }

    /** 应用授权数据范围需要的业务记录最小边界视图。 */
    public record ExternalRecordBoundary(Long id, Long ownerAccountId, Long ownerDeptId) {}

    /** 打印动作按自身菜单权限读取字段，避免强制依赖详情菜单授权。 */
    public AuthorizedRecordValues valuesByAuthorizedAction(String token, ModulePermissionService.Access access,
            Long recordId, Set<String> requestedFields) {
        if (access == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"打印动作授权不能为空");
        UnModuleRecord record = readableRecord(access, recordId, false);
        Set<String> requested = requestedFields == null ? Set.of() : Set.copyOf(requestedFields);
        List<UnModuleField> fields = config.currentFields(record.getModuleId());
        for (String code : requested) if (fields.stream().noneMatch(field -> code.equals(field.getFieldKey())))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"打印模板字段不存在：" + code);
        Map<String, UnModuleRecordData> stored = dataByCode(record.getId());
        Map<String, Object> values = new LinkedHashMap<>();
        for (UnModuleField field : fields) {
            String code = field.getFieldKey();
            if (!requested.isEmpty() && !requested.contains(code)) continue;
            if (!access.policy().readable(code)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,
                    "没有打印字段读取权限：" + code);
            FieldDefinition definition = fieldRules.configured(code, config.fieldConfig(field).configuration());
            Object value = "name".equals(code) ? record.getName()
                    : ModuleRecordStorage.isMain(definition) ? ModuleRecordStorage.read(record, definition)
                    : fieldRules.stored(stored.get(code), definition);
            if (value == null) continue;
            if (definition.type().options()) value = fieldRules.display(access.context().systemId(),
                    record.getTenantId(), definition, value);
            else if (Set.of(FieldType.USER, FieldType.DEPARTMENT, FieldType.DATE_TIME).contains(definition.type()))
                value = fieldRules.present(access.context().systemId(), record.getTenantId(), definition, value);
            else if (definition.type() == FieldType.RELATION)
                value = relationFields.display(token, access.context().systemId(), access.context().tenantId(), field, definition, value);
            values.put(code, value);
        }
        return new AuthorizedRecordValues(record.getId(), record.getName(), values);
    }

    /** 打印动作读取的脱敏字段值；只在 manage 服务之间传递，不作为 Entity 暴露。 */
    public record AuthorizedRecordValues(Long id, String name, Map<String, Object> values) {}

    /** 按当前保存的字段顺序和布局填充记录详情。 */
    public RecordInformation information(String token, Long systemId, Long tenantId,
                                         Long moduleId, Long recordId) {
        return informationForPage(token,systemId,tenantId,moduleId,recordId,"DETAIL");
    }

    /** 编辑表单使用独立页面布局，并在读取记录前取得编辑动作权限。 */
    public RecordInformation editInformation(String token, Long systemId, Long tenantId,
                                             Long moduleId, Long recordId) {
        return informationForPage(token,systemId,tenantId,moduleId,recordId,"EDIT");
    }

    private RecordInformation informationForPage(String token,Long systemId,Long tenantId,
            Long moduleId,Long recordId,String pageKind) {
        var access = permissions.require(token, systemId, tenantId, moduleId, pageKind);
        var context = access.context();
        config.activeModule(systemId, moduleId, context.tenantId());
        UnModuleRecord record = readableRecord(access,recordId,false);
        PermissionPolicy fieldPolicy=permissions.recordPolicy(access,record);
        boolean shared=!record.getTenantId().equals(context.tenantId());
        ModulePermissionService.Access editAccess="EDIT".equals(pageKind)?access:null;
        if (editAccess==null) try {
            editAccess=permissions.require(token,systemId,tenantId,moduleId,"EDIT");
            if (shared) shares.requireShared(editAccess,record);
            else if (!permissions.teamAllows(editAccess,record)) editAccess.policy().requireOwner(record.getOwnerUserId());
        } catch (BaseException ex) {
            if (ex.getCode()!=SystemCodeEnum.SYSTEM_NO_AUTH.getCode()
                    && ex.getCode()!=SystemCodeEnum.SYSTEM_NO_FOUND.getCode()) throw ex;
            editAccess=null;
        }
        final PermissionPolicy editPolicy=editAccess==null?null:permissions.recordPolicy(editAccess,record);
        Map<String, Object> values = new LinkedHashMap<>();
        var stored = dataByCode(record.getId());
        Map<Long,UnModuleField> byId=new HashMap<>();
        config.currentFields(moduleId).forEach(field->byId.put(field.getId(),field));
        List<UnModulePageField> slots=config.pageFields(moduleId,pageKind).stream()
                .filter(slot->byId.containsKey(slot.getFieldId())).toList();
        for (UnModulePageField slot:slots) {
            UnModuleField field=byId.get(slot.getFieldId());
            if (!fieldPolicy.readable(field.getFieldKey())) continue;
            FieldDefinition definition=fieldRules.configured(field.getFieldKey(),config.fieldConfig(field).configuration());
            Object raw="name".equals(field.getFieldKey())?record.getName()
                    :ModuleRecordStorage.isMain(definition)?ModuleRecordStorage.read(record,definition)
                    :fieldRules.stored(stored.get(field.getFieldKey()),definition);
            if (raw!=null) values.put(field.getFieldKey(),fieldRules.present(systemId,record.getTenantId(),definition,raw));
        }
        List<FieldConfig> form = slots.stream()
                .filter(slot->fieldPolicy.readable(byId.get(slot.getFieldId()).getFieldKey()))
                .map(slot -> {
                    UnModuleField field=byId.get(slot.getFieldId());
                    boolean editable=editPolicy!=null && editPolicy.writable(field.getFieldKey());
                    var item = config.runtimePageFieldConfig(systemId, record.getTenantId(), field, slot, editable);
                    FieldDefinition definition=fieldRules.configured(field.getFieldKey(),config.fieldConfig(field).configuration());
                    Object raw="name".equals(field.getFieldKey())?record.getName()
                            :ModuleRecordStorage.isMain(definition)?ModuleRecordStorage.read(record,definition)
                            :fieldRules.stored(stored.get(field.getFieldKey()),definition);
                    if (raw!=null) {
                        item.configuration().put("valueDisplay",definition.type()==FieldType.RELATION
                                ?relationFields.display(token,systemId,context.tenantId(),field,definition,raw)
                                :fieldRules.display(systemId,record.getTenantId(),definition,raw));
                    }
                    return item;
                }).toList();
        return new RecordInformation(recordId,record.getTenantId(),!record.getTenantId().equals(context.tenantId()),
                fieldPolicy.readable("status")?record.getStatus():null,
                fieldPolicy.readable("status")?record.getCheckStatus():null,
                bind(form,values),config.approvalStatuses(systemId,record.getTenantId(),moduleId,List.of(recordId))
                        .getOrDefault(recordId,Map.of()));
    }

    /** 业务列表在授权后的记录范围内分页，逻辑删除由 MyBatis-Plus 处理。 */
    public Page<RecordView> queryPageList(String token, Long systemId, Long tenantId, Long moduleId,
                                    ModuleRecordSearchBO search) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "LIST");
        var context = access.context();
        config.activeModule(systemId, moduleId, context.tenantId());
        // GET 查询也可能被内部服务直接调用，缺省条件按公共 SearchBO 规则处理，
        // 不让 Controller 的参数绑定成为业务 Service 正常工作的前置条件。
        ModuleRecordSearchBO criteria = search == null ? new ModuleRecordSearchBO() : search;
        String scope=criteria.getScope()==null?"ALL":criteria.getScope();
        Page<UnModuleRecord> requestPage=criteria.parse();
        if (!Set.of("ALL","MINE","SHARED").contains(scope)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"列表范围无效");
        QueryWrapper<UnModuleRecord> where=new QueryWrapper<UnModuleRecord>()
                .eq("system_id",systemId).eq("module_id",moduleId);
        if (criteria.getStatusCode()!=null || criteria.getCheckStatus()!=null) {
            if (!permissions.listFieldsPolicy(access).readable("status"))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"没有状态字段的查询权限");
            if (criteria.getStatusCode()!=null) {
                if (!criteria.getStatusCode().matches("[A-Z][A-Z0-9_]{0,39}"))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"业务状态编码无效");
                where.eq("status",criteria.getStatusCode());
            }
            if (criteria.getCheckStatus()!=null) {
                if (criteria.getCheckStatus()<0 || criteria.getCheckStatus()>4)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"审批状态值无效");
                where.eq("check_status",criteria.getCheckStatus());
            }
        }
        if (!shares.applyRecordScope(where,access,scope))
            return new Page<RecordView>(requestPage.getCurrent(),requestPage.getSize(),0).setRecords(List.of());
        if (criteria.getKeyword()!=null && !criteria.getKeyword().isBlank()) {
            if (!permissions.listFieldsPolicy(access).readable("name"))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"没有标题字段的查询权限");
            if (criteria.getKeyword().length()>100)
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"搜索文本过长");
            where.apply("LOCATE({0}, name) > 0",criteria.getKeyword().trim());
        }
        Page<UnModuleRecord> result=records.page(requestPage,where.orderByDesc("id"));
        PermissionPolicy listFields=permissions.listFieldsPolicy(access);
        return new Page<RecordView>(result.getCurrent(), result.getSize(), result.getTotal()).setRecords(result.getRecords().stream()
                .map(record -> view(record,permissions.teamAllows(access,record)?listFields:access.policy(),context.tenantId())).toList());
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
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"请选择要删除的业务记录");
        softDeleteRecords(token,systemId,tenantId,moduleId,new ArrayList<>(new java.util.LinkedHashSet<>(request.ids())));
    }

    private void softDeleteRecords(String token,Long systemId,Long tenantId,Long moduleId,List<Long> ids) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DELETE");
        var context = access.context();
        config.lockModule(systemId,moduleId);
        UnModule module = config.activeModule(systemId, moduleId, context.tenantId());
        List<ActionRecord> selected=new ArrayList<>();
        // 锁定本批记录并按主键顺序检查，阻止并发请求为同一记录重复建立删除审批。
        for (Long id:ids.stream().sorted().toList()) {
            records.getOne(new LambdaQueryWrapper<UnModuleRecord>()
                    .eq(UnModuleRecord::getId,id).last("FOR UPDATE"),false);
            selected.add(actionRecord(access,id));
        }
        LocalDateTime deletedTime=LocalDateTime.now();
        for (ActionRecord item:selected) {
            UnModuleRecord record=item.record();
            ModuleRecordProcessContext process=new ModuleRecordProcessContext(systemId,record.getTenantId(),
                    moduleId,record.getId(),access.accountId(),"DELETE",access.definition().menu().getId(),
                    "RECORD_DELETE",processHandlers.stream().anyMatch(handler -> handler instanceof ModuleDeleteApprovalStarter)
                            ? recordValues(record) : Map.of());
            Map<String,Object> approvalMetadata=new LinkedHashMap<>();
            approvalMetadata.put("deleteRequesterAccountId",access.accountId());
            approvalMetadata.put("deleteActorTenantId",context.tenantId());
            approvalMetadata.put("deleteActionMenuId",access.definition().menu().getId());
            if (item.share()!=null) approvalMetadata.put("deleteShareId",item.share().getId());
            boolean approvalStarted=false;
            for (ModuleRecordProcessHandler handler:processHandlers) {
                if (handler instanceof ModuleDeleteApprovalStarter starter
                        && starter.startDeleteApproval(process,approvalMetadata)) approvalStarted=true;
            }
            if (approvalStarted) {
                audit(module,record,access,item.share(),"RECORD_DELETE_REQUEST",
                        access.definition().menu().getName(),List.of(),null,null);
                publishEvent(process,"RECORD_DELETE_REQUEST",Map.of("approvalPending",true));
                continue;
            }
            record.setDeletedTime(deletedTime);
            record.setStatus(ModuleRecordStatusService.DELETED);
            record.setActualOperatorId(access.accountId()); record.setUpdateUserId(access.accountId());
            persistRecord(record);
            fileBatches.deleteForRecord(record.getModuleId(), record.getId());
            audit(module,record,access,item.share(),"RECORD_DELETE",access.definition().menu().getName(),List.of(),null,null);
            publishEvent(process,
                    "RECORD_DELETE", Map.of("deletedTime", deletedTime.toString()));
        }
    }

    /** 删除审批通过后在同一终态事务中删除记录；拒绝、取消均不会调用此入口。 */
    @Transactional(rollbackFor=Exception.class)
    public void completeApprovedDelete(ExamineCompletionContext approval) {
        if (approval==null || !"COMPLETED".equals(approval.status())
                || !Boolean.TRUE.equals(approval.metadata().get("deleteRequest"))) return;
        Long requester=metadataId(approval.metadata(),"deleteRequesterAccountId");
        Long actorTenant=metadataId(approval.metadata(),"deleteActorTenantId");
        Long menuId=metadataId(approval.metadata(),"deleteActionMenuId");
        Long shareId=metadataId(approval.metadata(),"deleteShareId");
        if (requester==null || actorTenant==null || menuId==null || approval.exampleId()==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"删除审批缺少原动作上下文");
        UnModuleRecord record=records.getOne(new LambdaQueryWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId,approval.recordId()).last("FOR UPDATE"),false);
        if (record==null) return;
        if (!Objects.equals(record.getSystemId(),approval.systemId())
                || !Objects.equals(record.getTenantId(),approval.tenantId())
                || !Objects.equals(record.getModuleId(),approval.moduleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"删除审批的业务记录归属已变化");
        LocalDateTime deletedTime=LocalDateTime.now();
        record.setDeletedTime(deletedTime);
        record.setStatus(ModuleRecordStatusService.DELETED);
        record.setActualOperatorId(requester);
        record.setUpdateUserId(requester);
        persistRecord(record);
        fileBatches.deleteForRecord(record.getModuleId(),record.getId());
        UnModuleLog log=new UnModuleLog();
        log.setSystemId(record.getSystemId()); log.setTenantId(record.getTenantId());
        log.setActorTenantId(actorTenant); log.setShareId(shareId);
        log.setModuleId(record.getModuleId()); log.setRecordId(record.getId());
        log.setOperatorAccountId(requester); log.setActionCode("RECORD_DELETE");
        log.setPermissionJson(writeValue(Map.of("menuId",menuId,"approvalExampleId",approval.exampleId(),
                "requesterAccountId",requester,"actorTenantId",actorTenant)));
        var account=accounts.queryById(requester);
        log.setDetailJson(writeValue(new AuditDetail("审批通过后删除",account==null?null:account.getDisplayName(),
                List.of(),null,null,"审批实例 " + approval.exampleId() + " 通过")));
        logs.addOrUpdate(log);
        publishEvent(new ModuleRecordProcessContext(record.getSystemId(),record.getTenantId(),
                        record.getModuleId(),record.getId(),requester,"DELETE",menuId,"RECORD_DELETE",approval.values()),
                "RECORD_DELETE",Map.of("approvalExampleId",approval.exampleId(),"deletedTime",deletedTime.toString()));
    }

    private Long metadataId(Map<String,Object> metadata,String key) {
        Object value=metadata.get(key);
        return value instanceof Number number && number.longValue()>0?number.longValue():null;
    }

    /** 发布本地同步事件；监听器异常随当前业务事务传播并触发回滚。 */
    private void publishEvent(ModuleRecordProcessContext context, String eventCode, Map<String, Object> metadata) {
        if (eventPublisher == null) return;
        eventPublisher.publish(new BusinessEventContext(eventCode, context.systemId(), context.tenantId(),
                context.moduleId(), context.recordId(), context.accountId(), context.actionMenuId(),
                context.values(), metadata));
    }

    /** 当前租户数据范围与有效共享范围求并集；共享撤销即时影响详情和历史入口。 */
    private UnModuleRecord readableRecord(ModulePermissionService.Access access,Long id,boolean includeDeleted) {
        UnModuleRecord record=records.queryById(id);
        if (record==null || !access.context().systemId().equals(record.getSystemId())
                || !access.definition().module().getId().equals(record.getModuleId())
                || (!includeDeleted && record.getDeletedTime()!=null))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"业务记录不存在");
        if (access.context().tenantId().equals(record.getTenantId())) {
            if (!permissions.teamAllows(access,record)) access.policy().requireOwner(record.getOwnerUserId());
        }
        else {
            if (record.getDeletedTime()!=null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"业务记录不存在");
            shares.requireShared(access,record);
        }
        return record;
    }

    /** 写动作同时检查本租户负责人范围或锁定有效共享授权。 */
    private record ActionRecord(UnModuleRecord record,UnModuleShare share) {}
    private ActionRecord actionRecord(ModulePermissionService.Access access,Long id) {
        UnModuleRecord record=records.queryById(id);
        if (record==null || !access.context().systemId().equals(record.getSystemId())
                || !access.definition().module().getId().equals(record.getModuleId())
                || record.getDeletedTime()!=null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"业务记录不存在");
        if (access.context().tenantId().equals(record.getTenantId())) {
            if (!permissions.teamAllows(access,record)) access.policy().requireOwner(record.getOwnerUserId());
            return new ActionRecord(record,null);
        }
        return new ActionRecord(record,shares.requireSharedForWrite(access,record));
    }

    /** 负责人必须是当前系统当前租户的有效成员，并以其部门计算数据范围。 */
    private UnSysTenantMember ownerMembership(Long systemId, Long tenantId, Long ownerAccountId) {
        var account=accounts.queryById(ownerAccountId);
        if (account==null || !"ACTIVE".equals(account.getStatus())) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"负责人账号不存在或已停用");
        UnSysMember member = members.getOne(new LambdaQueryWrapper<UnSysMember>()
                .eq(UnSysMember::getSystemId, systemId)
                .eq(UnSysMember::getAccountId, ownerAccountId)
                .eq(UnSysMember::getStatus, "ACTIVE"), false);
        if (member == null) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"负责人不是有效系统成员");
        }
        UnSysTenantMember link = tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId, systemId)
                .eq(UnSysTenantMember::getTenantId, tenantId)
                .eq(UnSysTenantMember::getMemberId, member.getId())
                .eq(UnSysTenantMember::getStatus, "ACTIVE"), false);
        if (link == null) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"负责人不是有效租户成员");
        }
        return link;
    }

    /** 读取已保存字段值并按字段编码组成详情数据。 */
    private Map<String, UnModuleRecordData> dataByCode(Long recordId) {
        Map<String, UnModuleRecordData> result = new LinkedHashMap<>();
        for (UnModuleRecordData item : data.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId, recordId))) {
            result.put(item.getFieldKey(), item);
        }
        return result;
    }

    /** 主记录的并发版本在业务 Service 中检查，负责人、部门及软删除允许显式清空旧值。 */
    private void persistRecord(UnModuleRecord record) {
        persistRecord(record,List.of());
    }

    /** 固定字段与当前字段的主表槽位共同参与乐观锁更新。 */
    private void persistRecord(UnModuleRecord record,List<UnModuleField> fields) {
        if (record.getId()==null) {
            record.setLockVersion(0L);
            records.addOrUpdate(record);
            return;
        }
        long before=record.getLockVersion();
        long next=before+1;
        UpdateWrapper<UnModuleRecord> update=new UpdateWrapper<UnModuleRecord>()
                .eq("id",record.getId()).eq("lock_version",before)
                .eq("system_id",record.getSystemId())
                .eq("tenant_id",record.getTenantId())
                .set("name",record.getName())
                .set("status",record.getStatus())
                .set("check_status",record.getCheckStatus())
                .set("owner_user_id",record.getOwnerUserId())
                .set("owner_dept_id",record.getOwnerDeptId())
                .set("actual_operator_id",record.getActualOperatorId())
                .set("update_user_id",record.getUpdateUserId())
                .set("deleted_time",record.getDeletedTime())
                .set("lock_version",next);
        for (UnModuleField field:fields) {
            if ("name".equals(field.getFieldKey())) continue;
            FieldDefinition definition=fieldRules.configured(field.getFieldKey(),config.fieldConfig(field).configuration());
            ModuleRecordStorage.apply(update,definition,ModuleRecordStorage.read(record,definition));
        }
        boolean updated=records.update(update);
        if (!updated) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"业务记录已被他人修改，请刷新后重试");
        record.setLockVersion(next);
    }

    /** 按字段键从主表槽位或扩展值读取当前业务值。 */
    private Object storedValue(UnModuleRecord record,UnModuleField field,FieldDefinition definition,
                               Map<String,UnModuleRecordData> rows) {
        if ("name".equals(field.getFieldKey())) return record.getName();
        return ModuleRecordStorage.isMain(definition)?ModuleRecordStorage.read(record,definition)
                :fieldRules.stored(rows.get(field.getFieldKey()),definition);
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
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段值无法保存", ex);
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
        SearchBO criteria = search == null ? new SearchBO() : search;
        var where=new LambdaQueryWrapper<UnModuleLog>().eq(UnModuleLog::getSystemId,systemId)
                .eq(UnModuleLog::getTenantId,record.getTenantId()).eq(UnModuleLog::getModuleId,moduleId)
                .eq(UnModuleLog::getRecordId,recordId).orderByDesc(UnModuleLog::getId);
        var result=logs.page(criteria.parse(),where);
        return new Page<OperationView>(result.getCurrent(), result.getSize(), result.getTotal()).setRecords(result.getRecords().stream().map(log->{
            AuditDetail detail;
            try { detail=log.getDetailJson()==null?new AuditDetail(log.getActionCode(),null,List.of(),null,null,null):json.readValue(log.getDetailJson(),AuditDetail.class); }
            catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"业务操作日志损坏",ex); }
            var visible=detail.changes()==null?List.<FieldChange>of():detail.changes().stream()
                    .filter(change->change.fieldId()==null || access.policy().readable(change.fieldKey())).toList();
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
                policy.readable("status") ? record.getCheckStatus() : null,
                policy.readable("ownerUserId") ? record.getOwnerUserId() : null,
                policy.readable("createUserId") ? record.getCreateUserId() : null,
                policy.readable("createTime") ? record.getCreateTime() : null,
                record.getTenantId(),!record.getTenantId().equals(viewerTenantId));
    }
}
