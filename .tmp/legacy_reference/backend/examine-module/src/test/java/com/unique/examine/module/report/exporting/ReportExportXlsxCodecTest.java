package com.unique.examine.module.report.exporting;

import com.unique.examine.module.report.api.ReportRuntimeViews;
import com.unique.examine.module.runtime.exporting.ExportXlsxCodec;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.AbstractList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportExportXlsxCodecTest {
    private final ExportXlsxCodec codec = new ExportXlsxCodec();

    @Test
    void writesOneHeaderRowAndPreservesPinnedFieldOrder() throws Exception {
        var content = codec.writeReport("ops_report", List.of(
                        new ExportXlsxCodec.ReportColumn(
                                "status", "Pinned status", "STATUS"),
                        new ExportXlsxCodec.ReportColumn(
                                "amount", "Pinned amount", "NUMBER")),
                List.of(new ReportRuntimeViews.Row(
                        "1", "R-1", 1, "ACTIVE", "row", List.of(
                        new ReportRuntimeViews.Value(
                                "amount", "Amount", "NUMBER", 12.5, "12.5"),
                        new ReportRuntimeViews.Value(
                                "status", "Status", "STATUS", "OPEN", "Open")))),
                6, true);

        try (var workbook = new XSSFWorkbook(
                new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("report");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Pinned status");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue())
                    .isEqualTo("Pinned amount");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("Open");
            assertThat(sheet.getRow(1).getCell(1).getNumericCellValue())
                    .isEqualTo(12.5);
            assertThat(sheet.getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheet("summary").getRow(2).getCell(1)
                    .getStringCellValue()).isEqualTo("true");
        }
    }

    @Test
    void rejectsMoreThanFiveThousandRowsBeforeWorkbookAllocation() {
        var oversized = new AbstractList<ReportRuntimeViews.Row>() {
            @Override
            public ReportRuntimeViews.Row get(int index) {
                throw new AssertionError("rows must not be read");
            }

            @Override
            public int size() {
                return 5_001;
            }
        };

        assertThatThrownBy(() -> codec.writeReport("ops_report", List.of(
                        new ExportXlsxCodec.ReportColumn(
                                "status", "Status", "STATUS")),
                oversized, 5_001, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input is invalid");
    }
}
