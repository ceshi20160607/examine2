package com.unique.examine.module.runtime.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImportXlsxCodecTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ImportXlsxCodec codec = new ImportXlsxCodec(mapper);

    @Test
    void generatesLiveHeadersAndParsesTypedRowsWithoutEvaluatingContent() throws Exception {
        var bytes = codec.template(template());
        byte[] populated;
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes));
             var output = new ByteArrayOutputStream()) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("external_key");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("quantity");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("# External key | TEXT | required | unique");
            var row = sheet.createRow(2);
            row.createCell(0, CellType.STRING).setCellValue("00125");
            row.createCell(1, CellType.NUMERIC).setCellValue(12.5d);
            row.createCell(2, CellType.BOOLEAN).setCellValue(true);
            var date = row.createCell(3);
            date.setCellValue(LocalDate.of(2026, 7, 29));
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            date.setCellStyle(dateStyle);
            workbook.write(output);
            populated = output.toByteArray();
        }

        var rows = codec.parse(populated, "items.xlsx", template());
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("external_key").textValue()).isEqualTo("00125");
        assertThat(rows.getFirst().get("quantity").decimalValue()).isEqualByComparingTo("12.5");
        assertThat(rows.getFirst().get("enabled").booleanValue()).isTrue();
        assertThat(rows.getFirst().get("due_date").textValue()).isEqualTo("2026-07-29");
    }

    @Test
    void rejectsUnknownDuplicateFormulaAndOversizedRowsBeforePreview() throws Exception {
        assertCode(workbook(workbook -> workbook.getSheetAt(0).getRow(0).getCell(0)
                .setCellValue("unknown")), "IMPORT_XLSX_HEADER_UNKNOWN");
        assertCode(workbook(workbook -> workbook.getSheetAt(0).getRow(0).getCell(1)
                .setCellValue("external_key")), "IMPORT_XLSX_HEADER_DUPLICATE");
        assertCode(workbook(workbook -> workbook.getSheetAt(0).createRow(2).createCell(0)
                .setCellFormula("1+1")), "IMPORT_XLSX_FORMULA");
        assertCode(workbook(workbook -> {
            var sheet = workbook.getSheetAt(0);
            for (var index = 0; index < 201; index++) {
                sheet.createRow(index + 2).createCell(0, CellType.STRING).setCellValue("K-" + index);
            }
        }), "IMPORT_XLSX_ROW_LIMIT");
        assertThatThrownBy(() -> codec.parse(codec.template(template()), "items.xls", template()))
                .isInstanceOf(ImportXlsxCodec.ImportWorkbookException.class)
                .extracting(exception -> ((ImportXlsxCodec.ImportWorkbookException) exception).code())
                .isEqualTo("IMPORT_XLSX_TYPE_INVALID");
    }

    @Test
    void writesOnlyFailedInputRowsAndStableErrorColumns() throws Exception {
        var now = LocalDateTime.now();
        var failed = new ImportRepository.RowRecord(1, 2, 3, 4, 7, null, "INVALID",
                Map.of("external_key", mapper.getNodeFactory().textNode("00125"),
                        "quantity", mapper.getNodeFactory().numberNode(4)),
                null, "IMPORT_DUPLICATE", "External key is duplicated", null, null, null, now, now);
        var bytes = codec.errors(List.of(failed));

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("external_key");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("quantity");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("_error_code");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("_error_message");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("00125");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue()).isEqualTo(4d);
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("IMPORT_DUPLICATE");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("External key is duplicated");
        }
    }

    private void assertCode(byte[] bytes, String code) {
        assertThatThrownBy(() -> codec.parse(bytes, "items.xlsx", template()))
                .isInstanceOf(ImportXlsxCodec.ImportWorkbookException.class)
                .extracting(exception -> ((ImportXlsxCodec.ImportWorkbookException) exception).code())
                .isEqualTo(code);
    }

    private byte[] workbook(WorkbookEdit edit) throws Exception {
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(codec.template(template())));
             var output = new ByteArrayOutputStream()) {
            edit.apply(workbook);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static ImportViews.Template template() {
        return new ImportViews.Template("10", "20", "a".repeat(64), List.of(
                new ImportViews.TemplateField("external_key", "External key", "TEXT", true, true),
                new ImportViews.TemplateField("quantity", "Quantity", "NUMBER", false, false),
                new ImportViews.TemplateField("enabled", "Enabled", "CHECKBOX", false, false),
                new ImportViews.TemplateField("due_date", "Due date", "DATE", false, false)
        ), List.of(new ImportViews.ExcludedField("created_at", "Created at", "CREATED_AT", "SYSTEM_OWNED")),
                List.of("NEW", "UPSERT"), 200);
    }

    @FunctionalInterface
    private interface WorkbookEdit { void apply(XSSFWorkbook workbook) throws Exception; }
}
