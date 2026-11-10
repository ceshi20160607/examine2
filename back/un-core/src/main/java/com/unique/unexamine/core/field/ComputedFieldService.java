package com.unique.unexamine.core.field;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动编号和公式字段的公共规则及执行器。
 *
 * <p>公式只允许受控的四则运算或字符串模板，不执行 Java、SQL 或脚本代码。
 * 模块业务层负责提供字段值和序列号，core 负责规则校验、依赖解析和结果计算。</p>
 */
public final class ComputedFieldService {
    private static final Pattern KEY = Pattern.compile("[a-z][a-zA-Z0-9_]{0,79}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([a-z][a-zA-Z0-9_]{0,79})}");
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z][a-zA-Z0-9_]{0,79}");
    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private ComputedFieldService() {
    }

    /** 自动编号配置；序号本身由业务模块的并发序列表分配。 */
    public record AutoNumberRule(String prefix, String suffix, int padding, long start,
                                 String period, String ruleCode) {
        public String periodKey(LocalDateTime now) {
            return switch (period) {
                case "YEAR" -> YEAR.format(now);
                case "MONTH" -> MONTH.format(now);
                case "DAY" -> DAY.format(now);
                default -> "ALL";
            };
        }

        /** 将日期占位符和已分配序号渲染为最终编号。 */
        public String render(LocalDateTime now, long number) {
            String datePrefix = replaceDateTokens(prefix, now);
            String dateSuffix = replaceDateTokens(suffix, now);
            String serial = String.format("%0" + padding + "d", number);
            return datePrefix + serial + dateSuffix;
        }
    }

    /** 公式配置；NUMBER 使用表达式，TEXT 使用 ${fieldKey} 模板。 */
    public record FormulaRule(String resultType, String expression, String template,
                              Integer scale, boolean round) {
    }

    /** 校验计算字段的专属配置，保存字段前必须调用。 */
    public static void validateDefinition(FieldDefinition field) {
        boolean hasAuto = field.rules().containsKey("autoNumberConfig");
        boolean hasFormula = field.rules().containsKey("formulaConfig");
        if (field.type() == FieldType.AUTO_NUMBER) {
            if (hasFormula || !hasAuto) fail("自动编号必须配置 autoNumberConfig");
            autoNumber(field);
        } else if (field.type() == FieldType.FORMULA) {
            if (hasAuto || !hasFormula) fail("公式字段必须配置 formulaConfig");
            formula(field);
        } else if (hasAuto || hasFormula) {
            fail("自动编号和公式规则只能用于对应的计算字段");
        }
    }

    /** 解析并校验自动编号规则。 */
    public static AutoNumberRule autoNumber(FieldDefinition field) {
        if (field.type() != FieldType.AUTO_NUMBER || !(field.rules().get("autoNumberConfig") instanceof Map<?, ?> raw))
            throw new IllegalArgumentException("字段不是有效的自动编号配置");
        String prefix = string(raw.get("prefix"), "prefix", true, 100);
        if (prefix==null) prefix="";
        String suffix = string(raw.get("suffix"), "suffix", true, 100);
        if (suffix==null) suffix="";
        int padding = integer(raw.get("padding"), "padding", 1, 20, 6);
        long start = longValue(raw.get("start"), "start", 1, Long.MAX_VALUE, 1);
        String period = enumValue(raw.get("period"), "period", Set.of("NONE", "YEAR", "MONTH", "DAY"), "NONE");
        String ruleCode = string(raw.get("ruleCode"), "ruleCode", true, 80);
        if (ruleCode == null) ruleCode = field.code();
        if (!KEY.matcher(ruleCode).matches()) fail("自动编号 ruleCode 无效");
        String pattern = prefix + suffix;
        if ("YEAR".equals(period) && !(pattern.contains("{YYYY}") || pattern.contains("{YY}")))
            fail("按年重置的编号必须包含 {YYYY} 或 {YY}");
        if ("MONTH".equals(period) && !(pattern.contains("{YYYY}") && pattern.contains("{MM}")))
            fail("按月重置的编号必须包含 {YYYY} 和 {MM}");
        if ("DAY".equals(period) && !(pattern.contains("{YYYY}") && pattern.contains("{MM}") && pattern.contains("{DD}")))
            fail("按日重置的编号必须包含 {YYYY}、{MM} 和 {DD}");
        if (pattern.length() > 200) fail("自动编号前后缀过长");
        return new AutoNumberRule(prefix, suffix, padding, start, period, ruleCode);
    }

    /** 解析并校验公式规则。 */
    public static FormulaRule formula(FieldDefinition field) {
        if (field.type() != FieldType.FORMULA || !(field.rules().get("formulaConfig") instanceof Map<?, ?> raw))
            throw new IllegalArgumentException("字段不是有效的公式配置");
        String resultType = enumValue(raw.get("resultType"), "resultType", Set.of("NUMBER", "TEXT"), "TEXT");
        String expression = string(raw.get("expression"), "expression", false, 500);
        String template = string(raw.get("template"), "template", false, 1000);
        Integer scale = raw.get("scale") == null ? null : integer(raw.get("scale"), "scale", 0, 30, 0);
        boolean round = raw.get("round") == null ? false : bool(raw.get("round"), "round");
        if ("NUMBER".equals(resultType)) {
            if (expression == null || expression.isBlank()) fail("数字公式必须配置 expression");
            if (template != null) fail("数字公式不能配置 template");
            if (round && scale == null) fail("数字公式启用四舍五入时必须配置 scale");
            Set<String> references=new ExpressionParser(expression, Map.of()).references();
            Map<String,Object> syntaxValues=new HashMap<>(); references.forEach(key->syntaxValues.put(key,BigDecimal.ZERO));
            new ExpressionParser(expression, syntaxValues).parse();
        } else {
            if (template == null) fail("文本公式必须配置 template");
            if (expression != null) fail("文本公式不能配置 expression");
            Matcher matcher = PLACEHOLDER.matcher(template);
            while (matcher.find()) {
                if (!KEY.matcher(matcher.group(1)).matches()) fail("文本公式字段引用无效");
            }
        }
        return new FormulaRule(resultType, expression, template, scale, round);
    }

    /** 返回公式引用的字段键，供业务层建立依赖计算顺序。 */
    public static Set<String> references(FormulaRule rule) {
        if ("TEXT".equals(rule.resultType())) {
            Set<String> result = new LinkedHashSet<>();
            Matcher matcher = PLACEHOLDER.matcher(rule.template());
            while (matcher.find()) result.add(matcher.group(1));
            return result;
        }
        return new ExpressionParser(rule.expression(), Map.of()).references();
    }

    /** 根据已准备的字段值计算公式结果。 */
    public static Object evaluate(FormulaRule rule, Map<String, Object> values) {
        if ("TEXT".equals(rule.resultType())) {
            Matcher matcher = PLACEHOLDER.matcher(rule.template());
            StringBuffer result = new StringBuffer();
            while (matcher.find()) {
                Object value = values.get(matcher.group(1));
                matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
            }
            matcher.appendTail(result);
            return result.toString();
        }
        BigDecimal value = new ExpressionParser(rule.expression(), values).parse();
        if (rule.round() && rule.scale() != null) value = value.setScale(rule.scale(), RoundingMode.HALF_UP);
        return rule.round() && rule.scale()!=null ? value : value.stripTrailingZeros();
    }

    private static String replaceDateTokens(String source, LocalDateTime now) {
        return source.replace("{YYYY}", YEAR.format(now)).replace("{YY}", YEAR.format(now).substring(2))
                .replace("{MM}", String.format("%02d", now.getMonthValue()))
                .replace("{DD}", String.format("%02d", now.getDayOfMonth()));
    }

    private static String string(Object value, String name, boolean optional, int max) {
        if (value == null) return null;
        if (!(value instanceof String)) fail(name + " 无效");
        String text=(String)value;
        if (text.length() > max) fail(name + " 无效");
        return text;
    }

    private static int integer(Object value, String name, int min, int max, int defaultValue) {
        if (value == null) return defaultValue;
        if (!(value instanceof Number)) fail(name + " 必须是整数");
        Number number=(Number)value;
        if (number.longValue() != number.doubleValue()) fail(name + " 必须是整数");
        long result = number.longValue();
        if (result < min || result > max) fail(name + " 超出范围");
        return (int) result;
    }

    private static long longValue(Object value, String name, long min, long max, long defaultValue) {
        if (value == null) return defaultValue;
        if (!(value instanceof Number)) fail(name + " 必须是整数");
        Number number=(Number)value;
        if (number.doubleValue() != number.longValue()) fail(name + " 必须是整数");
        long result = number.longValue();
        if (result < min || result > max) fail(name + " 超出范围");
        return result;
    }

    private static String enumValue(Object value, String name, Set<String> allowed, String defaultValue) {
        if (value == null) return defaultValue;
        if (!(value instanceof String)) fail(name + " 无效");
        String text=(String)value;
        if (!allowed.contains(text)) fail(name + " 无效");
        return text;
    }

    private static boolean bool(Object value, String name) {
        if (!(value instanceof Boolean)) fail(name + " 必须是开关值");
        return (Boolean)value;
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message);
    }

    /** 只实现数字字面量、字段键、括号和四则运算的安全表达式解析器。 */
    private static final class ExpressionParser {
        private final String source;
        private final Map<String, Object> values;
        private int position;

        private ExpressionParser(String source, Map<String, Object> values) {
            this.source = source;
            this.values = values;
        }

        private BigDecimal parse() {
            BigDecimal result = expression();
            skip();
            if (position != source.length()) fail("数字公式包含无法识别的内容");
            return result;
        }

        private Set<String> references() {
            Set<String> result = new LinkedHashSet<>();
            int index = 0;
            while (index < source.length()) {
                char current = source.charAt(index);
                if (Character.isLetter(current) || current == '_') {
                    int start = index++;
                    while (index < source.length() && (Character.isLetterOrDigit(source.charAt(index)) || source.charAt(index) == '_')) index++;
                    String name = source.substring(start, index);
                    if (!IDENTIFIER.matcher(name).matches()) fail("数字公式字段引用无效");
                    result.add(name);
                } else index++;
            }
            return result;
        }

        private BigDecimal expression() {
            BigDecimal result = term();
            while (true) {
                skip();
                if (take('+')) result = result.add(term());
                else if (take('-')) result = result.subtract(term());
                else return result;
            }
        }

        private BigDecimal term() {
            BigDecimal result = factor();
            while (true) {
                skip();
                if (take('*')) result = result.multiply(factor());
                else if (take('/')) {
                    BigDecimal divisor = factor();
                    if (divisor.signum() == 0) fail("数字公式不能除以零");
                    result = result.divide(divisor, 34, RoundingMode.HALF_UP);
                } else return result;
            }
        }

        private BigDecimal factor() {
            skip();
            if (take('+')) return factor();
            if (take('-')) return factor().negate();
            if (take('(')) {
                BigDecimal result = expression();
                if (!take(')')) fail("数字公式括号不匹配");
                return result;
            }
            int start = position;
            while (position < source.length() && (Character.isDigit(source.charAt(position)) || source.charAt(position) == '.')) position++;
            if (start != position) {
                try { return new BigDecimal(source.substring(start, position)); }
                catch (NumberFormatException ex) { fail("数字公式常量无效"); }
            }
            start = position;
            while (position < source.length() && (Character.isLetterOrDigit(source.charAt(position)) || source.charAt(position) == '_')) position++;
            if (start == position) fail("数字公式缺少数值");
            String key = source.substring(start, position);
            if (!IDENTIFIER.matcher(key).matches()) fail("数字公式字段引用无效");
            Object value = values.get(key);
            if (!(value instanceof Number)) fail("数字公式依赖字段没有数值: " + key);
            Number number=(Number)value;
            return new BigDecimal(number.toString());
        }

        private void skip() {
            while (position < source.length() && Character.isWhitespace(source.charAt(position))) position++;
        }

        private boolean take(char expected) {
            skip();
            if (position < source.length() && source.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }
    }
}
