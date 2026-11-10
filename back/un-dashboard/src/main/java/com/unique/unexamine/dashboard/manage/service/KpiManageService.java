package com.unique.unexamine.dashboard.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.dashboard.base.entity.UnKpi;
import com.unique.unexamine.dashboard.base.entity.UnKpiResult;
import com.unique.unexamine.dashboard.base.entity.UnKpiUser;
import com.unique.unexamine.dashboard.base.entity.UnDataSource;
import com.unique.unexamine.dashboard.base.entity.UnDataSourceConfig;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceConfigService;
import com.unique.unexamine.dashboard.base.service.IUnDataSourceService;
import com.unique.unexamine.dashboard.base.service.IUnKpiResultService;
import com.unique.unexamine.dashboard.base.service.IUnKpiService;
import com.unique.unexamine.dashboard.base.service.IUnKpiUserService;
import com.unique.unexamine.dashboard.manage.dto.DashboardRuntimeDto.ModuleDataView;
import com.unique.unexamine.dashboard.manage.dto.DashboardRuntimeDto.RowView;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.KpiView;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.RuntimeView;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.ResultView;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.SaveRequest;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.TargetRequest;
import com.unique.unexamine.dashboard.manage.dto.KpiManageDto.TargetView;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysMemberRole;
import com.unique.unexamine.sys.base.entity.UnSysRole;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysMemberRoleService;
import com.unique.unexamine.sys.base.service.IUnSysRoleService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** KPI 配置、目标和运行计算服务；数据读取仍由模块权限查询链统一执行。 */
@Service
public class KpiManageService {
    private static final List<String> AGGREGATIONS = List.of("COUNT", "SUM", "AVG", "MAX", "MIN");
    private static final List<String> PERIODS = List.of("MONTH", "QUARTER", "YEAR");

    private final IUnKpiService kpis;
    private final IUnKpiUserService targets;
    private final IUnKpiResultService results;
    private final IUnDataSourceService dataSources;
    private final IUnDataSourceConfigService dataSourceConfigs;
    private final IUnPlatAccountService accounts;
    private final IUnSysMemberService members;
    private final IUnSysMemberRoleService memberRoles;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysDepartmentService departments;
    private final IUnSysRoleService roles;
    private final IUnSysLogOptService logs;
    private final SystemManageService systems;
    private final ModuleConfigService modules;
    private final DashboardRuntimeService runtime;
    private final IUnModuleRecordService records;
    private final ObjectMapper json;

    /** 注入 KPI 基础服务、模块配置和统一数据读取入口。 */
    public KpiManageService(IUnKpiService kpis, IUnKpiUserService targets,
            IUnKpiResultService results, IUnDataSourceService dataSources,
            IUnDataSourceConfigService dataSourceConfigs,
            IUnPlatAccountService accounts, IUnSysMemberService members,
            IUnSysMemberRoleService memberRoles,
            IUnSysTenantMemberService tenantMembers, IUnSysDepartmentService departments,
            IUnSysRoleService roles, IUnSysLogOptService logs,
            SystemManageService systems, ModuleConfigService modules,
            DashboardRuntimeService runtime, IUnModuleRecordService records, ObjectMapper json) {
        this.kpis = kpis;
        this.targets = targets;
        this.results = results;
        this.dataSources = dataSources;
        this.dataSourceConfigs = dataSourceConfigs;
        this.accounts = accounts;
        this.members = members;
        this.memberRoles = memberRoles;
        this.tenantMembers = tenantMembers;
        this.departments = departments;
        this.roles = roles;
        this.logs = logs;
        this.systems = systems;
        this.modules = modules;
        this.runtime = runtime;
        this.records = records;
        this.json = json;
    }

    /** 查询当前租户的 KPI 配置。 */
    public KpiView query(String token, Long systemId, Long tenantId, Long kpiId) {
        return view(owned(systems.requireAdminContext(token, systemId, tenantId), kpiId));
    }

    /** 列出当前系统租户的 KPI 配置，供管理员进入口径、目标和发布管理。 */
    public List<KpiView> list(String token, Long systemId, Long tenantId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        return kpis.list(new LambdaQueryWrapper<UnKpi>()
                        .eq(UnKpi::getSystemId, context.systemId())
                        .eq(UnKpi::getTenantId, context.tenantId())
                        .orderByDesc(UnKpi::getUpdateTime).orderByDesc(UnKpi::getId))
                .stream().map(this::view).toList();
    }

    /** 管理员按周期查看目标及最近一次计算值，目标始终归属于当前系统租户的 KPI。 */
    public Page<TargetView> targets(String token, Long systemId, Long tenantId, Long kpiId,
                                    String periodKey, SearchBO search) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnKpi kpi = owned(context, kpiId);
        if (periodKey != null && !periodKey.isBlank()) validatePeriodKey(kpi.getPeriodType(), periodKey);
        Page<UnKpiUser> page = targets.page(search.parse(), new LambdaQueryWrapper<UnKpiUser>()
                .eq(UnKpiUser::getKpiId, kpiId)
                .eq(periodKey != null && !periodKey.isBlank(), UnKpiUser::getPeriodKey, periodKey)
                .orderByDesc(UnKpiUser::getUpdateTime).orderByDesc(UnKpiUser::getId));
        return new Page<TargetView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::targetView).toList());
    }

    /** 按当前系统租户读取计算历史；结果快照保留当时的指标口径和目标值。 */
    public Page<ResultView> results(String token, Long systemId, Long tenantId, Long kpiId,
                                    String periodKey, SearchBO search) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnKpi kpi = owned(context, kpiId);
        if (periodKey != null && !periodKey.isBlank()) validatePeriodKey(kpi.getPeriodType(), periodKey);
        Page<UnKpiResult> page = results.page(search.parse(), new LambdaQueryWrapper<UnKpiResult>()
                .eq(UnKpiResult::getKpiId, kpiId)
                .eq(periodKey != null && !periodKey.isBlank(), UnKpiResult::getPeriodKey, periodKey)
                .orderByDesc(UnKpiResult::getCalculationTime).orderByDesc(UnKpiResult::getId));
        Page<ResultView> view = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        view.setRecords(page.getRecords().stream().map(result -> new ResultView(result.getId(),
                result.getKpiId(), result.getPeriodKey(), result.getKpiUserId(), result.getKpiVersion(),
                result.getActualValue(), readConfig(result.getSourceJson()), result.getCalculationTime())).toList());
        return view;
    }

    /** 保存 KPI 草稿；修改后需要显式发布才影响运行态。 */
    @Transactional(rollbackFor = Exception.class)
    public KpiView save(String token, Long systemId, Long tenantId, SaveRequest request) {
        if (request == null || request.code() == null || request.code().isBlank() || request.code().length() > 80
                || request.name() == null || request.name().isBlank() || request.name().length() > 120)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 资料无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        validateSource(request.moduleId(), request.dataSourceId());
        validateMeasure(request.aggregation(), request.measureFieldId());
        validatePeriod(request.periodType());
        UnKpi kpi = request.id() == null ? new UnKpi() : ownedForUpdate(context, request.id());
        boolean existing = kpi.getId() != null;
        if (kpi.getId() == null) {
            kpi.setSystemId(context.systemId());
            kpi.setTenantId(context.tenantId());
            kpi.setPublishedVersion(0);
            kpi.setCreateTime(LocalDateTime.now());
        }
        kpi.setCode(request.code().trim());
        kpi.setName(request.name().trim());
        kpi.setDataSourceId(request.dataSourceId());
        kpi.setModuleId(request.moduleId());
        kpi.setMeasureFieldId(request.measureFieldId());
        kpi.setAggregation(request.aggregation().trim().toUpperCase());
        kpi.setPeriodType(request.periodType().trim().toUpperCase());
        kpi.setConfigJson(writeConfig(request.config()));
        kpi.setStatus("DRAFT");
        kpi.setUpdateTime(LocalDateTime.now());
        kpis.addOrUpdate(kpi);
        if (existing && (kpi.getDataSourceId() == null || kpi.getModuleId() == null
                || kpi.getMeasureFieldId() == null)) {
            // 数据来源互斥；COUNT 也允许清空原度量字段，基础更新不会自动写入 null。
            var clear = kpis.lambdaUpdate().eq(UnKpi::getId, kpi.getId());
            if (kpi.getDataSourceId() == null) clear.set(UnKpi::getDataSourceId, null);
            if (kpi.getModuleId() == null) clear.set(UnKpi::getModuleId, null);
            if (kpi.getMeasureFieldId() == null) clear.set(UnKpi::getMeasureFieldId, null);
            clear.update();
        }
        audit(context, "KPI_SAVE", kpi.getId());
        return view(kpi);
    }

    /** 发布 KPI 统计口径；发布版本由运行结果保存并解释。 */
    @Transactional(rollbackFor = Exception.class)
    public KpiView publish(String token, Long systemId, Long tenantId, Long kpiId) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnKpi kpi = ownedForUpdate(context, kpiId);
        validateSource(kpi.getModuleId(), kpi.getDataSourceId());
        validateMeasure(kpi.getAggregation(), kpi.getMeasureFieldId());
        validatePeriod(kpi.getPeriodType());
        if (kpi.getModuleId() != null) {
            modules.activeModule(context.systemId(), kpi.getModuleId(), context.tenantId());
            validateField(kpi.getModuleId(), kpi.getMeasureFieldId(), kpi.getAggregation());
            periodField(kpi.getModuleId(), readConfig(kpi.getConfigJson()));
        } else {
            UnDataSource source = dataSources.queryById(kpi.getDataSourceId());
            if (source == null || !"PUBLISHED".equals(source.getStatus())
                    || !context.systemId().equals(source.getSystemId())
                    || source.getTenantId() != null && !context.tenantId().equals(source.getTenantId())) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "KPI 数据源未发布或不属于当前租户");
            }
            if (source.getRootModuleId() == null)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "KPI 数据源未配置起始模块");
            modules.activeModule(context.systemId(), source.getRootModuleId(), context.tenantId());
            validateField(source.getRootModuleId(), kpi.getMeasureFieldId(), kpi.getAggregation());
            periodField(source.getRootModuleId(), readConfig(kpi.getConfigJson()));
        }
        JsonNode config = readConfig(kpi.getConfigJson());
        kpi.setPublishedConfigJson(write(config));
        kpi.setPublishedVersion((kpi.getPublishedVersion() == null ? 0 : kpi.getPublishedVersion()) + 1);
        kpi.setStatus("PUBLISHED");
        kpi.setUpdateTime(LocalDateTime.now());
        kpis.addOrUpdate(kpi);
        audit(context, "KPI_PUBLISH", kpiId);
        return view(kpi);
    }

    /** 保存 KPI 周期目标；目标与指标版本分开保存，便于调整下一周期。 */
    @Transactional(rollbackFor = Exception.class)
    public TargetView saveTarget(String token, Long systemId, Long tenantId, Long kpiId,
                                 TargetRequest request) {
        if (request == null || request.targetType() == null || request.targetType().isBlank()
                || request.targetId() == null || request.targetId() < 1
                || request.periodKey() == null || request.periodKey().isBlank()
                || request.targetValue() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 目标资料无效");
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnKpi kpi = ownedForUpdate(context, kpiId);
        String targetType = request.targetType().trim().toUpperCase();
        if (!List.of("ACCOUNT", "MEMBER", "TENANT_MEMBER", "DEPARTMENT", "ROLE").contains(targetType)) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 目标类型无效");
        }
        validateTarget(context, targetType, request.targetId());
        validatePeriodKey(kpi.getPeriodType(), request.periodKey());
        UnKpiUser target = request.id() == null ? new UnKpiUser() : targets.queryById(request.id());
        if (request.id() != null && target == null) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "KPI 目标不存在");
        }
        if (target.getId() != null && !kpiId.equals(target.getKpiId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "目标不属于当前 KPI");
        }
        String previousType = target.getTargetType();
        Long previousId = target.getTargetId();
        String previousPeriod = target.getPeriodKey();
        target.setKpiId(kpiId);
        target.setTargetType(targetType);
        target.setTargetId(request.targetId());
        target.setPeriodKey(request.periodKey().trim());
        target.setTargetValue(request.targetValue());
        target.setStatus(Boolean.FALSE.equals(request.active()) ? "DISABLED" : "ACTIVE");
        // 更换目标主体或周期后，原实际值和计算口径已不属于当前目标。
        boolean identityChanged = target.getId() != null && (!targetType.equals(previousType)
                || !request.targetId().equals(previousId)
                || !request.periodKey().trim().equals(previousPeriod));
        if (identityChanged) {
            target.setActualValue(null);
            target.setCalculatedTime(null);
            target.setCalculationJson(null);
        }
        target.setUpdateTime(LocalDateTime.now());
        targets.addOrUpdate(target);
        if (identityChanged) {
            // MyBatis-Plus 默认跳过 null 更新，明确清除数据库中的旧周期计算结果。
            targets.lambdaUpdate().eq(UnKpiUser::getId, target.getId())
                    .set(UnKpiUser::getActualValue, null)
                    .set(UnKpiUser::getCalculatedTime, null)
                    .set(UnKpiUser::getCalculationJson, null).update();
        }
        audit(context, "KPI_TARGET_SAVE", target.getId());
        return targetView(target);
    }

    /** 计算一个周期的 KPI，并更新当前租户中该周期的有效目标及历史结果。 */
    @Transactional(rollbackFor = Exception.class)
    public RuntimeView calculate(String token, Long systemId, Long tenantId, Long kpiId,
                                 String periodKey) {
        SystemContext context = systems.requireAdminContext(token, systemId, tenantId);
        UnKpi kpi = ownedForUpdate(context, kpiId);
        if (!"PUBLISHED".equals(kpi.getStatus()) || kpi.getPublishedVersion() == null
                || kpi.getPublishedVersion() < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "KPI 尚未发布");
        }
        validatePeriodKey(kpi.getPeriodType(), periodKey);
        List<UnKpiUser> current = targets.list(new LambdaQueryWrapper<UnKpiUser>()
                .eq(UnKpiUser::getKpiId, kpiId).eq(UnKpiUser::getPeriodKey, periodKey)
                .eq(UnKpiUser::getStatus, "ACTIVE"));
        for (UnKpiUser target : current)
            validateTarget(context, target.getTargetType(), target.getTargetId());
        PeriodWindow window = periodWindow(kpi.getPeriodType(), periodKey);
        ObjectNode source = sourceSummary(kpi);
        KpiCalculation calculation = calculateValues(token, context, kpi, current, window);
        LocalDateTime now = LocalDateTime.now();
        source.put("periodKey", periodKey);
        source.put("periodFromTime", window.fromTime().toString());
        source.put("periodToTime", window.toTime().toString());
        if (calculation.periodFieldId() != null) source.put("periodFieldId", calculation.periodFieldId());
        source.put("matchedRecordCount", calculation.overall().recordCount);
        source.put("scope", "TENANT");
        UnKpiResult overallResult = new UnKpiResult();
        overallResult.setKpiId(kpiId);
        overallResult.setPeriodKey(periodKey);
        overallResult.setKpiVersion(kpi.getPublishedVersion());
        overallResult.setActualValue(calculation.overall().value(kpi.getAggregation()));
        overallResult.setSourceJson(write(source));
        overallResult.setCalculationTime(now);
        results.addOrUpdate(overallResult);
        List<TargetView> views = new ArrayList<>();
        for (UnKpiUser target : current) {
            Aggregate targetAggregate = calculation.targets().get(target.getId());
            BigDecimal actual = targetAggregate.value(kpi.getAggregation());
            ObjectNode targetSource = source.deepCopy();
            targetSource.put("scope", "TARGET");
            targetSource.put("targetType", target.getTargetType());
            targetSource.put("targetId", target.getTargetId());
            targetSource.put("targetValue", target.getTargetValue().toPlainString());
            targetSource.put("matchedRecordCount", targetAggregate.recordCount);
            target.setActualValue(actual);
            target.setCalculatedTime(now);
            target.setCalculationJson(write(targetSource));
            target.setUpdateTime(now);
            targets.addOrUpdate(target);
            UnKpiResult result = new UnKpiResult();
            result.setKpiId(kpiId);
            result.setPeriodKey(periodKey);
            result.setKpiUserId(target.getId());
            result.setKpiVersion(kpi.getPublishedVersion());
            result.setActualValue(actual);
            result.setSourceJson(write(targetSource));
            result.setCalculationTime(now);
            results.addOrUpdate(result);
            views.add(targetView(target));
        }
        audit(context, "KPI_CALCULATE", kpiId);
        return new RuntimeView(kpi.getId(), kpi.getCode(), kpi.getName(), kpi.getPublishedVersion(),
                periodKey, calculation.overall().value(kpi.getAggregation()), now, source, views);
    }

    /** 同一批已授权根记录按周期筛选一次，再分别计算全租户口径和各目标对象口径。 */
    private KpiCalculation calculateValues(String token, SystemContext context, UnKpi kpi,
                                           List<UnKpiUser> targets, PeriodWindow window) {
        Long rootModuleId = kpi.getModuleId();
        if (rootModuleId == null) {
            UnDataSource source = dataSources.queryById(kpi.getDataSourceId());
            if (source == null || source.getRootModuleId() == null)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "KPI 数据源没有有效根模块");
            rootModuleId = source.getRootModuleId();
        }
        UnModuleField dateField = periodField(rootModuleId, readConfig(kpi.getPublishedConfigJson()));
        ModuleRecordSearchBO search = readSearch(kpi.getPublishedConfigJson());
        search.setPage(1);
        search.setLimit(100);
        List<Long> columns = new ArrayList<>(search.getColumnFieldIds() == null
                ? List.of() : search.getColumnFieldIds());
        if (kpi.getMeasureFieldId() != null && !columns.contains(kpi.getMeasureFieldId()))
            columns.add(kpi.getMeasureFieldId());
        if (dateField != null && !columns.contains(dateField.getId())) columns.add(dateField.getId());
        if (!columns.isEmpty()) search.setColumnFieldIds(columns);
        Map<Long, Set<Long>> accountScopes = new HashMap<>();
        Map<Long, Aggregate> byTarget = new LinkedHashMap<>();
        for (UnKpiUser target : targets) {
            byTarget.put(target.getId(), new Aggregate());
            if (!"DEPARTMENT".equals(target.getTargetType()))
                accountScopes.put(target.getId(), targetAccounts(context, target));
        }
        Aggregate overall = new Aggregate();
        String measureCode = null;
        int pageNo = 1;
        long total = Long.MAX_VALUE;
        // KPI 与仪表盘共用模块分页查询，不能用固定页数截断统计结果。
        // 只要数据库仍返回记录就继续读取，空页表示当前筛选结果已读完。
        long scanned = 0;
        while (scanned < total) {
            search.setPage(pageNo++);
            ModuleDataView data = kpi.getModuleId() != null
                    ? runtime.queryModule(token, context, kpi.getModuleId(), search)
                    : runtime.queryDataSource(token, context, kpi.getDataSourceId(), search);
            total = data.page().getTotal();
            if (!"COUNT".equals(kpi.getAggregation()))
                measureCode = findMeasureCode(data, kpi.getMeasureFieldId());
            if (dateField != null) findMeasureCode(data, dateField.getId());
            List<RowView> rows = data.page().getRecords();
            if (rows.isEmpty()) break;
            scanned += rows.size();
            Map<Long, UnModuleRecord> metadata = records.list(new LambdaQueryWrapper<UnModuleRecord>()
                    .in(UnModuleRecord::getId, rows.stream().map(RowView::id).toList())
                    .eq(UnModuleRecord::getSystemId, context.systemId())
                    .eq(UnModuleRecord::getTenantId, context.tenantId())
                    .eq(UnModuleRecord::getModuleId, rootModuleId)).stream()
                    .collect(Collectors.toMap(UnModuleRecord::getId, row -> row));
            for (RowView row : rows) {
                UnModuleRecord record = metadata.get(row.id());
                if (record == null) continue; // 共享记录不计入目标租户，逻辑删除由基础 Service 过滤。
                LocalDateTime businessTime = dateField == null ? record.getCreateTime()
                        : dateTime(row.values().get(dateField.getFieldKey()));
                if (businessTime == null || businessTime.isBefore(window.fromTime())
                        || !businessTime.isBefore(window.toTime())) continue;
                BigDecimal value = "COUNT".equals(kpi.getAggregation()) ? null
                        : number(row.values().get(measureCode));
                overall.accept(value);
                for (UnKpiUser target : targets) {
                    boolean matches = "DEPARTMENT".equals(target.getTargetType())
                            ? target.getTargetId().equals(record.getOwnerDeptId())
                            : accountScopes.get(target.getId()).contains(record.getOwnerUserId());
                    if (matches) byTarget.get(target.getId()).accept(value);
                }
            }
        }
        return new KpiCalculation(overall, byTarget, dateField == null ? null : dateField.getId());
    }

    private String findMeasureCode(ModuleDataView data, Long fieldId) {
        return data.columns().stream().filter(column -> fieldId != null && fieldId.equals(column.fieldId()))
                .map(column -> column.code()).findFirst().orElseThrow(() ->
                        new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "KPI 度量字段没有读取权限或未输出"));
    }

    /** KPI 周期默认按业务记录创建时间；配置日期字段后按该字段的实际保存值归期。 */
    private UnModuleField periodField(Long moduleId, JsonNode config) {
        JsonNode node = config == null ? null : config.get("periodFieldId");
        if (node == null || node.isNull()) return null;
        if (!node.canConvertToLong() || node.longValue() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 周期日期字段无效");
        UnModuleField field = modules.currentFields(moduleId).stream()
                .filter(item -> node.longValue() == item.getId()).findFirst().orElse(null);
        if (field == null || FieldType.parse(field.getFieldType()) != FieldType.DATE_TIME)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 周期字段必须是当前模块的日期时间字段");
        return field;
    }

    private PeriodWindow periodWindow(String type, String key) {
        LocalDate from = switch (type) {
            case "MONTH" -> YearMonth.parse(key).atDay(1);
            case "QUARTER" -> LocalDate.of(Integer.parseInt(key.substring(0, 4)),
                    (Integer.parseInt(key.substring(6)) - 1) * 3 + 1, 1);
            case "YEAR" -> LocalDate.of(Integer.parseInt(key), 1, 1);
            default -> throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 周期类型无效");
        };
        LocalDate to = switch (type) {
            case "MONTH" -> from.plusMonths(1);
            case "QUARTER" -> from.plusMonths(3);
            default -> from.plusYears(1);
        };
        return new PeriodWindow(from.atStartOfDay(), to.atStartOfDay());
    }

    private LocalDateTime dateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime time) return time;
        if (value instanceof LocalDate date) return date.atStartOfDay();
        try {
            String text = value.toString().trim().replace(' ', 'T');
            return text.length() == 10 ? LocalDate.parse(text).atStartOfDay() : LocalDateTime.parse(text);
        } catch (RuntimeException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "KPI 周期字段存在无法读取的日期值", ex);
        }
    }

    /** 角色按当前有效租户成员关系取负责人账号；部门目标使用记录保存的负责人部门。 */
    private Set<Long> targetAccounts(SystemContext context, UnKpiUser target) {
        String type = target.getTargetType();
        Long id = target.getTargetId();
        if ("ACCOUNT".equals(type)) return Set.of(id);
        if ("MEMBER".equals(type)) return Set.of(members.queryById(id).getAccountId());
        if ("TENANT_MEMBER".equals(type))
            return Set.of(members.queryById(tenantMembers.queryById(id).getMemberId()).getAccountId());
        if (!"ROLE".equals(type))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 目标类型无效");
        List<Long> tenantMemberIds = memberRoles.list(new LambdaQueryWrapper<UnSysMemberRole>()
                .eq(UnSysMemberRole::getRoleId, id)).stream()
                .map(UnSysMemberRole::getTenantMemberId).toList();
        if (tenantMemberIds.isEmpty()) return Set.of();
        List<Long> memberIds = tenantMembers.list(new LambdaQueryWrapper<UnSysTenantMember>()
                .in(UnSysTenantMember::getId, tenantMemberIds)
                .eq(UnSysTenantMember::getSystemId, context.systemId())
                .eq(UnSysTenantMember::getTenantId, context.tenantId())
                .eq(UnSysTenantMember::getStatus, "ACTIVE")).stream()
                .map(UnSysTenantMember::getMemberId).toList();
        if (memberIds.isEmpty()) return Set.of();
        return members.list(new LambdaQueryWrapper<UnSysMember>()
                .in(UnSysMember::getId, memberIds)
                .eq(UnSysMember::getSystemId, context.systemId())
                .eq(UnSysMember::getStatus, "ACTIVE")).stream()
                .map(UnSysMember::getAccountId).collect(Collectors.toSet());
    }

    private record PeriodWindow(LocalDateTime fromTime, LocalDateTime toTime) {}
    private record KpiCalculation(Aggregate overall, Map<Long, Aggregate> targets, Long periodFieldId) {}

    /** 计数含该周期的所有记录，数值聚合只累计非空数字字段。 */
    private static final class Aggregate {
        private long recordCount;
        private long numberCount;
        private BigDecimal sum = BigDecimal.ZERO;
        private BigDecimal max;
        private BigDecimal min;

        void accept(BigDecimal number) {
            recordCount++;
            if (number == null) return;
            numberCount++;
            sum = sum.add(number);
            max = max == null || number.compareTo(max) > 0 ? number : max;
            min = min == null || number.compareTo(min) < 0 ? number : min;
        }

        BigDecimal value(String aggregation) {
            return switch (aggregation) {
                case "COUNT" -> BigDecimal.valueOf(recordCount);
                case "SUM" -> sum;
                case "AVG" -> numberCount == 0 ? BigDecimal.ZERO
                        : sum.divide(BigDecimal.valueOf(numberCount), 4, RoundingMode.HALF_UP);
                case "MAX" -> max == null ? BigDecimal.ZERO : max;
                case "MIN" -> min == null ? BigDecimal.ZERO : min;
                default -> throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 统计方式无效");
            };
        }
    }

    private BigDecimal number(Object value) {
        if (value == null) return null;
        try { return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString()); }
        catch (NumberFormatException ex) { return null; }
    }

    private void validateSource(Long moduleId, Long dataSourceId) {
        if ((moduleId == null) == (dataSourceId == null))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 必须且只能绑定模块或数据源");
        if (moduleId != null && moduleId < 1 || dataSourceId != null && dataSourceId < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 数据来源主键无效");
    }

    private void validateMeasure(String aggregation, Long fieldId) {
        if (aggregation == null || !AGGREGATIONS.contains(aggregation.trim().toUpperCase()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 统计方式无效");
        if (fieldId != null && fieldId < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 度量字段主键无效");
        if (!"COUNT".equalsIgnoreCase(aggregation) && fieldId == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "非计数 KPI 必须指定度量字段");
    }

    private void validatePeriod(String period) {
        if (period == null || !PERIODS.contains(period.trim().toUpperCase()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 周期类型无效");
    }

    /** 周期键按当前口径固定格式保存，避免同一月份因自由文本产生多条目标和结果。 */
    private void validatePeriodKey(String periodType, String periodKey) {
        if (periodType == null || periodKey == null || periodKey.isBlank())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 周期键不能为空");
        String type = periodType.trim().toUpperCase();
        String key = periodKey.trim().toUpperCase();
        boolean valid = switch (type) {
            case "MONTH" -> key.matches("\\d{4}-(0[1-9]|1[0-2])");
            case "QUARTER" -> key.matches("\\d{4}-Q[1-4]");
            case "YEAR" -> key.matches("\\d{4}");
            default -> false;
        };
        if (!valid) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,
                "KPI 周期键与周期类型不匹配");
    }

    /** KPI 目标对象必须是当前系统租户内的有效主体，避免跨租户写入目标数据。 */
    private void validateTarget(SystemContext context, String targetType, Long targetId) {
        if (targetId == null || targetId < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 目标对象无效");
        boolean valid;
        if ("ACCOUNT".equals(targetType)) {
            UnPlatAccount account = accounts.queryById(targetId);
            valid = account != null && "ACTIVE".equals(account.getStatus());
            if (valid) {
                Long systemMemberId = members.list(new LambdaQueryWrapper<UnSysMember>()
                        .eq(UnSysMember::getSystemId, context.systemId())
                        .eq(UnSysMember::getAccountId, targetId)
                        .eq(UnSysMember::getStatus, "ACTIVE")).stream()
                        .map(UnSysMember::getId).findFirst().orElse(null);
                valid = systemMemberId != null && tenantMembers.count(new LambdaQueryWrapper<UnSysTenantMember>()
                        .eq(UnSysTenantMember::getSystemId, context.systemId())
                        .eq(UnSysTenantMember::getTenantId, context.tenantId())
                        .eq(UnSysTenantMember::getMemberId, systemMemberId)
                        .eq(UnSysTenantMember::getStatus, "ACTIVE")) > 0;
            }
        } else if ("MEMBER".equals(targetType)) {
            UnSysMember member = members.queryById(targetId);
            valid = member != null && context.systemId().equals(member.getSystemId())
                    && "ACTIVE".equals(member.getStatus());
            if (valid) valid = tenantMembers.count(new LambdaQueryWrapper<UnSysTenantMember>()
                    .eq(UnSysTenantMember::getSystemId, context.systemId())
                    .eq(UnSysTenantMember::getTenantId, context.tenantId())
                    .eq(UnSysTenantMember::getMemberId, targetId)
                    .eq(UnSysTenantMember::getStatus, "ACTIVE")) > 0;
        } else if ("TENANT_MEMBER".equals(targetType)) {
            UnSysTenantMember member = tenantMembers.queryById(targetId);
            valid = member != null && context.systemId().equals(member.getSystemId())
                    && context.tenantId().equals(member.getTenantId()) && "ACTIVE".equals(member.getStatus());
            if (valid) {
                UnSysMember systemMember = members.queryById(member.getMemberId());
                valid = systemMember != null && context.systemId().equals(systemMember.getSystemId())
                        && "ACTIVE".equals(systemMember.getStatus());
            }
        } else if ("DEPARTMENT".equals(targetType)) {
            UnSysDepartment department = departments.queryById(targetId);
            valid = department != null && context.systemId().equals(department.getSystemId())
                    && context.tenantId().equals(department.getTenantId()) && "ACTIVE".equals(department.getStatus());
        } else {
            UnSysRole role = roles.queryById(targetId);
            valid = role != null && context.systemId().equals(role.getSystemId())
                    && context.tenantId().equals(role.getTenantId()) && "ACTIVE".equals(role.getStatus());
        }
        if (!valid) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "KPI 目标对象不存在或不属于当前系统租户");
    }

    private void validateField(Long moduleId, Long fieldId, String aggregation) {
        if ("COUNT".equals(aggregation)) return;
        UnModuleField measure = modules.currentFields(moduleId).stream()
                .filter(field -> fieldId.equals(field.getId())).findFirst().orElse(null);
        if (measure == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 度量字段不存在");
        if (FieldType.parse(measure.getFieldType()) != FieldType.NUMBER)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 度量字段必须是数字类型");
    }

    private UnKpi owned(SystemContext context, Long id) {
        if (id == null || id < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 主键无效");
        }
        UnKpi kpi = kpis.queryById(id);
        if (kpi == null || !context.systemId().equals(kpi.getSystemId())
                || !context.tenantId().equals(kpi.getTenantId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "KPI 不存在");
        }
        return kpi;
    }

    /** 写入或计算 KPI 前锁定配置行，保证发布版本和结果口径一致。 */
    private UnKpi ownedForUpdate(SystemContext context, Long id) {
        if (id == null || id < 1) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 主键无效");
        }
        UnKpi kpi = kpis.getOne(new LambdaQueryWrapper<UnKpi>()
                .eq(UnKpi::getId, id).last("FOR UPDATE"), false);
        if (kpi == null || !context.systemId().equals(kpi.getSystemId())
                || !context.tenantId().equals(kpi.getTenantId())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "KPI 不存在");
        }
        return kpi;
    }

    private KpiView view(UnKpi kpi) {
        return new KpiView(kpi.getId(), kpi.getSystemId(), kpi.getTenantId(), kpi.getCode(), kpi.getName(),
                kpi.getDataSourceId(), kpi.getModuleId(), kpi.getMeasureFieldId(), kpi.getAggregation(),
                kpi.getPeriodType(), kpi.getStatus(), kpi.getPublishedVersion(), readConfig(kpi.getConfigJson()),
                readConfig(kpi.getPublishedConfigJson()), kpi.getUpdateTime());
    }

    private TargetView targetView(UnKpiUser target) {
        BigDecimal rate = target.getActualValue() == null || target.getTargetValue().signum() == 0
                ? null : target.getActualValue().multiply(BigDecimal.valueOf(100))
                .divide(target.getTargetValue(), 2, RoundingMode.HALF_UP);
        String achievement = target.getActualValue() == null ? null
                : target.getTargetValue().signum() == 0 ? "NO_TARGET"
                : target.getActualValue().compareTo(target.getTargetValue()) >= 0
                ? "ACHIEVED" : "BELOW_TARGET";
        return new TargetView(target.getId(), target.getKpiId(), target.getTargetType(), target.getTargetId(),
                target.getPeriodKey(), target.getTargetValue(), target.getActualValue(), target.getCalculatedTime(),
                target.getStatus(), rate, achievement);
    }

    private ObjectNode sourceSummary(UnKpi kpi) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("moduleId", kpi.getModuleId());
        value.put("dataSourceId", kpi.getDataSourceId());
        value.put("measureFieldId", kpi.getMeasureFieldId());
        value.put("aggregation", kpi.getAggregation());
        value.put("publishedVersion", kpi.getPublishedVersion());
        value.put("publishedConfig", readConfig(kpi.getPublishedConfigJson()));
        if (kpi.getDataSourceId() != null) {
            UnDataSource dataSource = dataSources.queryById(kpi.getDataSourceId());
            UnDataSourceConfig config = dataSourceConfigs.getOne(new LambdaQueryWrapper<UnDataSourceConfig>()
                    .eq(UnDataSourceConfig::getDataSourceId, kpi.getDataSourceId()), false);
            if (dataSource == null || !"PUBLISHED".equals(dataSource.getStatus())
                    || config == null || config.getPublishedJson() == null
                    || !java.util.Objects.equals(dataSource.getPublishedVersion(), config.getPublishedVersion()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "KPI 数据源发布配置已变化，请重新检查");
            // 历史结果保留本次实际使用的数据源快照，后续重新发布不会改写旧口径。
            value.put("dataSourcePublishedVersion", dataSource.getPublishedVersion());
            value.put("dataSourceRootModuleId", dataSource.getRootModuleId());
            value.put("dataSourcePublishedConfig", readConfig(config.getPublishedJson()));
        }
        return json.valueToTree(value);
    }

    private ModuleRecordSearchBO readSearch(String text) {
        JsonNode node = readConfig(text);
        if (node == null || !node.isObject()) return new ModuleRecordSearchBO();
        JsonNode search = node.get("search");
        JsonNode query = search;
        if (query == null) {
            // 顶层还可能包含日期口径和预警设置，仅将明确的列表条件交给查询对象。
            ObjectNode flat = json.createObjectNode();
            for (String key : List.of("page", "limit", "keyword", "scope", "sceneId", "filters",
                    "sortFieldId", "descending", "columnFieldIds", "recordIds")) {
                if (node.has(key)) flat.set(key, node.get(key));
            }
            query = flat;
        }
        try { return json.treeToValue(query, ModuleRecordSearchBO.class); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "KPI 筛选配置无效"); }
    }

    private JsonNode readConfig(String text) {
        if (text == null || text.isBlank()) return json.createObjectNode();
        try { return json.readTree(text); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "KPI 配置格式无效"); }
    }

    private String writeConfig(JsonNode value) {
        try { return json.writeValueAsString(value == null ? json.createObjectNode() : value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "KPI 配置无法保存"); }
    }

    private String write(JsonNode value) {
        return writeConfig(value);
    }

    private void audit(SystemContext context, String actionCode, Long objectId) {
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId());
        log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId());
        log.setActionCode(actionCode);
        log.setObjectType("KPI");
        log.setObjectId(objectId);
        logs.addOrUpdate(log);
    }
}
