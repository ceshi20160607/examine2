package com.unique.unexamine.dataexchange.manage;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 模块导入导出的表格编解码器。业务权限、字段映射和记录写入仍由服务层处理，
 * 这里仅负责受限的 CSV/Excel 表格读取与生成。
 */
@Component
public class SpreadsheetExchangeCodec {
    private static final int MAX_COLUMNS = 200;
    private static final int MAX_ROWS = 5_001;
    private static final int MAX_CELL_CHARACTERS = 20_000;

    public ParsedSheet read(byte[] content, String originalName, String contentType) {
        String name = originalName == null ? "" : originalName.toLowerCase(Locale.ROOT);
        String mediaType = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")
                || mediaType.contains("spreadsheet") || mediaType.contains("excel")) {
            return readWorkbook(content);
        }
        if (name.endsWith(".csv") || mediaType.contains("csv") || mediaType.startsWith("text/")) {
            return readCsv(new String(content, StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException("仅支持 .xlsx、.xls 或 .csv 导入文件");
    }

    public byte[] xlsx(String sheetName, List<String> headers, List<List<String>> rows) {
        if (headers.isEmpty() || headers.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException("表格列数必须在 1 到 200 之间");
        }
        if (rows.size() + 1 > MAX_ROWS) {
            throw new IllegalArgumentException("表格数据行数超过允许范围");
        }
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(safeSheetName(sheetName));
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            Row header = sheet.createRow(0);
            for (int index = 0; index < headers.size(); index++) {
                Cell cell = header.createCell(index, CellType.STRING);
                cell.setCellValue(limited(headers.get(index)));
                cell.setCellStyle(headerStyle);
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                List<String> values = rows.get(rowIndex);
                for (int column = 0; column < headers.size(); column++) {
                    row.createCell(column, CellType.STRING)
                            .setCellValue(limited(column < values.size() ? values.get(column) : ""));
                }
            }
            for (int index = 0; index < headers.size(); index++) {
                sheet.setColumnWidth(index, Math.min(40, Math.max(12, headers.get(index).length() + 4)) * 256);
            }
            sheet.createFreezePane(0, 1);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Excel 文件生成失败", exception);
        }
    }

    public byte[] csv(List<String> headers, List<List<String>> rows) {
        StringBuilder result = new StringBuilder("\uFEFF");
        result.append(headers.stream().map(this::csvCell).collect(java.util.stream.Collectors.joining(",")))
                .append('\n');
        rows.forEach(row -> result.append(row.stream().map(this::safeCsvValue).map(this::csvCell)
                .collect(java.util.stream.Collectors.joining(","))).append('\n'));
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private ParsedSheet readWorkbook(byte[] content) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            if (workbook.getNumberOfSheets() < 1) throw new IllegalArgumentException("Excel 文件没有工作表");
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet.getLastRowNum() + 1 > MAX_ROWS) throw new IllegalArgumentException("Excel 数据行数超过允许范围");
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<List<String>> records = new ArrayList<>();
            int width = 0;
            for (Row row : sheet) {
                if (row.getLastCellNum() > MAX_COLUMNS) throw new IllegalArgumentException("Excel 列数超过 200");
                width = Math.max(width, Math.max(0, row.getLastCellNum()));
            }
            if (width == 0) throw new IllegalArgumentException("Excel 表头不能为空");
            for (int rowIndex = sheet.getFirstRowNum(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                List<String> values = new ArrayList<>();
                boolean populated = false;
                for (int column = 0; column < width; column++) {
                    Cell cell = row == null ? null : row.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                    if (cell != null && cell.getCellType() == CellType.FORMULA) {
                        throw new IllegalArgumentException("Excel 第 " + (rowIndex + 1) + " 行包含公式，请改为普通值后重试");
                    }
                    String value = cell == null ? "" : limited(formatter.formatCellValue(cell)).strip();
                    values.add(value);
                    populated |= !value.isBlank();
                }
                if (populated) records.add(List.copyOf(values));
            }
            return validated("XLSX", records);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Excel 文件无法读取或已经损坏", exception);
        }
    }

    private ParsedSheet readCsv(String content) {
        String normalized = content.startsWith("\uFEFF") ? content.substring(1) : content;
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < normalized.length() && normalized.charAt(index + 1) == '"') {
                    cell.append('"');
                    index++;
                } else quoted = !quoted;
            } else if (current == ',' && !quoted) {
                record.add(limited(cell.toString()));
                cell.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < normalized.length() && normalized.charAt(index + 1) == '\n') index++;
                record.add(limited(cell.toString()));
                cell.setLength(0);
                if (record.stream().anyMatch(value -> !value.isBlank())) records.add(List.copyOf(record));
                record.clear();
            } else cell.append(current);
        }
        if (quoted) throw new IllegalArgumentException("CSV 引号没有闭合");
        record.add(limited(cell.toString()));
        if (record.stream().anyMatch(value -> !value.isBlank())) records.add(List.copyOf(record));
        return validated("CSV", records);
    }

    private ParsedSheet validated(String format, List<List<String>> records) {
        if (records.isEmpty()) throw new IllegalArgumentException("导入文件表头不能为空");
        if (records.size() > MAX_ROWS) throw new IllegalArgumentException("导入文件数据行数超过允许范围");
        List<String> headers = records.getFirst().stream().map(String::strip).toList();
        if (headers.size() > MAX_COLUMNS || headers.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("导入表头不能为空且最多 200 列");
        }
        Set<String> unique = new LinkedHashSet<>(headers);
        if (unique.size() != headers.size()) throw new IllegalArgumentException("导入表头不能重复");
        return new ParsedSheet(format, headers, records.subList(1, records.size()));
    }

    private String safeCsvValue(String value) {
        String result = value == null ? "" : value;
        return !result.isEmpty() && "=+-@".indexOf(result.charAt(0)) >= 0 ? "'" + result : result;
    }

    private String csvCell(String value) {
        String text = limited(value == null ? "" : value);
        return text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
                ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    private String limited(String value) {
        String result = value == null ? "" : value;
        if (result.length() > MAX_CELL_CHARACTERS) throw new IllegalArgumentException("单元格内容超过 20000 个字符");
        return result;
    }

    private String safeSheetName(String value) {
        String result = value == null || value.isBlank() ? "数据" : value.replaceAll("[\\\\/?*\\[\\]:]", "_");
        return result.substring(0, Math.min(31, result.length()));
    }

    public record ParsedSheet(String format, List<String> headers, List<List<String>> rows) {
    }
}
