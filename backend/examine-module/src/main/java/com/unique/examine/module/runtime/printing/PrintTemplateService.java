package com.unique.examine.module.runtime.printing;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.module.manage.security.ConfigSession;
import com.unique.examine.module.manage.service.ConfigMutationSupport;
import com.unique.examine.module.manage.service.RequestContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class PrintTemplateService {
    private static final Set<String> STATUSES = Set.of("ENABLED", "DISABLED", "ARCHIVED");
    private static final Set<String> PAPERS = Set.of("A4", "A5");
    private static final Set<String> ORIENTATIONS = Set.of("PORTRAIT", "LANDSCAPE");
    private static final Set<String> FORBIDDEN_TYPES = Set.of("IDENTITY", "SECRET");
    private static final Set<String> POSITIONED_KINDS = Set.of("SIGNATURE", "SEAL", "PREPRINT");
    private static final Set<String> CODE_KINDS = Set.of("QR", "BARCODE");

    private final PrintRepository repository;
    private final ConfigMutationSupport mutations;
    private final IdService ids;

    public PrintTemplateService(PrintRepository repository, ConfigMutationSupport mutations, IdService ids) {
        this.repository = repository;
        this.mutations = mutations;
        this.ids = ids;
    }

    public List<PrintViews.Template> list(ConfigSession session, long moduleId) {
        repository.requireModule(session.systemId(), moduleId);
        return repository.templates(session.systemId(), moduleId).stream().map(this::view).toList();
    }

    @Transactional
    public PrintViews.Template create(ConfigSession session, long moduleId, PrintViews.CreateTemplateRequest request,
                                      String idempotencyKey, RequestContext context) {
        return mutations.idempotent(session.systemId() + ":module:" + moduleId + ":print-template:create",
                idempotencyKey, request, PrintViews.Template.class,
                () -> createNow(session, moduleId, request, idempotencyKey, context));
    }

    private PrintViews.Template createNow(ConfigSession session, long moduleId,
                                          PrintViews.CreateTemplateRequest request, String key,
                                          RequestContext context) {
        var module = repository.requireModule(session.systemId(), moduleId);
        var input = validated(request == null ? null : request.code(), request == null ? null : request.name(),
                request == null ? null : request.status(), request == null ? null : request.paperSize(),
                request == null ? null : request.orientation(), request == null ? null : request.title(),
                request == null ? null : request.fieldCodes(), request == null ? null : request.footer(),
                request == null ? null : request.header(), request == null ? null : request.positionedElements(),
                request == null ? null : request.codeBlocks(),
                repository.draftFields(session.systemId(), moduleId));
        var now = LocalDateTime.now();
        var id = ids.nextId();
        repository.insertTemplate(new PrintRepository.TemplateRecord(id, session.systemId(), moduleId, module.code(),
                input.code(), input.name(), input.status(), input.paperSize(), input.orientation(),
                repository.write(input.definition()), null, null, null, now, session.accountId(), now,
                session.accountId(), 0));
        var after = view(repository.requireTemplate(session.systemId(), id));
        mutations.changed(session, "MODULE_PRINT_TEMPLATE", Long.toString(id), "PRINT_TEMPLATE_CREATED", null,
                after, after.version(), key, context);
        return after;
    }

    @Transactional
    public PrintViews.Template update(ConfigSession session, long moduleId, long templateId,
                                      PrintViews.UpdateTemplateRequest request, RequestContext context) {
        var before = repository.requireTemplate(session.systemId(), templateId);
        requireModule(before, moduleId);
        var input = validated(before.code(), request == null ? null : request.name(),
                request == null ? null : request.status(), request == null ? null : request.paperSize(),
                request == null ? null : request.orientation(), request == null ? null : request.title(),
                request == null ? null : request.fieldCodes(), request == null ? null : request.footer(),
                request == null ? null : request.header(), request == null ? null : request.positionedElements(),
                request == null ? null : request.codeBlocks(),
                repository.draftFields(session.systemId(), moduleId));
        repository.updateTemplate(session.systemId(), templateId, input.name(), input.status(), input.paperSize(),
                input.orientation(), repository.write(input.definition()), session.accountId(),
                request == null ? -1L : request.expectedVersion());
        var after = view(repository.requireTemplate(session.systemId(), templateId));
        mutations.changed(session, "MODULE_PRINT_TEMPLATE", Long.toString(templateId), "PRINT_TEMPLATE_UPDATED",
                view(before), after, after.version(), context.requestId(), context);
        return after;
    }

    @Transactional
    public PrintViews.Template publish(ConfigSession session, long moduleId, long templateId,
                                       PrintViews.PublishTemplateRequest request, String idempotencyKey,
                                       RequestContext context) {
        return mutations.idempotent(session.systemId() + ":print-template:" + templateId + ":publish",
                idempotencyKey, request, PrintViews.Template.class,
                () -> publishNow(session, moduleId, templateId, request, idempotencyKey, context));
    }

    private PrintViews.Template publishNow(ConfigSession session, long moduleId, long templateId,
                                           PrintViews.PublishTemplateRequest request, String key,
                                           RequestContext context) {
        var template = repository.requireTemplate(session.systemId(), templateId);
        requireModule(template, moduleId);
        if (request == null || request.expectedVersion() != template.version()) {
            throw conflict("PRINT_TEMPLATE_CONFLICT", "Print template changed; reload and retry");
        }
        var active = repository.requireActiveSchema(session.systemId(), moduleId);
        if (!active.moduleCode().equals(template.moduleCode())) {
            throw conflict("PRINT_TEMPLATE_SCHEMA_STALE", "Published module identity does not match the template");
        }
        var definition = repository.definition(template.definitionJson());
        validateFields(definition.fieldCodes(), repository.publishedFields(session.systemId(),
                active.schemaVersionId(), active.moduleSnapshotId()));
        validatePositions(definition.positionedElements(), definition.fieldCodes());
        validateCodeBlocks(definition.codeBlocks(), definition.fieldCodes());
        var versionNo = repository.nextVersionNo(session.systemId(), templateId);
        var versionId = ids.nextId();
        var now = LocalDateTime.now();
        repository.insertVersion(new PrintRepository.TemplateVersionRecord(versionId, session.systemId(), templateId,
                moduleId, template.moduleCode(), template.code(), template.name(), versionNo,
                active.schemaVersionId(), active.moduleSnapshotId(), template.paperSize(), template.orientation(),
                template.definitionJson(), sha256(template.definitionJson()), now, session.accountId()));
        repository.pointPublishedVersion(session.systemId(), templateId, versionId, session.accountId(),
                request.expectedVersion());
        var after = view(repository.requireTemplate(session.systemId(), templateId));
        mutations.changed(session, "MODULE_PRINT_TEMPLATE", Long.toString(templateId), "PRINT_TEMPLATE_PUBLISHED",
                view(template), after, after.version(), key, context);
        return after;
    }

    private Validated validated(String code, String name, String status, String paperSize, String orientation,
                                String title, List<String> fieldCodes, String footer,
                                String header, List<PrintViews.PositionedElement> positionedElements,
                                List<PrintViews.CodeBlock> codeBlocks,
                                List<PrintRepository.FieldRef> available) {
        var normalizedCode = value(code, 2, 64, "PRINT_TEMPLATE_CODE_INVALID", "Template code is required")
                .toLowerCase();
        if (!normalizedCode.matches("^[a-z][a-z0-9_]{1,63}$")) {
            throw invalid("PRINT_TEMPLATE_CODE_INVALID", "Template code must use lowercase letters, numbers and underscores");
        }
        var normalizedName = value(name, 1, 128, "PRINT_TEMPLATE_NAME_INVALID", "Template name is required");
        var normalizedStatus = upper(status);
        var normalizedPaper = upper(paperSize);
        var normalizedOrientation = upper(orientation);
        if (!STATUSES.contains(normalizedStatus)) throw invalid("PRINT_TEMPLATE_STATUS_INVALID", "Template status is invalid");
        if (!PAPERS.contains(normalizedPaper)) throw invalid("PRINT_TEMPLATE_PAPER_INVALID", "Paper size must be A4 or A5");
        if (!ORIENTATIONS.contains(normalizedOrientation)) {
            throw invalid("PRINT_TEMPLATE_ORIENTATION_INVALID", "Orientation must be PORTRAIT or LANDSCAPE");
        }
        var normalizedTitle = value(title, 1, 160, "PRINT_TEMPLATE_TITLE_INVALID", "Template title is required");
        var normalizedFooter = footer == null ? "" : footer.trim();
        if (normalizedFooter.length() > 300) throw invalid("PRINT_TEMPLATE_FOOTER_INVALID", "Template footer is too long");
        var normalizedHeader = header == null ? "" : header.trim();
        if (normalizedHeader.length() > 300) throw invalid("PRINT_TEMPLATE_HEADER_INVALID", "Template header is too long");
        var codes = fieldCodes == null ? List.<String>of() : fieldCodes.stream()
                .map(item -> item == null ? "" : item.trim()).toList();
        validateFields(codes, available);
        var positions = validatePositions(positionedElements, codes);
        var blocks = validateCodeBlocks(codeBlocks, codes);
        return new Validated(normalizedCode, normalizedName, normalizedStatus, normalizedPaper,
                normalizedOrientation, new PrintViews.TemplateDefinition(normalizedHeader, normalizedTitle, codes,
                normalizedFooter, positions, blocks));
    }

    private static List<PrintViews.PositionedElement> validatePositions(
            List<PrintViews.PositionedElement> values, List<String> fieldCodes) {
        var input = values == null ? List.<PrintViews.PositionedElement>of() : values;
        if (input.size() > 12) throw invalid("PRINT_TEMPLATE_POSITION_INVALID", "At most 12 positioned elements are allowed");
        for (var value : input) {
            if (value == null || !POSITIONED_KINDS.contains(upper(value.kind()))
                    || value.label() == null || value.label().isBlank() || value.label().length() > 80
                    || value.xMm() < 0 || value.yMm() < 0 || value.widthMm() < 5 || value.heightMm() < 5
                    || value.xMm() + value.widthMm() > 297 || value.yMm() + value.heightMm() > 420
                    || value.fieldCode() != null && !value.fieldCode().isBlank()
                    && !fieldCodes.contains(value.fieldCode())) {
                throw invalid("PRINT_TEMPLATE_POSITION_INVALID", "Signature, seal or preprint position is invalid");
            }
        }
        return List.copyOf(input);
    }

    private static List<PrintViews.CodeBlock> validateCodeBlocks(List<PrintViews.CodeBlock> values,
                                                                  List<String> fieldCodes) {
        var input = values == null ? List.<PrintViews.CodeBlock>of() : values;
        if (input.size() > 8) throw invalid("PRINT_TEMPLATE_CODE_BLOCK_INVALID", "At most 8 code blocks are allowed");
        for (var value : input) {
            if (value == null || !CODE_KINDS.contains(upper(value.kind())) || value.label() == null
                    || value.label().isBlank() || value.label().length() > 80 || value.fieldCode() == null
                    || !(fieldCodes.contains(value.fieldCode()) || Set.of("{recordNo}", "{recordId}").contains(value.fieldCode()))) {
                throw invalid("PRINT_TEMPLATE_CODE_BLOCK_INVALID", "QR/barcode source is invalid");
            }
        }
        return List.copyOf(input);
    }

    private static void validateFields(List<String> fieldCodes, List<PrintRepository.FieldRef> available) {
        if (fieldCodes == null || fieldCodes.isEmpty() || fieldCodes.size() > 50
                || new LinkedHashSet<>(fieldCodes).size() != fieldCodes.size()) {
            throw invalid("PRINT_TEMPLATE_FIELDS_INVALID", "Template requires 1..50 unique field codes");
        }
        var fields = new LinkedHashMap<String, PrintRepository.FieldRef>();
        available.forEach(field -> fields.put(field.code(), field));
        for (var code : fieldCodes) {
            var field = fields.get(code);
            if (field == null || field.hidden() || FORBIDDEN_TYPES.contains(field.type())) {
                throw invalid("PRINT_TEMPLATE_FIELD_FORBIDDEN", "Template field is unavailable or protected: " + code);
            }
        }
    }

    private PrintViews.Template view(PrintRepository.TemplateRecord value) {
        return new PrintViews.Template(Long.toString(value.id()), Long.toString(value.moduleId()), value.moduleCode(),
                value.code(), value.name(), value.status(), value.paperSize(), value.orientation(),
                repository.definition(value.definitionJson()), string(value.publishedVersionId()),
                value.publishedVersionNo(), string(value.publishedSchemaVersionId()), value.version(), value.updatedAt());
    }

    private static void requireModule(PrintRepository.TemplateRecord template, long moduleId) {
        if (template.moduleId() != moduleId) throw notFound("PRINT_TEMPLATE_NOT_FOUND", "Print template was not found");
    }
    private static String value(String value, int min, int max, String code, String message) {
        if (value == null || value.trim().length() < min || value.trim().length() > max) throw invalid(code, message);
        return value.trim();
    }
    private static String upper(String value) { return value == null ? "" : value.trim().toUpperCase(); }
    private static String string(Long value) { return value == null ? null : Long.toString(value); }
    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) { throw new IllegalStateException("SHA-256 is unavailable", exception); }
    }
    private static BusinessException invalid(String code, String message) {
        return new BusinessException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }
    private static BusinessException notFound(String code, String message) {
        return new BusinessException(code, message, HttpStatus.NOT_FOUND);
    }

    private record Validated(String code, String name, String status, String paperSize, String orientation,
                             PrintViews.TemplateDefinition definition) { }
}
