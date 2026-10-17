package com.unique.unexamine.runtimedata.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.moduleconfig.base.entity.ModuleField;
import com.unique.unexamine.moduleconfig.base.service.ModuleFieldBaseService;
import com.unique.unexamine.moduleconfig.manage.ModuleManageService;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.entity.RecordData;
import com.unique.unexamine.runtimedata.base.entity.RecordIndex;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.base.service.RecordDataBaseService;
import com.unique.unexamine.runtimedata.base.service.RecordIndexBaseService;
import com.unique.unexamine.system.manage.SystemPermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 已发布模块的基础记录写入、查询和详情；普通成员默认只能访问自己负责的记录。 */
@Service
public class RecordManageService {
    private final SystemPermissionService access;
    private final ModuleManageService modules;
    private final BusinessRecordBaseService records;
    private final RecordDataBaseService data;
    private final RecordIndexBaseService indexes;
    private final ModuleFieldBaseService fields;
    private final ObjectMapper json;

    public RecordManageService(SystemPermissionService access, ModuleManageService modules,
                               BusinessRecordBaseService records, RecordDataBaseService data,
                               RecordIndexBaseService indexes, ModuleFieldBaseService fields, ObjectMapper json) {
        this.access = access;
        this.modules = modules;
        this.records = records;
        this.data = data;
        this.indexes = indexes;
        this.fields = fields;
        this.json = json;
    }

    /** 主记录、字段值与可搜索索引同事务落库；失败时不留下半条记录。 */
    @Transactional
    public RecordView create(Long accountId, Long systemId, Long moduleId, Map<String, JsonNode> values) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "CREATE");
        ModuleManageService.PublishedModule published = modules.runtime(accountId, systemId, moduleId);
        Validated validated = validate(moduleId, published.snapshot().fields(), values);
        BusinessRecord record = new BusinessRecord();
        record.setSystemId(systemId);
        record.setTenantId(context.tenant().getId());
        record.setModuleId(moduleId);
        record.setModuleVersionId(published.versionId());
        record.setOwnerMemberId(context.member().getId());
        record.setRecordNo("R" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        record.setTitle(title(validated.values(), record.getRecordNo()));
        record.setRecordStatus("ACTIVE");
        record.setCreatedByAccountId(accountId);
        record.setUpdatedByAccountId(accountId);
        records.insert(record);
        saveValues(record.getId(), validated);
        return view(records.selectById(record.getId()));
    }

    /** 编辑时检查记录所属范围和乐观锁版本，避免覆盖他人刚保存的修改。 */
    @Transactional
    public RecordView update(Long accountId, Long systemId, Long moduleId, Long recordId, int expectedVersion,
                             Map<String, JsonNode> values) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "UPDATE");
        BusinessRecord record = requireRecord(context, moduleId, recordId);
        if (!List.of("ACTIVE", "REJECTED").contains(record.getRecordStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "审批中的记录不能编辑");
        }
        if (!Integer.valueOf(expectedVersion).equals(record.getVersion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "记录已被其他操作更新，请刷新后重试");
        }
        ModuleManageService.PublishedModule published = modules.runtime(accountId, systemId, moduleId);
        Validated validated = validate(moduleId, published.snapshot().fields(), values);
        record.setTitle(title(validated.values(), record.getRecordNo()));
        record.setUpdatedByAccountId(accountId);
        if (records.updateById(record) != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "记录已被其他操作更新，请刷新后重试");
        }
        saveValues(recordId, validated);
        return view(records.selectById(recordId));
    }

    /** 支持标题模糊查找或一个可搜索字段的精确筛选，并限制页大小。 */
    public PageView list(Long accountId, Long systemId, Long moduleId, int page, int size,
                         String search, String filterKey, String filterValue) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "VIEW");
        if (page < 1 || size < 1 || size > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码或页大小无效");
        LambdaQueryWrapper<BusinessRecord> query = new LambdaQueryWrapper<BusinessRecord>()
                .eq(BusinessRecord::getSystemId, systemId).eq(BusinessRecord::getTenantId, context.tenant().getId())
                .eq(BusinessRecord::getModuleId, moduleId).ne(BusinessRecord::getRecordStatus, "DELETED");
        if (!context.owner()) query.eq(BusinessRecord::getOwnerMemberId, context.member().getId());
        if (search != null && !search.isBlank()) query.like(BusinessRecord::getTitle, search.trim());
        if (filterKey != null && !filterKey.isBlank()) {
            if (filterValue == null || filterValue.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "筛选值不能为空");
            ModuleManageService.FieldSnapshot definition = modules.runtime(accountId, systemId, moduleId).snapshot().fields()
                    .stream().filter(item -> item.key().equals(filterKey) && item.searchable()).findFirst().orElse(null);
            if (definition == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段不可筛选");
            ModuleField field = definition.id() == null ? null : fields.selectById(definition.id());
            if (field == null) field = fields.selectOne(new LambdaQueryWrapper<ModuleField>()
                    .eq(ModuleField::getModuleId, moduleId).eq(ModuleField::getFieldKey, filterKey));
            if (field == null || !moduleId.equals(field.getModuleId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段不可筛选");
            LambdaQueryWrapper<RecordIndex> filter = new LambdaQueryWrapper<RecordIndex>().eq(RecordIndex::getFieldId, field.getId());
            switch (definition.type()) {
                case "TEXT", "SELECT" -> filter.eq(RecordIndex::getTextValue, filterValue);
                case "NUMBER" -> {
                    try { filter.eq(RecordIndex::getNumberValue, new BigDecimal(filterValue)); }
                    catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数字筛选值无效"); }
                }
                case "DATE" -> {
                    try { filter.eq(RecordIndex::getDateValue, LocalDate.parse(filterValue).atStartOfDay()); }
                    catch (DateTimeParseException exception) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "日期筛选值无效"); }
                }
                case "BOOLEAN" -> {
                    if (!List.of("true", "false").contains(filterValue)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "开关筛选值无效");
                    filter.eq(RecordIndex::getBooleanValue, Boolean.parseBoolean(filterValue));
                }
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段不可筛选");
            }
            List<Long> matched = indexes.selectList(filter).stream().map(RecordIndex::getRecordId).toList();
            if (matched.isEmpty()) return new PageView(List.of(), 0, page, size);
            query.in(BusinessRecord::getId, matched);
        } else if (filterValue != null && !filterValue.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "筛选字段不能为空");
        }
        query.orderByDesc(BusinessRecord::getCreateTime, BusinessRecord::getId);
        Page<BusinessRecord> result = (Page<BusinessRecord>) records.selectPage(new Page<>(page, size), query);
        return new PageView(result.getRecords().stream().map(this::view).toList(), result.getTotal(), page, size);
    }

    /** 详情仍校验系统、租户、模块和负责人范围，不能仅凭记录主键访问。 */
    public RecordView detail(Long accountId, Long systemId, Long moduleId, Long recordId) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "VIEW");
        return view(requireRecord(context, moduleId, recordId));
    }

    private BusinessRecord requireRecord(SystemPermissionService.Context context, Long moduleId, Long recordId) {
        BusinessRecord record = records.selectById(recordId);
        if (record == null || !context.system().getId().equals(record.getSystemId())
                || !context.tenant().getId().equals(record.getTenantId()) || !moduleId.equals(record.getModuleId())
                || "DELETED".equals(record.getRecordStatus())
                || (!context.owner() && !context.member().getId().equals(record.getOwnerMemberId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在或无访问权限");
        }
        return record;
    }

    /** 发起审批只能针对本人负责、尚未处于审批中的业务记录。 */
    public BusinessRecord ownedForSubmission(SystemPermissionService.Context context, Long moduleId, Long recordId) {
        BusinessRecord record = requireRecord(context, moduleId, recordId);
        if (!context.member().getId().equals(record.getOwnerMemberId())
                || !List.of("ACTIVE", "REJECTED").contains(record.getRecordStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前记录不能发起审批");
        }
        return record;
    }

    /** 已授权待办可读取对应业务数据，调用者须先验证任务处理人和当前菜单查看权限。 */
    public RecordView approvalDetail(SystemPermissionService.Context context, Long moduleId, Long recordId) {
        BusinessRecord record = records.selectById(recordId);
        if (record == null || !context.system().getId().equals(record.getSystemId())
                || !context.tenant().getId().equals(record.getTenantId()) || !moduleId.equals(record.getModuleId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        }
        return view(record);
    }

    /** 导入预检查复用运行态发布字段校验，不产生业务记录或索引写入。 */
    public void validateImportedValues(Long accountId, Long systemId, Long moduleId,
                                       Map<String, JsonNode> values) {
        ModuleManageService.PublishedModule published = modules.runtime(accountId, systemId, moduleId);
        validate(moduleId, published.snapshot().fields(), values);
    }

    /** 根据发布字段验证完整表单；不接受草稿字段、未知字段或错误类型。 */
    private Validated validate(Long moduleId, List<ModuleManageService.FieldSnapshot> definitions,
                               Map<String, JsonNode> submitted) {
        if (submitted == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少字段值");
        Set<String> allowed = new HashSet<>();
        Map<FieldValue, JsonNode> result = new LinkedHashMap<>();
        for (ModuleManageService.FieldSnapshot definition : definitions) {
            allowed.add(definition.key());
            JsonNode value = submitted.get(definition.key());
            boolean empty = value == null || value.isNull() || (value.isTextual() && value.asText().isBlank());
            if (empty) {
                if (definition.required()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, definition.label() + "不能为空");
                continue;
            }
            checkType(definition, value);
            ModuleField field = definition.id() == null ? null : fields.selectById(definition.id());
            if (field == null) field = fields.selectOne(new LambdaQueryWrapper<ModuleField>()
                    .eq(ModuleField::getModuleId, moduleId).eq(ModuleField::getFieldKey, definition.key()));
            if (field == null || !moduleId.equals(field.getModuleId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "发布字段已失效，请重新发布模块");
            }
            result.put(new FieldValue(field, definition), value);
        }
        if (!allowed.containsAll(submitted.keySet())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "提交包含未发布字段");
        return new Validated(result);
    }

    private void checkType(ModuleManageService.FieldSnapshot field, JsonNode value) {
        switch (field.type()) {
            case "TEXT", "SELECT" -> {
                if (!value.isTextual() || value.asText().length() > 2000) throw invalid(field);
                if ("SELECT".equals(field.type())) {
                    try {
                        JsonNode choices = json.readTree(field.optionsJson());
                        if (!choices.isArray()) throw invalid(field);
                        boolean found = false;
                        for (JsonNode choice : choices) {
                            if (choice.isTextual() && choice.asText().equals(value.asText())) found = true;
                        }
                        if (!found) throw invalid(field);
                    } catch (JsonProcessingException exception) { throw invalid(field); }
                }
                if (field.searchable() && value.asText().length() > 255) throw invalid(field);
            }
            case "NUMBER" -> {
                if (!value.isNumber() || value.decimalValue().precision() > 30 || value.decimalValue().scale() > 10) throw invalid(field);
            }
            case "DATE" -> {
                if (!value.isTextual()) throw invalid(field);
                try { LocalDate.parse(value.asText()); }
                catch (DateTimeParseException exception) { throw invalid(field); }
            }
            case "BOOLEAN" -> { if (!value.isBoolean()) throw invalid(field); }
            default -> throw invalid(field);
        }
    }

    private ResponseStatusException invalid(ModuleManageService.FieldSnapshot field) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, field.label() + "的字段值无效");
    }

    private String title(Map<FieldValue, JsonNode> values, String fallback) {
        return values.entrySet().stream().filter(entry -> entry.getValue().isTextual())
                .map(entry -> entry.getValue().asText()).filter(value -> !value.isBlank())
                .findFirst().map(value -> value.substring(0, Math.min(value.length(), 255))).orElse(fallback);
    }

    /** 全量替换已发布字段值，同时维护筛选索引。 */
    private void saveValues(Long recordId, Validated validated) {
        Map<Long, RecordData> oldData = new HashMap<>();
        for (RecordData item : data.selectList(new LambdaQueryWrapper<RecordData>().eq(RecordData::getRecordId, recordId))) {
            oldData.put(item.getFieldId(), item);
        }
        Map<Long, RecordIndex> oldIndexes = new HashMap<>();
        for (RecordIndex item : indexes.selectList(new LambdaQueryWrapper<RecordIndex>().eq(RecordIndex::getRecordId, recordId))) {
            oldIndexes.put(item.getFieldId(), item);
        }
        for (Map.Entry<FieldValue, JsonNode> entry : validated.values().entrySet()) {
            ModuleField field = entry.getKey().field();
            ModuleManageService.FieldSnapshot definition = entry.getKey().definition();
            JsonNode value = entry.getValue();
            RecordData item = oldData.remove(field.getId());
            if (item == null) {
                item = new RecordData();
                item.setRecordId(recordId);
                item.setFieldId(field.getId());
            }
            try { item.setValueJson(json.writeValueAsString(value)); }
            catch (JsonProcessingException exception) { throw new IllegalStateException("字段值无法序列化", exception); }
            if (item.getId() == null) data.insert(item);
            else data.updateById(item);
            RecordIndex index = oldIndexes.remove(field.getId());
            if (definition.searchable()) {
                if (index == null) {
                    index = new RecordIndex();
                    index.setRecordId(recordId);
                    index.setFieldId(field.getId());
                }
                index.setTextValue(null);
                index.setNumberValue(null);
                index.setDateValue(null);
                index.setBooleanValue(null);
                switch (definition.type()) {
                    case "TEXT", "SELECT" -> index.setTextValue(value.asText());
                    case "NUMBER" -> index.setNumberValue(value.decimalValue());
                    case "DATE" -> index.setDateValue(LocalDate.parse(value.asText()).atStartOfDay());
                    case "BOOLEAN" -> index.setBooleanValue(value.asBoolean());
                    default -> throw new IllegalStateException("不支持的索引字段类型");
                }
                if (index.getId() == null) indexes.insert(index);
                else indexes.updateById(index);
            } else if (index != null) indexes.deleteById(index.getId());
        }
        for (RecordData item : oldData.values()) data.deleteById(item.getId());
        for (RecordIndex item : oldIndexes.values()) indexes.deleteById(item.getId());
    }

    private RecordView view(BusinessRecord record) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (RecordData item : data.selectList(new LambdaQueryWrapper<RecordData>()
                .eq(RecordData::getRecordId, record.getId()).orderByAsc(RecordData::getFieldId))) {
            ModuleField field = fields.selectById(item.getFieldId());
            if (field == null) continue;
            try { values.put(field.getFieldKey(), json.readTree(item.getValueJson())); }
            catch (JsonProcessingException exception) { throw new IllegalStateException("字段值无法读取", exception); }
        }
        return new RecordView(record.getId(), record.getRecordNo(), record.getTitle(), record.getRecordStatus(), record.getOwnerMemberId(),
                record.getVersion(), record.getCreateTime(), record.getUpdateTime(), values);
    }

    /** 字段主键用于存储，发布定义用于类型校验和索引，草稿修改不能改变运行态语义。 */
    private record FieldValue(ModuleField field, ModuleManageService.FieldSnapshot definition) {}
    private record Validated(Map<FieldValue, JsonNode> values) {}

    /** 页面可见的主记录及按字段编码组织的值。 */
    public record RecordView(Long id, String recordNo, String title, String recordStatus, Long ownerMemberId, int version,
                             LocalDateTime createTime, LocalDateTime updateTime, Map<String, JsonNode> values) {}
    /** 分页记录列表，页码从 1 开始。 */
    public record PageView(List<RecordView> items, long total, int page, int size) {}
}
