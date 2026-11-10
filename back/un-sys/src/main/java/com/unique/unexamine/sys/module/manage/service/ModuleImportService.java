package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.module.base.entity.UnModuleExchangeJob;
import com.unique.unexamine.sys.module.base.entity.UnModuleExchangeRow;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleExchangeJobService;
import com.unique.unexamine.sys.module.base.service.IUnModuleExchangeRowService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.ImportResult;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.JobView;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.Mode;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.PreviewError;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.PreviewResult;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.RowSearch;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportDto.RowView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto.SaveRecordRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleExportDto.ExportFile;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.manage.service.SystemMessageTemplateService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * 模块默认导入动作的同步作业服务。
 *
 * <p>文件只在请求期间解析，业务记录逐行进入独立事务；每行结果写入交换作业表，
 * 因字段、权限或流程失败时不会影响同批已经成功的行。</p>
 */
@Service
public class ModuleImportService {
    private static final int MAX_ROWS = 500;
    private static final int MAX_FILE_BYTES = 8 * 1024 * 1024;

    private final ModuleConfigService config;
    private final SystemManageService systems;
    private final ModuleFieldService fieldRules;
    private final ModulePermissionService permissions;
    private final ModuleRecordService records;
    private final IUnModuleExchangeJobService jobs;
    private final IUnModuleExchangeRowService rows;
    private final IUnModuleRecordService recordRows;
    private final IUnModuleRecordDataService recordData;
    private final ObjectMapper json;
    private final TransactionTemplate rowTransaction;
    private final BusinessEventPublisher eventPublisher;
    private final StringRedisTemplate redis;
    private final SystemMessageTemplateService messages;

    /** 注入模块字段、授权、记录保存及逐行作业基础服务。 */
    public ModuleImportService(ModuleConfigService config, SystemManageService systems, ModuleFieldService fieldRules,
            ModulePermissionService permissions, ModuleRecordService records,
            IUnModuleExchangeJobService jobs, IUnModuleExchangeRowService rows,
            IUnModuleRecordService recordRows,IUnModuleRecordDataService recordData,
            ObjectMapper json, PlatformTransactionManager transactionManager,
            BusinessEventPublisher eventPublisher, StringRedisTemplate redis,
            SystemMessageTemplateService messages) {
        this.config = config;
        this.systems = systems;
        this.fieldRules = fieldRules;
        this.permissions = permissions;
        this.records = records;
        this.jobs = jobs;
        this.rows = rows;
        this.recordRows=recordRows; this.recordData=recordData;
        this.json = json;
        this.rowTransaction = new TransactionTemplate(transactionManager);
        this.rowTransaction.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
        this.eventPublisher = eventPublisher;
        this.redis = redis;
        this.messages = messages;
    }

    /** CSV 字段和数值预检只读，保存一次性短期凭据供用户确认同一文件。 */
    public PreviewResult previewCsv(String token, Long systemId, Long tenantId, Long moduleId,
            MultipartFile file, Mode mode) {
        validateFile(file);
        Mode actualMode = mode == null ? Mode.CREATE : mode;
        var access = permissions.require(token, systemId, tenantId, moduleId, "IMPORT");
        List<UnModuleField> fields = config.currentFields(moduleId);
        List<List<String>> csv = parse(file);
        if (csv.size() < 2) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "导入文件必须包含标题行和数据行");
        HeaderMapping header = header(csv.get(0), fields, actualMode);
        requireImportColumns(access, header);
        List<List<String>> dataRows = dataRows(csv);
        List<PreviewError> errors = new ArrayList<>();
        int failedCount = 0;
        for (int index = 0; index < dataRows.size(); index++) {
            List<String> row = dataRows.get(index);
            try {
                if (row.size()>header.keys().size())
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"数据列数超过标题列数");
                Long recordId = null;
                for (int column = 0; column < header.keys().size(); column++) {
                    String key = header.keys().get(column);
                    String raw = column < row.size() ? row.get(column) : "";
                    if ("id".equals(key)) {
                        if (!raw.isBlank()) recordId = Long.valueOf(raw.trim());
                    } else if (!raw.isBlank()) {
                        UnModuleField field = header.fields().get(key);
                        FieldDefinition definition = fieldRules.configured(field.getFieldKey(),
                                config.fieldConfig(field).configuration());
                        fieldRules.parseImportValue(definition, raw);
                    }
                }
                if (actualMode == Mode.UPDATE_BY_ID && recordId == null
                        || actualMode == Mode.CREATE && recordId != null)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "导入模式与 id 列不一致");
            } catch (RuntimeException ex) {
                failedCount++;
                if (errors.size() < 100) errors.add(new PreviewError(index + 2, message(ex)));
            }
        }
        String previewId = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(previewKey(previewId), fingerprint(file, access, moduleId, actualMode, fields),
                Duration.ofMinutes(15));
        return new PreviewResult(previewId, actualMode, dataRows.size(), failedCount,
                header.mapping(), List.copyOf(errors));
    }

    /**
     * 读取带标题行的 UTF-8 CSV，并使用 IMPORT 菜单权限执行逐行新增或更新。
     * 具体字段值仍走统一类型解析、字段权限、关联校验、Flow/examine 编排和操作日志。
     */
    public ImportResult confirmCsv(String token, Long systemId, Long tenantId, Long moduleId,
            MultipartFile file, Mode mode, String previewId) {
        validateFile(file);
        if (previewId == null || !previewId.matches("[0-9a-f]{32}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "导入预检凭据无效");
        Mode actualMode = mode == null ? Mode.CREATE : mode;
        // 先取得一次动作权限，后续每行复用同一权限快照，避免批处理中途权限变化导致行间结果不一致。
        var access = permissions.require(token, systemId, tenantId, moduleId, "IMPORT");
        List<UnModuleField> fields = config.currentFields(moduleId);
        String expected = redis.opsForValue().get(previewKey(previewId));
        if (expected == null || !expected.equals(fingerprint(file, access, moduleId, actualMode, fields)))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "导入预检已失效或文件、配置已变化");
        List<List<String>> csv = parse(file);
        if (csv.size() < 2) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"导入文件必须包含标题行和数据行");
        HeaderMapping header = header(csv.get(0), fields, actualMode);
        requireImportColumns(access, header);
        List<List<String>> dataRows = dataRows(csv);
        if (!Boolean.TRUE.equals(redis.delete(previewKey(previewId))))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "导入预检已被确认");

        UnModuleExchangeJob job = new UnModuleExchangeJob();
        job.setSystemId(systemId);
        job.setTenantId(access.context().tenantId());
        job.setModuleId(moduleId);
        job.setOperatorAccountId(access.accountId());
        job.setJobType("IMPORT");
        job.setImportMode(actualMode.name());
        job.setStatus("RUNNING");
        job.setConfigJson(write(Map.of("headers", header.keys(), "fieldMapping", header.mapping(), "mode", actualMode.name())));
        job.setTotalCount(dataRows.size());
        job.setSuccessCount(0);
        job.setFailureCount(0);
        job.setCreateTime(LocalDateTime.now());
        job.setStartTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobs.addOrUpdate(job);

        int success = 0;
        int failure = 0;
        for (int index = 0; index < dataRows.size(); index++) {
            int rowNo = index + 2;
            RowOutcome outcome = processRow(token, access, moduleId, actualMode, header,
                    job.getId(),rowNo,dataRows.get(index));
            if (!outcome.success()) {
                UnModuleExchangeRow result = new UnModuleExchangeRow();
                result.setJobId(job.getId()); result.setRowNo(rowNo);
                result.setRecordId(outcome.recordId()); result.setOperationCode(outcome.operation());
                result.setStatus("FAILED"); result.setErrorCode("IMPORT_ROW_FAILED");
                result.setDetailJson(write(Map.of("message", outcome.message(), "raw", dataRows.get(index))));
                result.setCreateTime(LocalDateTime.now()); rows.addOrUpdate(result);
            }
            if (outcome.success()) success++; else failure++;
            job.setSuccessCount(success);
            job.setFailureCount(failure);
            job.setUpdateTime(LocalDateTime.now());
            jobs.updateById(job);
        }
        job.setStatus(failure == 0 ? "SUCCESS" : success == 0 ? "FAILED" : "PARTIAL");
        job.setFinishTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        jobs.updateById(job);
        eventPublisher.publish(new BusinessEventContext("IMPORT_COMPLETED", systemId, access.context().tenantId(),
                moduleId, null, access.accountId(), access.definition().menu().getId(), Map.of(),
                Map.of("jobId", job.getId(), "status", job.getStatus(), "totalCount", dataRows.size(),
                        "successCount", success, "failureCount", failure, "mode", actualMode.name())));
        // 行事务和作业结果已落库；通知配置异常不能把实际完成的导入报告为失败。
        try {
            messages.exchangeCompleted(systemId, access.context().tenantId(), access.accountId(),
                    moduleId, job.getId(), "IMPORT", job.getStatus(), dataRows.size(), success, failure);
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("导入结果通知失败，作业编号 {}", job.getId(), ex);
        }
        return new ImportResult(job.getId(), job.getStatus(), dataRows.size(), success, failure);
    }

    /** 导入作业结束后仍可按原系统租户和操作人读取结果。 */
    public JobView job(String token, Long systemId, Long tenantId, Long moduleId, Long jobId) {
        UnModuleExchangeJob row = ownJob(token, systemId, tenantId, moduleId, jobId);
        return new JobView(row.getId(), row.getModuleId(), row.getImportMode(), row.getStatus(),
                row.getTotalCount(), row.getSuccessCount(), row.getFailureCount(),
                row.getCreateTime(), row.getFinishTime());
    }

    /** 租户管理员可看本租户导入，普通成员只看本人作业。 */
    public Page<JobView> listJobs(String token,Long systemId,Long tenantId,Long moduleId,SearchBO search) {
        SystemContext context=systems.context(token,systemId,tenantId);
        var where=new LambdaQueryWrapper<UnModuleExchangeJob>()
                .eq(UnModuleExchangeJob::getSystemId,systemId)
                .eq(UnModuleExchangeJob::getTenantId,context.tenantId())
                .eq(UnModuleExchangeJob::getModuleId,moduleId)
                .eq(UnModuleExchangeJob::getJobType,"IMPORT")
                .orderByDesc(UnModuleExchangeJob::getId);
        if (!context.owner() && !context.tenantAdmin())
            where.eq(UnModuleExchangeJob::getOperatorAccountId,context.accountId());
        var source=jobs.page(search==null?new SearchBO().parse():search.parse(),where);
        return new Page<JobView>(source.getCurrent(),source.getSize(),source.getTotal())
                .setRecords(source.getRecords().stream().map(row->new JobView(row.getId(),row.getModuleId(),
                        row.getImportMode(),row.getStatus(),row.getTotalCount(),row.getSuccessCount(),
                        row.getFailureCount(),row.getCreateTime(),row.getFinishTime())).toList());
    }

    /** 按状态分页查看导入行，错误说明来自当次持久化的作业结果。 */
    public Page<RowView> rows(String token, Long systemId, Long tenantId, Long moduleId,
            Long jobId, RowSearch search) {
        ownJob(token, systemId, tenantId, moduleId, jobId);
        RowSearch criteria = search == null ? new RowSearch() : search;
        var where = new LambdaQueryWrapper<UnModuleExchangeRow>()
                .eq(UnModuleExchangeRow::getJobId, jobId)
                .orderByAsc(UnModuleExchangeRow::getRowNo);
        if (criteria.getStatus() != null && !criteria.getStatus().isBlank()) {
            String status = criteria.getStatus().trim().toUpperCase(java.util.Locale.ROOT);
            if (!Set.of("SUCCESS", "FAILED").contains(status))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "导入行状态无效");
            where.eq(UnModuleExchangeRow::getStatus, status);
        }
        Page<UnModuleExchangeRow> page = rows.page(criteria.parse(), where);
        return new Page<RowView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(item -> new RowView(item.getRowNo(),
                        item.getRecordId(), item.getOperationCode(), item.getStatus(), item.getErrorCode(),
                        "SUCCESS".equals(item.getStatus())?null:item.getDetailJson(), item.getCreateTime())).toList());
    }

    /** 导入模板只列当前有权写入的有效字段，更新模式另含 id。 */
    public ExportFile template(String token, Long systemId, Long tenantId, Long moduleId, Mode mode) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "IMPORT");
        Long moduleTenant = access.definition().module().getTenantId();
        Set<Long> actionFields = Set.copyOf(access.definition().settings().fieldIds());
        List<String> keys = config.currentFields(moduleId).stream()
                .filter(field -> access.context().tenantId().equals(field.getTenantId())
                        || moduleTenant.equals(field.getTenantId()))
                .filter(field -> access.policy().writable(field.getFieldKey()))
                .filter(field -> !Boolean.TRUE.equals(field.getReadonlyFlag()))
                .filter(field -> actionFields.isEmpty() || actionFields.contains(field.getId()))
                .map(UnModuleField::getFieldKey).distinct().toList();
        if (keys.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "没有可导入的字段");
        StringBuilder csv = new StringBuilder("\uFEFF");
        if (mode == Mode.UPDATE_BY_ID || mode == Mode.UPSERT_BY_ID) csv.append("id,");
        for (int index = 0; index < keys.size(); index++) {
            if (index > 0) csv.append(',');
            csv.append(cell(keys.get(index)));
        }
        csv.append("\r\n");
        return new ExportFile("module-" + moduleId + "-import-template.csv",
                csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 错误文件携带原始列和本次失败原因，权限沿用导入作业详情。 */
    public ExportFile errors(String token, Long systemId, Long tenantId, Long moduleId, Long jobId) {
        UnModuleExchangeJob job = ownJob(token, systemId, tenantId, moduleId, jobId);
        JsonNode snapshot = parseJson(job.getConfigJson());
        JsonNode headers = snapshot.path("headers");
        if (!headers.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "导入作业字段快照无效");
        StringBuilder csv = new StringBuilder("\uFEFFrowNo,error");
        headers.forEach(header -> csv.append(',').append(cell(header.asText())));
        csv.append("\r\n");
        for (UnModuleExchangeRow row : rows.list(new LambdaQueryWrapper<UnModuleExchangeRow>()
                .eq(UnModuleExchangeRow::getJobId, jobId).eq(UnModuleExchangeRow::getStatus, "FAILED")
                .orderByAsc(UnModuleExchangeRow::getRowNo))) {
            JsonNode detail = parseJson(row.getDetailJson());
            JsonNode raw = detail.path("raw");
            if (!raw.isArray()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "导入失败行缺少原始值");
            csv.append(row.getRowNo()).append(',').append(cell(detail.path("message").asText()));
            for (int index = 0; index < headers.size(); index++)
                csv.append(',').append(cell(index < raw.size() ? raw.get(index).asText() : ""));
            csv.append("\r\n");
        }
        return new ExportFile("module-" + moduleId + "-import-" + jobId + "-errors.csv",
                csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private JsonNode parseJson(String value) {
        try { return json.readTree(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "导入作业结果无法读取", ex); }
    }

    private String cell(String text) {
        String safe = text == null ? "" : text;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    UnModuleExchangeJob ownJob(String token, Long systemId, Long tenantId,
            Long moduleId, Long jobId) {
        SystemContext context = systems.context(token, systemId, tenantId);
        if (jobId == null || jobId < 1) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "导入作业主键无效");
        UnModuleExchangeJob row = jobs.getById(jobId);
        if (row == null || !"IMPORT".equals(row.getJobType())
                || !context.systemId().equals(row.getSystemId())
                || !context.tenantId().equals(row.getTenantId())
                || !moduleId.equals(row.getModuleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "导入作业不存在");
        if (!context.owner() && !context.tenantAdmin()
                && !context.accountId().equals(row.getOperatorAccountId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "没有导入作业读取权限");
        return row;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "导入文件不能为空");
        if (file.getSize() > MAX_FILE_BYTES) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "导入文件不能超过8MB");
    }

    private List<List<String>> dataRows(List<List<String>> csv) {
        List<List<String>> rows = csv.subList(1, csv.size()).stream()
                .filter(row -> row.stream().anyMatch(value -> value != null && !value.isBlank())).toList();
        if (rows.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "导入文件没有有效数据行");
        if (rows.size() > MAX_ROWS) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "单次导入不能超过500行");
        return rows;
    }

    private String previewKey(String id) { return "examine:import-preview:" + id; }

    /** 文件、操作者、租户、导入模式和当前字段定义共同绑定预检。 */
    private String fingerprint(MultipartFile file, ModulePermissionService.Access access,
            Long moduleId, Mode mode, List<UnModuleField> fields) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(file.getBytes());
            String scope = access.context().systemId() + ":" + access.context().tenantId() + ":"
                    + access.accountId() + ":" + moduleId + ":" + mode.name();
            digest.update(scope.getBytes(StandardCharsets.UTF_8));
            for (UnModuleField field : fields) {
                String revision = field.getId() + ":" + field.getFieldKey() + ":" + field.getUpdateTime();
                digest.update(revision.getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "导入预检摘要无法生成", ex); }
    }

    /** 成功行的业务值、前值快照和作业行在同一事务提交；失败行在事务外另记。 */
    private RowOutcome processRow(String token, ModulePermissionService.Access access, Long moduleId,
            Mode mode, HeaderMapping header, Long jobId,int rowNo,List<String> row) {
        Map<String, Object> values = new LinkedHashMap<>();
        Long id = null;
        try {
            if (row.size()>header.keys().size())
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"数据列数超过标题列数");
            for (int column = 0; column < header.keys().size(); column++) {
                String key = header.keys().get(column);
                String raw = column < row.size() ? row.get(column) : "";
                if ("id".equals(key)) {
                    if (!raw.isBlank()) id = Long.valueOf(raw.trim());
                    continue;
                }
                UnModuleField field = header.fields().get(key);
                FieldDefinition definition = fieldRules.configured(field.getFieldKey(), config.fieldConfig(field).configuration());
                if (!raw.isBlank()) values.put(key, fieldRules.parseImportValue(definition, raw));
            }
            if (mode == Mode.UPDATE_BY_ID && id == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"更新模式必须提供 id");
            if (mode == Mode.CREATE && id != null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"新增模式不能提供 id");
            Long recordId = id;
            String operation = id == null ? "CREATE" : "UPDATE";
            RowOutcome outcome = rowTransaction.execute(status -> {
                try {
                    UnModuleRecord before=recordId==null?null:recordRows.getOne(
                            new LambdaQueryWrapper<UnModuleRecord>().eq(UnModuleRecord::getId,recordId)
                                    .last("FOR UPDATE"),false);
                    List<UnModuleRecordData> previous=before==null?List.of():recordData.list(
                            new LambdaQueryWrapper<UnModuleRecordData>()
                                    .eq(UnModuleRecordData::getRecordId,recordId));
                    var previousRecordSnapshot=before==null?null:json.valueToTree(before);
                    var previousDataSnapshot=json.valueToTree(previous);
                    var view = records.saveByAuthorizedAction(token, access, moduleId,
                            new SaveRecordRequest(recordId, null, values),
                            "RECORD_IMPORT_" + operation);
                    var saved=recordRows.getById(view.id());
                    if (saved==null || saved.getLockVersion()==null)
                        throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"导入后的记录版本不可读取");
                    Map<String,Object> snapshot=new LinkedHashMap<>();
                    snapshot.put("beforeRecord",previousRecordSnapshot); snapshot.put("beforeData",previousDataSnapshot);
                    snapshot.put("fieldRevision",config.currentFields(moduleId).stream()
                            .map(field->field.getId()+":"+field.getFieldKey()+":"+field.getUpdateTime()).toList());
                    var result=new UnModuleExchangeRow(); result.setJobId(jobId); result.setRowNo(rowNo);
                    result.setRecordId(view.id()); result.setOperationCode(operation); result.setStatus("SUCCESS");
                    result.setDetailJson(write(snapshot)); result.setRecordVersion(saved.getLockVersion());
                    result.setCreateTime(LocalDateTime.now()); rows.addOrUpdate(result);
                    return new RowOutcome(true, view.id(), operation, null);
                } catch (RuntimeException ex) {
                    status.setRollbackOnly();
                    return new RowOutcome(false, null, operation, message(ex));
                }
            });
            return outcome == null ? new RowOutcome(false, null, operation, "导入行未返回结果") : outcome;
        } catch (RuntimeException ex) {
            return new RowOutcome(false, null, id == null ? "CREATE" : "UPDATE", message(ex));
        }
    }

    /** 标题本身也受动作字段、租户字段和可编辑许可约束，空值列不能绕过预检。 */
    private void requireImportColumns(ModulePermissionService.Access access,HeaderMapping header) {
        Set<Long> actionFields=Set.copyOf(access.definition().settings().fieldIds());
        Long moduleTenant=access.definition().module().getTenantId();
        for (UnModuleField field:header.fields().values()) {
            if (!(access.context().tenantId().equals(field.getTenantId()) || moduleTenant.equals(field.getTenantId()))
                    || !access.policy().writable(field.getFieldKey())
                    || !actionFields.isEmpty() && !actionFields.contains(field.getId()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"导入列超出当前字段或动作权限");
            fieldRules.requireEditable(field);
        }
    }

    /** 标题支持 field_key 或当前字段名称；id 只用于按主键更新。 */
    private HeaderMapping header(List<String> source, List<UnModuleField> fields, Mode mode) {
        if (source == null || source.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"CSV 标题行不能为空");
        Map<String, UnModuleField> byKey = new HashMap<>();
        Map<String, UnModuleField> byName = new HashMap<>();
        for (UnModuleField field : fields) {
            byKey.put(field.getFieldKey(), field);
            byName.put(field.getFieldName(), field);
        }
        List<String> keys = new ArrayList<>();
        Map<String, UnModuleField> selected = new LinkedHashMap<>();
        Map<String, String> mapping = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        boolean hasId = false;
        for (String raw : source) {
            String title = raw == null ? "" : raw.trim();
            if (title.isBlank()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"CSV 标题不能为空");
            if ("id".equals(title)) {
                if (!seen.add("id")) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"CSV 标题不能重复");
                hasId = true;
                keys.add("id");
                mapping.put(title, "id");
                continue;
            }
            UnModuleField field = byKey.get(title);
            if (field == null) field = byName.get(title);
            if (field == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"CSV 标题不是当前模块字段：" + title);
            if (!seen.add(field.getFieldKey())) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"CSV 字段不能重复：" + title);
            keys.add(field.getFieldKey());
            selected.put(field.getFieldKey(), field);
            mapping.put(title, field.getFieldKey());
        }
        if (mode == Mode.UPDATE_BY_ID && !hasId) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"更新模式必须包含 id 标题");
        if (mode == Mode.UPSERT_BY_ID && !hasId) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"按主键匹配模式必须包含 id 标题");
        if (selected.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"导入至少需要一个业务字段列");
        return new HeaderMapping(List.copyOf(keys), Map.copyOf(selected), Map.copyOf(mapping));
    }

    /** 解析 RFC4180 常见 CSV：支持引号、逗号、换行和双引号转义。 */
    private List<List<String>> parse(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            String source = new String(bytes, StandardCharsets.UTF_8);
            if (source.startsWith("\uFEFF")) source = source.substring(1);
            List<List<String>> result = new ArrayList<>();
            List<String> line = new ArrayList<>();
            StringBuilder cell = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < source.length(); i++) {
                char current = source.charAt(i);
                if (quoted) {
                    if (current == '"') {
                        if (i + 1 < source.length() && source.charAt(i + 1) == '"') { cell.append('"'); i++; }
                        else quoted = false;
                    } else cell.append(current);
                    continue;
                }
                if (current == '"' && cell.isEmpty()) { quoted = true; continue; }
                if (current == ',') { line.add(cell.toString()); cell.setLength(0); continue; }
                if (current == '\n' || current == '\r') {
                    if (current == '\r' && i + 1 < source.length() && source.charAt(i + 1) == '\n') i++;
                    line.add(cell.toString()); cell.setLength(0); result.add(line); line = new ArrayList<>(); continue;
                }
                cell.append(current);
            }
            if (quoted) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"CSV 引号未闭合");
            if (!line.isEmpty() || cell.length() > 0) { line.add(cell.toString()); result.add(line); }
            return result;
        } catch (java.io.IOException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"读取导入文件失败", ex);
        }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"导入作业详情无法编码", ex); }
    }

    private String message(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && (current.getMessage() == null || current.getMessage().isBlank())) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank() ? "导入行处理失败" : current.getMessage();
    }

    private record HeaderMapping(List<String> keys, Map<String, UnModuleField> fields, Map<String, String> mapping) {}
    private record RowOutcome(boolean success, Long recordId, String operation, String message) {}
}
