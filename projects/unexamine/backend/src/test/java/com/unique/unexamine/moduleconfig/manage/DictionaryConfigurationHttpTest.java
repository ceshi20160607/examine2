package com.unique.unexamine.moduleconfig.manage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DictionaryConfigurationHttpTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("unexamine")
            .withUsername("unexamine")
            .withPassword("unexamine_test")
            .withCommand("--log-bin-trust-function-creators=1");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void administratorPublishesVersionedHierarchicalDictionaryAndModuleUsesOnlyPublishedSnapshot() {
        Session owner = register("dictionary_owner_a", "dictionary-system-a", "dictionary-a@example.com");
        Session outsider = register("dictionary_owner_b", "dictionary-system-b", "dictionary-b@example.com");

        ResponseEntity<Map> created = exchange("/api/admin/dictionaries", HttpMethod.POST, owner.token(),
                Map.of("code", "region", "name", "行政区划", "hierarchical", true));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.OK);
        long dictionaryId = number(((Map<?, ?>) data(created).get("dictionary")).get("id"));

        ResponseEntity<Map> runtimeBeforePublish = exchange("/api/runtime/dictionaries/region", HttpMethod.GET,
                owner.token(), null);
        assertThat(runtimeBeforePublish.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> provinceCreated = exchange("/api/admin/dictionaries/" + dictionaryId + "/items",
                HttpMethod.POST, owner.token(), Map.of("code", "zhejiang", "label", "浙江省", "color", "#1677ff", "sortOrder", 10));
        long provinceId = number(data(provinceCreated).get("id"));
        int provinceVersion = ((Number) data(provinceCreated).get("version")).intValue();
        ResponseEntity<Map> cityCreated = exchange("/api/admin/dictionaries/" + dictionaryId + "/items",
                HttpMethod.POST, owner.token(), Map.of("parentId", provinceId, "code", "hangzhou", "label", "杭州市", "sortOrder", 10));
        long cityId = number(data(cityCreated).get("id"));
        int cityVersion = ((Number) data(cityCreated).get("version")).intValue();
        assertThat(data(cityCreated).get("pathCode")).isEqualTo("zhejiang,hangzhou");

        ResponseEntity<Map> cycle = exchange("/api/admin/dictionaries/" + dictionaryId + "/items/" + provinceId,
                HttpMethod.PUT, owner.token(), Map.of("parentId", cityId, "label", "浙江省", "color", "#1677ff",
                        "sortOrder", 10, "status", "ACTIVE", "version", provinceVersion));
        assertThat(cycle.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(cycle.getBody().get("code")).isEqualTo("DICTIONARY_HIERARCHY_CYCLE");

        ResponseEntity<Map> check = exchange("/api/admin/dictionaries/" + dictionaryId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        assertThat(data(check).get("valid")).isEqualTo(true);
        int firstDraftRevision = ((Number) data(check).get("draftRevision")).intValue();
        ResponseEntity<Map> firstPublish = exchange("/api/admin/dictionaries/" + dictionaryId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", firstDraftRevision));
        assertThat(firstPublish.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(data(firstPublish).get("versionNumber")).isEqualTo(1);

        ResponseEntity<Map> topLevel = exchange("/api/runtime/dictionaries/region", HttpMethod.GET, owner.token(), null);
        assertThat(data(topLevel).get("versionNumber")).isEqualTo(1);
        assertThat(labels(topLevel)).containsExactly("浙江省");
        ResponseEntity<Map> secondLevel = exchange("/api/runtime/dictionaries/region?parentId=" + provinceId,
                HttpMethod.GET, owner.token(), null);
        assertThat(labels(secondLevel)).containsExactly("杭州市");

        ResponseEntity<Map> renamed = exchange("/api/admin/dictionaries/" + dictionaryId + "/items/" + cityId,
                HttpMethod.PUT, owner.token(), Map.of("parentId", provinceId, "label", "杭州", "color", "#52c41a",
                        "sortOrder", 10, "status", "ACTIVE", "version", cityVersion));
        assertThat(renamed.getStatusCode()).isEqualTo(HttpStatus.OK);
        int renamedVersion = ((Number) data(renamed).get("version")).intValue();
        ResponseEntity<Map> runtimeStillV1 = exchange("/api/runtime/dictionaries/region?parentId=" + provinceId,
                HttpMethod.GET, owner.token(), null);
        assertThat(labels(runtimeStillV1)).containsExactly("杭州市");

        ResponseEntity<Map> secondCheck = exchange("/api/admin/dictionaries/" + dictionaryId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        int secondDraftRevision = ((Number) data(secondCheck).get("draftRevision")).intValue();
        ResponseEntity<Map> secondPublish = exchange("/api/admin/dictionaries/" + dictionaryId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", secondDraftRevision));
        assertThat(data(secondPublish).get("versionNumber")).isEqualTo(2);
        ResponseEntity<Map> runtimeV2 = exchange("/api/runtime/dictionaries/region?parentId=" + provinceId,
                HttpMethod.GET, owner.token(), null);
        assertThat(labels(runtimeV2)).containsExactly("杭州");

        ResponseEntity<Map> group = exchange("/api/admin/module-config/groups", HttpMethod.POST, owner.token(),
                Map.of("code", "sales", "name", "销售", "sortOrder", 10));
        long groupId = number(data(group).get("id"));
        ResponseEntity<Map> module = exchange("/api/admin/module-config/modules", HttpMethod.POST, owner.token(),
                Map.of("groupId", groupId, "code", "customer_region", "name", "客户区域"));
        long moduleId = number(((Map<?, ?>) data(module).get("module")).get("id"));
        ResponseEntity<Map> cascadeField = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                HttpMethod.POST, owner.token(), Map.of("code", "region", "name", "区域", "fieldType", "CASCADE",
                        "required", true, "searchable", true, "dictionaryId", dictionaryId, "sortOrder", 10,
                        "config", Map.of("maxDepth", 3, "allowIntermediate", false, "saveMode", "PATH")));
        assertThat(cascadeField.getStatusCode()).isEqualTo(HttpStatus.OK);
        long fieldId = number(data(cascadeField).get("id"));

        ResponseEntity<Map> moduleCheck = exchange("/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        assertThat(data(moduleCheck).get("valid")).isEqualTo(true);
        int moduleRevision = ((Number) data(moduleCheck).get("draftRevision")).intValue();
        ResponseEntity<Map> modulePublish = exchange("/api/admin/module-config/modules/" + moduleId + "/publish",
                HttpMethod.POST, owner.token(), Map.of("expectedDraftRevision", moduleRevision));
        long moduleVersionId = number(data(modulePublish).get("versionId"));

        Long systemId = jdbc.queryForObject("select system_id from cfg_module where id=?", Long.class, moduleId);
        Long tenantId = jdbc.queryForObject("select owner_tenant_id from cfg_module where id=?", Long.class, moduleId);
        Long memberId = jdbc.queryForObject("select min(id) from sys_member where system_id=?", Long.class, systemId);
        jdbc.update("insert into biz_record(system_id,tenant_id,module_id,created_config_version_id,updated_config_version_id,"
                        + "record_number,title,status,created_by_member_id,updated_by_member_id) values(?,?,?,?,?,?,?,?,?,?)",
                systemId, tenantId, moduleId, moduleVersionId, moduleVersionId, "C22-001", "浙江客户", "ACTIVE", memberId, memberId);
        Long recordId = jdbc.queryForObject("select max(id) from biz_record where module_id=?", Long.class, moduleId);
        jdbc.update("insert into biz_record_value(record_id,field_id,field_code,value_type,value_text,normalized_text) "
                        + "values(?,?,?,?,?,?)", recordId, fieldId, "region", "TEXT", "hangzhou", "hangzhou");

        ResponseEntity<Map> disableInUse = exchange("/api/admin/dictionaries/" + dictionaryId + "/items/" + cityId,
                HttpMethod.PUT, owner.token(), Map.of("parentId", provinceId, "label", "杭州", "color", "#52c41a",
                        "sortOrder", 10, "status", "DISABLED", "version", renamedVersion));
        assertThat(disableInUse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(disableInUse.getBody().get("code")).isEqualTo("DICTIONARY_ITEM_IN_USE");

        ResponseEntity<Map> unpublished = exchange("/api/admin/dictionaries", HttpMethod.POST, owner.token(),
                Map.of("code", "unpublished_status", "name", "未发布状态", "hierarchical", false));
        long unpublishedId = number(((Map<?, ?>) data(unpublished).get("dictionary")).get("id"));
        exchange("/api/admin/dictionaries/" + unpublishedId + "/items", HttpMethod.POST, owner.token(),
                Map.of("code", "active", "label", "启用", "sortOrder", 10));
        ResponseEntity<Map> secondField = exchange("/api/admin/module-config/modules/" + moduleId + "/fields",
                HttpMethod.POST, owner.token(), Map.of("code", "unpublished", "name", "未发布选项", "fieldType", "SINGLE_SELECT",
                        "required", false, "dictionaryId", unpublishedId, "sortOrder", 20, "config", Map.of()));
        assertThat(secondField.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<Map> unpublishedCheck = exchange("/api/admin/module-config/modules/" + moduleId + "/publication-check",
                HttpMethod.GET, owner.token(), null);
        assertThat(((List<Map<String, Object>>) data(unpublishedCheck).get("issues")).stream().map(item -> item.get("code")))
                .contains("DICTIONARY_NOT_PUBLISHED");

        ResponseEntity<Map> versions = exchange("/api/admin/dictionaries/" + dictionaryId + "/versions",
                HttpMethod.GET, owner.token(), null);
        assertThat((List<?>) versions.getBody().get("data")).hasSize(2);
        assertThat(count("cfg_dictionary_version", "dictionary_id=" + dictionaryId)).isEqualTo(2);
        assertThat(count("cfg_dictionary_publication", "dictionary_id=" + dictionaryId)).isOne();
        assertThat(count("audit_event", "event_code='DICTIONARY_PUBLISHED' and object_type='DICTIONARY_VERSION'")).isEqualTo(2);

        ResponseEntity<Map> crossContext = exchange("/api/admin/dictionaries/" + dictionaryId, HttpMethod.GET,
                outsider.token(), null);
        assertThat(crossContext.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private Session register(String username, String systemCode, String email) {
        ResponseEntity<Map> response = http.postForEntity("/api/auth/register", Map.of(
                "username", username, "password", "correct-password", "displayName", username,
                "email", email, "systemName", systemCode, "systemCode", systemCode), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return new Session((String) ((Map<?, ?>) data(response).get("tokens")).get("accessToken"));
    }

    private ResponseEntity<Map> exchange(String path, HttpMethod method, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return http.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private List<String> labels(ResponseEntity<Map> response) {
        return ((List<Map<String, Object>>) data(response).get("items")).stream()
                .map(item -> item.get("label").toString()).toList();
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }

    private int count(String table, String where) {
        return jdbc.queryForObject("select count(*) from " + table + " where " + where, Integer.class);
    }

    private record Session(String token) {
    }
}
