package ${package.ServiceImpl};

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import ${package.Entity}.${entity};
import ${package.Mapper}.${table.mapperName};
import ${package.Service}.${table.serviceName};
import ${package.Service}.${table.serviceName}.FieldConfig;
import ${package.Service}.${table.serviceName}.FieldView;
import ${package.Service}.${table.serviceName}.PageQuery;
import java.util.ArrayList;
import java.util.Collections;
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
<#if !field.propertyName?lower_case?matches(".*(password|secret|token|credential|salt|hash|cipher|privatekey).*")>
            case "${field.propertyName?j_string}" -> "${field.name?j_string}";
</#if>
</#list>
            default -> throw new IllegalArgumentException("排序字段无效");
        };
        QueryWrapper<${entity}> wrapper = new QueryWrapper<>();
        if (query.filters() != null) {
            query.filters().forEach((field, value) -> {
                String filterColumn = switch (field) {
<#list table.fields as field>
<#if !field.propertyName?lower_case?matches(".*(password|secret|token|credential|salt|hash|cipher|privatekey).*")>
                    case "${field.propertyName?j_string}" -> "${field.name?j_string}";
</#if>
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

    /** 读取基础值并补入原表单字段配置，不重新排序或根据宽度计算位置。 */
    @Override
    public List<FieldView> information(Long id, List<FieldConfig> configurations,
                                       Map<String, Object> extensionValues) {
        if (configurations == null) {
            throw new IllegalArgumentException("详情缺少已保存的表单字段配置");
        }
        ${entity} model = queryById(id);
        Map<String, Object> values = new LinkedHashMap<>();
<#list table.fields as field>
<#if !field.propertyName?lower_case?matches(".*(password|secret|token|credential|salt|hash|cipher|privatekey).*")>
        values.put("${field.propertyName?j_string}", model.get${field.capitalName}());
</#if>
</#list>
        if (extensionValues != null) {
            extensionValues.forEach((field, value) -> {
                if (field == null || field.toLowerCase(Locale.ROOT)
                        .matches(".*(password|secret|token|credential|salt|hash|cipher|privatekey).*")) {
                    throw new IllegalArgumentException("详情字段无效");
                }
                if (!values.containsKey(field)) {
                    values.put(field, value);
                }
            });
        }
        List<FieldView> fields = new ArrayList<>();
        for (FieldConfig item : configurations) {
            if (item == null || item.fieldName() == null || item.configuration() == null
                    || item.fieldName().toLowerCase(Locale.ROOT)
                    .matches(".*(password|secret|token|credential|salt|hash|cipher|privatekey).*")) {
                throw new IllegalArgumentException("详情字段配置无效");
            }
            fields.add(new FieldView(item.fieldName(),
                    Collections.unmodifiableMap(new LinkedHashMap<>(item.configuration())),
                    values.get(item.fieldName())));
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
