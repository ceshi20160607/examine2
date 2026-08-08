package com.unique.examine.module.runtime.exporting;

import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.report.api.ReportRuntimeViews;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class ExportXlsxCodec {
    public record Column(String code, String name, String type) { }

    public record ReportColumn(String code, String name, String type) { }

    public byte[] write(String moduleCode, List<Column> fields, List<RecordRuntimeViews.RecordSummary> records) {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("export");
            var headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            var headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            var codeStyle = workbook.createCellStyle();
            codeStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            codeStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var codeFont = workbook.createFont();
            codeFont.setItalic(true);
            codeFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            codeStyle.setFont(codeFont);

            var columns = new java.util.ArrayList<Column>();
            columns.add(new Column("_record_no", "Record no", "TEXT"));
            columns.add(new Column("_status", "Status", "TEXT"));
            columns.add(new Column("_title", "Title", "TEXT"));
            columns.addAll(fields);
            var header = sheet.createRow(0);
            var codes = sheet.createRow(1);
            for (var index = 0; index < columns.size(); index++) {
                var column = columns.get(index);
                var name = header.createCell(index, CellType.STRING);
                name.setCellValue(column.name());
                name.setCellStyle(headerStyle);
                var code = codes.createCell(index, CellType.STRING);
                code.setCellValue(column.code());
                code.setCellStyle(codeStyle);
                sheet.setColumnWidth(index, Math.min(60, Math.max(14,
                        Math.max(column.name().length(), column.code().length()) + 4)) * 256);
            }
            var rowIndex = 2;
            for (var record : records) {
                var row = sheet.createRow(rowIndex++);
                text(row.createCell(0), record.recordNo());
                text(row.createCell(1), record.status());
                text(row.createCell(2), record.title());
                var values = new LinkedHashMap<String, RecordRuntimeViews.FieldValue>();
                record.values().forEach(value -> values.put(value.fieldCode(), value));
                for (var index = 0; index < fields.size(); index++) {
                    var value = values.get(fields.get(index).code());
                    if (value != null) writeValue(row.createCell(index + 3), value.value(), value.displayValue());
                }
            }
            sheet.createFreezePane(0, 2);
            if (!columns.isEmpty()) sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.size() - 1));
            workbook.getProperties().getCoreProperties().setTitle(moduleCode + " module export");
            workbook.getProperties().getCoreProperties().setDescription("Permission-scoped runtime XLSX export");
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate export workbook", exception);
        }
    }

    /** Creates a report workbook whose data sheet has exactly one ordered header row. */
    public byte[] writeReport(
            String reportCode,
            List<ReportColumn> columns,
            List<ReportRuntimeViews.Row> rows,
            long totalRows,
            boolean truncated
    ) {
        if (reportCode == null || reportCode.isBlank() || columns == null
                || columns.isEmpty() || columns.size() > 100 || rows == null
                || rows.size() > 5_000 || totalRows < rows.size()
                || truncated != (totalRows > rows.size())) {
            throw new IllegalArgumentException("Report workbook input is invalid");
        }
        var codes = new LinkedHashSet<String>();
        if (columns.stream().anyMatch(column -> column == null
                || column.code() == null || column.code().isBlank()
                || column.name() == null || column.name().isBlank()
                || !codes.add(column.code()))) {
            throw new IllegalArgumentException("Report workbook columns are invalid");
        }
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("report");
            var headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            var font = workbook.createFont();
            font.setBold(true);
            font.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(font);
            var header = sheet.createRow(0);
            for (var index = 0; index < columns.size(); index++) {
                var column = columns.get(index);
                var cell = header.createCell(index, CellType.STRING);
                cell.setCellValue(column.name());
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(index, Math.min(60, Math.max(14,
                        Math.max(column.name().length(), column.code().length()) + 4)) * 256);
            }
            var rowIndex = 1;
            for (var source : rows) {
                var row = sheet.createRow(rowIndex++);
                var values = new LinkedHashMap<String, ReportRuntimeViews.Value>();
                source.values().forEach(value -> values.put(value.fieldCode(), value));
                for (var index = 0; index < columns.size(); index++) {
                    var value = values.get(columns.get(index).code());
                    if (value != null) {
                        writeValue(row.createCell(index), value.value(), value.displayValue());
                    }
                }
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, columns.size() - 1));

            var summary = workbook.createSheet("summary");
            var labels = List.of("totalRows", "exportedRows", "truncated", "rowLimit");
            var values = List.of(Long.toString(totalRows), Integer.toString(rows.size()),
                    Boolean.toString(truncated), "5000");
            for (var index = 0; index < labels.size(); index++) {
                var line = summary.createRow(index);
                line.createCell(0, CellType.STRING).setCellValue(labels.get(index));
                line.createCell(1, CellType.STRING).setCellValue(values.get(index));
            }
            summary.setColumnWidth(0, 20 * 256);
            summary.setColumnWidth(1, 20 * 256);
            workbook.getProperties().getCoreProperties().setTitle(reportCode + " report export");
            workbook.getProperties().getCoreProperties().setDescription(
                    "Permission-scoped report XLSX export");
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to generate report workbook", exception);
        }
    }

    private static void writeValue(Cell cell, Object value, String display) {
        if (value instanceof Boolean booleanValue) cell.setCellValue(booleanValue);
        else if (value instanceof Byte || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Float || value instanceof Double
                || value instanceof BigDecimal || value instanceof BigInteger) {
            cell.setCellValue(((Number) value).doubleValue());
        } else text(cell, display != null ? display : value == null ? null : String.valueOf(value));
    }

    private static void text(Cell cell, String value) {
        if (value != null) cell.setCellValue(value);
    }
}
