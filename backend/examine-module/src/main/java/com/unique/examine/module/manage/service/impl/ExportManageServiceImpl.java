package com.unique.examine.module.manage.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.common.response.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.EffectivePermissionVO;
import com.unique.examine.core.permission.FieldPermissionVO;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.module.base.entity.ExportJob;
import com.unique.examine.module.base.entity.ExportJobLog;
import com.unique.examine.module.base.entity.ExportTemplate;
import com.unique.examine.module.base.entity.ExportTemplateField;
import com.unique.examine.module.base.entity.Field;
import com.unique.examine.module.base.entity.Model;
import com.unique.examine.module.base.entity.PublishVersion;
import com.unique.examine.module.base.entity.Record;
import com.unique.examine.module.base.entity.RecordValue;
import com.unique.examine.module.base.service.IExportJobLogService;
import com.unique.examine.module.base.service.IExportJobService;
import com.unique.examine.module.base.service.IExportTemplateFieldService;
import com.unique.examine.module.base.service.IExportTemplateService;
import com.unique.examine.module.base.service.IFieldService;
import com.unique.examine.module.base.service.IModelService;
import com.unique.examine.module.base.service.IPublishVersionService;
import com.unique.examine.module.base.service.IRecordService;
import com.unique.examine.module.base.service.IRecordValueService;
import com.unique.examine.module.manage.bo.ExportJobActionBO;
import com.unique.examine.module.manage.bo.ExportJobCreateBO;
import com.unique.examine.module.manage.bo.ExportJobQueryBO;
import com.unique.examine.module.manage.bo.ExportTemplateFieldBO;
import com.unique.examine.module.manage.bo.ExportTemplateSaveBO;
import com.unique.examine.module.manage.enums.ExportErrorCode;
import com.unique.examine.module.manage.enums.ModuleConfigErrorCode;
import com.unique.examine.module.manage.service.ExportManageService;
import com.unique.examine.module.manage.vo.ExportFailureReasonVO;
import com.unique.examine.module.manage.vo.ExportJobDetailVO;
import com.unique.examine.module.manage.vo.ExportJobListItemVO;
import com.unique.examine.module.manage.vo.ExportTemplateFieldVO;
import com.unique.examine.module.manage.vo.ExportTemplateVO;
import com.unique.examine.upload.manage.bo.FileBindDTO;
import com.unique.examine.upload.manage.service.UploadFileService;
import com.unique.examine.upload.manage.vo.FileInfoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 导出模板和任务管理服务实现。
 */
@Service
@RequiredArgsConstructor
public class ExportManageServiceImpl implements ExportManageService {

    private static final String ACTIVE_DELETE_MARKER = "0";

    private static final String ENABLED = "ENABLED";

    private static final String PUBLISHED = "PUBLISHED";

    private static final String QUEUED = "QUEUED";

    private static final String PROCESSING = "PROCESSING";

    private static final String SUCCESS = "SUCCESS";

    private static final String FAILED = "FAILED";

    private static final String CANCELED = "CANCELED";

    private static final byte YES = 1;

    private static final byte NO = 0;

    private final IExportTemplateService exportTemplateService;

    private final IExportTemplateFieldService exportTemplateFieldService;

    private final IExportJobService exportJobService;

    private final IExportJobLogService exportJobLogService;

    private final IModelService modelService;

    private final IFieldService fieldService;

    private final IPublishVersionService publishVersionService;

    private final IRecordService recordService;

    private final IRecordValueService recordValueService;

    private final PermissionService permissionService;

    private final UploadFileService uploadFileService;

    private final ObjectMapper objectMapper;

    /**
     * 查询导出模板列表。
     */
    @Override
    public List<ExportTemplateVO> listTemplates(Long systemId, Long moduleId) {
        permissionService.requireOperation("EXPORT_TEMPLATE_VIEW");
        return exportTemplateService.lambdaQuery()
                .eq(ExportTemplate::getSystemId, systemId)
                .eq(Objects.nonNull(moduleId), ExportTemplate::getModuleId, moduleId)
                .eq(ExportTemplate::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .sorted(Comparator.comparing(ExportTemplate::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::templateVO)
                .toList();
    }

    /**
     * 创建导出模板。
     */
    @Override
    @Transactional
    public ExportTemplateVO createTemplate(Long systemId, ExportTemplateSaveBO saveBO) {
        permissionService.requireOperation("EXPORT_TEMPLATE_CREATE");
        ensureModulePublished(systemId, saveBO.getModuleId());
        ensureTemplateCodeUnique(systemId, saveBO.getModuleId(), saveBO.getTemplateCode(), null);
        LocalDateTime now = LocalDateTime.now();
        ExportTemplate template = new ExportTemplate()
                .setSystemId(systemId)
                .setTenantId(currentTenantId())
                .setModuleId(saveBO.getModuleId())
                .setTemplateCode(saveBO.getTemplateCode())
                .setTemplateName(saveBO.getTemplateName())
                .setTemplateStatus(ENABLED)
                .setFileNamePattern(saveBO.getFileNamePattern())
                .setExportFormat(StringUtils.hasText(saveBO.getExportFormat()) ? saveBO.getExportFormat() : "CSV")
                .setIncludeHistoryFlag(Objects.isNull(saveBO.getIncludeHistoryFlag()) ? NO : saveBO.getIncludeHistoryFlag())
                .setConfigJson(saveBO.getConfigJson())
                .setVersion(1)
                .setDeleteMarker(ACTIVE_DELETE_MARKER)
                .setCreatedBy(currentMemberId())
                .setUpdatedBy(currentMemberId())
                .setCreatedAt(now)
                .setUpdatedAt(now);
        exportTemplateService.save(template);
        saveTemplateFields(systemId, template, saveBO.getFields());
        return templateVO(template);
    }

    /**
     * 更新导出模板。
     */
    @Override
    @Transactional
    public ExportTemplateVO updateTemplate(Long systemId, Long templateId, ExportTemplateSaveBO saveBO) {
        permissionService.requireOperation("EXPORT_TEMPLATE_EDIT");
        ExportTemplate template = requireTemplate(systemId, templateId);
        ensureModulePublished(systemId, saveBO.getModuleId());
        ensureTemplateCodeUnique(systemId, saveBO.getModuleId(), saveBO.getTemplateCode(), templateId);
        template.setModuleId(saveBO.getModuleId())
                .setTemplateCode(saveBO.getTemplateCode())
                .setTemplateName(saveBO.getTemplateName())
                .setFileNamePattern(saveBO.getFileNamePattern())
                .setExportFormat(StringUtils.hasText(saveBO.getExportFormat()) ? saveBO.getExportFormat() : "CSV")
                .setIncludeHistoryFlag(Objects.isNull(saveBO.getIncludeHistoryFlag()) ? NO : saveBO.getIncludeHistoryFlag())
                .setConfigJson(saveBO.getConfigJson())
                .setVersion(template.getVersion() + 1)
                .setUpdatedBy(currentMemberId())
                .setUpdatedAt(LocalDateTime.now());
        exportTemplateService.updateById(template);
        List<ExportTemplateField> existing = exportTemplateFieldService.lambdaQuery()
                .eq(ExportTemplateField::getTemplateId, templateId)
                .list();
        if (!existing.isEmpty()) {
            exportTemplateFieldService.deleteByIds(existing.stream().map(ExportTemplateField::getTemplateFieldId).toList());
        }
        saveTemplateFields(systemId, template, saveBO.getFields());
        return templateVO(template);
    }

    /**
     * 创建导出任务并同步生成结果文件。
     */
    @Override
    public ExportJobDetailVO createJob(Long systemId, ExportJobCreateBO createBO) {
        permissionService.requireOperation("RECORD_EXPORT");
        Model model = ensureModulePublished(systemId, createBO.getModuleId());
        String idempotencyKey = StringUtils.hasText(createBO.getIdempotencyKey()) ? createBO.getIdempotencyKey()
                : currentIdempotencyKey();
        if (StringUtils.hasText(idempotencyKey)) {
            ExportJob existing = exportJobService.lambdaQuery()
                    .eq(ExportJob::getSystemId, systemId)
                    .eq(ExportJob::getCreatedBy, currentMemberId())
                    .eq(ExportJob::getIdempotencyKey, idempotencyKey)
                    .one();
            if (Objects.nonNull(existing)) {
                return jobDetailVO(existing);
            }
        }
        ExportTemplate template = Objects.isNull(createBO.getTemplateId()) ? null
                : requireTemplate(systemId, createBO.getTemplateId());
        List<Field> fields = exportFields(systemId, model.getModuleId(), template);
        PublishVersion publishVersion = publishVersionService.getById(model.getCurrentPublishVersionId());
        LocalDateTime now = LocalDateTime.now();
        ExportJob job = new ExportJob()
                .setSystemId(systemId)
                .setTenantId(currentTenantId())
                .setModuleId(model.getModuleId())
                .setTemplateId(Objects.isNull(template) ? null : template.getTemplateId())
                .setPublishVersionId(Objects.isNull(publishVersion) ? null : publishVersion.getPublishVersionId())
                .setJobStatus(QUEUED)
                .setProgress(0)
                .setSelectedRecordIdsJson(writeJson(createBO.getSelectedRecordIds()))
                .setFilterSnapshotJson(writeJson(createBO.getFilters()))
                .setSorterSnapshotJson(writeJson(createBO.getSorter()))
                .setFieldSnapshotJson(writeJson(fieldSnapshot(fields, template)))
                .setPermissionSnapshotJson(writeJson(permissionService.currentPermission()))
                .setDataScopeSnapshotJson(writeJson(permissionService.currentPermission().getDataScopes()))
                .setFileName(resolveFileName(createBO, model))
                .setRetryableFlag(NO)
                .setRetryCount(0)
                .setMaxRetryCount(3)
                .setRequestId(currentRequestId())
                .setIdempotencyKey(idempotencyKey)
                .setRequestHash(String.valueOf(Objects.hash(systemId, createBO.getModuleId(), createBO.getTemplateId(),
                        createBO.getSelectedRecordIds(), createBO.getFilters(), createBO.getSorter())))
                .setCreatedBy(currentMemberId())
                .setVersion(1)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        exportJobService.save(job);
        log(job, "CREATE", null, QUEUED, "创建导出任务", null);
        processJob(job, fields);
        return jobDetail(systemId, job.getJobId());
    }

    /**
     * 查询导出任务列表。
     */
    @Override
    public PageResult<ExportJobListItemVO> listJobs(Long systemId, ExportJobQueryBO queryBO) {
        permissionService.requireOperation("EXPORT_JOB_VIEW");
        ExportJobQueryBO query = Objects.isNull(queryBO) ? new ExportJobQueryBO() : queryBO;
        List<ExportJob> jobs = exportJobService.lambdaQuery()
                .eq(ExportJob::getSystemId, systemId)
                .list()
                .stream()
                .filter(job -> Objects.isNull(query.getModuleId()) || Objects.equals(job.getModuleId(), query.getModuleId()))
                .filter(job -> !StringUtils.hasText(query.getStatus()) || Objects.equals(job.getJobStatus(), query.getStatus()))
                .filter(job -> !StringUtils.hasText(query.getKeyword()) || contains(job.getFileName(), query.getKeyword())
                        || contains(job.getFailureMessage(), query.getKeyword()))
                .sorted(Comparator.comparing(ExportJob::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        long pageNo = normalizePageNo(query.getPageNo());
        long pageSize = normalizePageSize(query.getPageSize());
        int from = Math.toIntExact(Math.min((pageNo - 1) * pageSize, jobs.size()));
        int to = Math.toIntExact(Math.min(from + pageSize, jobs.size()));
        return PageResult.<ExportJobListItemVO>builder()
                .records(jobs.subList(from, to).stream().map(this::jobListItemVO).toList())
                .total(jobs.size())
                .pageNo(pageNo)
                .pageSize(pageSize)
                .hasNext(to < jobs.size())
                .build();
    }

    /**
     * 查询导出任务详情。
     */
    @Override
    public ExportJobDetailVO jobDetail(Long systemId, Long jobId) {
        permissionService.requireOperation("EXPORT_JOB_VIEW");
        return jobDetailVO(requireJob(systemId, jobId));
    }

    /**
     * 重试失败导出任务。
     */
    @Override
    public ExportJobDetailVO retryJob(Long systemId, Long jobId, ExportJobActionBO actionBO) {
        permissionService.requireOperation("EXPORT_JOB_RETRY");
        ExportJob job = requireJob(systemId, jobId);
        if (!FAILED.equals(job.getJobStatus()) || !Objects.equals(job.getRetryableFlag(), YES)
                || job.getRetryCount() >= job.getMaxRetryCount()) {
            throw new BusinessException(ExportErrorCode.JOB_STATUS_CONFLICT);
        }
        String fromStatus = job.getJobStatus();
        job.setJobStatus(QUEUED)
                .setProgress(0)
                .setFailureCode(null)
                .setFailureMessage(null)
                .setFailureSnapshotJson(null)
                .setRetryableFlag(NO)
                .setRetryCount(job.getRetryCount() + 1)
                .setUpdatedAt(LocalDateTime.now());
        exportJobService.updateById(job);
        log(job, "RETRY", fromStatus, QUEUED, Objects.isNull(actionBO) ? "重试导出任务" : actionBO.getReason(), null);
        List<Field> fields = exportFields(systemId, job.getModuleId(), Objects.isNull(job.getTemplateId()) ? null
                : requireTemplate(systemId, job.getTemplateId()));
        processJob(job, fields);
        return jobDetail(systemId, jobId);
    }

    /**
     * 取消排队或处理中的导出任务。
     */
    @Override
    @Transactional
    public ExportJobDetailVO cancelJob(Long systemId, Long jobId, ExportJobActionBO actionBO) {
        permissionService.requireOperation("EXPORT_JOB_CANCEL");
        ExportJob job = requireJob(systemId, jobId);
        if (!Set.of(QUEUED, PROCESSING, FAILED).contains(job.getJobStatus())) {
            throw new BusinessException(ExportErrorCode.JOB_STATUS_CONFLICT);
        }
        String fromStatus = job.getJobStatus();
        job.setJobStatus(CANCELED)
                .setProgress(0)
                .setFinishedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        exportJobService.updateById(job);
        log(job, "CANCEL", fromStatus, CANCELED, Objects.isNull(actionBO) ? "取消导出任务" : actionBO.getReason(), null);
        return jobDetailVO(job);
    }

    private void processJob(ExportJob job, List<Field> fields) {
        String fromStatus = job.getJobStatus();
        job.setJobStatus(PROCESSING)
                .setProgress(50)
                .setClaimedBy("sync-runner")
                .setClaimedAt(LocalDateTime.now())
                .setStartedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        exportJobService.updateById(job);
        log(job, "CLAIM", fromStatus, PROCESSING, "同步领取导出任务", null);
        try {
            byte[] content = buildCsv(job, fields).getBytes(StandardCharsets.UTF_8);
            FileBindDTO bindDTO = new FileBindDTO();
            bindDTO.setBizType("EXPORT_RESULT");
            bindDTO.setBizId(job.getJobId());
            bindDTO.setDisplayName(job.getFileName());
            FileInfoVO fileInfo = uploadFileService.saveGeneratedFile(job.getSystemId(), job.getFileName(), "text/csv",
                    content, bindDTO);
            job.setJobStatus(SUCCESS)
                    .setProgress(100)
                    .setResultFileId(Long.valueOf(fileInfo.getFileId()))
                    .setRetryableFlag(NO)
                    .setFinishedAt(LocalDateTime.now())
                    .setUpdatedAt(LocalDateTime.now());
            exportJobService.updateById(job);
            log(job, "SUCCESS", PROCESSING, SUCCESS, "导出文件生成成功", Map.of("resultFileId", fileInfo.getFileId()));
        } catch (BusinessException ex) {
            markFailed(job, ex.getErrorCode().getCode(), ex.getMessage(), ex.getErrorCode().isRetryable());
        } catch (RuntimeException ex) {
            markFailed(job, ExportErrorCode.FILE_GENERATE_FAILED.getCode(), ex.getMessage(), true);
        }
    }

    private void markFailed(ExportJob job, String code, String message, boolean retryable) {
        ExportFailureReasonVO failureReason = ExportFailureReasonVO.builder()
                .code(code)
                .message(message)
                .retryable(retryable)
                .stackSummary(message)
                .failedAt(LocalDateTime.now())
                .build();
        job.setJobStatus(FAILED)
                .setProgress(0)
                .setFailureCode(code)
                .setFailureMessage(message)
                .setFailureSnapshotJson(writeJson(failureReason))
                .setRetryableFlag(retryable ? YES : NO)
                .setFinishedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        exportJobService.updateById(job);
        log(job, "FAIL", PROCESSING, FAILED, message, failureReason);
    }

    private String buildCsv(ExportJob job, List<Field> fields) {
        List<Record> records = recordService.lambdaQuery()
                .eq(Record::getSystemId, job.getSystemId())
                .eq(Record::getModuleId, job.getModuleId())
                .in(StringUtils.hasText(job.getSelectedRecordIdsJson()) && !"[]".equals(job.getSelectedRecordIdsJson()),
                        Record::getRecordId, readLongList(job.getSelectedRecordIdsJson()))
                .list();
        Map<Long, Map<String, RecordValue>> valuesByRecord = recordValueService.lambdaQuery()
                .eq(RecordValue::getSystemId, job.getSystemId())
                .eq(RecordValue::getModuleId, job.getModuleId())
                .list()
                .stream()
                .collect(Collectors.groupingBy(RecordValue::getRecordId,
                        Collectors.toMap(RecordValue::getFieldCode, Function.identity(), (left, right) -> left)));
        StringBuilder csv = new StringBuilder();
        csv.append("recordId,recordNo,title");
        fields.forEach(field -> csv.append(',').append(escapeCsv(field.getName())));
        csv.append('\n');
        for (Record record : records) {
            permissionService.requireDataScope("RECORD", toId(record.getRecordId()), toId(record.getCreatedBy()));
            csv.append(escapeCsv(toId(record.getRecordId()))).append(',')
                    .append(escapeCsv(record.getRecordNo())).append(',')
                    .append(escapeCsv(record.getTitle()));
            Map<String, RecordValue> values = valuesByRecord.getOrDefault(record.getRecordId(), Map.of());
            for (Field field : fields) {
                csv.append(',').append(escapeCsv(displayValue(values.get(field.getCode()), field.getCode())));
            }
            csv.append('\n');
        }
        return csv.toString();
    }

    private String displayValue(RecordValue value, String fieldCode) {
        if (Objects.isNull(value)) {
            return "";
        }
        EffectivePermissionVO permission = permissionService.currentPermission();
        FieldPermissionVO fieldPermission = permission.getFieldPermissions().get(fieldCode);
        if (Objects.nonNull(fieldPermission) && !fieldPermission.isExportPlain()) {
            return "***";
        }
        if (StringUtils.hasText(value.getValueText())) {
            return value.getValueText();
        }
        if (Objects.nonNull(value.getValueNumber())) {
            return value.getValueNumber().toPlainString();
        }
        if (Objects.nonNull(value.getValueDate())) {
            return value.getValueDate().toString();
        }
        if (Objects.nonNull(value.getValueDatetime())) {
            return value.getValueDatetime().toString();
        }
        if (Objects.nonNull(value.getValueBool())) {
            return value.getValueBool() == YES ? "true" : "false";
        }
        return StringUtils.hasText(value.getDisplayValueJson()) ? value.getDisplayValueJson() : value.getValueJson();
    }

    private void saveTemplateFields(Long systemId, ExportTemplate template, List<ExportTemplateFieldBO> fieldBOList) {
        if (CollectionUtils.isEmpty(fieldBOList)) {
            return;
        }
        Map<Long, Field> fieldMap = fields(systemId, template.getModuleId()).stream()
                .collect(Collectors.toMap(Field::getFieldId, Function.identity()));
        int index = 0;
        for (ExportTemplateFieldBO fieldBO : fieldBOList) {
            Field field = fieldMap.get(fieldBO.getFieldId());
            if (Objects.isNull(field)) {
                throw new BusinessException(ExportErrorCode.TEMPLATE_FIELD_INVALID);
            }
            exportTemplateFieldService.save(new ExportTemplateField()
                    .setTemplateId(template.getTemplateId())
                    .setFieldId(field.getFieldId())
                    .setFieldCode(field.getCode())
                    .setHeaderName(StringUtils.hasText(fieldBO.getHeaderName()) ? fieldBO.getHeaderName() : field.getName())
                    .setColumnOrder(Objects.isNull(fieldBO.getColumnOrder()) ? index++ : fieldBO.getColumnOrder())
                    .setPlainRequiredFlag(Objects.isNull(fieldBO.getPlainRequiredFlag()) ? NO : fieldBO.getPlainRequiredFlag())
                    .setMaskStrategy(fieldBO.getMaskStrategy())
                    .setFormatJson(fieldBO.getFormatJson())
                    .setEnabledFlag(YES)
                    .setCreatedAt(LocalDateTime.now())
                    .setUpdatedAt(LocalDateTime.now()));
        }
    }

    private List<Field> exportFields(Long systemId, Long moduleId, ExportTemplate template) {
        List<Field> allFields = fields(systemId, moduleId);
        if (Objects.isNull(template)) {
            return allFields;
        }
        Map<Long, Field> fieldMap = allFields.stream().collect(Collectors.toMap(Field::getFieldId, Function.identity()));
        List<ExportTemplateField> templateFields = exportTemplateFieldService.lambdaQuery()
                .eq(ExportTemplateField::getTemplateId, template.getTemplateId())
                .eq(ExportTemplateField::getEnabledFlag, YES)
                .list();
        return templateFields.stream()
                .sorted(Comparator.comparing(ExportTemplateField::getColumnOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(field -> fieldMap.get(field.getFieldId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<Field> fields(Long systemId, Long moduleId) {
        return fieldService.lambdaQuery()
                .eq(Field::getSystemId, systemId)
                .eq(Field::getModuleId, moduleId)
                .eq(Field::getFieldStatus, ENABLED)
                .eq(Field::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .list()
                .stream()
                .sorted(Comparator.comparing(Field::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<Map<String, Object>> fieldSnapshot(List<Field> fields, ExportTemplate template) {
        Map<String, ExportTemplateField> templateFieldMap = Objects.isNull(template) ? Map.of()
                : exportTemplateFieldService.lambdaQuery()
                        .eq(ExportTemplateField::getTemplateId, template.getTemplateId())
                        .list()
                        .stream()
                        .collect(Collectors.toMap(ExportTemplateField::getFieldCode, Function.identity(),
                                (left, right) -> left, LinkedHashMap::new));
        return fields.stream().map(field -> {
            ExportTemplateField templateField = templateFieldMap.get(field.getCode());
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("fieldId", toId(field.getFieldId()));
            snapshot.put("fieldCode", field.getCode());
            snapshot.put("fieldName", field.getName());
            snapshot.put("fieldType", field.getFieldType());
            snapshot.put("headerName", Objects.isNull(templateField) ? field.getName() : templateField.getHeaderName());
            snapshot.put("plainRequired", Objects.nonNull(templateField) && Objects.equals(templateField.getPlainRequiredFlag(), YES));
            return snapshot;
        }).toList();
    }

    private ExportTemplate requireTemplate(Long systemId, Long templateId) {
        ExportTemplate template = exportTemplateService.getById(templateId);
        if (Objects.isNull(template) || !Objects.equals(template.getSystemId(), systemId)
                || !ACTIVE_DELETE_MARKER.equals(template.getDeleteMarker())) {
            throw new BusinessException(ExportErrorCode.TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private ExportJob requireJob(Long systemId, Long jobId) {
        ExportJob job = exportJobService.getById(jobId);
        if (Objects.isNull(job) || !Objects.equals(job.getSystemId(), systemId)) {
            throw new BusinessException(ExportErrorCode.JOB_NOT_FOUND);
        }
        return job;
    }

    private Model ensureModulePublished(Long systemId, Long moduleId) {
        Model model = modelService.lambdaQuery()
                .eq(Model::getSystemId, systemId)
                .eq(Model::getModuleId, moduleId)
                .eq(Model::getModuleStatus, PUBLISHED)
                .eq(Model::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.isNull(model)) {
            throw new BusinessException(ModuleConfigErrorCode.MODULE_NOT_FOUND);
        }
        return model;
    }

    private void ensureTemplateCodeUnique(Long systemId, Long moduleId, String code, Long currentTemplateId) {
        ExportTemplate existing = exportTemplateService.lambdaQuery()
                .eq(ExportTemplate::getSystemId, systemId)
                .eq(ExportTemplate::getModuleId, moduleId)
                .eq(ExportTemplate::getTemplateCode, code)
                .eq(ExportTemplate::getDeleteMarker, ACTIVE_DELETE_MARKER)
                .one();
        if (Objects.nonNull(existing) && !Objects.equals(existing.getTemplateId(), currentTemplateId)) {
            throw new BusinessException(ExportErrorCode.TEMPLATE_FIELD_INVALID, "导出模板编码已存在");
        }
    }

    private ExportTemplateVO templateVO(ExportTemplate template) {
        List<ExportTemplateFieldVO> fields = exportTemplateFieldService.lambdaQuery()
                .eq(ExportTemplateField::getTemplateId, template.getTemplateId())
                .list()
                .stream()
                .sorted(Comparator.comparing(ExportTemplateField::getColumnOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(this::templateFieldVO)
                .toList();
        return ExportTemplateVO.builder()
                .templateId(toId(template.getTemplateId()))
                .moduleId(toId(template.getModuleId()))
                .templateCode(template.getTemplateCode())
                .templateName(template.getTemplateName())
                .templateStatus(template.getTemplateStatus())
                .fileNamePattern(template.getFileNamePattern())
                .exportFormat(template.getExportFormat())
                .includeHistory(Objects.equals(template.getIncludeHistoryFlag(), YES))
                .fields(fields)
                .updatedAt(template.getUpdatedAt())
                .build();
    }

    private ExportTemplateFieldVO templateFieldVO(ExportTemplateField field) {
        return ExportTemplateFieldVO.builder()
                .templateFieldId(toId(field.getTemplateFieldId()))
                .fieldId(toId(field.getFieldId()))
                .fieldCode(field.getFieldCode())
                .headerName(field.getHeaderName())
                .columnOrder(field.getColumnOrder())
                .plainRequired(Objects.equals(field.getPlainRequiredFlag(), YES))
                .maskStrategy(field.getMaskStrategy())
                .build();
    }

    private ExportJobListItemVO jobListItemVO(ExportJob job) {
        return ExportJobListItemVO.builder()
                .jobId(toId(job.getJobId()))
                .moduleId(toId(job.getModuleId()))
                .templateId(toId(job.getTemplateId()))
                .status(job.getJobStatus())
                .progress(job.getProgress())
                .resultFileId(toId(job.getResultFileId()))
                .fileName(job.getFileName())
                .failureReason(failureReason(job))
                .retryable(Objects.equals(job.getRetryableFlag(), YES))
                .createdBy(toId(job.getCreatedBy()))
                .createdAt(job.getCreatedAt())
                .finishedAt(job.getFinishedAt())
                .build();
    }

    private ExportJobDetailVO jobDetailVO(ExportJob job) {
        return ExportJobDetailVO.builder()
                .jobId(toId(job.getJobId()))
                .moduleId(toId(job.getModuleId()))
                .templateId(toId(job.getTemplateId()))
                .status(job.getJobStatus())
                .progress(job.getProgress())
                .resultFileId(toId(job.getResultFileId()))
                .fileName(job.getFileName())
                .failureReason(failureReason(job))
                .retryable(Objects.equals(job.getRetryableFlag(), YES))
                .createdBy(toId(job.getCreatedBy()))
                .createdAt(job.getCreatedAt())
                .finishedAt(job.getFinishedAt())
                .selectedRecordIdsJson(job.getSelectedRecordIdsJson())
                .filterSnapshotJson(job.getFilterSnapshotJson())
                .sorterSnapshotJson(job.getSorterSnapshotJson())
                .fieldSnapshotJson(job.getFieldSnapshotJson())
                .permissionSnapshotJson(job.getPermissionSnapshotJson())
                .dataScopeSnapshotJson(job.getDataScopeSnapshotJson())
                .build();
    }

    private ExportFailureReasonVO failureReason(ExportJob job) {
        if (!StringUtils.hasText(job.getFailureCode())) {
            return null;
        }
        return ExportFailureReasonVO.builder()
                .code(job.getFailureCode())
                .message(job.getFailureMessage())
                .retryable(Objects.equals(job.getRetryableFlag(), YES))
                .failedAt(job.getFinishedAt())
                .build();
    }

    private void log(ExportJob job, String type, String fromStatus, String toStatus, String message, Object snapshot) {
        exportJobLogService.save(new ExportJobLog()
                .setJobId(job.getJobId())
                .setSystemId(job.getSystemId())
                .setTenantId(job.getTenantId())
                .setLogType(type)
                .setFromStatus(fromStatus)
                .setToStatus(toStatus)
                .setMessage(StringUtils.hasText(message) ? message : type)
                .setSnapshotJson(writeJson(snapshot))
                .setRequestId(currentRequestId())
                .setOperatorId(toId(currentMemberId()))
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now())
                .setDeleted(NO)
                .setCreatedBy(currentMemberId())
                .setUpdatedBy(currentMemberId()));
    }

    private String resolveFileName(ExportJobCreateBO createBO, Model model) {
        if (StringUtils.hasText(createBO.getFileName())) {
            return createBO.getFileName().endsWith(".csv") ? createBO.getFileName() : createBO.getFileName() + ".csv";
        }
        return (StringUtils.hasText(model.getName()) ? model.getName() : "export") + "-" + System.currentTimeMillis() + ".csv";
    }

    private String escapeCsv(String value) {
        if (Objects.isNull(value)) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value) && value.toLowerCase().contains(keyword.toLowerCase());
    }

    private List<Long> readLongList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readerForListOf(Long.class).readValue(json);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        if (Objects.isNull(value)) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(ExportErrorCode.FILE_GENERATE_FAILED, "导出快照序列化失败");
        }
    }

    private Long currentTenantId() {
        RequestContext context = RequestContextHolder.get();
        return context == null || !StringUtils.hasText(context.getTenantId()) ? null : Long.valueOf(context.getTenantId());
    }

    private Long currentMemberId() {
        RequestContext context = RequestContextHolder.get();
        return context == null || !StringUtils.hasText(context.getMemberId()) ? null : Long.valueOf(context.getMemberId());
    }

    private String currentRequestId() {
        RequestContext context = RequestContextHolder.get();
        return context == null || !StringUtils.hasText(context.getRequestId()) ? "NO_REQUEST_ID" : context.getRequestId();
    }

    private String currentIdempotencyKey() {
        RequestContext context = RequestContextHolder.get();
        return context == null ? null : context.getIdempotencyKey();
    }

    private long normalizePageNo(Long pageNo) {
        return pageNo == null || pageNo < 1 ? 1 : pageNo;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 200);
    }

    private String toId(Long value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }
}
