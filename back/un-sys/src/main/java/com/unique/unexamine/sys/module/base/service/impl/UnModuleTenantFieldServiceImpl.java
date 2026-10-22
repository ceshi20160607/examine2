package com.unique.unexamine.sys.module.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.unique.unexamine.sys.module.base.entity.UnModuleTenantField;
import com.unique.unexamine.sys.module.base.mapper.UnModuleTenantFieldMapper;
import com.unique.unexamine.sys.module.base.service.IUnModuleTenantFieldService;
import com.unique.unexamine.sys.module.base.service.IUnModuleTenantFieldService.FieldConfig;
import com.unique.unexamine.sys.module.base.service.IUnModuleTenantFieldService.FieldView;
import com.unique.unexamine.sys.module.base.service.IUnModuleTenantFieldService.PageQuery;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 租户对字段的扩展记录的基础 CRUD 实现；不在此推断租户权限或业务状态。 */
@Service
public class UnModuleTenantFieldServiceImpl extends ServiceImpl<UnModuleTenantFieldMapper, UnModuleTenantField>
        implements IUnModuleTenantFieldService {
    /** 查询条件及排序在 Service 内构建，字段只从生成时的数据库列白名单选择。 */
    @Override
    public Page<UnModuleTenantField> queryPageList(PageQuery query) {
        if (query == null || query.page() < 1 || query.size() < 1 || query.size() > 100) {
            throw new IllegalArgumentException("分页参数无效");
        }
        String column = switch (query.sortField() == null || query.sortField().isBlank()
                ? "id" : query.sortField()) {
            case "id" -> "id";
            case "moduleId" -> "module_id";
            case "tenantId" -> "tenant_id";
            case "fieldId" -> "field_id";
            case "status" -> "status";
            case "publishedVersion" -> "published_version";
            case "createTime" -> "create_time";
            case "updateTime" -> "update_time";
            default -> throw new IllegalArgumentException("排序字段无效");
        };
        QueryWrapper<UnModuleTenantField> wrapper = new QueryWrapper<>();
        if (query.filters() != null) {
            query.filters().forEach((field, value) -> {
                String filterColumn = switch (field) {
                    case "id" -> "id";
                    case "moduleId" -> "module_id";
                    case "tenantId" -> "tenant_id";
                    case "fieldId" -> "field_id";
                    case "status" -> "status";
                    case "publishedVersion" -> "published_version";
                    case "createTime" -> "create_time";
                    case "updateTime" -> "update_time";
                    default -> throw new IllegalArgumentException("筛选字段无效");
                };
                if (value == null) {
                    wrapper.isNull(filterColumn);
                } else {
                    wrapper.eq(filterColumn, value);
                }
            });
        }
        wrapper.orderBy(true, !query.descending(), column);
        return page(new Page<>(query.page(), query.size()), wrapper);
    }

    /** 新增或更新都检查写入结果，避免空实现掩盖失败。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public UnModuleTenantField addOrUpdate(UnModuleTenantField model) {
        if (model == null) {
            throw new IllegalArgumentException("保存数据不能为空");
        }
        if (model.getId() == null) {
            if (!save(model)) {
                throw new IllegalStateException("新增记录失败");
            }
        } else {
            queryById(model.getId());
            if (!updateById(model)) {
                throw new IllegalStateException("更新记录失败");
            }
        }
        return model;
    }

    /** 查无记录时抛出明确异常，业务服务可转换为本业务的 404 结果。 */
    @Override
    public UnModuleTenantField queryById(Long id) {
        if (id == null || id < 1) {
            throw new IllegalArgumentException("主键无效");
        }
        UnModuleTenantField model = getById(id);
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
        UnModuleTenantField model = queryById(id);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", model.getId());
        values.put("moduleId", model.getModuleId());
        values.put("tenantId", model.getTenantId());
        values.put("fieldId", model.getFieldId());
        values.put("status", model.getStatus());
        values.put("publishedVersion", model.getPublishedVersion());
        values.put("createTime", model.getCreateTime());
        values.put("updateTime", model.getUpdateTime());
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
