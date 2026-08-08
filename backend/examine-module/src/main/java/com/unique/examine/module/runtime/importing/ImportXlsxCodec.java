package com.unique.examine.module.runtime.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DecimalNode;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ImportXlsxCodec {
    public static final int MAX_BYTES = 1024 * 1024;
    public static final int MAX_ROWS = 200;
    private static final String HINT_PREFIX = "# ";

    static {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(4L * MAX_BYTES);
    }

    private final ObjectMapper mapper;

    public ImportXlsxCodec(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public byte[] template(ImportViews.Template template) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("import");
            var headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            var hintStyle = workbook.createCellStyle();
            hintStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            hintStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var hintFont = workbook.createFont();
            hintFont.setItalic(true);
            hintFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            hintStyle.setFont(hintFont);

            var header = sheet.createRow(0);
            var hints = sheet.createRow(1);
            for (var index = 0; index < template.fields().size(); index++) {
                var field = template.fields().get(index);
                var cell = header.createCell(index, CellType.STRING);
                cell.setCellValue(field.fieldCode());
                cell.setCellStyle(headerStyle);
                var hint = hints.createCell(index, CellType.STRING);
                hint.setCellValue(HINT_PREFIX + field.fieldName() + " | " + field.type()
                        + (field.required() ? " | required" : " | optional")
                        + (field.unique() ? " | unique" : ""));
                hint.setCellStyle(hintStyle);
                sheet.setColumnWidth(index, Math.min(60, Math.max(16, field.fieldCode().length() + 4)) * 256);
            }
            if (!template.fields().isEmpty()) {
                sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, template.fields().size() - 1));
            }
            sheet.createFreezePane(0, 2);
            workbook.getProperties().getCoreProperties().setTitle("Module import template");
            workbook.getProperties().getCoreProperties().setDescription(
                    "schemaVersion=" + template.schemaVersionId() + ";checksum=" + template.checksum());
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate import workbook", exception);
        }
    }

    public List<Map<String, JsonNode>> parse(byte[] content, String filename, ImportViews.Template template) {
        if (content == null || content.length == 0 || content.length > MAX_BYTES) {
            throw new ImportWorkbookException("IMPORT_XLSX_SIZE_INVALID", "XLSX file must be between 1 byte and 1 MiB");
        }
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new ImportWorkbookException("IMPORT_XLSX_TYPE_INVALID", "Only .xlsx OOXML workbooks are accepted");
        }
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() != 1
                    || workbook.getSheetVisibility(0) != SheetVisibility.VISIBLE) {
                throw invalid("IMPORT_XLSX_SHEET_INVALID", "Workbook must contain exactly one visible data sheet");
            }
            if (!workbook.getExternalLinksTable().isEmpty()) {
                throw invalid("IMPORT_XLSX_EXTERNAL_LINK", "External workbook links are not accepted");
            }
            var sheet = workbook.getSheetAt(0);
            var header = sheet.getRow(0);
            if (header == null) throw invalid("IMPORT_XLSX_HEADER_INVALID", "Header row is required");

            var allowed = new LinkedHashMap<String, ImportViews.TemplateField>();
            for (var field : template.fields()) allowed.put(field.fieldCode(), field);
            var columns = readHeaders(header, allowed);
            var firstDataRow = hintRow(sheet.getRow(1), columns.size()) ? 2 : 1;
            var rows = new ArrayList<Map<String, JsonNode>>();
            for (var rowIndex = firstDataRow; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                var source = sheet.getRow(rowIndex);
                if (source == null) continue;
                var row = readRow(source, columns, allowed);
                if (row.isEmpty()) continue;
                rows.add(Map.copyOf(row));
                if (rows.size() > MAX_ROWS) {
                    throw invalid("IMPORT_XLSX_ROW_LIMIT", "Workbook contains more than 200 non-empty data rows");
                }
            }
            if (rows.isEmpty()) throw invalid("IMPORT_XLSX_EMPTY", "Workbook contains no data rows");
            return List.copyOf(rows);
        } catch (ImportWorkbookException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ImportWorkbookException("IMPORT_XLSX_INVALID", "Workbook cannot be parsed safely", exception);
        }
    }

    public byte[] errors(List<ImportRepository.RowRecord> failedRows) {
        var headers = new LinkedHashSet<String>();
        failedRows.stream().sorted(Comparator.comparingInt(ImportRepository.RowRecord::rowNumber))
                .forEach(row -> headers.addAll(row.input().keySet().stream().sorted().toList()));
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("errors");
            var columns = new ArrayList<>(headers);
            columns.add("_error_code");
            columns.add("_error_message");
            var header = sheet.createRow(0);
            for (var index = 0; index < columns.size(); index++) {
                header.createCell(index, CellType.STRING).setCellValue(columns.get(index));
                sheet.setColumnWidth(index, Math.min(60, Math.max(16, columns.get(index).length() + 4)) * 256);
            }
            var outputIndex = 1;
            for (var source : failedRows.stream().sorted(Comparator.comparingInt(ImportRepository.RowRecord::rowNumber)).toList()) {
                var row = sheet.createRow(outputIndex++);
                for (var columnIndex = 0; columnIndex < headers.size(); columnIndex++) {
                    writeJson(row.createCell(columnIndex), source.input().get(columns.get(columnIndex)));
                }
                row.createCell(headers.size(), CellType.STRING).setCellValue(nullToEmpty(source.errorCode()));
                row.createCell(headers.size() + 1, CellType.STRING).setCellValue(nullToEmpty(source.errorMessage()));
            }
            sheet.createFreezePane(0, 1);
            if (!columns.isEmpty()) sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.size() - 1));
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate import error workbook", exception);
        }
    }

    private Map<Integer, String> readHeaders(Row header, Map<String, ImportViews.TemplateField> allowed) {
        var columns = new LinkedHashMap<Integer, String>();
        var seen = new HashSet<String>();
        for (var index = 0; index < header.getLastCellNum(); index++) {
            var cell = header.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) continue;
            if (cell.getCellType() != CellType.STRING) {
                throw invalid("IMPORT_XLSX_HEADER_INVALID", "Every header must be a field-code string");
            }
            var code = cell.getStringCellValue().trim();
            if (code.isEmpty()) continue;
            if (!allowed.containsKey(code)) {
                throw invalid("IMPORT_XLSX_HEADER_UNKNOWN", "Unknown or excluded import header: " + code);
            }
            if (!seen.add(code)) throw invalid("IMPORT_XLSX_HEADER_DUPLICATE", "Duplicate import header: " + code);
            columns.put(index, code);
        }
        if (columns.isEmpty()) throw invalid("IMPORT_XLSX_HEADER_INVALID", "At least one import header is required");
            return Collections.unmodifiableMap(new LinkedHashMap<>(columns));
    }

    private Map<String, JsonNode> readRow(Row source, Map<Integer, String> columns,
                                           Map<String, ImportViews.TemplateField> allowed) {
        var result = new LinkedHashMap<String, JsonNode>();
        for (var column : columns.entrySet()) {
            var cell = source.getCell(column.getKey(), Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null || cell.getCellType() == CellType.BLANK) continue;
            result.put(column.getValue(), readCell(cell, allowed.get(column.getValue())));
        }
        for (var index = 0; index < source.getLastCellNum(); index++) {
            var cell = source.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell != null && cell.getCellType() == CellType.FORMULA) {
                throw invalid("IMPORT_XLSX_FORMULA", "Formula cells are not accepted");
            }
            if (cell != null && cell.getCellType() != CellType.BLANK && !columns.containsKey(index)) {
                throw invalid("IMPORT_XLSX_UNMAPPED_CELL", "Data exists below an empty or unknown header");
            }
        }
        return result;
    }

    private JsonNode readCell(Cell cell, ImportViews.TemplateField field) {
        return switch (cell.getCellType()) {
            case STRING -> mapper.getNodeFactory().textNode(cell.getStringCellValue());
            case BOOLEAN -> mapper.getNodeFactory().booleanNode(cell.getBooleanCellValue());
            case NUMERIC -> numeric(cell, field);
            case FORMULA -> throw invalid("IMPORT_XLSX_FORMULA", "Formula cells are not accepted");
            case ERROR -> throw invalid("IMPORT_XLSX_CELL_ERROR", "Excel error cells are not accepted");
            case BLANK, _NONE -> mapper.getNodeFactory().nullNode();
        };
    }

    private JsonNode numeric(Cell cell, ImportViews.TemplateField field) {
        if (DateUtil.isCellDateFormatted(cell)) {
            LocalDateTime value = cell.getLocalDateTimeCellValue();
            if ("DATE".equals(field.type())) return mapper.getNodeFactory().textNode(value.toLocalDate().toString());
            return mapper.getNodeFactory().textNode(value.toString());
        }
        return DecimalNode.valueOf(BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros());
    }

    private static boolean hintRow(Row row, int columnCount) {
        if (row == null || columnCount == 0) return false;
        var values = 0;
        for (var index = 0; index < row.getLastCellNum(); index++) {
            var cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) continue;
            if (cell.getCellType() != CellType.STRING || !cell.getStringCellValue().startsWith(HINT_PREFIX)) return false;
            values++;
        }
        return values == columnCount;
    }

    private static void writeJson(Cell cell, JsonNode value) {
        if (value == null || value.isNull()) return;
        if (value.isBoolean()) cell.setCellValue(value.booleanValue());
        else if (value.isNumber()) cell.setCellValue(value.decimalValue().doubleValue());
        else cell.setCellValue(value.isTextual() ? value.textValue() : value.toString());
    }

    private static ImportWorkbookException invalid(String code, String message) {
        return new ImportWorkbookException(code, message);
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

    public static final class ImportWorkbookException extends RuntimeException {
        private final String code;

        public ImportWorkbookException(String code, String message) {
            super(message);
            this.code = code;
        }

        public ImportWorkbookException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() { return code; }
    }
}
