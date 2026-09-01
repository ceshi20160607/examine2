package com.unique.unexamine.analytics.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.analytics.base.entity.AnaDataSource;
import com.unique.unexamine.analytics.base.entity.AnaDataSourceVersion;
import com.unique.unexamine.analytics.base.service.AnaDataSourceBaseService;
import com.unique.unexamine.analytics.base.service.AnaDataSourceVersionBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleCatalogItem;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleCatalogService;
import com.unique.unexamine.moduleconfig.manage.RuntimeModuleConfiguration;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ReportService {
    private static final int MAXIMUM_MODULES = 3;
    private static final int MAXIMUM_SCAN_ROWS = 1000;
    private static final int PAGE_SIZE = 200;
    private static final Set<String> METRICS = Set.of("COUNT", "SUM", "AVG", "MIN", "MAX");
    private static final Set<String> DIMENSIONS = Set.of("FIELD", "TIME", "PERSON", "DEPARTMENT", "STATUS");
    private static final Set<String> RELATIONS = Set.of("REFERENCE", "SUBTABLE", "CASCADE");
    private static final Set<String> OPERATORS = Set.of(
            "EQ", "NE", "CONTAINS", "GT", "GTE", "LT", "LTE", "EMPTY", "NOT_EMPTY");
    private static final Set<String> NUMERIC_TYPES = Set.of(
            "NUMBER", "DECIMAL", "INTEGER", "LONG", "MONEY", "PERCENT");
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() { };

    private final AnaDataSourceBaseService sourceService;
    private final AnaDataSourceVersionBaseService sourceVersionService;
    private final RuntimeModuleCatalogService catalogService;
    private final ModulePublicationService modulePublicationService;
    private final RuntimeDataService runtimeDataService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public ReportService(
            AnaDataSourceBaseService sourceService,
            AnaDataSourceVersionBaseService sourceVersionService,
            RuntimeModuleCatalogService catalogService,
            ModulePublicationService modulePublicationService,
            RuntimeDataService runtimeDataService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.sourceService = sourceService;
        this.sourceVersionService = sourceVersionService;
        this.catalogService = catalogService;
        this.modulePublicationService = modulePublicationService;
        this.runtimeDataService = runtimeDataService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    public ReportModels.Metadata metadata(AuthenticatedContext context, String traceId) {
        requireSystemAdmin(context);
        List<ReportModels.ModuleMetadata> modules = catalogService.list(context).stream()
                .map(module -> moduleMetadata(context, module, traceId)).toList();
        return new ReportModels.Metadata(modules, METRICS.stream().sorted().toList(),
                DIMENSIONS.stream().sorted().toList(), RELATIONS.stream().sorted().toList(),
                MAXIMUM_MODULES, MAXIMUM_SCAN_ROWS, false);
    }

    public Map<String, Object> prepareDefinition(
            AuthenticatedContext context, Map<String, Object> definition, String traceId) {
        Compiled compiled = compile(context, definition, traceId);
        LinkedHashMap<String, Object> prepared = new LinkedHashMap<>(definition);
        prepared.put("queryExplanation", objectMapper.convertValue(compiled.plan(), MAP_TYPE));
        return prepared;
    }

    public void assertPublishable(
            AuthenticatedContext context, Map<String, Object> definition, String traceId) {
        executeDefinition(context, definition, null, null, null, traceId);
    }

    public ReportModels.Preview previewDraft(
            AuthenticatedContext context, Long sourceId, String traceId) {
        requireSystemAdmin(context);
        AnaDataSource source = requireSource(context, sourceId);
        if (!"MODULE_REPORT".equals(source.getSourceType())) {
            throw invalid("REPORT_SOURCE_TYPE_REQUIRED", "只有结构化模块报表数据源可以预览");
        }
        try {
            ReportModels.Result result = executeDefinition(context, readMap(source.getDefinitionJson()),
                    null, null, null, traceId);
            audit(context, traceId, "ANALYTICS_REPORT_PREVIEWED", sourceId,
                    Map.of("draftRevision", source.getDraftRevision(),
                            "estimatedRows", result.queryPlan().estimatedRows()));
            return new ReportModels.Preview(sourceId, source.getDraftRevision(), true, List.of(),
                    result.queryPlan(), result);
        } catch (DomainException exception) {
            return new ReportModels.Preview(sourceId, source.getDraftRevision(), false,
                    List.of(new ReportModels.Issue(exception.code(), exception.getMessage(), "definition")),
                    null, null);
        }
    }

    public ReportModels.Result executePublished(
            AuthenticatedContext context, Long sourceId, String traceId) {
        AnaDataSource source = requireSource(context, sourceId);
        if (!"MODULE_REPORT".equals(source.getSourceType())) {
            throw invalid("REPORT_SOURCE_TYPE_REQUIRED", "只有结构化模块报表数据源可以执行");
        }
        AnaDataSourceVersion version = latestVersion(sourceId);
        if (version == null) {
            throw invalid("REPORT_SOURCE_NOT_PUBLISHED", "数据源尚未发布，运行态不会读取草稿");
        }
        Map<String, Object> snapshot = readMap(version.getSnapshotJson());
        requireSnapshotContext(context, snapshot);
        if (!allowsPolicy(context, readMapValue(snapshot.get("permissionPolicy")))) {
            throw forbidden("REPORT_SOURCE_FORBIDDEN", "当前上下文无权执行该已发布数据源");
        }
        ReportModels.Result result = executeDefinition(context, readMapValue(snapshot.get("definition")),
                version.getId(), version.getVersionNumber(), version.getDefinitionHash(), traceId);
        audit(context, traceId, "ANALYTICS_REPORT_EXECUTED", sourceId,
                Map.of("dataSourceVersionId", version.getId(), "versionNumber", version.getVersionNumber(),
                        "estimatedRows", result.queryPlan().estimatedRows()));
        return result;
    }

    public ReportModels.Result executeVersion(
            AuthenticatedContext context, Long versionId, String traceId) {
        AnaDataSourceVersion version = sourceVersionService.selectById(versionId);
        if (version == null) {
            throw new DomainException("REPORT_SOURCE_VERSION_NOT_FOUND", "数据源发布版本不存在",
                    HttpStatus.NOT_FOUND);
        }
        AnaDataSource source = requireSource(context, version.getDataSourceId());
        if (!"MODULE_REPORT".equals(source.getSourceType())) {
            throw invalid("REPORT_SOURCE_TYPE_REQUIRED", "KPI 只能绑定结构化模块报表发布版本");
        }
        Map<String, Object> snapshot = readMap(version.getSnapshotJson());
        requireSnapshotContext(context, snapshot);
        if (!allowsPolicy(context, readMapValue(snapshot.get("permissionPolicy")))) {
            throw forbidden("REPORT_SOURCE_FORBIDDEN", "当前上下文无权执行该数据源发布版本");
        }
        return executeDefinition(context, readMapValue(snapshot.get("definition")), version.getId(),
                version.getVersionNumber(), version.getDefinitionHash(), traceId);
    }

    public ReportModels.Result executeSnapshot(
            AuthenticatedContext context, Map<String, Object> snapshot, String traceId) {
        requireSnapshotContext(context, snapshot);
        if (!allowsPolicy(context, readMapValue(snapshot.get("permissionPolicy")))) {
            throw forbidden("REPORT_SOURCE_FORBIDDEN", "当前上下文无权执行该已发布数据源");
        }
        return executeDefinition(context, readMapValue(snapshot.get("definition")),
                longValue(snapshot.get("dataSourceVersionId")),
                integerOrNull(snapshot.get("dataSourceVersionNumber")),
                stringOrNull(snapshot.get("definitionHash")), traceId);
    }

    private ReportModels.Result executeDefinition(
            AuthenticatedContext context, Map<String, Object> definition,
            Long versionId, Integer versionNumber, String definitionHash, String traceId) {
        Compiled compiled = compile(context, definition, traceId);
        int maximumRows = compiled.maximumScanRows();
        LinkedHashMap<String, List<RuntimeRecordView>> recordsByAlias = new LinkedHashMap<>();
        long totalScanned = 0;
        long joinCost = 1;
        for (ModuleSpec module : compiled.modules()) {
            List<Map<String, Object>> filters = compiled.filters().stream()
                    .filter(filter -> module.alias().equals(string(filter.get("alias"))))
                    .map(filter -> filterForRuntime(filter)).toList();
            Map<String, Object> time = compiled.timeField();
            if (module.alias().equals(string(time.get("alias")))) {
                String fieldCode = string(time.get("fieldCode"));
                if (stringOrNull(time.get("from")) != null) {
                    filters = append(filters, Map.of("fieldCode", fieldCode, "operator", "GTE",
                            "value", string(time.get("from"))));
                }
                if (stringOrNull(time.get("to")) != null) {
                    filters = append(filters, Map.of("fieldCode", fieldCode, "operator", "LTE",
                            "value", string(time.get("to"))));
                }
            }
            String sortField = module.alias().equals(string(compiled.sort().get("alias")))
                    ? stringOrDefault(compiled.sort().get("fieldCode"), "updatedAt") : "updatedAt";
            String sortDirection = module.alias().equals(string(compiled.sort().get("alias")))
                    ? stringOrDefault(compiled.sort().get("direction"), "DESC") : "DESC";
            RuntimeRecordList first = runtimeDataService.list(context, module.moduleCode(), "ACTIVE", "ALL", "",
                    writeJson(filters), sortField, sortDirection, 1, PAGE_SIZE, traceId);
            if (first.total() > maximumRows || totalScanned + first.total() > maximumRows) {
                throw invalid("REPORT_QUERY_COST_EXCEEDED",
                        "权限过滤后的扫描记录超过成本上限 " + maximumRows + "，请增加固定筛选或降低范围");
            }
            joinCost = Math.multiplyExact(joinCost, Math.max(1, first.total()));
            if (joinCost > maximumRows) {
                throw invalid("REPORT_QUERY_COST_EXCEEDED",
                        "多模块关系的预计组合行数超过成本上限 " + maximumRows + "，不能发布或执行");
            }
            List<RuntimeRecordView> records = new ArrayList<>(first.records());
            for (int page = 2; records.size() < first.total(); page++) {
                records.addAll(runtimeDataService.list(context, module.moduleCode(), "ACTIVE", "ALL", "",
                        writeJson(filters), sortField, sortDirection, page, PAGE_SIZE, traceId).records());
            }
            recordsByAlias.put(module.alias(), records);
            totalScanned += first.total();
        }

        List<JoinedRow> rows = recordsByAlias.get(compiled.modules().getFirst().alias()).stream()
                .map(record -> new JoinedRow(new LinkedHashMap<>(Map.of(compiled.modules().getFirst().alias(), record))))
                .toList();
        for (Map<String, Object> relation : compiled.relations()) {
            rows = join(rows, recordsByAlias.get(string(relation.get("rightAlias"))), relation);
            if (rows.size() > maximumRows) {
                throw invalid("REPORT_QUERY_COST_EXCEEDED", "关联后的授权行数超过成本上限，不能继续聚合");
            }
        }
        if (!compiled.sort().isEmpty() && !compiled.modules().getFirst().alias()
                .equals(string(compiled.sort().get("alias")))) {
            String alias = string(compiled.sort().get("alias"));
            String field = string(compiled.sort().get("fieldCode"));
            Comparator<JoinedRow> comparator = Comparator.comparing(
                    row -> comparable(value(row.records().get(alias), field)),
                    Comparator.nullsLast(Comparator.naturalOrder()));
            if ("DESC".equals(string(compiled.sort().get("direction")))) comparator = comparator.reversed();
            rows = rows.stream().sorted(comparator).toList();
        }

        Object metricValue = metric(rows, compiled.metric());
        List<ReportModels.Group> groups = groups(rows, compiled.metric(), compiled.dimension());
        int limit = intValue(definition.get("limit"), 20);
        List<Map<String, Object>> items = rows.stream().limit(limit)
                .map(row -> item(context, compiled, row)).toList();
        List<String> sourceFields = compiled.outputFields().stream()
                .map(field -> string(field.get("alias")) + "." + string(field.get("fieldCode"))).toList();
        ReportModels.QueryPlan plan = withEstimatedRows(compiled.plan(), rows.size());
        String metricDefinition = metricDefinition(compiled, rows.size());
        return new ReportModels.Result("READY", metricValue, groups, items, metricDefinition,
                sourceFields, plan.permissionFilters(), plan, versionId, versionNumber, definitionHash,
                LocalDateTime.now());
    }

    private Compiled compile(AuthenticatedContext context, Map<String, Object> definition, String traceId) {
        if (context.systemId() == null || context.tenantId() == null) {
            throw invalid("REPORT_SYSTEM_CONTEXT_REQUIRED", "结构化模块报表只能在系统租户上下文配置和执行");
        }
        if (containsArbitrarySql(definition)) {
            throw invalid("REPORT_ARBITRARY_SQL_FORBIDDEN", "数据源只接受结构化模块、字段、关系与指标，不允许任意 SQL");
        }
        List<Map<String, Object>> moduleInputs = mapList(definition.get("modules"));
        if (moduleInputs.isEmpty() || moduleInputs.size() > MAXIMUM_MODULES) {
            throw invalid("REPORT_MODULE_COUNT_INVALID", "数据源必须选择 1 到 " + MAXIMUM_MODULES + " 个模块");
        }
        List<ModuleSpec> modules = new ArrayList<>();
        Set<String> aliases = new HashSet<>();
        for (Map<String, Object> input : moduleInputs) {
            String alias = string(input.get("alias")).strip();
            String moduleCode = string(input.get("moduleCode")).strip();
            if (!alias.matches("[a-z][a-z0-9_]{0,39}") || !aliases.add(alias)) {
                throw invalid("REPORT_MODULE_ALIAS_INVALID", "模块别名必须唯一且使用小写字母、数字或下划线");
            }
            RuntimeModuleConfiguration runtime = modulePublicationService.runtime(context, moduleCode, traceId);
            LinkedHashMap<String, FieldSpec> fields = fields(runtime.configuration());
            modules.add(new ModuleSpec(alias, moduleCode,
                    runtime.configuration().path("module").path("name").asText(moduleCode),
                    runtime.versionId(), runtime.versionNumber(), fields));
        }
        Map<String, ModuleSpec> byAlias = modules.stream().collect(java.util.stream.Collectors.toMap(
                ModuleSpec::alias, module -> module, (left, right) -> left, LinkedHashMap::new));

        List<Map<String, Object>> relations = mapList(definition.get("relations"));
        validateRelations(modules, byAlias, relations);
        List<Map<String, Object>> outputs = mapList(definition.get("outputFields"));
        if (outputs.isEmpty() || outputs.size() > 30) {
            throw invalid("REPORT_OUTPUT_FIELDS_INVALID", "必须选择 1 到 30 个输出字段");
        }
        Set<String> outputKeys = new HashSet<>();
        for (Map<String, Object> output : outputs) {
            FieldSpec field = requireReadableField(byAlias, output, "REPORT_OUTPUT_FIELD_FORBIDDEN");
            String key = string(output.get("alias")) + "." + field.code();
            if (!outputKeys.add(key)) throw invalid("REPORT_OUTPUT_FIELD_DUPLICATE", "输出字段不能重复");
        }

        Map<String, Object> metric = readMapValue(definition.get("metric"));
        String operation = stringOrDefault(metric.get("operation"), "COUNT").toUpperCase(Locale.ROOT);
        if (!METRICS.contains(operation)) throw invalid("REPORT_METRIC_INVALID", "指标计算方式不受支持");
        LinkedHashMap<String, Object> normalizedMetric = new LinkedHashMap<>(metric);
        normalizedMetric.put("operation", operation);
        if (!"COUNT".equals(operation)) {
            FieldSpec field = requireReadableField(byAlias, metric, "REPORT_METRIC_FIELD_FORBIDDEN");
            if (!NUMERIC_TYPES.contains(field.fieldType().toUpperCase(Locale.ROOT))) {
                throw invalid("REPORT_METRIC_FIELD_TYPE_INVALID", "SUM、AVG、MIN、MAX 只能使用数值字段");
            }
        }

        Map<String, Object> dimension = readMapValue(definition.get("dimension"));
        LinkedHashMap<String, Object> normalizedDimension = new LinkedHashMap<>(dimension);
        if (!dimension.isEmpty()) {
            String type = stringOrDefault(dimension.get("type"), "FIELD").toUpperCase(Locale.ROOT);
            if (!DIMENSIONS.contains(type)) throw invalid("REPORT_DIMENSION_INVALID", "统计维度不受支持");
            normalizedDimension.put("type", type);
            FieldSpec field = requireReadableField(byAlias, dimension, "REPORT_DIMENSION_FIELD_FORBIDDEN");
            validateDimensionType(type, field);
        }

        List<Map<String, Object>> filters = mapList(definition.get("filters"));
        if (filters.size() > 10) throw invalid("REPORT_FILTER_COUNT_INVALID", "固定筛选最多支持 10 项");
        for (Map<String, Object> filter : filters) {
            FieldSpec field = requireReadableField(byAlias, filter, "REPORT_FILTER_FIELD_FORBIDDEN");
            String operator = string(filter.get("operator")).toUpperCase(Locale.ROOT);
            if (!OPERATORS.contains(operator)) throw invalid("REPORT_FILTER_OPERATOR_INVALID", "筛选运算符不受支持");
            if (!field.indexed()) throw invalid("REPORT_QUERY_NOT_INDEXABLE", "筛选字段未启用检索或索引：" + field.code());
        }
        Map<String, Object> sort = readMapValue(definition.get("sort"));
        LinkedHashMap<String, Object> normalizedSort = new LinkedHashMap<>(sort);
        if (!sort.isEmpty()) {
            FieldSpec field = requireReadableField(byAlias, sort, "REPORT_SORT_FIELD_FORBIDDEN");
            if (!field.indexed()) throw invalid("REPORT_QUERY_NOT_INDEXABLE", "排序字段未启用检索或索引：" + field.code());
            String direction = stringOrDefault(sort.get("direction"), "DESC").toUpperCase(Locale.ROOT);
            if (!Set.of("ASC", "DESC").contains(direction)) throw invalid("REPORT_SORT_INVALID", "排序方向不受支持");
            normalizedSort.put("direction", direction);
        }
        Map<String, Object> time = readMapValue(definition.get("timeField"));
        if (!time.isEmpty()) {
            FieldSpec field = requireReadableField(byAlias, time, "REPORT_TIME_FIELD_FORBIDDEN");
            if (!Set.of("DATE", "DATETIME").contains(field.fieldType().toUpperCase(Locale.ROOT))) {
                throw invalid("REPORT_TIME_FIELD_TYPE_INVALID", "时间范围字段必须是日期或日期时间");
            }
            if (!field.indexed()) throw invalid("REPORT_QUERY_NOT_INDEXABLE", "时间字段未启用检索或索引：" + field.code());
        }
        int maximumRows = intValue(definition.get("maxScanRows"), 500);
        if (maximumRows < 1 || maximumRows > MAXIMUM_SCAN_ROWS) {
            throw invalid("REPORT_QUERY_COST_LIMIT_INVALID", "扫描成本上限必须在 1 到 " + MAXIMUM_SCAN_ROWS + " 之间");
        }
        int limit = intValue(definition.get("limit"), 20);
        if (limit < 1 || limit > 50) throw invalid("REPORT_RESULT_LIMIT_INVALID", "样例与下钻条数必须在 1 到 50 之间");

        List<String> permissionFilters = modules.stream().flatMap(module -> List.of(
                module.moduleCode() + "：先执行 MODULE/LIST、系统、租户、共享与数据范围过滤",
                module.moduleCode() + "：仅保留当前请求可读且未脱敏的输出/筛选/聚合字段").stream()).toList();
        List<String> steps = new ArrayList<>();
        steps.add("读取每个模块当前发布配置并校验字段类型、可读性与可索引性");
        steps.add("分别通过运行态 LIST 查询执行系统、租户、共享、数据范围和字段权限");
        if (!relations.isEmpty()) steps.add("仅在已授权记录之间按声明的关系树进行有界关联");
        steps.add("在权限过滤完成后执行 " + operation + " 指标与可选维度聚合");
        steps.add("只返回显式输出字段和主模块授权记录下钻路径");
        List<Map<String, Object>> planModules = modules.stream().map(module -> Map.<String, Object>of(
                "alias", module.alias(), "moduleCode", module.moduleCode(), "moduleName", module.moduleName(),
                "moduleVersionId", module.versionId(), "moduleVersionNumber", module.versionNumber())).toList();
        List<String> outputNames = outputs.stream().map(output ->
                string(output.get("alias")) + "." + string(output.get("fieldCode"))).toList();
        ReportModels.QueryPlan plan = new ReportModels.QueryPlan(
                modules.size() == 1 ? "SINGLE_MODULE" : "MULTI_MODULE", planModules, relations,
                outputNames, normalizedMetric, normalizedDimension, filters, normalizedSort, time,
                0, maximumRows, steps, permissionFilters, false);
        return new Compiled(modules, relations, outputs, normalizedMetric, normalizedDimension,
                filters, normalizedSort, time, maximumRows, plan);
    }

    private void validateRelations(
            List<ModuleSpec> modules, Map<String, ModuleSpec> byAlias, List<Map<String, Object>> relations) {
        if (modules.size() == 1 && !relations.isEmpty()) {
            throw invalid("REPORT_RELATION_INVALID", "单模块数据源不能配置跨模块关系");
        }
        if (modules.size() > 1 && relations.size() != modules.size() - 1) {
            throw invalid("REPORT_RELATION_AMBIGUOUS", "多模块必须使用无歧义关系树，每个附加模块恰好一条关系");
        }
        Set<String> connected = new LinkedHashSet<>();
        connected.add(modules.getFirst().alias());
        Set<String> pairs = new HashSet<>();
        for (int index = 0; index < relations.size(); index++) {
            Map<String, Object> relation = relations.get(index);
            String left = string(relation.get("leftAlias"));
            String right = string(relation.get("rightAlias"));
            String type = string(relation.get("relationType")).toUpperCase(Locale.ROOT);
            String joinType = stringOrDefault(relation.get("joinType"), "INNER").toUpperCase(Locale.ROOT);
            if (!RELATIONS.contains(type) || !Set.of("INNER", "LEFT").contains(joinType)) {
                throw invalid("REPORT_RELATION_INVALID", "关系类型或连接方式不受支持");
            }
            if (!connected.contains(left) || connected.contains(right)
                    || !right.equals(modules.get(index + 1).alias())) {
                throw invalid("REPORT_RELATION_AMBIGUOUS", "关系必须按模块顺序形成单向、无环、无歧义关系树");
            }
            String pair = left + "->" + right;
            if (!pairs.add(pair)) throw invalid("REPORT_RELATION_AMBIGUOUS", "同一模块对存在重复关系");
            FieldSpec leftField = requireReadableField(byAlias,
                    fieldReference(left, relation.get("leftField")), "REPORT_RELATION_FIELD_FORBIDDEN");
            FieldSpec rightField = requireReadableField(byAlias,
                    fieldReference(right, relation.get("rightField")), "REPORT_RELATION_FIELD_FORBIDDEN");
            if (!leftField.indexed() || !rightField.indexed()) {
                throw invalid("REPORT_QUERY_NOT_INDEXABLE", "关联两侧字段必须可检索或有索引");
            }
            connected.add(right);
        }
    }

    private void validateDimensionType(String type, FieldSpec field) {
        String fieldType = field.fieldType().toUpperCase(Locale.ROOT);
        if ("TIME".equals(type) && !Set.of("DATE", "DATETIME").contains(fieldType)) {
            throw invalid("REPORT_DIMENSION_FIELD_TYPE_INVALID", "时间维度必须选择日期或日期时间字段");
        }
        if ("PERSON".equals(type) && !Set.of("PERSON", "USER", "LONG", "INTEGER").contains(fieldType)) {
            throw invalid("REPORT_DIMENSION_FIELD_TYPE_INVALID", "人员维度必须选择人员字段");
        }
        if ("DEPARTMENT".equals(type) && !Set.of("DEPARTMENT", "LONG", "INTEGER").contains(fieldType)) {
            throw invalid("REPORT_DIMENSION_FIELD_TYPE_INVALID", "部门维度必须选择部门字段");
        }
        if ("STATUS".equals(type) && !Set.of("STATUS", "SELECT", "TEXT").contains(fieldType)) {
            throw invalid("REPORT_DIMENSION_FIELD_TYPE_INVALID", "状态维度必须选择状态或选项字段");
        }
    }

    private List<JoinedRow> join(
            List<JoinedRow> rows, List<RuntimeRecordView> targets, Map<String, Object> relation) {
        String leftAlias = string(relation.get("leftAlias"));
        String rightAlias = string(relation.get("rightAlias"));
        String leftField = string(relation.get("leftField"));
        String rightField = string(relation.get("rightField"));
        boolean leftJoin = "LEFT".equals(stringOrDefault(relation.get("joinType"), "INNER").toUpperCase(Locale.ROOT));
        List<JoinedRow> joined = new ArrayList<>();
        for (JoinedRow row : rows) {
            Object leftValue = value(row.records().get(leftAlias), leftField);
            List<RuntimeRecordView> matches = targets.stream()
                    .filter(target -> equalValue(leftValue, value(target, rightField))).toList();
            if (matches.isEmpty() && leftJoin) {
                LinkedHashMap<String, RuntimeRecordView> copy = new LinkedHashMap<>(row.records());
                copy.put(rightAlias, null);
                joined.add(new JoinedRow(copy));
            } else {
                for (RuntimeRecordView target : matches) {
                    LinkedHashMap<String, RuntimeRecordView> copy = new LinkedHashMap<>(row.records());
                    copy.put(rightAlias, target);
                    joined.add(new JoinedRow(copy));
                }
            }
        }
        return joined;
    }

    private List<ReportModels.Group> groups(
            List<JoinedRow> rows, Map<String, Object> metric, Map<String, Object> dimension) {
        if (dimension.isEmpty()) return List.of();
        String alias = string(dimension.get("alias"));
        String field = string(dimension.get("fieldCode"));
        String type = string(dimension.get("type"));
        String interval = stringOrDefault(dimension.get("interval"), "DAY").toUpperCase(Locale.ROOT);
        LinkedHashMap<Object, List<JoinedRow>> grouped = new LinkedHashMap<>();
        for (JoinedRow row : rows) {
            Object key = value(row.records().get(alias), field);
            if ("TIME".equals(type)) key = timeBucket(key, interval);
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
        }
        return grouped.entrySet().stream().map(entry -> new ReportModels.Group(entry.getKey(),
                entry.getKey() == null ? "未设置" : String.valueOf(entry.getKey()),
                metric(entry.getValue(), metric), entry.getValue().size())).toList();
    }

    private Object metric(List<JoinedRow> rows, Map<String, Object> metric) {
        String operation = stringOrDefault(metric.get("operation"), "COUNT").toUpperCase(Locale.ROOT);
        if ("COUNT".equals(operation)) return (long) rows.size();
        String alias = string(metric.get("alias"));
        String field = string(metric.get("fieldCode"));
        List<BigDecimal> values = rows.stream().map(row -> number(value(row.records().get(alias), field)))
                .filter(Objects::nonNull).toList();
        if (values.isEmpty()) return null;
        if ("SUM".equals(operation)) return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if ("AVG".equals(operation)) return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 6, RoundingMode.HALF_UP).stripTrailingZeros();
        if ("MIN".equals(operation)) return values.stream().min(BigDecimal::compareTo).orElse(null);
        if ("MAX".equals(operation)) return values.stream().max(BigDecimal::compareTo).orElse(null);
        return null;
    }

    private Map<String, Object> item(AuthenticatedContext context, Compiled compiled, JoinedRow row) {
        RuntimeRecordView primary = row.records().get(compiled.modules().getFirst().alias());
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (Map<String, Object> output : compiled.outputFields()) {
            String alias = string(output.get("alias"));
            String fieldCode = string(output.get("fieldCode"));
            values.put(alias + "." + fieldCode, value(row.records().get(alias), fieldCode));
        }
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("id", primary == null ? null : primary.id());
        item.put("title", primary == null ? "关联记录不存在" : primary.title());
        item.put("subtitle", primary == null ? null : primary.recordNumber());
        item.put("status", primary == null ? null : primary.status());
        item.put("fields", values);
        item.put("route", primary == null ? null : "/systems/" + context.systemId()
                + "?workspace=runtime&module=" + compiled.modules().getFirst().moduleCode()
                + "&recordId=" + primary.id());
        return item;
    }

    private Object value(RuntimeRecordView record, String fieldCode) {
        if (record == null) return null;
        return switch (fieldCode) {
            case "id" -> record.id();
            case "title" -> record.title();
            case "recordNumber" -> record.recordNumber();
            case "status" -> record.status();
            case "ownerMemberId" -> record.ownerMemberId();
            case "departmentId" -> record.departmentId();
            case "createdAt" -> record.createdAt();
            case "updatedAt" -> record.updatedAt();
            case "dataTenantName" -> record.dataTenantName();
            default -> jsonValue(record.fields().get(fieldCode));
        };
    }

    private Object jsonValue(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isIntegralNumber()) return value.longValue();
        if (value.isFloatingPointNumber()) return value.decimalValue();
        if (value.isBoolean()) return value.booleanValue();
        if (value.isTextual()) return value.textValue();
        return objectMapper.convertValue(value, Object.class);
    }

    private Object timeBucket(Object value, String interval) {
        if (value == null) return null;
        String text = String.valueOf(value);
        try {
            LocalDate date = text.length() >= 10 ? LocalDate.parse(text.substring(0, 10))
                    : LocalDateTime.parse(text).toLocalDate();
            return switch (interval) {
                case "YEAR" -> String.valueOf(date.getYear());
                case "MONTH" -> "%04d-%02d".formatted(date.getYear(), date.getMonthValue());
                default -> date.toString();
            };
        } catch (RuntimeException exception) {
            return text;
        }
    }

    private String metricDefinition(Compiled compiled, int joinedRows) {
        String operation = string(compiled.metric().get("operation"));
        String metricField = "COUNT".equals(operation) ? "授权关联行"
                : string(compiled.metric().get("alias")) + "." + string(compiled.metric().get("fieldCode"));
        String dimension = compiled.dimension().isEmpty() ? "无分组"
                : string(compiled.dimension().get("type")) + " "
                + string(compiled.dimension().get("alias")) + "." + string(compiled.dimension().get("fieldCode"));
        return operation + "(" + metricField + ")，维度=" + dimension + "；在系统、租户、共享、数据范围和字段权限"
                + "全部过滤后，对 " + joinedRows + " 行授权结果聚合。";
    }

    private ReportModels.ModuleMetadata moduleMetadata(
            AuthenticatedContext context, RuntimeModuleCatalogItem item, String traceId) {
        RuntimeModuleConfiguration runtime = modulePublicationService.runtime(context, item.moduleCode(), traceId);
        return new ReportModels.ModuleMetadata(runtime.moduleId(), item.moduleCode(), item.moduleName(),
                runtime.versionId(), runtime.versionNumber(), fields(runtime.configuration()).values().stream()
                .map(field -> new ReportModels.FieldMetadata(field.code(), field.name(), field.fieldType(),
                        field.searchable(), field.readable(), field.indexed(), field.builtIn())).toList());
    }

    private LinkedHashMap<String, FieldSpec> fields(JsonNode configuration) {
        LinkedHashMap<String, FieldSpec> result = new LinkedHashMap<>();
        addBuiltIn(result, "id", "记录 ID", "LONG", true);
        addBuiltIn(result, "title", "标题", "TEXT", true);
        addBuiltIn(result, "recordNumber", "记录编号", "TEXT", true);
        addBuiltIn(result, "status", "状态", "STATUS", true);
        addBuiltIn(result, "ownerMemberId", "负责人", "PERSON", true);
        addBuiltIn(result, "departmentId", "部门", "DEPARTMENT", true);
        addBuiltIn(result, "createdAt", "创建时间", "DATETIME", true);
        addBuiltIn(result, "updatedAt", "更新时间", "DATETIME", true);
        addBuiltIn(result, "dataTenantName", "数据租户", "TEXT", true);
        configuration.path("fields").forEach(field -> {
            String code = field.path("code").asText();
            JsonNode access = field.path("access");
            boolean readable = access.path("readable").asBoolean(false)
                    && access.path("maskStrategy").asText("").isBlank();
            boolean searchable = field.path("searchable").asBoolean(false);
            result.put(code, new FieldSpec(code, field.path("name").asText(code),
                    field.path("fieldType").asText("UNKNOWN"), searchable, readable, searchable, false));
        });
        return result;
    }

    private void addBuiltIn(
            LinkedHashMap<String, FieldSpec> fields, String code, String name, String type, boolean indexed) {
        fields.put(code, new FieldSpec(code, name, type, true, true, indexed, true));
    }

    private FieldSpec requireReadableField(
            Map<String, ModuleSpec> modules, Map<String, Object> reference, String errorCode) {
        String alias = string(reference.get("alias"));
        String fieldCode = string(reference.get("fieldCode"));
        ModuleSpec module = modules.get(alias);
        FieldSpec field = module == null ? null : module.fields().get(fieldCode);
        if (field == null || !field.readable()) {
            throw forbidden(errorCode, "字段不存在、不可读或已脱敏：" + alias + "." + fieldCode);
        }
        return field;
    }

    private Map<String, Object> filterForRuntime(Map<String, Object> filter) {
        return Map.of("fieldCode", string(filter.get("fieldCode")),
                "operator", string(filter.get("operator")).toUpperCase(Locale.ROOT),
                "value", stringOrDefault(filter.get("value"), ""));
    }

    private Map<String, Object> fieldReference(String alias, Object fieldCode) {
        LinkedHashMap<String, Object> reference = new LinkedHashMap<>();
        reference.put("alias", alias);
        reference.put("fieldCode", fieldCode);
        return reference;
    }

    private List<Map<String, Object>> append(
            List<Map<String, Object>> values, Map<String, Object> value) {
        ArrayList<Map<String, Object>> result = new ArrayList<>(values);
        result.add(value);
        return result;
    }

    private ReportModels.QueryPlan withEstimatedRows(ReportModels.QueryPlan plan, long rows) {
        return new ReportModels.QueryPlan(plan.mode(), plan.modules(), plan.relations(), plan.outputFields(),
                plan.metric(), plan.dimension(), plan.fixedFilters(), plan.sort(), plan.timeField(), rows,
                plan.maximumScanRows(), plan.steps(), plan.permissionFilters(), false);
    }

    private boolean containsArbitrarySql(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                if (Set.of("sql", "rawsql", "sqltext", "statement").contains(key)) return true;
                if (containsArbitrarySql(entry.getValue())) return true;
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) if (containsArbitrarySql(item)) return true;
        }
        return false;
    }

    private void requireSnapshotContext(AuthenticatedContext context, Map<String, Object> snapshot) {
        if (!"SYSTEM".equals(string(snapshot.get("contextType")))
                || !Objects.equals(context.platformId(), longValue(snapshot.get("platformId")))
                || !Objects.equals(context.systemId(), longValue(snapshot.get("systemId")))
                || !Objects.equals(context.tenantId(), longValue(snapshot.get("ownerTenantId")))) {
            throw forbidden("REPORT_SOURCE_CONTEXT_MISMATCH", "数据源版本不属于当前系统租户上下文");
        }
    }

    private boolean allowsPolicy(AuthenticatedContext context, Map<String, Object> policy) {
        if (policy.isEmpty()) return true;
        String resourceType = stringOrNull(policy.get("resourceType"));
        String resourceCode = stringOrNull(policy.get("resourceCode"));
        String actionCode = stringOrNull(policy.get("actionCode"));
        return resourceType == null || resourceCode == null || actionCode == null
                || permissionChecker.allows(context, resourceType, resourceCode, actionCode);
    }

    private void requireSystemAdmin(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null
                || !permissionChecker.allows(context, "CONFIG", "SYSTEM", "MANAGE")) {
            throw forbidden("REPORT_ADMIN_DENIED", "当前系统租户上下文没有数据源配置权限");
        }
    }

    private AnaDataSource requireSource(AuthenticatedContext context, Long sourceId) {
        AnaDataSource source = sourceService.selectById(sourceId);
        if (source == null || !"SYSTEM".equals(source.getContextType())
                || !Objects.equals(context.platformId(), source.getPlatformId())
                || !Objects.equals(context.systemId(), source.getSystemId())
                || !Objects.equals(context.tenantId(), source.getOwnerTenantId())) {
            throw new DomainException("REPORT_SOURCE_NOT_FOUND", "数据源不存在或不属于当前系统租户",
                    HttpStatus.NOT_FOUND);
        }
        return source;
    }

    private AnaDataSourceVersion latestVersion(Long sourceId) {
        return sourceVersionService.selectList(Wrappers.<AnaDataSourceVersion>lambdaQuery()
                        .eq(AnaDataSourceVersion::getDataSourceId, sourceId)
                        .orderByDesc(AnaDataSourceVersion::getVersionNumber))
                .stream().findFirst().orElse(null);
    }

    private void audit(AuthenticatedContext context, String traceId, String event, Long sourceId,
                       Map<String, Object> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                event, "ANA_DATA_SOURCE", sourceId.toString(), "SUCCESS", detail);
    }

    private boolean equalValue(Object left, Object right) {
        if (left == null || right == null) return false;
        BigDecimal leftNumber = number(left);
        BigDecimal rightNumber = number(right);
        if (leftNumber != null && rightNumber != null) return leftNumber.compareTo(rightNumber) == 0;
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private BigDecimal number(Object value) {
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        if (value == null) return null;
        try { return new BigDecimal(String.valueOf(value)); }
        catch (NumberFormatException exception) { return null; }
    }

    private String comparable(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, MAP_TYPE); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot read report JSON", exception); }
    }

    private Map<String, Object> readMapValue(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) return objectMapper.convertValue(map, MAP_TYPE);
        if (value instanceof String string) return readMap(string);
        return Map.of();
    }

    private List<Map<String, Object>> mapList(Object value) {
        if (value == null) return List.of();
        try { return objectMapper.convertValue(value, MAP_LIST_TYPE); }
        catch (IllegalArgumentException exception) { throw invalid("REPORT_DEFINITION_INVALID", "结构化配置列表格式无效"); }
    }

    private String writeJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Cannot write report JSON", exception); }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stringOrNull(Object value) {
        String result = string(value).strip();
        return result.isBlank() ? null : result;
    }

    private String stringOrDefault(Object value, String defaultValue) {
        String result = stringOrNull(value);
        return result == null ? defaultValue : result;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { return null; }
    }

    private Integer integerOrNull(Object value) {
        if (value instanceof Number number) return number.intValue();
        if (value == null || String.valueOf(value).isBlank()) return null;
        try { return Integer.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { return null; }
    }

    private int intValue(Object value, int fallback) {
        Integer parsed = integerOrNull(value);
        return parsed == null ? fallback : parsed;
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }

    private record FieldSpec(String code, String name, String fieldType, boolean searchable,
                             boolean readable, boolean indexed, boolean builtIn) {
    }

    private record ModuleSpec(String alias, String moduleCode, String moduleName,
                              Long versionId, Integer versionNumber,
                              LinkedHashMap<String, FieldSpec> fields) {
    }

    private record Compiled(List<ModuleSpec> modules, List<Map<String, Object>> relations,
                            List<Map<String, Object>> outputFields, Map<String, Object> metric,
                            Map<String, Object> dimension, List<Map<String, Object>> filters,
                            Map<String, Object> sort, Map<String, Object> timeField,
                            int maximumScanRows, ReportModels.QueryPlan plan) {
    }

    private record JoinedRow(LinkedHashMap<String, RuntimeRecordView> records) {
    }
}
