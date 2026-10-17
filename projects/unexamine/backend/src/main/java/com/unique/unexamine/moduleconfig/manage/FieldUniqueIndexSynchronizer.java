package com.unique.unexamine.moduleconfig.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndex;
import com.unique.unexamine.moduleconfig.base.entity.CfgQueryIndexField;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleField;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexBaseService;
import com.unique.unexamine.moduleconfig.base.service.CfgQueryIndexFieldBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/** Keeps the field-level unique switch on the shared query-index runtime path. */
@Service
public class FieldUniqueIndexSynchronizer {
    static final String CODE_PREFIX = "field_unique_";

    private final CfgQueryIndexBaseService indexService;
    private final CfgQueryIndexFieldBaseService indexFieldService;

    public FieldUniqueIndexSynchronizer(
            CfgQueryIndexBaseService indexService,
            CfgQueryIndexFieldBaseService indexFieldService) {
        this.indexService = indexService;
        this.indexFieldService = indexFieldService;
    }

    public void sync(ConfiguredModuleField field) {
        String code = CODE_PREFIX + field.getId();
        CfgQueryIndex index = indexService.selectList(Wrappers.<CfgQueryIndex>lambdaQuery()
                        .eq(CfgQueryIndex::getModuleId, field.getModuleId())
                        .eq(CfgQueryIndex::getCode, code))
                .stream().findFirst().orElse(null);
        boolean enabled = Boolean.TRUE.equals(field.getUniqueValue()) && "ACTIVE".equals(field.getStatus());
        if (!enabled) {
            disable(index);
            return;
        }
        if (!ModuleRuleConfigurationService.indexable(field.getFieldType())) {
            throw new DomainException("FIELD_UNIQUE_UNSUPPORTED",
                    "字段类型 " + field.getFieldType() + " 不支持唯一值约束", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (index == null) {
            index = new CfgQueryIndex();
            index.setSystemId(field.getSystemId());
            index.setOwnerTenantId(field.getOwnerTenantId());
            index.setModuleId(field.getModuleId());
            index.setCode(code);
            index.setName(field.getName() + "唯一值");
            index.setUniqueIndex(true);
            index.setStatus("ACTIVE");
            index.setVersion(0);
            indexService.insert(index);
        } else if (!Objects.equals(index.getName(), field.getName() + "唯一值")
                || !Boolean.TRUE.equals(index.getUniqueIndex()) || !"ACTIVE".equals(index.getStatus())) {
            index.setName(field.getName() + "唯一值");
            index.setUniqueIndex(true);
            index.setStatus("ACTIVE");
            if (indexService.updateById(index) == 0) {
                throw new DomainException("DRAFT_VERSION_CONFLICT", "唯一值规则已被其他操作修改", HttpStatus.CONFLICT);
            }
        }
        ensureSingleField(field.getModuleId(), index.getId(), field.getId());
    }

    static boolean managed(CfgQueryIndex index) {
        return index != null && index.getCode() != null && index.getCode().startsWith(CODE_PREFIX);
    }

    private void disable(CfgQueryIndex index) {
        if (index == null || "DISABLED".equals(index.getStatus())) return;
        index.setStatus("DISABLED");
        if (indexService.updateById(index) == 0) {
            throw new DomainException("DRAFT_VERSION_CONFLICT", "唯一值规则已被其他操作修改", HttpStatus.CONFLICT);
        }
    }

    /** 索引字段行也保存所属模块，供后续按模块发布和清理。 */
    private void ensureSingleField(Long moduleId, Long indexId, Long fieldId) {
        List<CfgQueryIndexField> definitions = indexFieldService.selectList(
                Wrappers.<CfgQueryIndexField>lambdaQuery().eq(CfgQueryIndexField::getQueryIndexId, indexId));
        if (definitions.size() == 1 && Objects.equals(definitions.get(0).getFieldId(), fieldId)
                && Objects.equals(definitions.get(0).getSortOrder(), 0)
                && "ASC".equals(definitions.get(0).getSortDirection())) return;
        definitions.forEach(definition -> indexFieldService.deleteById(definition.getId()));
        CfgQueryIndexField definition = new CfgQueryIndexField();
        definition.setModuleId(moduleId);
        definition.setQueryIndexId(indexId);
        definition.setFieldId(fieldId);
        definition.setSortOrder(0);
        definition.setSortDirection("ASC");
        indexFieldService.insert(definition);
    }
}
