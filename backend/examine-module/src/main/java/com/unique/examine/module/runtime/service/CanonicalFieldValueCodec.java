package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.unique.examine.core.api.ApiError;
import com.unique.examine.core.error.BusinessException;
import org.apache.commons.validator.routines.EmailValidator;
import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;
import org.jsoup.safety.Safelist;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.IDN;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
final class CanonicalFieldValueCodec {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Set<String> MONEY_KEYS = Set.of("amount", "currency");
    private static final Set<String> ADDRESS_KEYS = Set.of(
            "countryCode", "display", "regionCode", "city", "district", "postalCode", "detail");
    private static final Set<String> GEO_KEYS = Set.of("lat", "lng");
    private static final Set<String> BARCODE_KEYS = Set.of("symbology", "payload");
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());
    private static final Pattern CN_ID = Pattern.compile(
            "^[1-9][0-9]{5}(18|19|20)[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx]$");
    private static final int[] CN_ID_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CN_ID_CHECKS = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
    private static final Safelist RICH_TEXT_ALLOWLIST = Safelist.basic()
            .addTags("p", "h1", "h2", "h3", "ul", "ol", "li", "blockquote", "pre", "code", "br", "hr")
            .addAttributes("a", "target")
            .addProtocols("a", "href", "http", "https", "mailto");

    private final ObjectMapper objectMapper;
    private final PhoneNumberUtil phoneNumbers;

    CanonicalFieldValueCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.phoneNumbers = PhoneNumberUtil.getInstance();
    }

    CanonicalValue normalize(FieldContract field, JsonNode node) {
        try {
            return switch (field.type()) {
                case "TEXT" -> text(field, node, Math.min(field.schema().path("maxLength").asInt(4000), 4000), false);
                case "TEXTAREA" -> text(field, node,
                        Math.min(field.schema().path("maxLength").asInt(65535), 65535), true);
                case "PHONE" -> phones(field, node);
                case "EMAIL" -> emails(field, node);
                case "URL" -> url(field, node);
                case "IDENTITY" -> identity(field, node);
                case "NUMBER" -> decimal(field, node, null, null, 10);
                case "PERCENT" -> decimal(field, node, BigDecimal.ZERO, new BigDecimal("100"), 4);
                case "PROGRESS" -> decimal(field, node, BigDecimal.ZERO, new BigDecimal("100"), 2);
                case "RATING" -> rating(field, node);
                case "MONEY" -> money(field, node);
                case "DATE" -> date(field, node);
                case "DATETIME" -> dateTime(field, node);
                case "DATE_RANGE" -> dateRange(field, node);
                case "TIME" -> time(field, node);
                case "TIME_RANGE" -> timeRange(field, node);
                case "RADIO", "MEMBER", "DEPARTMENT" -> reference(field, node);
                case "MULTI_SELECT" -> multiSelect(field, node);
                case "CASCADE" -> cascade(field, node);
                case "SWITCH" -> switchValue(field, node);
                case "TAG" -> tags(field, node);
                case "ADDRESS" -> address(field, node);
                case "GEO" -> geo(field, node);
                case "BARCODE" -> barcode(field, node);
                case "RICH_TEXT" -> richText(field, node);
                case "JSON" -> json(field, node);
                case "SECRET" -> secret(field, node);
                case "STATUS" -> status(field, node);
                default -> throw new IllegalStateException("Unsupported runtime field type " + field.type());
            };
        } catch (DateTimeParseException | ArithmeticException | URISyntaxException exception) {
            throw invalid(field, "has an invalid value format");
        }
    }

    private CanonicalValue phones(FieldContract field, JsonNode node) {
        requireArray(field, node, 10);
        var region = field.schema().path("defaultCountry").asText("").toUpperCase(Locale.ROOT);
        var values = new ArrayList<String>();
        var rows = new ArrayList<ValueRow>();
        var seen = new HashSet<String>();
        for (var index = 0; index < node.size(); index++) {
            var item = node.get(index);
            requireText(field, item);
            try {
                var raw = item.textValue().trim();
                var parsed = phoneNumbers.parse(raw, raw.startsWith("+") ? null : region);
                if (!phoneNumbers.isValidNumber(parsed)) {
                    throw invalid(field, "contains an invalid phone number");
                }
                var value = phoneNumbers.format(parsed, PhoneNumberUtil.PhoneNumberFormat.E164);
                if (!seen.add(value)) {
                    throw invalid(field, "phone numbers must be unique after normalization");
                }
                values.add(value);
                rows.add(ValueRow.string(index, value, value));
            } catch (NumberParseException exception) {
                throw invalid(field, "contains an invalid phone number");
            }
        }
        return new CanonicalValue(List.copyOf(values), String.join(", ", values), List.copyOf(rows));
    }

    private CanonicalValue emails(FieldContract field, JsonNode node) {
        requireArray(field, node, 10);
        var values = new ArrayList<String>();
        var rows = new ArrayList<ValueRow>();
        var seen = new HashSet<String>();
        var validator = EmailValidator.getInstance(false, false);
        for (var index = 0; index < node.size(); index++) {
            var item = node.get(index);
            requireText(field, item);
            var supplied = item.textValue().trim();
            var at = supplied.lastIndexOf('@');
            if (at <= 0 || at == supplied.length() - 1 || supplied.length() > 254
                    || !validator.isValid(supplied)) {
                throw invalid(field, "contains an invalid email address");
            }
            var value = supplied.substring(0, at) + "@" + supplied.substring(at + 1).toLowerCase(Locale.ROOT);
            if (!seen.add(value)) {
                throw invalid(field, "email addresses must be unique after normalization");
            }
            values.add(value);
            rows.add(ValueRow.string(index, value, value));
        }
        return new CanonicalValue(List.copyOf(values), String.join(", ", values), List.copyOf(rows));
    }

    private CanonicalValue url(FieldContract field, JsonNode node) throws URISyntaxException {
        requireText(field, node);
        var supplied = node.textValue().trim();
        if (supplied.length() > 2048) {
            throw invalid(field, "must not exceed 2048 characters");
        }
        var parsed = new URI(supplied);
        var scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
        if (!Set.of("http", "https").contains(scheme) || parsed.getHost() == null || parsed.getUserInfo() != null) {
            throw invalid(field, "must be an absolute HTTP or HTTPS URL without credentials");
        }
        var host = IDN.toASCII(parsed.getHost()).toLowerCase(Locale.ROOT);
        var port = parsed.getPort();
        if ("http".equals(scheme) && port == 80 || "https".equals(scheme) && port == 443) {
            port = -1;
        }
        var path = parsed.getRawPath() == null || parsed.getRawPath().isEmpty() ? "/" : parsed.getRawPath();
        var value = new URI(scheme, null, host, port, path, parsed.getRawQuery(), parsed.getRawFragment())
                .normalize().toASCIIString();
        return new CanonicalValue(value, value, List.of(ValueRow.string(0, value, value)));
    }

    private CanonicalValue identity(FieldContract field, JsonNode node) {
        requireText(field, node);
        var value = node.textValue().trim();
        if (value.isEmpty() || value.length() > 128) {
            throw invalid(field, "must contain 1..128 characters");
        }
        var kind = field.schema().path("identityKind").asText();
        if ("CN_RESIDENT_ID".equals(kind)) {
            value = value.toUpperCase(Locale.ROOT);
            if (!validCnIdentity(value)) {
                throw invalid(field, "contains an invalid CN resident identity number");
            }
        } else if ("GENERIC".equals(kind)) {
            var expression = field.schema().path("pattern").asText();
            try {
                if (expression.isBlank() || !Pattern.compile(expression).matcher(value).matches()) {
                    throw invalid(field, "does not match the published identity pattern");
                }
            } catch (PatternSyntaxException exception) {
                throw invalid(field, "has an invalid published identity pattern");
            }
        } else {
            throw invalid(field, "has no supported identity kind");
        }
        var display = mask(value, 4);
        return new CanonicalValue(value, display, List.of(ValueRow.sensitive(0, value, display)));
    }

    private static boolean validCnIdentity(String value) {
        if (!CN_ID.matcher(value).matches()) {
            return false;
        }
        try {
            LocalDate.parse(value.substring(6, 14), DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException exception) {
            return false;
        }
        var sum = 0;
        for (var index = 0; index < CN_ID_WEIGHTS.length; index++) {
            sum += (value.charAt(index) - '0') * CN_ID_WEIGHTS[index];
        }
        return value.charAt(17) == CN_ID_CHECKS[sum % 11];
    }

    private CanonicalValue address(FieldContract field, JsonNode node) {
        requireObject(field, node, ADDRESS_KEYS);
        var country = node.path("countryCode").asText("").toUpperCase(Locale.ROOT);
        var display = normalizedText(node.path("display"), 1000);
        if (!ISO_COUNTRIES.contains(country) || display.isEmpty()) {
            throw invalid(field, "must contain a known countryCode and display");
        }
        var value = objectMapper.createObjectNode();
        value.put("countryCode", country);
        value.put("display", display);
        for (var key : List.of("regionCode", "city", "district", "postalCode", "detail")) {
            if (node.has(key) && !node.get(key).isNull()) {
                value.put(key, normalizedText(node.get(key), "detail".equals(key) ? 2000 : 256));
            }
        }
        return boundedJson(field, value, 16_384, display);
    }

    private CanonicalValue geo(FieldContract field, JsonNode node) {
        requireObject(field, node, GEO_KEYS);
        if (!node.path("lat").isNumber() || !node.path("lng").isNumber()) {
            throw invalid(field, "must contain numeric lat and lng");
        }
        var lat = node.path("lat").decimalValue();
        var lng = node.path("lng").decimalValue();
        if (lat.compareTo(new BigDecimal("-90")) < 0 || lat.compareTo(new BigDecimal("90")) > 0
                || lng.compareTo(new BigDecimal("-180")) < 0 || lng.compareTo(new BigDecimal("180")) > 0) {
            throw invalid(field, "contains coordinates outside WGS84 bounds");
        }
        var value = objectMapper.createObjectNode();
        value.put("lat", lat.stripTrailingZeros());
        value.put("lng", lng.stripTrailingZeros());
        return boundedJson(field, value, 1024, lat.toPlainString() + ", " + lng.toPlainString());
    }

    private CanonicalValue barcode(FieldContract field, JsonNode node) {
        requireObject(field, node, BARCODE_KEYS);
        var symbology = node.path("symbology").asText("").toUpperCase(Locale.ROOT);
        var payload = node.path("payload").asText("");
        var enabled = new HashSet<String>();
        field.schema().path("symbologies").forEach(value -> enabled.add(value.asText()));
        if (!Set.of("CODE128", "EAN13").contains(symbology) || !enabled.contains(symbology)) {
            throw invalid(field, "uses a barcode symbology that is not enabled");
        }
        if ("CODE128".equals(symbology)
                && (payload.isEmpty() || payload.length() > 128 || !payload.matches("^[\\x20-\\x7E]+$"))) {
            throw invalid(field, "contains an invalid CODE128 payload");
        }
        if ("EAN13".equals(symbology) && !validEan13(payload)) {
            throw invalid(field, "contains an invalid EAN13 check digit");
        }
        var value = objectMapper.createObjectNode().put("symbology", symbology).put("payload", payload);
        return boundedJson(field, value, 2048, symbology + " " + payload);
    }

    private static boolean validEan13(String value) {
        if (!value.matches("^[0-9]{13}$")) {
            return false;
        }
        var sum = 0;
        for (var index = 0; index < 12; index++) {
            sum += (value.charAt(index) - '0') * (index % 2 == 0 ? 1 : 3);
        }
        return (10 - sum % 10) % 10 == value.charAt(12) - '0';
    }

    private CanonicalValue richText(FieldContract field, JsonNode node) {
        requireText(field, node);
        if (node.textValue().getBytes(StandardCharsets.UTF_8).length > 262_144) {
            throw invalid(field, "must not exceed 256 KiB");
        }
        var document = Jsoup.parseBodyFragment(node.textValue());
        document.select("script").forEach(script -> script.replaceWith(new TextNode(script.data())));
        var settings = new org.jsoup.nodes.Document.OutputSettings().prettyPrint(false);
        document.outputSettings(settings);
        var value = Jsoup.clean(document.body().html(), "", RICH_TEXT_ALLOWLIST, settings);
        var display = truncate(Jsoup.parseBodyFragment(value).text(), 1000);
        return new CanonicalValue(value, display, List.of(ValueRow.text(0, value, display)));
    }

    private CanonicalValue json(FieldContract field, JsonNode node) {
        if (!node.isObject()) {
            throw invalid(field, "must be a JSON object");
        }
        requireDepth(field, node, 1);
        validateJsonSchema(field, node, field.schema().path("jsonSchema"), "$");
        var canonical = canonicalNode(node);
        return boundedJson(field, canonical, 262_144, truncate(write(canonical), 1000));
    }

    private CanonicalValue secret(FieldContract field, JsonNode node) {
        requireText(field, node);
        var value = node.textValue();
        if (value.isEmpty() || value.length() > 4096) {
            throw invalid(field, "must contain 1..4096 characters");
        }
        return new CanonicalValue(value, "********", List.of(ValueRow.sensitive(0, value, "********")));
    }

    private CanonicalValue status(FieldContract field, JsonNode node) {
        var selected = option(field, node);
        return new CanonicalValue(selected.value(), selected.label(),
                List.of(ValueRow.reference(0, Long.parseLong(selected.value()), selected.label())));
    }

    private CanonicalValue text(FieldContract field, JsonNode node, int maxLength, boolean longText) {
        requireText(field, node);
        var value = node.textValue();
        if (value.length() > maxLength) {
            throw invalid(field, "must not exceed " + maxLength + " characters");
        }
        var row = longText ? ValueRow.text(0, value, value) : ValueRow.string(0, value, value);
        return new CanonicalValue(value, value, List.of(row));
    }

    private CanonicalValue decimal(
            FieldContract field,
            JsonNode node,
            BigDecimal minimum,
            BigDecimal maximum,
            int maximumScale
    ) {
        if (!node.isNumber()) {
            throw invalid(field, "must be a JSON number");
        }
        var value = node.decimalValue();
        if (value.precision() > 38 || Math.max(value.scale(), 0) > maximumScale
                || minimum != null && value.compareTo(minimum) < 0
                || maximum != null && value.compareTo(maximum) > 0) {
            throw invalid(field, "is outside its canonical numeric range or scale");
        }
        return new CanonicalValue(value, value.toPlainString(),
                List.of(ValueRow.decimal(0, value, value.toPlainString())));
    }

    private CanonicalValue rating(FieldContract field, JsonNode node) {
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid(field, "must be an integer from 1 through 5");
        }
        var value = node.intValue();
        if (value < 1 || value > 5) {
            throw invalid(field, "must be an integer from 1 through 5");
        }
        return new CanonicalValue(value, Integer.toString(value),
                List.of(ValueRow.decimal(0, BigDecimal.valueOf(value), Integer.toString(value))));
    }

    private CanonicalValue money(FieldContract field, JsonNode node) {
        if (!node.isObject()) {
            throw invalid(field, "must contain amount and currency");
        }
        var suppliedKeys = new HashSet<String>();
        node.fieldNames().forEachRemaining(suppliedKeys::add);
        if (!MONEY_KEYS.equals(suppliedKeys) || !node.path("amount").isTextual()
                || !node.path("currency").isTextual()) {
            throw invalid(field, "must contain only amount and currency");
        }
        var currencyCode = node.path("currency").textValue().toUpperCase(Locale.ROOT);
        final Currency currency;
        try {
            currency = Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw invalid(field, "contains an invalid ISO-4217 currency");
        }
        if (currency.getDefaultFractionDigits() < 0 || !allowedCurrencies(field.schema()).contains(currencyCode)) {
            throw invalid(field, "uses a currency that is not enabled for this field");
        }
        var amountText = node.path("amount").textValue();
        if (!amountText.matches("^-?(0|[1-9][0-9]*)(\\.[0-9]+)?$")) {
            throw invalid(field, "amount must be an exact decimal string");
        }
        var amount = new BigDecimal(amountText);
        if (amount.precision() > 38 || amount.scale() != currency.getDefaultFractionDigits()) {
            throw invalid(field, "amount scale must match its currency");
        }
        var canonical = new MoneyValue(amount.toPlainString(), currencyCode);
        var display = currencyCode + " " + canonical.amount();
        return new CanonicalValue(canonical, display,
                List.of(ValueRow.money(0, amount, currencyCode, display)));
    }

    private Set<String> allowedCurrencies(ObjectNode schema) {
        var result = new HashSet<String>();
        if (schema.path("currencies").isArray()) {
            schema.path("currencies").forEach(item -> result.add(item.asText().toUpperCase(Locale.ROOT)));
        }
        for (var key : List.of("currency", "fixedCurrency")) {
            if (schema.path(key).isTextual()) {
                result.add(schema.path(key).textValue().toUpperCase(Locale.ROOT));
            }
        }
        if (result.isEmpty()) {
            result.add("CNY");
            result.add("USD");
        }
        return result;
    }

    private CanonicalValue date(FieldContract field, JsonNode node) {
        requireText(field, node);
        var value = LocalDate.parse(node.textValue(), DateTimeFormatter.ISO_LOCAL_DATE);
        return new CanonicalValue(value, value.toString(), List.of(ValueRow.date(0, value, value.toString())));
    }

    private CanonicalValue dateTime(FieldContract field, JsonNode node) {
        requireText(field, node);
        var value = LocalDateTime.parse(node.textValue(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        var canonical = value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return new CanonicalValue(value, canonical, List.of(ValueRow.dateTime(0, value, canonical)));
    }

    private CanonicalValue dateRange(FieldContract field, JsonNode node) {
        requirePair(field, node);
        var start = LocalDate.parse(node.get(0).textValue(), DateTimeFormatter.ISO_LOCAL_DATE);
        var end = LocalDate.parse(node.get(1).textValue(), DateTimeFormatter.ISO_LOCAL_DATE);
        if (start.isAfter(end)) {
            throw invalid(field, "range start must not exceed range end");
        }
        var value = List.of(start.toString(), end.toString());
        return new CanonicalValue(value, String.join(" - ", value), List.of(
                ValueRow.date(0, start, start.toString()), ValueRow.date(1, end, end.toString())));
    }

    private CanonicalValue time(FieldContract field, JsonNode node) {
        requireText(field, node);
        var value = parseTime(field, node.textValue());
        var canonical = value.format(TIME_FORMAT);
        return new CanonicalValue(canonical, canonical, List.of(ValueRow.time(0, value, canonical)));
    }

    private CanonicalValue timeRange(FieldContract field, JsonNode node) {
        requirePair(field, node);
        var start = parseTime(field, node.get(0).textValue());
        var end = parseTime(field, node.get(1).textValue());
        if (start.isAfter(end)) {
            throw invalid(field, "range start must not exceed range end");
        }
        var values = List.of(start.format(TIME_FORMAT), end.format(TIME_FORMAT));
        return new CanonicalValue(values, String.join(" - ", values), List.of(
                ValueRow.time(0, start, values.get(0)), ValueRow.time(1, end, values.get(1))));
    }

    private CanonicalValue reference(FieldContract field, JsonNode node) {
        var selected = option(field, node);
        return new CanonicalValue(selected.value(), selected.label(),
                List.of(ValueRow.reference(0, Long.parseLong(selected.value()), selected.label())));
    }

    private CanonicalValue multiSelect(FieldContract field, JsonNode node) {
        requireArray(field, node, 100);
        var seen = new HashSet<String>();
        var values = new ArrayList<String>();
        var labels = new ArrayList<String>();
        var rows = new ArrayList<ValueRow>();
        for (var index = 0; index < node.size(); index++) {
            var selected = option(field, node.get(index));
            if (!seen.add(selected.value())) {
                throw invalid(field, "option ids must be unique");
            }
            values.add(selected.value());
            labels.add(selected.label());
            rows.add(ValueRow.reference(index, Long.parseLong(selected.value()), selected.label()));
        }
        return new CanonicalValue(List.copyOf(values), String.join(", ", labels), List.copyOf(rows));
    }

    private CanonicalValue cascade(FieldContract field, JsonNode node) {
        requireArray(field, node, 20);
        var seen = new HashSet<String>();
        var values = new ArrayList<String>();
        var labels = new ArrayList<String>();
        var rows = new ArrayList<ValueRow>();
        String expectedParent = null;
        for (var index = 0; index < node.size(); index++) {
            var selected = option(field, node.get(index));
            if (!seen.add(selected.value()) || !equalsNullable(expectedParent, selected.parentValue())) {
                throw invalid(field, "must be one contiguous root-to-descendant path");
            }
            values.add(selected.value());
            labels.add(selected.label());
            rows.add(ValueRow.reference(index, Long.parseLong(selected.value()), selected.label()));
            expectedParent = selected.value();
        }
        return new CanonicalValue(List.copyOf(values), String.join(" / ", labels), List.copyOf(rows));
    }

    private CanonicalValue switchValue(FieldContract field, JsonNode node) {
        if (!node.isBoolean()) {
            throw invalid(field, "must be a JSON boolean");
        }
        var value = node.booleanValue();
        return new CanonicalValue(value, Boolean.toString(value),
                List.of(ValueRow.bool(0, value, Boolean.toString(value))));
    }

    private CanonicalValue tags(FieldContract field, JsonNode node) {
        requireArray(field, node, 100);
        var seen = new HashSet<String>();
        var values = new ArrayList<String>();
        var rows = new ArrayList<ValueRow>();
        for (var index = 0; index < node.size(); index++) {
            var item = node.get(index);
            if (!item.isTextual()) {
                throw invalid(field, "tags must be strings");
            }
            var value = Normalizer.normalize(item.textValue(), Normalizer.Form.NFKC).trim();
            if (value.isEmpty() || value.length() > 64 || !seen.add(value)) {
                throw invalid(field, "tags must be unique normalized strings of 1..64 characters");
            }
            values.add(value);
            rows.add(ValueRow.string(index, value, value));
        }
        return new CanonicalValue(List.copyOf(values), String.join(", ", values), List.copyOf(rows));
    }

    private CanonicalValue boundedJson(FieldContract field, JsonNode value, int maximumBytes, String display) {
        var canonical = write(value);
        if (canonical.getBytes(StandardCharsets.UTF_8).length > maximumBytes) {
            throw invalid(field, "exceeds its canonical JSON size limit");
        }
        return new CanonicalValue(value, display, List.of(ValueRow.text(0, canonical, display)));
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Canonical JSON serialization failed", exception);
        }
    }

    private JsonNode canonicalNode(JsonNode node) {
        if (node.isObject()) {
            var sorted = new TreeMap<String, JsonNode>();
            node.properties().forEach(entry -> sorted.put(entry.getKey(), canonicalNode(entry.getValue())));
            var result = objectMapper.createObjectNode();
            sorted.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            var result = objectMapper.createArrayNode();
            node.forEach(value -> result.add(canonicalNode(value)));
            return result;
        }
        return node.deepCopy();
    }

    private static void requireDepth(FieldContract field, JsonNode node, int depth) {
        if (depth > 10) {
            throw invalid(field, "must not exceed JSON depth 10");
        }
        if (node.isContainerNode()) {
            node.forEach(value -> requireDepth(field, value, depth + 1));
        }
    }

    private void validateJsonSchema(FieldContract field, JsonNode value, JsonNode schema, String path) {
        if (schema == null || schema.isMissingNode() || schema.isNull() || schema.isEmpty()) {
            return;
        }
        if (!schema.isObject()) {
            throw invalid(field, "has an invalid published JSON schema");
        }
        var type = schema.path("type").asText("");
        if (!type.isEmpty() && !matchesType(value, type)) {
            throw invalid(field, path + " does not match the published JSON type");
        }
        if (value.isObject()) {
            var required = schema.path("required");
            if (required.isArray()) {
                required.forEach(name -> {
                    if (!name.isTextual() || !value.has(name.textValue())) {
                        throw invalid(field, path + " is missing a required property");
                    }
                });
            }
            var properties = schema.path("properties");
            if (properties.isObject()) {
                value.properties().forEach(entry -> {
                    var childSchema = properties.get(entry.getKey());
                    if (childSchema != null) {
                        validateJsonSchema(field, entry.getValue(), childSchema, path + "." + entry.getKey());
                    } else if (schema.path("additionalProperties").isBoolean()
                            && !schema.path("additionalProperties").booleanValue()) {
                        throw invalid(field, path + " contains an undeclared property");
                    }
                });
            }
        }
        if (schema.path("enum").isArray()) {
            var accepted = false;
            for (var candidate : schema.path("enum")) {
                accepted |= candidate.equals(value);
            }
            if (!accepted) {
                throw invalid(field, path + " is not one of the published enum values");
            }
        }
        if (value.isTextual()) {
            var length = value.textValue().length();
            if (schema.path("minLength").canConvertToInt() && length < schema.path("minLength").intValue()
                    || schema.path("maxLength").canConvertToInt() && length > schema.path("maxLength").intValue()) {
                throw invalid(field, path + " is outside its published string length");
            }
        }
        if (value.isNumber()) {
            if (schema.path("minimum").isNumber()
                    && value.decimalValue().compareTo(schema.path("minimum").decimalValue()) < 0
                    || schema.path("maximum").isNumber()
                    && value.decimalValue().compareTo(schema.path("maximum").decimalValue()) > 0) {
                throw invalid(field, path + " is outside its published numeric range");
            }
        }
    }

    private static boolean matchesType(JsonNode value, String type) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private static void requireObject(FieldContract field, JsonNode node, Set<String> keys) {
        if (!node.isObject()) {
            throw invalid(field, "must be a JSON object");
        }
        node.fieldNames().forEachRemaining(key -> {
            if (!keys.contains(key)) {
                throw invalid(field, "contains an unknown property " + key);
            }
        });
    }

    private static String normalizedText(JsonNode node, int maximumLength) {
        if (!node.isTextual()) {
            return "";
        }
        var value = Normalizer.normalize(node.textValue(), Normalizer.Form.NFKC).trim();
        if (value.length() > maximumLength || value.indexOf('\u0000') >= 0) {
            return "";
        }
        return value;
    }

    private static String mask(String value, int visibleSuffix) {
        var suffix = Math.min(visibleSuffix, value.length());
        return "*".repeat(value.length() - suffix) + value.substring(value.length() - suffix);
    }

    private static String truncate(String value, int maximumLength) {
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    private ValueOption option(FieldContract field, JsonNode node) {
        final String id;
        if (node.isIntegralNumber() && node.canConvertToLong()) {
            id = Long.toString(node.longValue());
        } else if (node.isTextual() && node.textValue().matches("^[1-9][0-9]{0,18}$")) {
            id = node.textValue();
        } else {
            throw invalid(field, "must contain valid option ids");
        }
        return field.options().stream().filter(candidate -> candidate.value().equals(id)).findFirst()
                .orElseThrow(() -> invalid(field, "contains an unavailable option id"));
    }

    private static LocalTime parseTime(FieldContract field, String value) {
        if (!value.matches("^[0-9]{2}:[0-9]{2}:[0-9]{2}$")) {
            throw invalid(field, "must use HH:mm:ss");
        }
        return LocalTime.parse(value, TIME_FORMAT);
    }

    private static void requireText(FieldContract field, JsonNode node) {
        if (!node.isTextual()) {
            throw invalid(field, "must be a string");
        }
    }

    private static void requirePair(FieldContract field, JsonNode node) {
        if (!node.isArray() || node.size() != 2 || !node.get(0).isTextual() || !node.get(1).isTextual()) {
            throw invalid(field, "must contain exactly two string bounds");
        }
    }

    private static void requireArray(FieldContract field, JsonNode node, int maximum) {
        if (!node.isArray() || node.size() > maximum) {
            throw invalid(field, "must be an array containing no more than " + maximum + " values");
        }
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null || right.isBlank() : left.equals(right);
    }

    private static BusinessException invalid(FieldContract field, String reason) {
        var message = field.name() + " " + reason;
        return new BusinessException("FIELD_VALUE_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new ApiError("FIELD_VALUE_INVALID", "values." + field.code(), message)));
    }

    record FieldContract(String code, String name, String type, ObjectNode schema, List<ValueOption> options) {
        FieldContract {
            options = List.copyOf(options);
        }
    }

    record ValueOption(String value, String label, String parentValue) { }

    record MoneyValue(String amount, String currency) { }

    record CanonicalValue(Object value, String display, List<ValueRow> rows) {
        CanonicalValue {
            rows = List.copyOf(rows);
        }
    }

    record ValueRow(
            int ordinal,
            String stringValue,
            String textValue,
            BigDecimal decimalValue,
            LocalDate dateValue,
            LocalDateTime dateTimeValue,
            LocalTime timeValue,
            Long referenceValue,
            Boolean booleanValue,
            String currencyCode,
            String display,
            String sensitiveValue
    ) {
        static ValueRow string(int ordinal, String value, String display) {
            return new ValueRow(ordinal, value, null, null, null, null, null, null, null, null, display, null);
        }

        static ValueRow text(int ordinal, String value, String display) {
            return new ValueRow(ordinal, null, value, null, null, null, null, null, null, null, display, null);
        }

        static ValueRow decimal(int ordinal, BigDecimal value, String display) {
            return new ValueRow(ordinal, null, null, value, null, null, null, null, null, null, display, null);
        }

        static ValueRow money(int ordinal, BigDecimal value, String currency, String display) {
            return new ValueRow(ordinal, null, null, value, null, null, null, null, null, currency, display, null);
        }

        static ValueRow date(int ordinal, LocalDate value, String display) {
            return new ValueRow(ordinal, null, null, null, value, null, null, null, null, null, display, null);
        }

        static ValueRow dateTime(int ordinal, LocalDateTime value, String display) {
            return new ValueRow(ordinal, null, null, null, null, value, null, null, null, null, display, null);
        }

        static ValueRow time(int ordinal, LocalTime value, String display) {
            return new ValueRow(ordinal, null, null, null, null, null, value, null, null, null, display, null);
        }

        static ValueRow reference(int ordinal, long value, String display) {
            return new ValueRow(ordinal, null, null, null, null, null, null, value, null, null, display, null);
        }

        static ValueRow bool(int ordinal, boolean value, String display) {
            return new ValueRow(ordinal, null, null, null, null, null, null, null, value, null, display, null);
        }

        static ValueRow sensitive(int ordinal, String value, String display) {
            return new ValueRow(ordinal, null, null, null, null, null, null, null, null, null, display, value);
        }
    }
}
