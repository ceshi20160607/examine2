package com.unique.unexamine.dashboard.manage;

import com.unique.unexamine.dashboard.base.entity.UnDataSource;
import com.unique.unexamine.dashboard.base.entity.UnDataSourceConfig;
import com.unique.unexamine.dashboard.base.mapper.UnDataSourceConfigMapper;
import com.unique.unexamine.dashboard.base.mapper.UnDataSourceMapper;
import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.module.base.entity.UnModule;
import com.unique.unexamine.module.base.entity.UnModuleTenant;
import com.unique.unexamine.module.base.mapper.UnModuleMapper;
import com.unique.unexamine.module.base.mapper.UnModuleTenantMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 数据源先连接本产品的模块记录，供仪表盘和 KPI 读取真实数据。 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/data-sources")
public class DataSourceController {
    private final SystemSessionAccess access;
    private final UnDataSourceMapper sources;
    private final UnDataSourceConfigMapper configs;
    private final UnSysTenantMapper tenants;
    private final UnModuleMapper modules;
    private final UnModuleTenantMapper tenantModules;
    private final ModuleMetric metrics;

    public DataSourceController(SystemSessionAccess access, UnDataSourceMapper sources,
                                UnDataSourceConfigMapper configs, UnSysTenantMapper tenants,
                                UnModuleMapper modules, UnModuleTenantMapper tenantModules,
                                ModuleMetric metrics) {
        this.access = access;
        this.sources = sources;
        this.configs = configs;
        this.tenants = tenants;
        this.modules = modules;
        this.tenantModules = tenantModules;
        this.metrics = metrics;
    }

    /** 数据源的模块 ID 由服务端核对系统与租户启用关系。 */
    @PostMapping
    @Transactional
    public SourceView create(@PathVariable long tenantId, @Valid @RequestBody SourceInput input,
                             HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        validateModule(tenantId, input.moduleId());
        if (!input.sourceCode().matches("[a-z][a-z0-9_]{0,63}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源编码无效");
        }
        UnDataSource source = new UnDataSource();
        source.setTenantId(tenantId);
        source.setSourceCode(input.sourceCode());
        source.setSourceName(input.sourceName().trim());
        source.setSourceType("module");
        source.setStatus((byte) 1);
        sources.insert(source);
        UnDataSourceConfig config = new UnDataSourceConfig();
        config.setDataSourceId(source.getId());
        config.setConfigKey("module_id");
        config.setConfigValue(Long.toString(input.moduleId()));
        configs.insert(config);
        return view(source, input.moduleId());
    }

    @GetMapping
    public SourcePage list(@PathVariable long tenantId,
                           @RequestParam(defaultValue = "1") long page,
                           @RequestParam(defaultValue = "20") long size,
                           @RequestParam(defaultValue = "createTime") String sortField,
                           @RequestParam(defaultValue = "desc") String sortOrder,
                           HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        if (page < 1 || size < 1 || size > 100
                || !List.of("createTime", "sourceName", "sourceCode").contains(sortField)
                || !List.of("asc", "desc").contains(sortOrder.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页或排序参数无效");
        }
        LambdaQueryWrapper<UnDataSource> query = new LambdaQueryWrapper<UnDataSource>()
                .eq(UnDataSource::getTenantId, tenantId);
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        switch (sortField) {
            case "sourceName" -> query.orderBy(true, asc, UnDataSource::getSourceName);
            case "sourceCode" -> query.orderBy(true, asc, UnDataSource::getSourceCode);
            default -> query.orderBy(true, asc, UnDataSource::getCreateTime);
        }
        query.orderByAsc(UnDataSource::getId);
        Page<UnDataSource> result = sources.selectPage(new Page<>(page, size), query);
        return new SourcePage(result.getRecords().stream().map(item -> view(item, moduleId(item))).toList(),
                result.getTotal(), page, size);
    }

    @GetMapping("/{sourceId}")
    public SourceView detail(@PathVariable long tenantId, @PathVariable long sourceId,
                             HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        UnDataSource source = requireSource(tenantId, sourceId);
        return view(source, moduleId(source));
    }

    @PutMapping("/{sourceId}")
    public SourceView update(@PathVariable long tenantId, @PathVariable long sourceId,
                             @Valid @RequestBody SourceUpdate input, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnDataSource source = requireSource(tenantId, sourceId);
        source.setSourceName(input.sourceName().trim());
        source.setStatus(input.enabled() ? (byte) 1 : (byte) 0);
        sources.updateById(source);
        return view(source, moduleId(source));
    }

    /** 预览直接统计该租户有效模块记录，证明数据源可读而非仅保存配置。 */
    @GetMapping("/{sourceId}/preview")
    public PreviewView preview(@PathVariable long tenantId, @PathVariable long sourceId,
                               HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        long moduleId = resolveModule(tenantId, sourceId);
        long count = metrics.value(tenantId, moduleId, "*", "count", request).longValueExact();
        return new PreviewView(sourceId, moduleId, count);
    }

    /** 仪表盘和 KPI 复用这一步归属检查，不允许用其他租户的数据源 ID 读取记录。 */
    public long resolveModule(long tenantId, long sourceId) {
        UnDataSource source = requireSource(tenantId, sourceId);
        if (source.getStatus() == null || source.getStatus() != 1 || !"module".equals(source.getSourceType())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已停用或暂不支持");
        }
        long moduleId = moduleId(source);
        validateModule(tenantId, moduleId);
        return moduleId;
    }

    private void validateModule(long tenantId, long moduleId) {
        UnSysTenant tenant = tenants.selectById(tenantId);
        UnModule module = modules.selectById(moduleId);
        if (tenant == null || module == null || !module.getSystemId().equals(tenant.getSystemId())
                || module.getStatus() == null || module.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模块不属于此租户的有效系统");
        }
        if (tenantModules.selectCount(new LambdaQueryWrapper<UnModuleTenant>()
                .eq(UnModuleTenant::getTenantId, tenantId)
                .eq(UnModuleTenant::getModuleId, moduleId)
                .eq(UnModuleTenant::getStatus, 1)) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "租户未启用此模块");
        }
    }

    private UnDataSource requireSource(long tenantId, long sourceId) {
        UnDataSource source = sources.selectById(sourceId);
        if (source == null || !source.getTenantId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在");
        }
        return source;
    }

    private long moduleId(UnDataSource source) {
        UnDataSourceConfig config = configs.selectOne(new LambdaQueryWrapper<UnDataSourceConfig>()
                .eq(UnDataSourceConfig::getDataSourceId, source.getId())
                .eq(UnDataSourceConfig::getConfigKey, "module_id"));
        if (config == null || config.getConfigValue() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源缺少模块配置");
        }
        try {
            return Long.parseLong(config.getConfigValue());
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源模块配置无效");
        }
    }

    private SourceView view(UnDataSource source, long moduleId) {
        return new SourceView(source.getId(), source.getSourceCode(), source.getSourceName(),
                source.getSourceType(), moduleId, source.getStatus());
    }

    public record SourceInput(@NotBlank @Size(max = 64) String sourceCode,
                              @NotBlank @Size(max = 120) String sourceName, long moduleId) { }
    public record SourceUpdate(@NotBlank @Size(max = 120) String sourceName, boolean enabled) { }
    public record SourceView(Long id, String sourceCode, String sourceName,
                             String sourceType, long moduleId, Byte status) { }
    public record SourcePage(List<SourceView> records, long total, long page, long size) { }
    public record PreviewView(long sourceId, long moduleId, long activeRecordCount) { }
}
