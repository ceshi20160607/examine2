package ${package.ServiceImpl};

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import ${package.Entity}.${entity};
import ${package.Mapper}.${table.mapperName};
import ${package.Service}.${table.serviceName};
import ${package.Service}.${table.serviceName}.FieldLayout;
import ${package.Service}.${table.serviceName}.FieldView;
import ${package.Service}.${table.serviceName}.PageQuery;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ${table.comment!entity}的基础 CRUD 实现；不在此推断租户权限或业务状态。 */
@Service
public class ${table.serviceImplName} extends ServiceImpl<${table.mapperName}, ${entity}>
        implements ${table.serviceName} {
    /** 查询条件及排序在 Service 内构建，字段只从生成时的数据库列白名单选择。 */
    @Override
    public Page<${entity}> queryPageList(PageQuery query) {
        if (query == null || query.page() < 1 || query.size() < 1 || query.size() > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        String column = switch (query.sortField() == null || query.sortField().isBlank()
                ? "id" : query.sortField()) {
<#list table.fields as field>
            case "${field.propertyName?j_string}" -> "${field.name?j_string}";
</#list>
            default -> throw new IllegalArgumentException("排序字段无效");
        };
        QueryWrapper<${entity}> wrapper = new QueryWrapper<>();
        if (query.filters() != null) {
            query.filters().forEach((field, value) -> {
                String filterColumn = switch (field) {
<#list table.fields as field>
                    case "${field.propertyName?j_string}" -> "${field.name?j_string}";
</#list>
                    default -> throw new IllegalArgumentException("筛选字段无效");
                };
                wrapper.eq(filterColumn, value);
            });
        }
        wrapper.orderBy(true, !query.descending(), column);
        return page(new Page<>(query.page(), query.size()), wrapper);
    }

    /** 新增或更新都检查写入结果，避免空实现掩盖失败。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ${entity} addOrUpdate(${entity} model) {
        if (model == null) {
            throw new IllegalArgumentException("保存数据不能为空");
        }
<#assign hasKey = false>
<#list table.fields as field>
<#if field.keyFlag>
<#assign hasKey = true>
        if (model.get${field.capitalName}() == null) {
            if (!save(model)) {
                throw new IllegalStateException("新增记录失败");
            }
        } else {
            queryById(model.get${field.capitalName}());
            if (!updateById(model)) {
                throw new IllegalStateException("更新记录失败");
            }
        }
</#if>
</#list>
<#if !hasKey><#stop "基础 CRUD 必须有主键"></#if>
        return model;
    }

    /** 查无记录时抛出明确异常，业务服务可转换为本业务的 404 结果。 */
    @Override
    public ${entity} queryById(Long id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("主键无效");
        }
        ${entity} model = getById(id);
        if (model == null) {
            throw new NoSuchElementException("记录不存在: " + id);
        }
        return model;
    }

    /** 普通表使用字段定义的默认半行宽度；可配置模块调用带布局参数的重载方法。 */
    @Override
    public List<FieldView> information(Long id) {
        return information(id, List.of(), Map.of());
    }

    /** 按字段宽度顺序计算行号和行内偏移，50% + 50% 排在同一行。 */
    @Override
    public List<FieldView> information(Long id, List<FieldLayout> layout,
                                       Map<String, Object> extensionValues) {
        ${entity} model = queryById(id);
        Map<String, Object> values = new LinkedHashMap<>();
        List<FieldLayout> defaults = new ArrayList<>();
<#assign displayIndex = 0>
<#list table.fields as field>
<#if !field.propertyName?lower_case?matches(".*(password|secret|token|credential|salt|hash|cipher|privatekey).*")>
        values.put("${field.propertyName?j_string}", model.get${field.capitalName}());
        defaults.add(new FieldLayout("${field.propertyName?j_string}",
                "${(field.comment!field.propertyName)?j_string}", 50, ${displayIndex}));
<#assign displayIndex = displayIndex + 1>
</#if>
</#list>
        if (extensionValues != null) {
            extensionValues.forEach((field, value) -> {
                if (field == null || field.toLowerCase(Locale.ROOT)
                        .matches(".*(password|secret|token|credential|salt|hash|cipher|privatekey).*")) {
                    throw new IllegalArgumentException("详情字段无效");
                }
                values.putIfAbsent(field, value);
            });
        }
        List<FieldLayout> selected = layout == null || layout.isEmpty() ? defaults : layout;
        List<FieldView> fields = new ArrayList<>();
        int row = 1;
        int usedWidth = 0;
        for (FieldLayout item : selected.stream()
                .sorted(Comparator.comparingInt(FieldLayout::sortOrder)).toList()) {
            if (item.fieldName() == null || !values.containsKey(item.fieldName())
                    || item.widthPercent() < 1 || item.widthPercent() > 100) {
                throw new IllegalArgumentException("详情布局字段或宽度无效");
            }
            if (usedWidth + item.widthPercent() > 100) {
                row++;
                usedWidth = 0;
            }
            fields.add(new FieldView(item.fieldName(), item.label(), values.get(item.fieldName()),
                    row, usedWidth, item.widthPercent()));
            usedWidth += item.widthPercent();
        }
        return List.copyOf(fields);
    }

    /** 只接受非空主键集，删除事务由 Service 持有。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.stream().anyMatch(id -> id == null || id < 1)) {
            throw new IllegalArgumentException("删除主键无效");
        }
        return removeByIds(ids);
    }
}
