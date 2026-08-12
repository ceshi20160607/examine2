package com.unique.examine.module.runtime.exporting;

import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExportXlsxCodecTest {
    private final ExportXlsxCodec codec = new ExportXlsxCodec();

    @Test
    void writesStableSystemAndFieldColumnsWithTypedSafeValues() throws Exception {
        var content = codec.write("work_order", List.of(
                new ExportXlsxCodec.Column("quantity", "Quantity", "DECIMAL"),
                new ExportXlsxCodec.Column("enabled", "Enabled", "SWITCH"),
                new ExportXlsxCodec.Column("note", "Note", "TEXT")
        ), List.of(record(List.of(
                value("quantity", "Quantity", "DECIMAL", new BigDecimal("12.50"), "12.50"),
                value("enabled", "Enabled", "SWITCH", true, "Yes"),
                value("note", "Note", "TEXT", "=HYPERLINK(\"https://bad.test\")", null)
        ))));

        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("export");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Record no");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("_record_no");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("quantity");
            assertThat(sheet.getRow(2).getCell(3).getNumericCellValue()).isEqualTo(12.5d);
            assertThat(sheet.getRow(2).getCell(4).getBooleanCellValue()).isTrue();
            assertThat(sheet.getRow(2).getCell(5).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(2).getCell(5).getStringCellValue()).startsWith("=HYPERLINK");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
            assertThat(sheet.getCTWorksheet().isSetAutoFilter()).isTrue();
        }
    }

    @Test
    void createsHeaderOnlyWorkbookForAnEmptyAuthorizedResult() throws Exception {
        var content = codec.write("work_order",
                List.of(new ExportXlsxCodec.Column("name", "Name", "TEXT")), List.of());
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            assertThat(workbook.getNumberOfSheets()).isOne();
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isOne();
        }
    }

    private static RecordRuntimeViews.RecordSummary record(List<RecordRuntimeViews.FieldValue> values) {
        return new RecordRuntimeViews.RecordSummary("100", "WO-100", 3L, "ACTIVE", "Example", values);
    }

    private static RecordRuntimeViews.FieldValue value(
            String code, String name, String type, Object value, String display) {
        return new RecordRuntimeViews.FieldValue(code, name, type, value, display);
    }
}
