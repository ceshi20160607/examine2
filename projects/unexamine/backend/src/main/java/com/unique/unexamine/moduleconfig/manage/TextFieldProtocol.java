package com.unique.unexamine.moduleconfig.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Shared configuration and value protocol for text-family module fields. */
public final class TextFieldProtocol {
    /** 条码和二维码保存识别出的文本，沿用普通文本的长度与格式规则。 */
    public static final Set<String> TYPES = Set.of(
            "TEXT", "MULTILINE_TEXT", "PHONE", "EMAIL", "URL", "ID_CARD", "BARCODE", "QRCODE");
    public static final Set<String> PATTERN_MODES = Set.of(
            "NONE", "DIGITS", "LETTERS", "ALPHANUMERIC", "CHINESE", "CUSTOM");

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final int MAX_CONFIGURED_LENGTH = 100_000;
    private static final int MAX_PATTERN_LENGTH = 512;
    private static final int[] ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECKSUM = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private TextFieldProtocol() {
    }

    public static void validateConfiguration(String fieldType, JsonNode config) {
        if (!TYPES.contains(fieldType)) return;
        Integer minimum = configuredInteger(config, "minLength");
        Integer maximum = configuredInteger(config, "maxLength");
        if (minimum != null && (minimum < 0 || minimum > MAX_CONFIGURED_LENGTH)) {
            throw new IllegalArgumentException("最短长度必须在 0 到 " + MAX_CONFIGURED_LENGTH + " 之间");
        }
        if (maximum != null && (maximum < 1 || maximum > MAX_CONFIGURED_LENGTH)) {
            throw new IllegalArgumentException("最长长度必须在 1 到 " + MAX_CONFIGURED_LENGTH + " 之间");
        }
        if (minimum != null && maximum != null && minimum > maximum) {
            throw new IllegalArgumentException("最短长度不能大于最长长度");
        }
        String mode = patternMode(config);
        if (!PATTERN_MODES.contains(mode)) {
            throw new IllegalArgumentException("附加格式规则不受支持");
        }
        String custom = config.path("customPattern").asText("");
        if ("CUSTOM".equals(mode)) {
            if (custom.isBlank()) throw new IllegalArgumentException("自定义格式必须填写正则表达式");
            if (custom.length() > MAX_PATTERN_LENGTH) throw new IllegalArgumentException("自定义格式最多 512 个字符");
            try {
                Pattern.compile(custom);
            } catch (PatternSyntaxException exception) {
                throw new IllegalArgumentException("自定义格式不是有效的正则表达式");
            }
        }
    }

    public static JsonNode normalize(String fieldType, JsonNode config, JsonNode value) {
        if (!TYPES.contains(fieldType)) throw new IllegalArgumentException("不是文本字段类型");
        if (!value.isTextual()) throw new IllegalArgumentException("必须是文本");
        validateConfiguration(fieldType, config);
        String normalized = switch (fieldType) {
            case "PHONE" -> normalizePhone(value.textValue());
            case "EMAIL" -> normalizeEmail(value.textValue());
            case "URL" -> normalizeUrl(value.textValue());
            case "ID_CARD" -> normalizeIdCard(value.textValue());
            default -> value.textValue();
        };
        validateLength(config, normalized);
        validateConfiguredPattern(config, normalized);
        return TextNode.valueOf(normalized);
    }

    private static String normalizePhone(String submitted) {
        String phone = submitted.strip().replaceAll("[\\s()－—-]", "");
        if (!phone.matches("^\\+?[0-9]{6,20}$")) throw new IllegalArgumentException("手机号格式不正确");
        return phone;
    }

    private static String normalizeEmail(String submitted) {
        String email = submitted.strip();
        if (email.length() > 320 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("邮箱格式不正确");
        }
        return email;
    }

    private static String normalizeUrl(String submitted) {
        String url = submitted.strip();
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("URL 必须是完整的 http 或 https 地址");
            }
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("URL 格式不正确");
        }
        return url;
    }

    private static String normalizeIdCard(String submitted) {
        String identity = submitted.strip().replace(" ", "").toUpperCase(Locale.ROOT);
        if (identity.matches("^[1-9][0-9]{14}$")) {
            validateBirthDate("19" + identity.substring(6, 12));
            return identity;
        }
        if (!identity.matches("^[1-9][0-9]{16}[0-9X]$")) {
            throw new IllegalArgumentException("身份证号应为 15 位或 18 位");
        }
        validateBirthDate(identity.substring(6, 14));
        int sum = 0;
        for (int index = 0; index < ID_CARD_WEIGHTS.length; index++) {
            sum += Character.digit(identity.charAt(index), 10) * ID_CARD_WEIGHTS[index];
        }
        if (identity.charAt(17) != ID_CARD_CHECKSUM[sum % 11]) {
            throw new IllegalArgumentException("身份证号校验位不正确");
        }
        return identity;
    }

    private static void validateBirthDate(String value) {
        try {
            LocalDate.of(Integer.parseInt(value.substring(0, 4)), Integer.parseInt(value.substring(4, 6)),
                    Integer.parseInt(value.substring(6, 8)));
        } catch (DateTimeException | NumberFormatException | IndexOutOfBoundsException exception) {
            throw new IllegalArgumentException("身份证号出生日期不正确");
        }
    }

    private static void validateLength(JsonNode config, String value) {
        int length = value.codePointCount(0, value.length());
        Integer minimum = configuredInteger(config, "minLength");
        Integer maximum = configuredInteger(config, "maxLength");
        if (minimum != null && length < minimum) throw new IllegalArgumentException("长度不能少于 " + minimum + " 个字符");
        if (maximum != null && length > maximum) throw new IllegalArgumentException("长度不能超过 " + maximum + " 个字符");
    }

    private static void validateConfiguredPattern(JsonNode config, String value) {
        String mode = patternMode(config);
        String expression = switch (mode) {
            case "DIGITS" -> "[0-9]+";
            case "LETTERS" -> "[A-Za-z]+";
            case "ALPHANUMERIC" -> "[A-Za-z0-9]+";
            case "CHINESE" -> "[\\u4E00-\\u9FFF]+";
            case "CUSTOM" -> config.path("customPattern").asText();
            default -> null;
        };
        if (expression != null && !Pattern.compile(expression).matcher(value).matches()) {
            throw new IllegalArgumentException("格式不符合字段配置规则");
        }
    }

    private static String patternMode(JsonNode config) {
        String mode = config.path("patternMode").asText("NONE");
        return mode.isBlank() ? "NONE" : mode.strip().toUpperCase(Locale.ROOT);
    }

    private static Integer configuredInteger(JsonNode config, String property) {
        JsonNode value = config.get(property);
        if (value == null || value.isNull() || value.isMissingNode()) return null;
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw new IllegalArgumentException(property.equals("minLength") ? "最短长度必须是整数" : "最长长度必须是整数");
        }
        return value.intValue();
    }
}
