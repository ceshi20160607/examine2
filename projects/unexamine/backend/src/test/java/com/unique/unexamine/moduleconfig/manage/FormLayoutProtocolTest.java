package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class FormLayoutProtocolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Set<String> active = Set.of("name", "phone", "email");

    @Test void acceptsLegacyAndCompleteGroupedLayouts() throws Exception {
        assertThat(FormLayoutProtocol.validate(mapper.readTree("{}"), active)).isEmpty();
        assertThat(FormLayoutProtocol.validate(mapper.readTree("""
                {"fieldCodes":["name","phone","email"],"fieldSpans":{"name":2},"components":[
                 {"id":"identity","type":"SECTION","columns":2,"fieldCodes":["name"]},
                 {"id":"contact","type":"SECTION","columns":1,"fieldCodes":["phone","email"]}]}
                """), active)).isEmpty();
    }

    @Test void rejectsDuplicateUnknownAndInconsistentGroupReferences() throws Exception {
        var problems = FormLayoutProtocol.validate(mapper.readTree("""
                {"fieldCodes":["name","phone"],"components":[
                 {"id":"main","type":"SECTION","fieldCodes":["phone","name","name","missing"]}]}
                """), active);
        assertThat(problems).anyMatch(problem -> problem.message().contains("重复"))
                .anyMatch(problem -> problem.message().contains("不一致"));
    }

    @Test void rejectsFractionalColumnsAndInvalidSpanOwnership() throws Exception {
        var problems = FormLayoutProtocol.validate(mapper.readTree("""
                {"fieldCodes":["name"],"fieldSpans":{"name":0,"phone":2},"components":[
                 {"id":"main","type":"SECTION","columns":1.5,"fieldCodes":["name"]}]}
                """), active);
        assertThat(problems).extracting(FormLayoutProtocol.Problem::path)
                .contains("components.main.columns", "fieldSpans.name", "fieldSpans.phone");
        assertThat(FormLayoutProtocol.validate(mapper.readTree("{\"fieldSpans\":{\"name\":4294967297}}"), active)).isNotEmpty();
    }
}
