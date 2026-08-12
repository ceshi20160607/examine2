package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class DerivedFieldContractServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DerivedFieldContractService contracts = new DerivedFieldContractService(objectMapper);

    @Test
    void freezesTypedDependenciesChecksumsAndTopologicalRanks() throws Exception {
        var result = contracts.analyze(objectMapper.readTree("""
                {"fields":[
                  {"id":"101","module_id":"10","field_type":"NUMBER","is_required":false,"is_readonly":false,"property_json":{}},
                  {"id":"102","module_id":"10","field_type":"NUMBER","is_required":false,"is_readonly":false,"property_json":{}},
                  {"id":"201","module_id":"10","field_type":"FORMULA","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"DECIMAL","astVersion":1,"expressionAst":{"op":"MULTIPLY","args":[{"fieldId":"101"},{"fieldId":"102"}]}}},
                  {"id":"202","module_id":"10","field_type":"FORMULA","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"DECIMAL","astVersion":1,"expressionAst":{"op":"ADD","args":[{"fieldId":"201"},{"literalType":"INTEGER","value":1}]}}}
                ]}
                """));

        assertThat(result.issues()).isEmpty();
        assertThat(result.metadataByField()).containsOnlyKeys(201L, 202L);
        assertThat(result.metadataByField().get(201L).topologicalRank()).isZero();
        assertThat(result.metadataByField().get(202L).topologicalRank()).isEqualTo(1);
        assertThat(result.metadataByField().get(201L).expressionChecksum()).matches("[a-f0-9]{64}");
        assertThat(result.metadataByField().get(201L).dependencies())
                .extracting(DerivedFieldContractService.Dependency::fieldId)
                .containsExactly("101", "102");
    }

    @Test
    void validatesRelationLookupSummaryAndSubtableAggregateSources() throws Exception {
        var result = contracts.analyze(objectMapper.readTree("""
                {"fields":[
                  {"id":"110","module_id":"10","target_module_id":"20","field_type":"RELATION","is_readonly":false,"property_json":{"multiple":true}},
                  {"id":"120","module_id":"10","target_module_id":"30","field_type":"SUBTABLE","is_readonly":false,
                   "property_json":{"aggregates":[{"id":"lineTotal","function":"SUM","columnFieldId":"301"}]}},
                  {"id":"201","module_id":"20","field_type":"NUMBER","is_readonly":false,"property_json":{}},
                  {"id":"202","module_id":"20","field_type":"TEXT","is_readonly":false,"property_json":{}},
                  {"id":"301","module_id":"30","field_type":"NUMBER","is_readonly":false,"property_json":{}},
                  {"id":"401","module_id":"10","field_type":"SUMMARY","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"DECIMAL","relationFieldId":"110","targetFieldId":"201","reduction":"SUM"}},
                  {"id":"402","module_id":"10","field_type":"LOOKUP","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"STRING","relationFieldId":"110","targetFieldId":"202","distinct":false}},
                  {"id":"403","module_id":"10","field_type":"AGGREGATE","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"DECIMAL","subtableFieldId":"120","aggregateId":"lineTotal"}}
                ]}
                """));

        assertThat(result.issues()).isEmpty();
        assertThat(result.metadataByField()).containsOnlyKeys(401L, 402L, 403L);
        assertThat(result.metadataByField().get(402L).dependencies())
                .extracting(DerivedFieldContractService.Dependency::kind)
                .containsExactly("RELATION", "TARGET_FIELD");
    }

    @Test
    void rejectsCyclesTypeMismatchMissingTargetsAndEditableDerivedFields() throws Exception {
        var result = contracts.analyze(objectMapper.readTree("""
                {"fields":[
                  {"id":"201","module_id":"10","field_type":"FORMULA","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"DECIMAL","astVersion":1,"expressionAst":{"fieldId":"202"}}},
                  {"id":"202","module_id":"10","field_type":"FORMULA","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"DECIMAL","astVersion":1,"expressionAst":{"fieldId":"201"}}},
                  {"id":"203","module_id":"10","field_type":"FORMULA","is_required":true,"is_readonly":false,
                   "property_json":{"resultSchema":"BOOLEAN","astVersion":1,"expressionAst":{"literalType":"INTEGER","value":1}}},
                  {"id":"204","module_id":"10","field_type":"LOOKUP","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"STRING","relationFieldId":"999","targetFieldId":"998"}}
                ]}
                """));

        assertThat(result.issues()).extracting(DerivedFieldContractService.ContractIssue::code)
                .contains("SCHEMA_CYCLE", "SCHEMA_TYPE", "DEPENDENCY_UNREACHABLE");
        assertThat(result.metadataByField()).isEmpty();
    }

    @Test
    void publishesAiFillSourcesAndRejectsHiddenCrossModuleSecretAndDerivedSources() throws Exception {
        var valid = contracts.analyze(objectMapper.readTree("""
                {"fields":[
                  {"id":"101","module_id":"10","field_type":"TEXT","is_readonly":false,"property_json":{}},
                  {"id":"102","module_id":"10","field_type":"NUMBER","is_readonly":false,"property_json":{}},
                  {"id":"201","module_id":"10","field_type":"AI_FILL","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"STRING","sourceFieldIds":["101","102"],
                   "promptTemplate":"p","modelPolicy":"SYSTEM_DEFAULT","minConfidence":0.80,"overwriteMode":"CONFIRM"}}
                ]}
                """));

        assertThat(valid.issues()).isEmpty();
        assertThat(valid.metadataByField().get(201L).dependencies())
                .extracting(DerivedFieldContractService.Dependency::kind,
                        DerivedFieldContractService.Dependency::fieldId)
                .containsExactly(tuple("AI_SOURCE", "101"), tuple("AI_SOURCE", "102"));

        var invalid = contracts.analyze(objectMapper.readTree("""
                {"fields":[
                  {"id":"101","module_id":"10","field_type":"SECRET","property_json":{}},
                  {"id":"102","module_id":"10","field_type":"TEXT","is_hidden":true,"property_json":{}},
                  {"id":"103","module_id":"11","field_type":"TEXT","property_json":{}},
                  {"id":"104","module_id":"10","field_type":"FORMULA","is_readonly":true,
                   "property_json":{"resultSchema":"STRING","astVersion":1,"expressionAst":{"literalType":"STRING","value":"x"}}},
                  {"id":"201","module_id":"10","field_type":"AI_FILL","is_required":false,"is_readonly":true,
                   "property_json":{"resultSchema":"STRING","sourceFieldIds":["101","102","103","104"],
                   "promptTemplate":"p","modelPolicy":"SYSTEM_DEFAULT","minConfidence":0.80,"overwriteMode":"NEVER"}}
                ]}
                """));

        assertThat(invalid.issues()).extracting(DerivedFieldContractService.ContractIssue::code)
                .contains("AI_FILL_SOURCE_INVALID");
        assertThat(invalid.metadataByField()).doesNotContainKey(201L);
    }
}
