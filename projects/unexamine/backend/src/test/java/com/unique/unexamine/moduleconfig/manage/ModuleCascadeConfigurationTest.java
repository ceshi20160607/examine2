package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleVersion;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleVersionBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 模块级联的父级关系须来自目标模块当前发布版本，不能由未发布草稿或任意数字字段冒充。 */
class ModuleCascadeConfigurationTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ConfiguredModulePublicationBaseService publications = mock(ConfiguredModulePublicationBaseService.class);
    private final ConfiguredModuleVersionBaseService versions = mock(ConfiguredModuleVersionBaseService.class);
    private final ModuleConfigurationService configuration = new ModuleConfigurationService(
            null, null, null, null, null, null, publications, versions,
            null, null, null, json, null, null, null);

    @Test
    void acceptsPublishedSelfReferenceAndRejectsUnrelatedParent() throws Exception {
        ConfiguredModulePublication publication = new ConfiguredModulePublication();
        publication.setCurrentVersionId(201L);
        when(publications.selectList(any())).thenReturn(List.of(publication));
        ConfiguredModuleVersion version = new ConfiguredModuleVersion();
        version.setSnapshotJson(json.writeValueAsString(Map.of("fields", List.of(
                Map.of("code", "parent_product", "fieldType", "REFERENCE", "referenceModuleId", 102L),
                Map.of("code", "product_code", "fieldType", "TEXT", "uniqueValue", true, "searchable", true)))));
        when(versions.selectById(201L)).thenReturn(version);

        ReflectionTestUtils.invokeMethod(configuration, "validateModuleCascadeFields", 102L,
                json.valueToTree(Map.of("displayFieldCode", "title", "valueFieldCode", "product_code",
                        "parentFieldCode", "parent_product")));
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(configuration,
                "validateModuleCascadeFields", 103L, json.valueToTree(Map.of(
                        "displayFieldCode", "title", "valueFieldCode", "product_code",
                        "parentFieldCode", "parent_product"))))
                .isInstanceOfSatisfying(DomainException.class,
                        error -> assertThat(error.code()).isEqualTo("FIELD_CONFIG_INVALID"));
    }
}
