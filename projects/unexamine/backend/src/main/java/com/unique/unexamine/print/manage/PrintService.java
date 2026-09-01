package com.unique.unexamine.print.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.file.manage.FileModels;
import com.unique.unexamine.file.manage.FileStorageService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.print.base.entity.PrintJob;
import com.unique.unexamine.print.base.entity.PrintTemplate;
import com.unique.unexamine.print.base.entity.PrintTemplatePublication;
import com.unique.unexamine.print.base.entity.PrintTemplateVersion;
import com.unique.unexamine.print.base.service.PrintJobBaseService;
import com.unique.unexamine.print.base.service.PrintTemplateBaseService;
import com.unique.unexamine.print.base.service.PrintTemplatePublicationBaseService;
import com.unique.unexamine.print.base.service.PrintTemplateVersionBaseService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class PrintService {
    private final PrintTemplateBaseService templateService;
    private final PrintTemplateVersionBaseService versionService;
    private final PrintTemplatePublicationBaseService publicationPointerService;
    private final PrintJobBaseService jobService;
    private final ConfiguredModuleBaseService moduleService;
    private final ModulePublicationService modulePublicationService;
    private final RuntimeDataService runtimeDataService;
    private final FileStorageService fileStorageService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final PrintPdfRenderer pdfRenderer;
    private final ObjectMapper objectMapper;

    public PrintService(
            PrintTemplateBaseService templateService,
            PrintTemplateVersionBaseService versionService,
            PrintTemplatePublicationBaseService publicationPointerService,
            PrintJobBaseService jobService,
            ConfiguredModuleBaseService moduleService,
            ModulePublicationService modulePublicationService,
            RuntimeDataService runtimeDataService,
            FileStorageService fileStorageService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            PrintPdfRenderer pdfRenderer,
            ObjectMapper objectMapper) {
        this.templateService = templateService;
        this.versionService = versionService;
        this.publicationPointerService = publicationPointerService;
        this.jobService = jobService;
        this.moduleService = moduleService;
        this.modulePublicationService = modulePublicationService;
        this.runtimeDataService = runtimeDataService;
        this.fileStorageService = fileStorageService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.pdfRenderer = pdfRenderer;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PrintModels.AdminOverview adminOverview(AuthenticatedContext context, String traceId) {
        requireManage(context, traceId);
        List<ConfiguredModule> modules = moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                .eq(ConfiguredModule::getSystemId, context.systemId())
                .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                .orderByAsc(ConfiguredModule::getName, ConfiguredModule::getId));
        List<PrintModels.ModuleOption> moduleViews = new ArrayList<>();
        for (ConfiguredModule module : modules) {
            try {
                moduleViews.add(moduleOption(context, module));
            } catch (DomainException ignored) {
                // Draft-only modules cannot be used by a published print template.
            }
        }
        Map<Long, ConfiguredModule> moduleById = new LinkedHashMap<>();
        modules.forEach(module -> moduleById.put(module.getId(), module));
        List<PrintModels.TemplateView> templates = templateService.selectList(Wrappers.<PrintTemplate>lambdaQuery()
                        .eq(PrintTemplate::getSystemId, context.systemId())
                        .eq(PrintTemplate::getOwnerTenantId, context.tenantId())
                        .orderByAsc(PrintTemplate::getName, PrintTemplate::getId))
                .stream().map(template -> view(template, moduleById.get(template.getModuleId()))).toList();
        return new PrintModels.AdminOverview(moduleViews, templates);
    }

    @Transactional
    public PrintModels.TemplateView create(AuthenticatedContext context, PrintModels.SaveTemplateRequest request,
                                           String traceId) {
        requireManage(context, traceId);
        ConfiguredModule module = requireModule(context, request.moduleId());
        RuntimeModuleConfiguration published = modulePublicationService.published(context, module.getCode());
        PrintModels.Layout layout = normalizeLayout(request.layout());
        validateFields(layout, published);
        PrintTemplate template = new PrintTemplate();
        template.setSystemId(context.systemId());
        template.setOwnerTenantId(context.tenantId());
        template.setModuleId(module.getId());
        template.setCode(request.code().strip());
        template.setName(request.name().strip());
        template.setPageSize(request.pageSize());
        template.setOrientation(request.orientation());
        template.setDraftRevision(1);
        template.setTemplateJson(json(layout));
        template.setStatus("DRAFT");
        template.setCreatedByMemberId(context.memberId());
        template.setVersion(0);
        try {
            templateService.insert(template);
        } catch (DataIntegrityViolationException exception) {
            throw new DomainException("PRINT_TEMPLATE_CODE_EXISTS", "同一模块内打印模板编码已存在", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "PRINT_TEMPLATE_CREATED", "PRINT_TEMPLATE", template.getId().toString(), "SUCCESS",
                Map.of("moduleId", module.getId(), "moduleCode", module.getCode(), "draftRevision", 1));
        return view(templateService.selectById(template.getId()), module);
    }

    @Transactional
    public PrintModels.TemplateView update(AuthenticatedContext context, Long templateId,
                                           PrintModels.SaveTemplateRequest request, String traceId) {
        requireManage(context, traceId);
        PrintTemplate template = requireTemplate(context, templateId);
        if (request.expectedVersion() == null || !request.expectedVersion().equals(template.getVersion())) {
            throw new DomainException("PRINT_TEMPLATE_VERSION_CONFLICT", "打印模板草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        if (!template.getModuleId().equals(request.moduleId()) || !template.getCode().equals(request.code().strip())) {
            throw new DomainException("PRINT_TEMPLATE_IDENTITY_IMMUTABLE", "模板所属模块和编码创建后不可修改", HttpStatus.CONFLICT);
        }
        ConfiguredModule module = requireModule(context, template.getModuleId());
        RuntimeModuleConfiguration published = modulePublicationService.published(context, module.getCode());
        PrintModels.Layout layout = normalizeLayout(request.layout());
        validateFields(layout, published);
        template.setName(request.name().strip());
        template.setPageSize(request.pageSize());
        template.setOrientation(request.orientation());
        template.setTemplateJson(json(layout));
        template.setDraftRevision(template.getDraftRevision() + 1);
        template.setStatus("DRAFT");
        template.setUpdatedAt(null);
        if (templateService.updateById(template) == 0) {
            throw new DomainException("PRINT_TEMPLATE_VERSION_CONFLICT", "打印模板草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "PRINT_TEMPLATE_DRAFT_SAVED", "PRINT_TEMPLATE", templateId.toString(), "SUCCESS",
                Map.of("draftRevision", template.getDraftRevision()));
        return view(templateService.selectById(templateId), module);
    }

    @Transactional
    public PrintModels.Preview preview(AuthenticatedContext context, Long templateId, Long sampleRecordId,
                                       String traceId) {
        requireManage(context, traceId);
        PrintTemplate template = requireTemplate(context, templateId);
        ConfiguredModule module = requireModule(context, template.getModuleId());
        RuntimeModuleConfiguration published = modulePublicationService.published(context, module.getCode());
        PrintModels.Layout layout = layout(template.getTemplateJson());
        List<FieldDefinition> fields = validateFields(layout, published);
        RuntimeRecordView record = runtimeDataService.printView(context, module.getCode(), sampleRecordId, traceId);
        Rendered rendered = render(template.getName(), layout, fields, record);
        String hash = snapshot(template, module, published, layout, fields).hash();
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "PRINT_TEMPLATE_PREVIEWED", "PRINT_TEMPLATE", templateId.toString(), "SUCCESS",
                Map.of("sampleRecordId", sampleRecordId, "draftRevision", template.getDraftRevision(),
                        "previewHash", hash, "pageCount", rendered.pages().size(),
                        "visibleFieldCodes", rendered.visible(), "omittedFieldCodes", rendered.omitted()));
        return new PrintModels.Preview(templateId, template.getDraftRevision(), hash,
                published.versionId(), published.versionNumber(), sampleRecordId,
                rendered.visible(), rendered.omitted(), rendered.pages(), LocalDateTime.now());
    }

    @Transactional
    public PrintModels.Publication publish(AuthenticatedContext context, Long templateId,
                                           Integer expectedDraftRevision, String traceId) {
        requireManage(context, traceId);
        PrintTemplate template = requireTemplate(context, templateId);
        if (!Objects.equals(template.getDraftRevision(), expectedDraftRevision)) {
            throw new DomainException("PRINT_TEMPLATE_DRAFT_CHANGED", "打印模板草稿已变化，请重新预览", HttpStatus.CONFLICT);
        }
        ConfiguredModule module = requireModule(context, template.getModuleId());
        RuntimeModuleConfiguration published = modulePublicationService.published(context, module.getCode());
        PrintModels.Layout layout = layout(template.getTemplateJson());
        List<FieldDefinition> fields = validateFields(layout, published);
        Snapshot snapshot = snapshot(template, module, published, layout, fields);
        int nextVersion = versionService.selectList(Wrappers.<PrintTemplateVersion>lambdaQuery()
                        .eq(PrintTemplateVersion::getTemplateId, templateId))
                .stream().map(PrintTemplateVersion::getVersionNumber).max(Integer::compareTo).orElse(0) + 1;
        PrintTemplateVersion version = new PrintTemplateVersion();
        version.setTemplateId(templateId);
        version.setVersionNumber(nextVersion);
        version.setDraftRevision(template.getDraftRevision());
        version.setSnapshotHash(snapshot.hash());
        version.setSnapshotJson(snapshot.json());
        version.setPublishedByMemberId(context.memberId());
        versionService.insert(version);
        PrintTemplateVersion persistedVersion = versionService.selectById(version.getId());

        PrintTemplatePublication pointer = publicationPointerService.selectList(
                        Wrappers.<PrintTemplatePublication>lambdaQuery()
                                .eq(PrintTemplatePublication::getTemplateId, templateId))
                .stream().findFirst().orElse(null);
        if (pointer == null) {
            pointer = new PrintTemplatePublication();
            pointer.setTemplateId(templateId);
            pointer.setCurrentVersionId(version.getId());
            pointer.setUpdatedByMemberId(context.memberId());
            pointer.setVersion(0);
            publicationPointerService.insert(pointer);
        } else {
            pointer.setCurrentVersionId(version.getId());
            pointer.setUpdatedByMemberId(context.memberId());
            pointer.setUpdatedAt(null);
            if (publicationPointerService.updateById(pointer) == 0) {
                throw new DomainException("PRINT_PUBLICATION_VERSION_CONFLICT", "打印模板发布指针已变化", HttpStatus.CONFLICT);
            }
        }
        template.setStatus("PUBLISHED");
        template.setUpdatedAt(null);
        if (templateService.updateById(template) == 0) {
            throw new DomainException("PRINT_TEMPLATE_VERSION_CONFLICT", "打印模板草稿已被其他操作修改", HttpStatus.CONFLICT);
        }
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "PRINT_TEMPLATE_PUBLISHED", "PRINT_TEMPLATE_VERSION", version.getId().toString(), "SUCCESS",
                Map.of("templateId", templateId, "versionNumber", nextVersion,
                        "draftRevision", template.getDraftRevision(), "snapshotHash", snapshot.hash(),
                        "moduleVersionId", published.versionId()));
        return new PrintModels.Publication(templateId, version.getId(), nextVersion,
                template.getDraftRevision(), snapshot.hash(), published.versionId(),
                published.versionNumber(), persistedVersion == null ? null : persistedVersion.getPublishedAt());
    }

    @Transactional(readOnly = true)
    public List<PrintModels.RuntimeTemplate> runtimeTemplates(AuthenticatedContext context, String moduleCode,
                                                              String traceId) {
        requirePrint(context, moduleCode, traceId);
        ConfiguredModule module = requireModule(context, moduleCode);
        List<PrintModels.RuntimeTemplate> result = new ArrayList<>();
        for (PrintTemplate template : templateService.selectList(Wrappers.<PrintTemplate>lambdaQuery()
                .eq(PrintTemplate::getSystemId, context.systemId())
                .eq(PrintTemplate::getOwnerTenantId, context.tenantId())
                .eq(PrintTemplate::getModuleId, module.getId())
                .eq(PrintTemplate::getStatus, "PUBLISHED")
                .orderByAsc(PrintTemplate::getName))) {
            PrintTemplatePublication pointer = publication(template.getId());
            if (pointer == null) continue;
            PrintTemplateVersion version = versionService.selectById(pointer.getCurrentVersionId());
            if (version == null) continue;
            result.add(new PrintModels.RuntimeTemplate(template.getId(), template.getCode(), template.getName(),
                    template.getPageSize(), template.getOrientation(), version.getId(), version.getVersionNumber(),
                    version.getSnapshotHash(), version.getPublishedAt()));
        }
        return result;
    }

    @Transactional
    public PrintModels.PrintJobView print(AuthenticatedContext context, String moduleCode, Long recordId,
                                          Long templateId, Long requestedVersionId, String traceId) {
        ConfiguredModule module = requireModule(context, moduleCode);
        PrintTemplate template = requireTemplate(context, templateId);
        if (!template.getModuleId().equals(module.getId())) {
            throw new DomainException("PRINT_TEMPLATE_MODULE_MISMATCH", "打印模板不属于当前模块", HttpStatus.CONFLICT);
        }
        PrintTemplateVersion version = resolveVersion(templateId, requestedVersionId);
        SnapshotDocument snapshot = snapshotDocument(version.getSnapshotJson());
        RuntimeModuleConfiguration currentModule = modulePublicationService.published(context, moduleCode);
        validateSnapshotFields(snapshot, currentModule);
        RuntimeRecordView record = runtimeDataService.printView(context, moduleCode, recordId, traceId);
        Rendered rendered = render(snapshot.templateName(), snapshot.layout(), snapshot.fields(), record);

        Map<String, Object> authorization = authorizationSnapshot(context, moduleCode, record, rendered);
        Map<String, Object> recordSnapshot = recordSnapshot(record, rendered);
        PrintJob job = new PrintJob();
        job.setSystemId(context.systemId());
        job.setTenantId(context.tenantId());
        job.setModuleId(module.getId());
        job.setRecordId(recordId);
        job.setTemplateVersionId(version.getId());
        job.setAuthorizationSnapshotJson(json(authorization));
        job.setRecordSnapshotJson(json(recordSnapshot));
        job.setStatus("GENERATING");
        job.setCreatedByMemberId(context.memberId());
        job.setVersion(0);
        jobService.insert(job);

        byte[] pdf = pdfRenderer.render(snapshot.templateName(), snapshot.pageSize(), snapshot.orientation(),
                rendered.pages());
        String fileName = template.getCode() + "-" + printable(record.recordNumber(), "record-" + recordId)
                + "-v" + version.getVersionNumber() + ".pdf";
        FileModels.FileView file = fileStorageService.storeGenerated(context, fileName, "application/pdf", pdf,
                "print_job_" + job.getId(), traceId);
        job.setStatus("SUCCEEDED");
        job.setOutputFileId(file.id());
        job.setFinishedAt(LocalDateTime.now());
        job.setErrorMessage(null);
        job.setVersion(0);
        if (jobService.updateById(job) == 0) {
            throw new DomainException("PRINT_JOB_VERSION_CONFLICT", "打印任务状态已变化", HttpStatus.CONFLICT);
        }
        auditRecorder.recordWithPermissionSnapshot(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "BUSINESS_RECORD_PRINTED", "PRINT_JOB", job.getId().toString(), "SUCCESS",
                authorization, Map.of("recordId", recordId, "templateVersionId", version.getId(),
                        "snapshotHash", version.getSnapshotHash(), "fileId", file.id(),
                        "fileSha256", file.sha256(), "pageCount", rendered.pages().size()));
        return jobView(jobService.selectById(job.getId()), template, version);
    }

    @Transactional(readOnly = true)
    public PrintModels.PrintJobView job(AuthenticatedContext context, Long jobId) {
        PrintJob job = requireJob(context, jobId);
        PrintTemplateVersion version = versionService.selectById(job.getTemplateVersionId());
        PrintTemplate template = version == null ? null : templateService.selectById(version.getTemplateId());
        if (version == null || template == null) {
            throw new DomainException("PRINT_JOB_TEMPLATE_MISSING", "打印任务引用的模板版本不存在", HttpStatus.CONFLICT);
        }
        return jobView(job, template, version);
    }

    @Transactional
    public FileModels.BinaryContent binary(AuthenticatedContext context, Long jobId, boolean inline, String traceId) {
        PrintJob job = requireJob(context, jobId);
        if (!"SUCCEEDED".equals(job.getStatus()) || job.getOutputFileId() == null) {
            throw new DomainException("PRINT_JOB_OUTPUT_NOT_READY", "打印文件尚未生成", HttpStatus.CONFLICT);
        }
        FileModels.BinaryContent content = fileStorageService.downloadGenerated(context, job.getOutputFileId(), traceId);
        return new FileModels.BinaryContent(content.bytes(), content.contentType(), content.fileName(), inline);
    }

    private PrintModels.ModuleOption moduleOption(AuthenticatedContext context, ConfiguredModule module) {
        RuntimeModuleConfiguration published = modulePublicationService.published(context, module.getCode());
        List<PrintModels.FieldOption> fields = fields(published).stream()
                .map(field -> new PrintModels.FieldOption(field.id(), field.code(), field.name(), field.type())).toList();
        return new PrintModels.ModuleOption(module.getId(), module.getCode(), module.getName(),
                published.versionId(), published.versionNumber(), fields);
    }

    private PrintModels.TemplateView view(PrintTemplate template, ConfiguredModule module) {
        PrintTemplatePublication pointer = publication(template.getId());
        Long currentVersionId = pointer == null ? null : pointer.getCurrentVersionId();
        List<PrintModels.VersionView> versions = versionService.selectList(Wrappers.<PrintTemplateVersion>lambdaQuery()
                        .eq(PrintTemplateVersion::getTemplateId, template.getId())
                        .orderByDesc(PrintTemplateVersion::getVersionNumber))
                .stream().map(version -> new PrintModels.VersionView(version.getId(), version.getVersionNumber(),
                        version.getDraftRevision(), version.getSnapshotHash(), version.getPublishedAt(),
                        Objects.equals(currentVersionId, version.getId()))).toList();
        return new PrintModels.TemplateView(template.getId(), template.getModuleId(),
                module == null ? "unknown" : module.getCode(), module == null ? "未知模块" : module.getName(),
                template.getCode(), template.getName(), template.getPageSize(), template.getOrientation(),
                template.getDraftRevision(), layout(template.getTemplateJson()), template.getStatus(),
                template.getVersion(), currentVersionId, versions, template.getCreatedAt(), template.getUpdatedAt());
    }

    private PrintTemplateVersion resolveVersion(Long templateId, Long requestedVersionId) {
        Long versionId = requestedVersionId;
        if (versionId == null) {
            PrintTemplatePublication pointer = publication(templateId);
            if (pointer == null) {
                throw new DomainException("PRINT_TEMPLATE_NOT_PUBLISHED", "打印模板尚未发布", HttpStatus.CONFLICT);
            }
            versionId = pointer.getCurrentVersionId();
        }
        PrintTemplateVersion version = versionService.selectById(versionId);
        if (version == null || !version.getTemplateId().equals(templateId)) {
            throw new DomainException("PRINT_TEMPLATE_VERSION_NOT_FOUND", "打印模板版本不存在", HttpStatus.NOT_FOUND);
        }
        return version;
    }

    private PrintTemplatePublication publication(Long templateId) {
        return publicationPointerService.selectList(Wrappers.<PrintTemplatePublication>lambdaQuery()
                        .eq(PrintTemplatePublication::getTemplateId, templateId))
                .stream().findFirst().orElse(null);
    }

    private List<FieldDefinition> validateFields(PrintModels.Layout layout, RuntimeModuleConfiguration published) {
        List<FieldDefinition> available = fields(published);
        Set<String> availableCodes = available.stream().map(FieldDefinition::code)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> selected = selectedCodes(layout);
        List<String> missing = selected.stream().filter(code -> !availableCodes.contains(code)).toList();
        if (!missing.isEmpty()) {
            throw new DomainException("PRINT_TEMPLATE_FIELD_INVALID", "模板引用了已失效字段：" + String.join("、", missing),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, FieldDefinition> byCode = available.stream().collect(java.util.stream.Collectors.toMap(
                FieldDefinition::code, field -> field));
        return selected.stream().map(byCode::get).toList();
    }

    private void validateSnapshotFields(SnapshotDocument snapshot, RuntimeModuleConfiguration current) {
        Set<String> currentCodes = fields(current).stream().map(FieldDefinition::code).collect(java.util.stream.Collectors.toSet());
        List<String> invalid = snapshot.fields().stream().map(FieldDefinition::code)
                .filter(code -> !currentCodes.contains(code)).toList();
        if (!invalid.isEmpty()) {
            throw new DomainException("PRINT_TEMPLATE_FIELD_INVALID", "已发布模板引用的字段已失效：" + String.join("、", invalid),
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private List<FieldDefinition> fields(RuntimeModuleConfiguration configuration) {
        List<FieldDefinition> result = new ArrayList<>();
        for (JsonNode field : configuration.configuration().path("fields")) {
            result.add(new FieldDefinition(field.path("id").longValue(), field.path("code").asText(),
                    field.path("name").asText(), field.path("fieldType").asText()));
        }
        result.sort(Comparator.comparing(FieldDefinition::code));
        return result;
    }

    private Rendered render(String title, PrintModels.Layout layout, List<FieldDefinition> fields,
                            RuntimeRecordView record) {
        Set<String> detailCodes = new LinkedHashSet<>(layout.detailFieldCodes());
        List<PrintModels.PageField> visible = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        for (FieldDefinition field : fields) {
            JsonNode value = record.fields().get(field.code());
            if (value == null) {
                omitted.add(field.code());
                continue;
            }
            visible.add(new PrintModels.PageField(field.code(), field.name(), display(value),
                    detailCodes.contains(field.code())));
        }
        int rows = layout.rowsPerPage();
        int pageCount = Math.max(1, (visible.size() + rows - 1) / rows);
        List<PrintModels.PreviewPage> pages = new ArrayList<>();
        for (int index = 0; index < pageCount; index++) {
            int from = Math.min(index * rows, visible.size());
            int to = Math.min(from + rows, visible.size());
            pages.add(new PrintModels.PreviewPage(index + 1, pageCount, layout.header(),
                    record.recordNumber(), record.title(), List.copyOf(visible.subList(from, to)),
                    layout.signatureLabel(), layout.footer()));
        }
        return new Rendered(pages, visible.stream().map(PrintModels.PageField::code).toList(), omitted);
    }

    private Snapshot snapshot(PrintTemplate template, ConfiguredModule module,
                              RuntimeModuleConfiguration published, PrintModels.Layout layout,
                              List<FieldDefinition> fields) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("templateId", template.getId());
        value.put("templateCode", template.getCode());
        value.put("templateName", template.getName());
        value.put("moduleId", module.getId());
        value.put("moduleCode", module.getCode());
        value.put("moduleName", module.getName());
        value.put("moduleVersionId", published.versionId());
        value.put("moduleVersionNumber", published.versionNumber());
        value.put("pageSize", template.getPageSize());
        value.put("orientation", template.getOrientation());
        value.put("layout", layout);
        value.put("fields", fields);
        String json = json(value);
        return new Snapshot(json, sha256(json));
    }

    private SnapshotDocument snapshotDocument(String value) {
        try {
            JsonNode root = objectMapper.readTree(value);
            PrintModels.Layout layout = objectMapper.treeToValue(root.path("layout"), PrintModels.Layout.class);
            List<FieldDefinition> fields = objectMapper.convertValue(root.path("fields"),
                    new TypeReference<List<FieldDefinition>>() { });
            return new SnapshotDocument(root.path("templateName").asText(), root.path("moduleCode").asText(),
                    root.path("moduleVersionId").longValue(), root.path("moduleVersionNumber").intValue(),
                    root.path("pageSize").asText(), root.path("orientation").asText(), normalizeLayout(layout), fields);
        } catch (Exception exception) {
            throw new DomainException("PRINT_TEMPLATE_SNAPSHOT_INVALID", "打印模板发布快照损坏", HttpStatus.CONFLICT);
        }
    }

    private PrintModels.Layout normalizeLayout(PrintModels.Layout layout) {
        List<String> normal = distinct(layout.fieldCodes());
        List<String> detail = distinct(layout.detailFieldCodes() == null ? List.of() : layout.detailFieldCodes());
        Set<String> duplicate = new LinkedHashSet<>(normal);
        duplicate.retainAll(detail);
        if (!duplicate.isEmpty()) {
            throw new DomainException("PRINT_TEMPLATE_FIELD_DUPLICATED", "普通字段与明细字段不能重复", HttpStatus.BAD_REQUEST);
        }
        return new PrintModels.Layout(strip(layout.header()), strip(layout.footer()), strip(layout.signatureLabel()),
                normal, detail, layout.rowsPerPage());
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values.stream().map(String::strip).toList()));
    }

    private List<String> selectedCodes(PrintModels.Layout layout) {
        List<String> result = new ArrayList<>(layout.fieldCodes());
        result.addAll(layout.detailFieldCodes());
        return result;
    }

    private Map<String, Object> authorizationSnapshot(AuthenticatedContext context, String moduleCode,
                                                       RuntimeRecordView record, Rendered rendered) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accountId", context.accountId());
        result.put("memberId", context.memberId());
        result.put("systemId", context.systemId());
        result.put("tenantId", context.tenantId());
        result.put("dataTenantId", record.dataTenantId());
        result.put("resource", "MODULE:" + moduleCode);
        result.put("requiredActions", List.of("DETAIL", "PRINT"));
        result.put("fieldChannels", List.of("PAGE", "FILE"));
        result.put("roleIds", context.roleIds());
        result.put("dataScopes", context.dataScopes());
        result.put("visibleFieldCodes", rendered.visible());
        result.put("omittedFieldCodes", rendered.omitted());
        return result;
    }

    private Map<String, Object> recordSnapshot(RuntimeRecordView record, Rendered rendered) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recordId", record.id());
        result.put("recordNumber", record.recordNumber());
        result.put("title", record.title());
        result.put("status", record.status());
        result.put("recordVersion", record.version());
        result.put("updatedConfigVersionId", record.updatedConfigVersionId());
        result.put("dataTenantId", record.dataTenantId());
        result.put("pageCount", rendered.pages().size());
        result.put("pages", rendered.pages());
        return result;
    }

    private PrintModels.PrintJobView jobView(PrintJob job, PrintTemplate template, PrintTemplateVersion version) {
        Map<String, Object> authorization = map(job.getAuthorizationSnapshotJson());
        Map<String, Object> snapshot = map(job.getRecordSnapshotJson());
        Integer pageCount = snapshot.get("pageCount") instanceof Number number ? number.intValue() : 0;
        List<PrintModels.PreviewPage> pages = objectMapper.convertValue(
                snapshot.getOrDefault("pages", List.of()),
                new TypeReference<List<PrintModels.PreviewPage>>() {});
        String fileName = template.getCode() + "-record-" + job.getRecordId() + "-v" + version.getVersionNumber() + ".pdf";
        return new PrintModels.PrintJobView(job.getId(), job.getRecordId(), version.getId(),
                version.getVersionNumber(), template.getName(), job.getStatus(), job.getOutputFileId(), fileName,
                job.getOutputFileId() == null ? null : "/api/print/jobs/" + job.getId() + "/preview",
                job.getOutputFileId() == null ? null : "/api/print/jobs/" + job.getId() + "/download",
                pageCount, pages, authorization, snapshot, job.getErrorMessage(), job.getCreatedAt(), job.getFinishedAt());
    }

    private ConfiguredModule requireModule(AuthenticatedContext context, Long moduleId) {
        ConfiguredModule module = moduleService.selectById(moduleId);
        if (module == null || !Objects.equals(module.getSystemId(), context.systemId())
                || !Objects.equals(module.getOwnerTenantId(), context.tenantId())) {
            throw new DomainException("PRINT_MODULE_NOT_FOUND", "模块不存在或不属于当前系统租户", HttpStatus.NOT_FOUND);
        }
        return module;
    }

    private ConfiguredModule requireModule(AuthenticatedContext context, String moduleCode) {
        return moduleService.selectList(Wrappers.<ConfiguredModule>lambdaQuery()
                        .eq(ConfiguredModule::getSystemId, context.systemId())
                        .eq(ConfiguredModule::getOwnerTenantId, context.tenantId())
                        .eq(ConfiguredModule::getCode, moduleCode))
                .stream().findFirst().orElseThrow(() ->
                        new DomainException("PRINT_MODULE_NOT_FOUND", "模块不存在或不属于当前系统租户", HttpStatus.NOT_FOUND));
    }

    private PrintTemplate requireTemplate(AuthenticatedContext context, Long templateId) {
        PrintTemplate template = templateService.selectById(templateId);
        if (template == null || !Objects.equals(template.getSystemId(), context.systemId())
                || !Objects.equals(template.getOwnerTenantId(), context.tenantId())) {
            throw new DomainException("PRINT_TEMPLATE_NOT_FOUND", "打印模板不存在", HttpStatus.NOT_FOUND);
        }
        return template;
    }

    private PrintJob requireJob(AuthenticatedContext context, Long jobId) {
        PrintJob job = jobService.selectById(jobId);
        if (job == null || !Objects.equals(job.getSystemId(), context.systemId())
                || !Objects.equals(job.getTenantId(), context.tenantId())
                || !Objects.equals(job.getCreatedByMemberId(), context.memberId())) {
            throw new DomainException("PRINT_JOB_NOT_FOUND", "打印任务不存在", HttpStatus.NOT_FOUND);
        }
        return job;
    }

    private void requireManage(AuthenticatedContext context, String traceId) {
        if (context.systemId() != null && permissionChecker.allows(context, "CONFIG", "MODULE", "MANAGE")) return;
        auditRecorder.recordPermissionDenied(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "CONFIG:MODULE:MANAGE",
                Map.of("roleIds", context.roleIds(), "permissions", context.permissions()));
        throw new DomainException("PERMISSION_DENIED", "没有管理打印模板的权限", HttpStatus.FORBIDDEN);
    }

    private void requirePrint(AuthenticatedContext context, String moduleCode, String traceId) {
        if (permissionChecker.allows(context, "MODULE", moduleCode, "PRINT")) return;
        auditRecorder.recordPermissionDenied(traceId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "MODULE:" + moduleCode + ":PRINT",
                Map.of("roleIds", context.roleIds(), "permissions", context.permissions()));
        throw new DomainException("PERMISSION_DENIED", "没有打印当前模块记录的权限", HttpStatus.FORBIDDEN);
    }

    private PrintModels.Layout layout(String value) {
        try {
            return normalizeLayout(objectMapper.readValue(value, PrintModels.Layout.class));
        } catch (DomainException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new DomainException("PRINT_TEMPLATE_LAYOUT_INVALID", "打印模板布局损坏", HttpStatus.CONFLICT);
        }
    }

    private String display(JsonNode value) {
        if (value == null || value.isNull()) return "—";
        if (value.isTextual()) return value.asText();
        if (value.isNumber()) return new BigDecimal(value.asText()).stripTrailingZeros().toPlainString();
        if (value.isBoolean()) return value.asBoolean() ? "是" : "否";
        return value.toString();
    }

    private String printable(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String normalized = value.replaceAll("[^A-Za-z0-9_-]", "-").replaceAll("-+", "-");
        return normalized.isBlank() ? fallback : normalized;
    }

    private String strip(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize print state", exception);
        }
    }

    private Map<String, Object> map(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() { });
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte item : digest) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private record FieldDefinition(Long id, String code, String name, String type) {
    }

    private record Snapshot(String json, String hash) {
    }

    private record SnapshotDocument(String templateName, String moduleCode, Long moduleVersionId,
                                    Integer moduleVersionNumber, String pageSize, String orientation,
                                    PrintModels.Layout layout, List<FieldDefinition> fields) {
    }

    private record Rendered(List<PrintModels.PreviewPage> pages, List<String> visible, List<String> omitted) {
    }
}
