package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredContentFieldProtocolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode json(String source) throws Exception { return mapper.readTree(source); }

    @Test void richTextReconstructsOnlySafeMarkupAndValidatesVisibleLength() throws Exception {
        var config = json("{\"minLength\":2,\"maxLength\":30}");
        String safe = StructuredContentFieldProtocol.normalize("RICH_TEXT", config, mapper.valueToTree(
                "<h2 onclick='steal()'>合作方案</h2><script>alert(1)</script><style>body{}</style>"
                + "<p><b>正常内容</b><a href='javascript:alert(1)' onmouseover='steal()'>危险链接</a>"
                + "<a href='https://example.com/?a=1&b=2' target='_blank'>参考</a>"
                + "<img src=x onerror=steal()><iframe src=x>隐藏内容</iframe></p>")).textValue();
        assertThat(safe).contains("<h2>", "<b>正常内容</b>", "https://example.com/", "noopener noreferrer")
                .doesNotContain("onclick", "onmouseover", "javascript:", "<script", "alert(", "<style", "<img", "<iframe", "隐藏内容");
        assertThat(StructuredContentFieldProtocol.plainText(safe)).contains("合作方案", "正常内容");
        assertThatThrownBy(() -> StructuredContentFieldProtocol.normalize("RICH_TEXT", config,
                mapper.valueToTree("<p><b>一</b></p>"))).hasMessageContaining("长度不能少于");
        assertThat(StructuredContentFieldProtocol.normalize("RICH_TEXT", json("{}"),
                mapper.valueToTree("<script>alert(1)</script>")).textValue()).isEmpty();
    }

    @Test void recursiveSchemaProtectsRequiredTypesBoundsAndUnknownProperties() throws Exception {
        var config = json("""
                {"jsonSchema":{"type":"OBJECT","allowExtraProperties":false,"properties":[
                  {"key":"姓名","required":true,"schema":{"type":"STRING","minLength":2}},
                  {"key":"明细","schema":{"type":"ARRAY","maxItems":2,"items":{"type":"OBJECT","properties":[
                    {"key":"金额","required":true,"schema":{"type":"NUMBER","minimum":0,"maximum":100}},
                    {"key":"启用","required":true,"schema":{"type":"BOOLEAN"}}
                  ]}}}
                ]}}
                """);
        var valid = json("{\"姓名\":\"张三\",\"明细\":[{\"金额\":12.5,\"启用\":false}]}");
        StructuredContentFieldProtocol.validateConfiguration("JSON", config);
        assertThat(StructuredContentFieldProtocol.normalize("JSON", config, valid)).isEqualTo(valid).isNotSameAs(valid);
        for (String invalid : new String[]{"{}", "{\"姓名\":\" \"}", "{\"姓名\":\"张三\",\"陌生属性\":1}",
                "{\"姓名\":\"张三\",\"明细\":[{\"金额\":-1,\"启用\":true}]}",
                "{\"姓名\":\"张三\",\"明细\":[{\"金额\":1,\"启用\":\"false\"}]}"}) {
            assertThatThrownBy(() -> StructuredContentFieldProtocol.normalize("JSON", config, json(invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        var withDefault = config.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) withDefault).set("defaultValue", json("{}"));
        assertThatThrownBy(() -> StructuredContentFieldProtocol.validateConfiguration("JSON", withDefault))
                .hasMessageContaining("姓名必须填写");
    }

    @Test void unconfiguredObjectsAllowTypedExtrasButBoundRecursionAndReservedNames() throws Exception {
        var value = json("{\"文本\":\"内容\",\"数字\":2,\"是否\":false,\"多行\":[1,\"二\",{\"嵌套\":true}]}");
        assertThat(StructuredContentFieldProtocol.normalize("JSON", json("{}"), value)).isEqualTo(value);
        assertThatThrownBy(() -> StructuredContentFieldProtocol.normalize("JSON", json("{}"), json("{\"__proto__\":{}}")))
                .hasMessageContaining("保留属性");
        String deep = "0";
        for (int index = 0; index < 10; index++) deep = "{\"层\":" + deep + "}";
        JsonNode tooDeep = json(deep);
        assertThatThrownBy(() -> StructuredContentFieldProtocol.normalize("JSON", json("{}"), tooDeep)).hasMessageContaining("最多 8 层");
        assertThatThrownBy(() -> StructuredContentFieldProtocol.validateConfiguration("JSON", json("""
                {"jsonSchema":{"type":"OBJECT","properties":[
                {"key":"重复","schema":{"type":"STRING"}},{"key":"重复","schema":{"type":"NUMBER"}}]}}
                """))).hasMessageContaining("不能重复");
    }
}
