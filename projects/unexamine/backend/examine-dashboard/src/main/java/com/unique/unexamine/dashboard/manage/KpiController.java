package com.unique.unexamine.dashboard.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.dashboard.base.entity.UnKpi;
import com.unique.unexamine.dashboard.base.entity.UnKpiUser;
import com.unique.unexamine.dashboard.base.mapper.UnKpiMapper;
import com.unique.unexamine.dashboard.base.mapper.UnKpiUserMapper;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMemberMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/** 租户 KPI 配置、成员目标和真实模块记录统计入口。 */
@Tag(name = "KPI", description = "KPI 定义、成员目标和统计值。")
@RestController
@RequestMapping("/api/tenants/{tenantId}/kpis")
public class KpiController {
    private final SystemSessionAccess access;
    private final DataSourceController sources;
    private final ModuleMetric metrics;
    private final UnKpiMapper kpis;
    private final UnKpiUserMapper users;
    private final UnSysTenantMemberMapper tenantMembers;
    private final JdbcTemplate jdbc;

    public KpiController(SystemSessionAccess access, DataSourceController sources, ModuleMetric metrics,
                         UnKpiMapper kpis, UnKpiUserMapper users,
                         UnSysTenantMemberMapper tenantMembers, JdbcTemplate jdbc) {
        this.access = access;
        this.sources = sources;
        this.metrics = metrics;
        this.kpis = kpis;
        this.users = users;
        this.tenantMembers = tenantMembers;
        this.jdbc = jdbc;
    }

    /** 管理员创建 KPI，统计口径先验证为当前可执行的全周期计数或求和。 */
    @Operation(summary = "新建（KPI）")
    @PostMapping
    public KpiView create(@PathVariable long tenantId, @Valid @RequestBody KpiInput input,
                          HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        validateInput(tenantId, input);
        if (kpis.selectCount(new LambdaQueryWrapper<UnKpi>().eq(UnKpi::getTenantId, tenantId)
                .eq(UnKpi::getKpiCode, input.kpiCode())) > 0) conflict("KPI 编码已存在");
        UnKpi kpi = new UnKpi();
        kpi.setTenantId(tenantId);
        kpi.setKpiCode(input.kpiCode());
        fill(kpi, input);
        kpi.setStatus((byte) 1);
        kpis.insert(kpi);
        return view(kpis.selectById(kpi.getId()));
    }

    /** 管理员查看本租户全部配置；成员只查看明确分配给自己的启用 KPI。 */
    @Operation(summary = "查询列表（KPI）")
    @GetMapping
    public KpiPage list(@PathVariable long tenantId, @RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "20") long size,
                        @RequestParam(defaultValue = "createTime") String sortField,
                        @RequestParam(defaultValue = "desc") String sortOrder,
                        HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        if (page < 1 || size < 1 || size > 100
                || !List.of("createTime", "kpiName", "kpiCode").contains(sortField)
                || !List.of("asc", "desc").contains(sortOrder.toLowerCase())) bad("分页或排序参数无效");
        LambdaQueryWrapper<UnKpi> query = new LambdaQueryWrapper<UnKpi>().eq(UnKpi::getTenantId, tenantId);
        if (!isAdmin(request, tenantId)) {
            List<Long> assigned = users.selectList(new LambdaQueryWrapper<UnKpiUser>()
                    .eq(UnKpiUser::getMemberId, actorId)).stream().map(UnKpiUser::getKpiId).toList();
            if (assigned.isEmpty()) return new KpiPage(List.of(), 0, page, size);
            query.in(UnKpi::getId, assigned).eq(UnKpi::getStatus, 1);
        }
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortField) {
            case "kpiName" -> query.orderBy(true, asc, UnKpi::getKpiName);
            case "kpiCode" -> query.orderBy(true, asc, UnKpi::getKpiCode);
            default -> query.orderBy(true, asc, UnKpi::getCreateTime);
        }
        query.orderByAsc(UnKpi::getId);
        Page<UnKpi> result = kpis.selectPage(new Page<>(page, size), query);
        return new KpiPage(result.getRecords().stream().map(this::view).toList(), result.getTotal(), page, size);
    }

    /** 租户管理员或被分配成员可以读取定义，停用 KPI 仅管理员可见。 */
    @Operation(summary = "查询详情（KPI）")
    @GetMapping("/{kpiId}")
    public KpiView detail(@PathVariable long tenantId, @PathVariable long kpiId, HttpServletRequest request) {
        readable(tenantId, kpiId, request);
        return view(kpiInTenant(tenantId, kpiId));
    }

    /** 允许修改统计口径，编码固定不变以免外部引用失效。 */
    @Operation(summary = "修改（KPI）")
    @PutMapping("/{kpiId}")
    public KpiView update(@PathVariable long tenantId, @PathVariable long kpiId,
                          @Valid @RequestBody KpiUpdate input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnKpi kpi = kpiInTenant(tenantId, kpiId);
        validateMetric(tenantId, input.dataSourceId(), input.metricField(), input.aggregation(),
                input.cycleType(), input.filterData(), input.targetValue());
        kpi.setKpiName(input.kpiName().trim());
        kpi.setDataSourceId(input.dataSourceId());
        kpi.setMetricField("count".equals(input.aggregation()) ? "*" : input.metricField());
        kpi.setAggregation(input.aggregation());
        kpi.setCycleType(input.cycleType());
        kpi.setFilterData(null);
        kpi.setTargetValue(input.targetValue());
        kpis.updateById(kpi);
        return view(kpis.selectById(kpiId));
    }

    /** 启停状态单独修改；停用后成员不可读取统计值。 */
    @Operation(summary = "修改状态（KPI）")
    @PutMapping("/{kpiId}/status")
    public KpiView status(@PathVariable long tenantId, @PathVariable long kpiId,
                          @Valid @RequestBody StatusInput input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnKpi kpi = kpiInTenant(tenantId, kpiId);
        kpi.setStatus((byte) (input.enabled() ? 1 : 0));
        kpis.updateById(kpi);
        return view(kpis.selectById(kpiId));
    }

    /** 成员只看到自己的目标；管理员可查看此 KPI 的全部成员目标。 */
    @Operation(summary = "查询成员（KPI）")
    @GetMapping("/{kpiId}/members")
    public List<UnKpiUser> members(@PathVariable long tenantId, @PathVariable long kpiId,
                                   HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        readable(tenantId, kpiId, request);
        LambdaQueryWrapper<UnKpiUser> query = new LambdaQueryWrapper<UnKpiUser>()
                .eq(UnKpiUser::getKpiId, kpiId).orderByAsc(UnKpiUser::getMemberId);
        if (!isAdmin(request, tenantId)) query.eq(UnKpiUser::getMemberId, actorId);
        return users.selectList(query);
    }

    /** 管理员给有效租户成员设置个人目标，更新和新建共用同一入口。 */
    @Operation(summary = "设置成员目标（KPI）")
    @PutMapping("/{kpiId}/members/{memberId}")
    @Transactional
    public UnKpiUser setMember(@PathVariable long tenantId, @PathVariable long kpiId,
                               @PathVariable long memberId, @Valid @RequestBody TargetInput input,
                               HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        lockKpi(tenantId, kpiId);
        requireTenantMember(tenantId, memberId);
        target(input.targetValue());
        UnKpiUser row = users.selectOne(new LambdaQueryWrapper<UnKpiUser>()
                .eq(UnKpiUser::getKpiId, kpiId).eq(UnKpiUser::getMemberId, memberId));
        if (row == null) {
            row = new UnKpiUser();
            row.setKpiId(kpiId);
            row.setMemberId(memberId);
            row.setTargetValue(input.targetValue());
            users.insert(row);
        } else {
            row.setTargetValue(input.targetValue());
            users.updateById(row);
        }
        return users.selectById(row.getId());
    }

    /** 取消成员目标同时撤销其 KPI 读取范围。 */
    @Operation(summary = "移除成员（KPI）")
    @DeleteMapping("/{kpiId}/members/{memberId}")
    @Transactional
    public void removeMember(@PathVariable long tenantId, @PathVariable long kpiId,
                             @PathVariable long memberId, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        lockKpi(tenantId, kpiId);
        users.delete(new LambdaQueryWrapper<UnKpiUser>().eq(UnKpiUser::getKpiId, kpiId)
                .eq(UnKpiUser::getMemberId, memberId));
    }

    /** 汇总值来自当前租户未删除的模块记录，全周期统计不制造模拟数值。 */
    @Operation(summary = "查询统计值（KPI）")
    @GetMapping("/{kpiId}/value")
    public ValueView value(@PathVariable long tenantId, @PathVariable long kpiId,
                           HttpServletRequest request) {
        readable(tenantId, kpiId, request);
        UnKpi kpi = kpiInTenant(tenantId, kpiId);
        long moduleId = sources.resolveModule(tenantId, kpi.getDataSourceId());
        BigDecimal actual = metrics.value(tenantId, moduleId, kpi.getMetricField(), kpi.getAggregation(), request);
        return new ValueView(kpiId, kpi.getTargetValue(), actual, kpi.getCycleType());
    }

    /** 个人实际值按记录负责人过滤，避免把租户总量误报成个人业绩。 */
    @Operation(summary = "查询成员统计值（KPI）")
    @GetMapping("/{kpiId}/members/{memberId}/value")
    public MemberValueView memberValue(@PathVariable long tenantId, @PathVariable long kpiId,
                                       @PathVariable long memberId, HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        boolean admin = isAdmin(request, tenantId);
        if (!admin && actorId != memberId) forbidden("只能查看自己的 KPI");
        UnKpi kpi = kpiInTenant(tenantId, kpiId);
        UnKpiUser assignment = users.selectOne(new LambdaQueryWrapper<UnKpiUser>()
                .eq(UnKpiUser::getKpiId, kpiId).eq(UnKpiUser::getMemberId, memberId));
        if (assignment == null || (!admin && (kpi.getStatus() == null || kpi.getStatus() != 1))) forbidden("未分配此 KPI");
        requireTenantMember(tenantId, memberId);
        long moduleId = sources.resolveModule(tenantId, kpi.getDataSourceId());
        // 先复用公共指标计算核对聚合方式和求和字段类型，再追加负责人条件计算个人值。
        metrics.value(tenantId, moduleId, kpi.getMetricField(), kpi.getAggregation());
        BigDecimal actual;
        if ("count".equals(kpi.getAggregation())) {
            Long count = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM un_module_record
                    WHERE tenant_id = ? AND module_id = ? AND owner_member_id = ? AND record_status <> 2
                    """, Long.class, tenantId, moduleId, memberId);
            actual = BigDecimal.valueOf(count == null ? 0 : count);
        } else {
            // 通过主记录重新限制租户、模块和负责人，避免扩展值跨范围参与个人求和。
            actual = jdbc.queryForObject("""
                    SELECT COALESCE(SUM(d.value_number), 0)
                    FROM un_module_record_data d
                    JOIN un_module_record r ON r.id = d.record_id
                    WHERE r.tenant_id = ? AND r.module_id = ? AND r.owner_member_id = ? AND r.record_status <> 2
                      AND d.tenant_id = ? AND d.module_id = ? AND d.field_name = ?
                    """, BigDecimal.class, tenantId, moduleId, memberId,
                    tenantId, moduleId, kpi.getMetricField());
            if (actual == null) actual = BigDecimal.ZERO;
        }
        BigDecimal goal = assignment.getTargetValue() == null ? kpi.getTargetValue() : assignment.getTargetValue();
        return new MemberValueView(kpiId, memberId, goal, actual, kpi.getCycleType());
    }

    private void validateInput(long tenantId, KpiInput input) {
        if (!input.kpiCode().matches("[a-z][a-z0-9_]{0,63}")) bad("KPI 编码无效");
        validateMetric(tenantId, input.dataSourceId(), input.metricField(), input.aggregation(),
                input.cycleType(), input.filterData(), input.targetValue());
    }

    /** 非 all 周期和自定义过滤尚无执行器，直接拒绝以免出现错误的统计结果。 */
    private void validateMetric(long tenantId, Long dataSourceId, String metricField, String aggregation,
                                String cycleType, String filterData, BigDecimal targetValue) {
        if (!"all".equals(cycleType)) bad("当前仅支持 all 全周期统计");
        if (filterData != null && !filterData.isBlank()) bad("当前不支持 KPI 自定义过滤");
        if (!List.of("count", "sum").contains(aggregation)) bad("仅支持 count 或 sum 统计");
        if (dataSourceId == null) bad("数据源不能为空");
        long moduleId = sources.resolveModule(tenantId, dataSourceId);
        if ("sum".equals(aggregation) && (metricField == null || metricField.isBlank())) bad("求和字段不能为空");
        if ("sum".equals(aggregation)) metrics.value(tenantId, moduleId, metricField, aggregation);
        target(targetValue);
    }

    private void target(BigDecimal amount) {
        if (amount != null && (amount.signum() < 0 || amount.scale() > 4
                || amount.precision() - amount.scale() > 16))
            bad("目标值须为非负且最多四位小数");
    }

    private void fill(UnKpi kpi, KpiInput input) {
        kpi.setKpiName(input.kpiName().trim());
        kpi.setDataSourceId(input.dataSourceId());
        kpi.setMetricField("count".equals(input.aggregation()) ? "*" : input.metricField());
        kpi.setAggregation(input.aggregation());
        kpi.setCycleType(input.cycleType());
        kpi.setFilterData(null);
        kpi.setTargetValue(input.targetValue());
    }

    private KpiView view(UnKpi kpi) {
        return new KpiView(kpi.getId(), kpi.getTenantId(), kpi.getKpiCode(), kpi.getKpiName(),
                kpi.getDataSourceId(), kpi.getMetricField(), kpi.getAggregation(), kpi.getCycleType(),
                kpi.getTargetValue(), kpi.getStatus(), kpi.getCreateTime(), kpi.getUpdateTime());
    }

    private UnKpi kpiInTenant(long tenantId, long kpiId) {
        UnKpi kpi = kpis.selectById(kpiId);
        if (kpi == null || !kpi.getTenantId().equals(tenantId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "KPI 不存在");
        return kpi;
    }

    private void readable(long tenantId, long kpiId, HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        UnKpi kpi = kpiInTenant(tenantId, kpiId);
        if (isAdmin(request, tenantId)) return;
        if (kpi.getStatus() == null || kpi.getStatus() != 1 || users.selectCount(new LambdaQueryWrapper<UnKpiUser>()
                .eq(UnKpiUser::getKpiId, kpiId).eq(UnKpiUser::getMemberId, actorId)) == 0)
            forbidden("未分配此 KPI");
    }

    /** 读取范围区分管理员与被分配成员；鉴权异常除权限不足外原样抛出。 */
    private boolean isAdmin(HttpServletRequest request, long tenantId) {
        try {
            access.tenantAdmin(request, tenantId);
            return true;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN) return false;
            throw ex;
        }
    }

    private void requireTenantMember(long tenantId, long memberId) {
        if (tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId).eq(UnSysTenantMember::getMemberId, memberId)
                .eq(UnSysTenantMember::getStatus, 1)) == 0) bad("成员不属于当前租户");
    }

    /** 配置成员目标时串行化同一 KPI 下的新增和覆盖操作。 */
    private void lockKpi(long tenantId, long kpiId) {
        jdbc.queryForList("SELECT id FROM un_kpi WHERE id = ? AND tenant_id = ? FOR UPDATE", kpiId, tenantId);
        kpiInTenant(tenantId, kpiId);
    }

    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, message); }

    /** KPI 输入只允许已实现的全周期统计，过滤条件暂不接收。 */
    public record KpiInput(@NotBlank @Size(max = 64) String kpiCode,
                           @NotBlank @Size(max = 120) String kpiName,
                           @NotNull Long dataSourceId, String metricField, @NotBlank String aggregation,
                           @NotBlank String cycleType, String filterData, BigDecimal targetValue) {}
    public record KpiUpdate(@NotBlank @Size(max = 120) String kpiName,
                            @NotNull Long dataSourceId, String metricField, @NotBlank String aggregation,
                            @NotBlank String cycleType, String filterData, BigDecimal targetValue) {}
    public record StatusInput(@NotNull Boolean enabled) {}
    public record TargetInput(BigDecimal targetValue) {}
    public record KpiView(Long id, Long tenantId, String kpiCode, String kpiName, Long dataSourceId,
                          String metricField, String aggregation, String cycleType, BigDecimal targetValue,
                          Byte status, java.time.LocalDateTime createTime, java.time.LocalDateTime updateTime) {}
    public record KpiPage(List<KpiView> records, long total, long page, long size) {}
    public record ValueView(Long kpiId, BigDecimal targetValue, BigDecimal actualValue, String cycleType) {}
    public record MemberValueView(Long kpiId, Long memberId, BigDecimal targetValue,
                                  BigDecimal actualValue, String cycleType) {}
}
