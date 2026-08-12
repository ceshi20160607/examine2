package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.error.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CanonicalFieldValueCodecTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalFieldValueCodec codec = new CanonicalFieldValueCodec(objectMapper);
    private final List<CanonicalFieldValueCodec.ValueOption> options = List.of(
            new CanonicalFieldValueCodec.ValueOption("101", "Root", null),
            new CanonicalFieldValueCodec.ValueOption("102", "Child", "101"),
            new CanonicalFieldValueCodec.ValueOption("103", "Other", null));

    @Test
    void normalizesEveryP4C1CanonicalType() throws Exception {
        assertValue("PERCENT", "12.3456", objectMapper.readTree("12.3456").decimalValue(), 1);

        var money = normalize("MONEY", "{\"amount\":\"12.30\",\"currency\":\"cny\"}",
                objectMapper.readTree("{\"currencies\":[\"CNY\",\"USD\"]}"));
        assertThat(money.value()).isEqualTo(new CanonicalFieldValueCodec.MoneyValue("12.30", "CNY"));
        assertThat(money.rows()).singleElement().satisfies(row -> {
            assertThat(row.decimalValue()).isEqualByComparingTo("12.30");
            assertThat(row.currencyCode()).isEqualTo("CNY");
        });

        assertValue("DATE_RANGE", "[\"2026-01-01\",\"2026-01-31\"]",
                List.of("2026-01-01", "2026-01-31"), 2);
        assertValue("TIME", "\"09:05:07\"", "09:05:07", 1);
        assertValue("TIME_RANGE", "[\"09:00:00\",\"18:00:00\"]",
                List.of("09:00:00", "18:00:00"), 2);
        assertValue("MULTI_SELECT", "[\"103\",\"101\"]", List.of("103", "101"), 2);
        assertValue("CASCADE", "[\"101\",\"102\"]", List.of("101", "102"), 2);
        assertValue("SWITCH", "true", true, 1);
        assertValue("RATING", "5", 5, 1);
        assertValue("PROGRESS", "99.25", objectMapper.readTree("99.25").decimalValue(), 1);
        assertValue("TAG", "[\"  \\uFF21  \",\"Beta\"]", List.of("A", "Beta"), 2);
    }

    @Test
    void rejectsEveryP4C1IllegalVector() throws Exception {
        assertInvalid("PERCENT", "100.00001");
        assertInvalid("PERCENT", "-0.01");
        assertInvalid("MONEY", "{\"amount\":\"1.001\",\"currency\":\"CNY\"}",
                objectMapper.readTree("{\"currencies\":[\"CNY\",\"USD\"]}"));
        assertInvalid("MONEY", "{\"amount\":\"1.00\",\"currency\":\"CNY\",\"extra\":true}",
                objectMapper.readTree("{\"currencies\":[\"CNY\"]}"));
        assertInvalid("DATE_RANGE", "[\"2026-02-01\",\"2026-01-01\"]");
        assertInvalid("TIME", "\"9:00\"");
        assertInvalid("TIME", "\"24:00:00\"");
        assertInvalid("TIME_RANGE", "[\"18:00:00\",\"09:00:00\"]");
        assertInvalid("MULTI_SELECT", "[\"101\",\"101\"]");
        assertInvalid("CASCADE", "[\"101\",\"103\"]");
        assertInvalid("SWITCH", "\"true\"");
        assertInvalid("RATING", "0");
        assertInvalid("RATING", "1.5");
        assertInvalid("PROGRESS", "100.001");
        assertInvalid("TAG", "[\"A\",\"\\uFF21\"]");
    }

    @Test
    void normalizesEveryP4C2CanonicalType() throws Exception {
        assertValue("PHONE", "[\"138 1234 5678\"]", List.of("+8613812345678"), 1,
                "{\"defaultCountry\":\"CN\"}");
        assertValue("EMAIL", "[\" A@EXAMPLE.COM \"]", List.of("A@example.com"), 1);
        assertValue("URL", "\"HTTPS://EXAMPLE.COM\"", "https://example.com/", 1);

        var identity = normalize("IDENTITY", "\" 110101199001011237 \"",
                objectMapper.readTree("{\"identityKind\":\"CN_RESIDENT_ID\"}"));
        assertThat(identity.value()).isEqualTo("110101199001011237");
        assertThat(identity.display()).isEqualTo("**************1237");
        assertThat(identity.rows().getFirst().sensitiveValue()).isEqualTo("110101199001011237");

        var address = normalize("ADDRESS", "{\"countryCode\":\"cn\",\"display\":\"北京市\"}",
                objectMapper.createObjectNode());
        assertThat(address.value()).isEqualTo(objectMapper.readTree(
                "{\"countryCode\":\"CN\",\"display\":\"北京市\"}"));

        var geo = objectMapper.createObjectNode();
        geo.put("lat", new BigDecimal("39.9"));
        geo.put("lng", new BigDecimal("116.4"));
        assertValue("GEO", "{\"lat\":39.9000,\"lng\":116.4000}", geo, 1);
        assertValue("BARCODE", "{\"symbology\":\"EAN13\",\"payload\":\"4006381333931\"}",
                objectMapper.readTree("{\"symbology\":\"EAN13\",\"payload\":\"4006381333931\"}"), 1,
                "{\"symbologies\":[\"CODE128\",\"EAN13\"]}");

        var richText = normalize("RICH_TEXT", "\"<p>Hello</p><script>x</script>\"",
                objectMapper.createObjectNode());
        assertThat(richText.value()).isEqualTo("<p>Hello</p>x");

        var json = normalize("JSON", "{\"b\":1,\"a\":2}", objectMapper.createObjectNode());
        assertThat(json.rows().getFirst().textValue()).isEqualTo("{\"a\":2,\"b\":1}");

        var secret = normalize("SECRET", "\"secret-value\"", objectMapper.createObjectNode());
        assertThat(secret.value()).isEqualTo("secret-value");
        assertThat(secret.display()).isEqualTo("********");
        assertValue("STATUS", "\"101\"", "101", 1);
    }

    @Test
    void rejectsEveryP4C2IllegalVector() throws Exception {
        assertInvalid("PHONE", "[\"123\"]", "{\"defaultCountry\":\"CN\"}");
        assertInvalid("EMAIL", "[\"a@\"]");
        assertInvalid("URL", "\"javascript:alert(1)\"");
        assertInvalid("IDENTITY", "\"x\"", "{\"identityKind\":\"CN_RESIDENT_ID\"}");
        assertInvalid("IDENTITY", "\"110101199001011234\"", "{\"identityKind\":\"CN_RESIDENT_ID\"}");
        assertInvalid("ADDRESS", "{\"countryCode\":\"XX\",\"display\":\"x\"}");
        assertInvalid("GEO", "{\"lat\":91,\"lng\":0}");
        assertInvalid("BARCODE", "{\"symbology\":\"EAN13\",\"payload\":\"123\"}",
                "{\"symbologies\":[\"EAN13\"]}");
        assertInvalid("JSON", nestedJson(11));
        assertInvalid("SECRET", "\"\"");
        assertInvalid("STATUS", "\"999\"");
    }

    private void assertValue(String type, String json, Object expected, int rowCount) throws Exception {
        var normalized = normalize(type, json, objectMapper.createObjectNode());
        assertThat(normalized.value()).isEqualTo(expected);
        assertThat(normalized.rows()).hasSize(rowCount);
        for (var index = 0; index < normalized.rows().size(); index++) {
            assertThat(normalized.rows().get(index).ordinal()).isEqualTo(index);
        }
    }

    private void assertValue(String type, String json, Object expected, int rowCount, String schema) throws Exception {
        var normalized = normalize(type, json, objectMapper.readTree(schema));
        assertThat(normalized.value()).isEqualTo(expected);
        assertThat(normalized.rows()).hasSize(rowCount);
    }

    private CanonicalFieldValueCodec.CanonicalValue normalize(String type, String json, JsonNode schema)
            throws Exception {
        return codec.normalize(new CanonicalFieldValueCodec.FieldContract(
                "field", "Field", type, (ObjectNode) schema, options), objectMapper.readTree(json));
    }

    private void assertInvalid(String type, String json) throws Exception {
        assertInvalid(type, json, objectMapper.createObjectNode());
    }

    private void assertInvalid(String type, String json, JsonNode schema) throws Exception {
        assertThatThrownBy(() -> normalize(type, json, schema))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code()).isEqualTo("FIELD_VALUE_INVALID"));
    }

    private void assertInvalid(String type, String json, String schema) throws Exception {
        assertInvalid(type, json, objectMapper.readTree(schema));
    }

    private static String nestedJson(int depth) {
        return "{\"a\":".repeat(depth) + "1" + "}".repeat(depth);
    }
}
