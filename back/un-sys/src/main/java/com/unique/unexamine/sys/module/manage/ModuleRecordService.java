package com.unique.unexamine.sys.module.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.unique.unexamine.sys.manage.SystemManageService;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.permission.PermissionPolicy;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.HashMap;
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
            ModulePermissionService permissions, ModuleFieldService fieldRules) {
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
    }

    /** 表单保存同时校验发布字段类型、主数据归属与自定义数据。 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public RecordView save(String token, Long systemId, Long tenantId, Long moduleId,
                           SaveRecordRequest request) {
        var access = permissions.require(token, systemId, tenantId, moduleId, request.id() == null ? "CREATE" : "EDIT");
        var context = access.context();
        access.policy().requireWritable(request.values().keySet());
        config.lockModule(systemId, moduleId);
        UnModule module = config.publishedModule(systemId, moduleId, context.tenantId());
        List<UnModuleField> fields = config.publishedFields(moduleId);
        Set<String> allowed = fields.stream().map(UnModuleField::getFieldCode)
                .collect(java.util.stream.Collectors.toSet());
        if (!allowed.containsAll(request.values().keySet())) {
            throw new IllegalArgumentException("提交包含未发布的字段");
        }
        UnModuleRecord record = request.id() == null ? new UnModuleRecord()
                : visibleRecord(systemId, context.tenantId(), moduleId, request.id());
        if (request.id() != null) access.policy().requireOwner(record.getOwnerUserId());
        Map<String, UnModuleRecordData> existing = request.id() == null ? Map.of()
                : dataByCode(record.getId());
        Map<String, Object> effective = new LinkedHashMap<>();
        Map<String, Map<String, String>> displays = new LinkedHashMap<>();
        for (UnModuleField field : fields) {
            String code = field.getFieldCode();
            Object previous = "name".equals(code) ? record.getName()
                    : existing.containsKey(code) ? readValue(existing.get(code).getValueJson()) : null;
            var published = config.fieldConfig(field).configuration();
            FieldDefinition definition = fieldRules.published(code, published);
            Object value = request.values().containsKey(code) ? request.values().get(code) : previous;
            if (request.id() == null && !request.values().containsKey(code)) value = definition.rules().get("defaultValue");
            // 标题入库时去除首尾空白，校验必须使用同一值，避免绕过唯一约束。
            if ("name".equals(code) && value instanceof String text) value = text.trim();
            fieldRules.validate(systemId, context.tenantId(), definition, value, previous);
            fieldRules.validateUnique(systemId, context.tenantId(), moduleId, request.id(), definition, value);
            Map<String,String> oldDisplay = existing.containsKey(code) ? display(existing.get(code)) : Map.of();
            displays.put(code, fieldRules.display(systemId, context.tenantId(), definition, value, oldDisplay, previous));
            effective.put(code, value);
        }
        String name = (String) effective.get("name");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("记录标题不能为空");
        }
        Long ownerId = request.ownerAccountId() != null ? request.ownerAccountId()
                : request.id() == null ? systemRows.queryById(systemId).getOwnerAccountId()
                : record.getOwnerUserId();
        UnSysTenantMember ownerLink = ownerMembership(systemId, context.tenantId(), ownerId);
        access.policy().requireOwner(ownerId);
        if (request.id() == null) {
            record.setSystemId(systemId);
            record.setTenantId(context.tenantId());
            record.setModuleId(module.getId());
            record.setStatus("ACTIVE");
            record.setCreateUserId(systemRows.queryById(systemId).getOwnerAccountId());
        }
        record.setName(name.trim());
        record.setOwnerUserId(ownerId);
        record.setOwnerDeptId(ownerLink.getDepartmentId());
        record.setActualOperatorId(systems.accountId(token, systemId));
        records.addOrUpdate(record);
        for (UnModuleField field : fields) {
            if ("name".equals(field.getFieldCode())) {
                continue;
            }
            UnModuleRecordData item = existing.getOrDefault(field.getFieldCode(), new UnModuleRecordData());
            item.setRecordId(record.getId());
            item.setFieldId(field.getId());
            item.setFieldCode(field.getFieldCode());
            item.setValueJson(writeValue(effective.get(field.getFieldCode())));
            item.setValueDisplayJson(writeValue(displays.get(field.getFieldCode())));
            data.addOrUpdate(item);
        }
        audit(module, record, systems.accountId(token, systemId),
                request.id() == null ? "RECORD_CREATE" : "RECORD_UPDATE");
        return view(record, access.policy());
    }

    /** 基础记录查询，不混入扩展字段或详情布局。 */
    public RecordView queryById(String token, Long systemId, Long tenantId, Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "VIEW");
        var context = access.context();
        config.publishedModule(systemId, moduleId, context.tenantId());
        var record = visibleRecord(systemId, context.tenantId(), moduleId, recordId);
        access.policy().requireOwner(record.getOwnerUserId());
        return view(record, access.policy());
    }

    /** 按发布时保存的字段顺序和布局填充记录详情。 */
    public RecordInformation information(String token, Long systemId, Long tenantId,
                                         Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "VIEW");
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
                    if (stored.containsKey(field.getFieldCode())) item.configuration().put("valueDisplay", display(stored.get(field.getFieldCode())));
                    return item;
                }).toList();
        return new RecordInformation(recordId, records.information(recordId, form, values));
    }

    /** 列表分页委托生成的基础 Service，软删除字段由 IS NULL 筛选。 */
    public RecordPage queryPageList(String token, Long systemId, Long tenantId, Long moduleId,
                                    long page, long size) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "VIEW");
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
        record.setActualOperatorId(systems.accountId(token, systemId));
        records.addOrUpdate(record);
        audit(module, record, systems.accountId(token, systemId), "RECORD_DELETE");
    }

    /** 防止跨模块、跨租户读取和操作逻辑删除记录。 */
    private UnModuleRecord visibleRecord(Long systemId, Long tenantId, Long moduleId, Long id) {
        UnModuleRecord record = records.queryById(id);
        if (!systemId.equals(record.getSystemId()) || !tenantId.equals(record.getTenantId())
                || !moduleId.equals(record.getModuleId()) || record.getDeletedTime() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "业务记录不存在");
        }
        return record;
    }

    /** 负责人必须是当前系统当前租户的有效成员，并以其部门计算数据范围。 */
    private UnSysTenantMember ownerMembership(Long systemId, Long tenantId, Long ownerAccountId) {
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

    /** 历史字典名称来自记录保存快照，不以当前字典名称覆盖。 */
    @SuppressWarnings("unchecked")
    private Map<String,String> display(UnModuleRecordData row) {
        return row.getValueDisplayJson() == null ? Map.of() : json.convertValue(readValue(row.getValueDisplayJson()), Map.class);
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
        try {
            return json.readValue(value, Object.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("业务字段值损坏", ex);
        }
    }

    /** 记录实际操作人和动作，删除时不清除历史。 */
    private void audit(UnModule module, UnModuleRecord record, Long operatorId, String action) {
        UnModuleLog log = new UnModuleLog();
        log.setSystemId(record.getSystemId());
        log.setTenantId(record.getTenantId());
        log.setModuleId(module.getId());
        log.setRecordId(record.getId());
        log.setOperatorAccountId(operatorId);
        log.setActionCode(action);
        logs.addOrUpdate(log);
    }

    /** 基础记录响应不包含字段扩展和布局。 */
    private RecordView view(UnModuleRecord record, PermissionPolicy policy) {
        return new RecordView(record.getId(), policy.readable("name") ? record.getName() : null, record.getStatus(),
                record.getOwnerUserId(), record.getCreateUserId(), record.getCreateTime());
    }
}
