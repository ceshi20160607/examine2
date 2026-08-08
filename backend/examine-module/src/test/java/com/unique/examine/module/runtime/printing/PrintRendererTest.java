package com.unique.examine.module.runtime.printing;

import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PrintRendererTest {
    private final PrintRenderer renderer = new PrintRenderer();

    @Test
    void escapesPreviewAndCreatesAValidImmutablePdf() throws Exception {
        var snapshot = new PrintViews.Snapshot("合同 <A>", "HT-1 · ACTIVE · v3", "内部打印",
                "A4", "PORTRAIT", List.of(
                new PrintViews.Line("客户", "customer", "甲方 & <script>alert(1)</script>"),
                new PrintViews.Line("金额", "amount", "12800.50")
        ));

        var html = renderer.html(snapshot);
        var pdf = renderer.pdf(snapshot);

        assertThat(html).contains("合同 &lt;A&gt;")
                .contains("甲方 &amp; &lt;script&gt;alert(1)&lt;/script&gt;")
                .doesNotContain("<script>alert(1)</script>");
        assertThat(pdf).startsWith('%', 'P', 'D', 'F');
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isOne();
            assertThat(document.getPage(0).getMediaBox().getWidth()).isEqualTo(595.27563f);
        }
    }

    @Test
    void paginatesLongValuesAndHonorsLandscapePaper() throws Exception {
        var longValue = "打印字段值".repeat(400);
        var snapshot = new PrintViews.Snapshot("长记录", "REC-2", "page footer", "A5", "LANDSCAPE",
                List.of(new PrintViews.Line("说明", "note", longValue),
                        new PrintViews.Line("结果", "result", longValue)));

        var pdf = renderer.pdf(snapshot);

        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            assertThat(document.getPage(0).getMediaBox().getWidth())
                    .isGreaterThan(document.getPage(0).getMediaBox().getHeight());
        }
    }

    @Test
    void rendersCompositionHeaderSignatureSealPreprintAndMachineReadableCodes() throws Exception {
        var snapshot = new PrintViews.Snapshot(
                "采购中心 · 受控文件", "采购申请单", "PO-2026-001 · ACTIVE · v7", "第 {page} 页",
                "A4", "PORTRAIT", List.of(new PrintViews.Line("供应商", "supplier", "示例供应商")),
                List.of(new PrintViews.Table("采购明细", "lines", List.of("物料", "数量", "单价"),
                        List.of(List.of("M-01", "2", "18.50"), List.of("M-02", "4", "7.25")))),
                List.of(
                        new PrintViews.ResolvedPositionedElement("SIGNATURE", "申请人签名\n张三", "signature",
                                20, 220, 50, 24, null, null),
                        new PrintViews.ResolvedPositionedElement("SEAL", "审批章", null, 135, 220, 36, 36,
                                null, null),
                        new PrintViews.ResolvedPositionedElement("PREPRINT", "财务联", null, 165, 15, 25, 12,
                                null, null)),
                List.of(new PrintViews.ResolvedCodeBlock("QR", "单据二维码", "PO-2026-001"),
                        new PrintViews.ResolvedCodeBlock("BARCODE", "单据条码", "PO2026001")));

        var html = renderer.html(snapshot);
        var pdf = renderer.pdf(snapshot);

        assertThat(html).contains("受控文件", "print-table", "采购明细", "data-kind=\"SIGNATURE\"",
                        "data-kind=\"SEAL\"", "data-kind=\"PREPRINT\"", "data:image/png;base64,")
                .doesNotContain("<script>");
        try (var document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }
}
