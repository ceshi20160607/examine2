package com.unique.examine.module.runtime.printing;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;
import java.util.ArrayList;
import javax.imageio.ImageIO;

@Component
public class PrintRenderer {
    private static final float SCALE = 2F;

    public String html(PrintViews.Snapshot snapshot) {
        var landscape = "LANDSCAPE".equals(snapshot.orientation());
        var page = snapshot.paperSize().toLowerCase() + (landscape ? " landscape" : "");
        var html = new StringBuilder(2048);
        html.append("<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\"><style>")
                .append("@page{size:").append(page).append(";margin:18mm}*")
                .append("{box-sizing:border-box}body{margin:0;color:#172026;font:14px/1.55 Arial,'Microsoft YaHei',sans-serif}")
                .append("main{max-width:100%;margin:auto}h1{margin:0 0 4px;font-size:24px;text-align:center}")
                .append(".subtitle{text-align:center;color:#65727b;margin-bottom:24px}.fields{border-top:2px solid #263238}")
                .append(".field{display:grid;grid-template-columns:32% 1fr;border-bottom:1px solid #cfd8dc;break-inside:avoid}")
                .append(".field strong,.field span{padding:10px 12px;overflow-wrap:anywhere}.field strong{background:#f3f6f7}")
                .append(".doc-header{text-align:center;color:#4f5f68;margin-bottom:8px}.print-table{width:100%;border-collapse:collapse;margin:18px 0;break-inside:auto}")
                .append(".print-table caption{text-align:left;font-weight:700;padding:7px;background:#edf2f4}.print-table th,.print-table td{border:1px solid #cfd8dc;padding:7px;overflow-wrap:anywhere}")
                .append(".code-blocks{display:flex;gap:20px;flex-wrap:wrap;margin-top:18px}.code-block{display:grid;gap:5px;text-align:center}.code-block img{max-width:190px;height:96px;object-fit:contain}")
                .append(".position-layer{position:relative;min-height:55mm;margin-top:14mm}.positioned{position:absolute;border:1px dashed #7d8b92;padding:4px;overflow:hidden;white-space:pre-wrap}")
                .append("footer{margin-top:24px;padding-top:10px;border-top:1px solid #cfd8dc;color:#6b7780;text-align:center}")
                .append("</style></head><body><main><div class=\"doc-header\">").append(escape(snapshot.header()))
                .append("</div><h1>").append(escape(snapshot.title())).append("</h1><div class=\"subtitle\">")
                .append(escape(snapshot.subtitle())).append("</div><section class=\"fields\">");
        for (var line : snapshot.lines()) {
            html.append("<div class=\"field\" data-field-code=\"").append(escape(line.fieldCode()))
                    .append("\"><strong>").append(escape(line.label())).append("</strong><span>")
                    .append(escape(line.value())).append("</span></div>");
        }
        html.append("</section>");
        for (var table : snapshot.tables()) {
            html.append("<table class=\"print-table\" data-field-code=\"").append(escape(table.fieldCode()))
                    .append("\"><caption>").append(escape(table.label())).append("</caption><thead><tr>");
            table.columns().forEach(column -> html.append("<th>").append(escape(column)).append("</th>"));
            html.append("</tr></thead><tbody>");
            table.rows().forEach(row -> {
                html.append("<tr>");
                row.forEach(value -> html.append("<td>").append(escape(value)).append("</td>"));
                html.append("</tr>");
            });
            html.append("</tbody></table>");
        }
        if (!snapshot.codeBlocks().isEmpty()) {
            html.append("<section class=\"code-blocks\">");
            snapshot.codeBlocks().forEach(block -> html.append("<div class=\"code-block\"><strong>")
                    .append(escape(block.label())).append("</strong><img alt=\"").append(escape(block.kind()))
                    .append("\" src=\"").append(codeDataUri(block)).append("\"><small>")
                    .append(escape(block.value())).append("</small></div>"));
            html.append("</section>");
        }
        if (!snapshot.positionedElements().isEmpty()) {
            html.append("<section class=\"position-layer\">");
            snapshot.positionedElements().forEach(item -> html.append("<div class=\"positioned\" data-kind=\"")
                    .append(escape(item.kind())).append("\" style=\"left:").append(item.xMm()).append("mm;top:")
                    .append(item.yMm()).append("mm;width:").append(item.widthMm()).append("mm;height:")
                    .append(item.heightMm()).append("mm\">").append(escape(item.label()))
                    .append(item.imageDataUri() == null ? "" : "<img alt=\"positioned asset\" src=\""
                            + escape(item.imageDataUri()) + "\" style=\"max-width:100%;max-height:calc(100% - 18px)\">")
                    .append("</div>"));
            html.append("</section>");
        }
        html.append("<footer>").append(escape(snapshot.footer())).append("</footer></main></body></html>");
        return html.toString();
    }

    public byte[] pdf(PrintViews.Snapshot snapshot) {
        var rectangle = rectangle(snapshot.paperSize(), snapshot.orientation());
        var width = Math.round(rectangle.getWidth());
        var height = Math.round(rectangle.getHeight());
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            renderPages(snapshot, width, height, image -> {
                var page = new PDPage(rectangle);
                document.addPage(page);
                var resource = LosslessFactory.createFromImage(document, image);
                try (var stream = new PDPageContentStream(document, page)) {
                    stream.drawImage(resource, 0, 0, rectangle.getWidth(), rectangle.getHeight());
                }
                image.flush();
            });
            document.save(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Print PDF could not be generated", exception);
        }
    }

    private void renderPages(PrintViews.Snapshot snapshot, int pageWidth, int pageHeight,
                             PageConsumer consumer) throws Exception {
        var state = new PageState(image(pageWidth, pageHeight), 0);
        var graphics = graphics(state.image());
        var pageNo = 1;
        try {
            state = header(graphics, state, snapshot, pageWidth, false);
            for (var line : snapshot.lines()) {
                var valueLines = wrap(graphics, safe(line.value()), pageWidth - 174);
                var offset = 0;
                while (offset < valueLines.size()) {
                    var available = pageHeight - 74 - state.y();
                    var maxLines = Math.max(0, (available - 24) / 16);
                    if (maxLines < 1 || available < 42) {
                        footer(graphics, snapshot.footer(), pageWidth, pageHeight, pageNo++);
                        graphics.dispose();
                        consumer.accept(state.image());
                        state = new PageState(image(pageWidth, pageHeight), 0);
                        graphics = graphics(state.image());
                        state = header(graphics, state, snapshot, pageWidth, true);
                        continue;
                    }
                    var count = Math.min(maxLines, valueLines.size() - offset);
                    var slice = valueLines.subList(offset, offset + count);
                    var blockHeight = Math.max(42, 24 + slice.size() * 16);
                    var renderedLine = offset == 0 ? line
                            : new PrintViews.Line(line.label() + "（续）", line.fieldCode(), line.value());
                    drawField(graphics, renderedLine, slice, state.y(), pageWidth, blockHeight);
                    state = new PageState(state.image(), state.y() + blockHeight);
                    offset += count;
                    if (offset < valueLines.size()) {
                        footer(graphics, snapshot.footer(), pageWidth, pageHeight, pageNo++);
                        graphics.dispose();
                        consumer.accept(state.image());
                        state = new PageState(image(pageWidth, pageHeight), 0);
                        graphics = graphics(state.image());
                        state = header(graphics, state, snapshot, pageWidth, true);
                    }
                }
            }
            for (var table : snapshot.tables()) {
                var tableLines = new ArrayList<PrintViews.Line>();
                if (table.rows().isEmpty()) {
                    tableLines.add(new PrintViews.Line(table.label(), table.fieldCode(), "（无明细）"));
                } else {
                    for (var row = 0; row < table.rows().size(); row++) {
                        var values = table.rows().get(row);
                        var parts = new ArrayList<String>();
                        for (var column = 0; column < table.columns().size(); column++) {
                            parts.add(table.columns().get(column) + "："
                                    + (column < values.size() ? safe(values.get(column)) : ""));
                        }
                        tableLines.add(new PrintViews.Line(
                                row == 0 ? table.label() : table.label() + " #" + (row + 1),
                                table.fieldCode(), String.join(" | ", parts)));
                    }
                }
                for (var line : tableLines) {
                    var valueLines = wrap(graphics, safe(line.value()), pageWidth - 174);
                    var blockHeight = Math.max(42, 24 + valueLines.size() * 16);
                    if (pageHeight - 74 - state.y() < blockHeight) {
                        footer(graphics, snapshot.footer(), pageWidth, pageHeight, pageNo++);
                        graphics.dispose();
                        consumer.accept(state.image());
                        state = new PageState(image(pageWidth, pageHeight), 0);
                        graphics = graphics(state.image());
                        state = header(graphics, state, snapshot, pageWidth, true);
                    }
                    drawField(graphics, line, valueLines, state.y(), pageWidth, blockHeight);
                    state = new PageState(state.image(), state.y() + blockHeight);
                }
            }
            for (var block : snapshot.codeBlocks()) {
                if (pageHeight - 74 - state.y() < 122) {
                    footer(graphics, snapshot.footer(), pageWidth, pageHeight, pageNo++);
                    graphics.dispose();
                    consumer.accept(state.image());
                    state = new PageState(image(pageWidth, pageHeight), 0);
                    graphics = graphics(state.image());
                    state = header(graphics, state, snapshot, pageWidth, true);
                }
                drawCodeBlock(graphics, block, 42, state.y() + 8, pageWidth - 84, 100);
                state = new PageState(state.image(), state.y() + 112);
            }
            drawPositionedElements(graphics, snapshot.positionedElements(), pageWidth, pageHeight);
            footer(graphics, snapshot.footer(), pageWidth, pageHeight, pageNo);
            graphics.dispose();
            consumer.accept(state.image());
        } finally {
            graphics.dispose();
        }
    }

    private PageState header(Graphics2D graphics, PageState state, PrintViews.Snapshot snapshot,
                             int pageWidth, boolean continuation) {
        graphics.setColor(new Color(28, 39, 44));
        graphics.setFont(new Font("SansSerif", Font.BOLD, continuation ? 16 : 22));
        centered(graphics, continuation ? snapshot.title() + "（续）" : snapshot.title(), pageWidth,
                continuation ? 48 : 56);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 10));
        graphics.setColor(new Color(100, 114, 123));
        centered(graphics, snapshot.subtitle(), pageWidth, continuation ? 67 : 78);
        if (!continuation && !safe(snapshot.header()).isBlank()) {
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 9));
            graphics.drawString(safe(snapshot.header()), 42, 36);
        }
        graphics.setColor(new Color(38, 50, 56));
        graphics.fillRect(42, continuation ? 79 : 92, pageWidth - 84, 2);
        return new PageState(state.image(), continuation ? 81 : 94);
    }

    private void drawField(Graphics2D graphics, PrintViews.Line line, List<String> values, int y,
                           int pageWidth, int height) {
        var left = 42;
        var labelWidth = 116;
        var contentWidth = pageWidth - 84;
        graphics.setColor(new Color(245, 247, 248));
        graphics.fillRect(left, y, labelWidth, height);
        graphics.setColor(new Color(207, 216, 220));
        graphics.drawRect(left, y, contentWidth, height);
        graphics.drawLine(left + labelWidth, y, left + labelWidth, y + height);
        graphics.setColor(new Color(35, 47, 53));
        graphics.setFont(new Font("SansSerif", Font.BOLD, 10));
        graphics.drawString(safe(line.label()), left + 10, y + 23);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 11));
        var valueY = y + 21;
        for (var value : values) {
            graphics.drawString(value, left + labelWidth + 12, valueY);
            valueY += 16;
        }
    }

    private void footer(Graphics2D graphics, String footer, int pageWidth, int pageHeight, int pageNo) {
        graphics.setColor(new Color(207, 216, 220));
        graphics.drawLine(42, pageHeight - 53, pageWidth - 42, pageHeight - 53);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 9));
        graphics.setColor(new Color(105, 117, 125));
        graphics.drawString(safe(footer), 42, pageHeight - 35);
        var page = "第 " + pageNo + " 页";
        graphics.drawString(page, pageWidth - 42 - graphics.getFontMetrics().stringWidth(page), pageHeight - 35);
    }

    private void drawCodeBlock(Graphics2D graphics, PrintViews.ResolvedCodeBlock block,
                               int x, int y, int width, int height) {
        graphics.setColor(new Color(245, 247, 248));
        graphics.fillRect(x, y, width, height);
        graphics.setColor(new Color(207, 216, 220));
        graphics.drawRect(x, y, width, height);
        graphics.setFont(new Font("SansSerif", Font.BOLD, 10));
        graphics.setColor(new Color(35, 47, 53));
        graphics.drawString(safe(block.label()), x + 10, y + 18);
        var matrix = codeMatrix(block, "QR".equalsIgnoreCase(block.kind()) ? 74 : 210, 58);
        var image = matrixImage(matrix);
        graphics.drawImage(image, x + 10, y + 27, "QR".equalsIgnoreCase(block.kind()) ? 74 : 210, 58, null);
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 8));
        graphics.drawString(truncate(safe(block.value()), 64), x + 230, y + 62);
        image.flush();
    }

    private void drawPositionedElements(Graphics2D graphics, List<PrintViews.ResolvedPositionedElement> values,
                                        int pageWidth, int pageHeight) {
        for (var value : values) {
            var x = mm(value.xMm());
            var y = mm(value.yMm());
            var width = mm(value.widthMm());
            var height = mm(value.heightMm());
            if (x >= pageWidth || y >= pageHeight - 54) continue;
            graphics.setColor(new Color(110, 122, 129));
            graphics.drawRect(x, y, Math.min(width, pageWidth - x - 4), Math.min(height, pageHeight - y - 58));
            graphics.setFont(new Font("SansSerif", Font.PLAIN, 9));
            graphics.drawString(safe(value.label()), x + 4, y + 14);
            if (value.imageDataUri() != null) {
                try {
                    var comma = value.imageDataUri().indexOf(',');
                    var content = Base64.getDecoder().decode(value.imageDataUri().substring(comma + 1));
                    var image = ImageIO.read(new java.io.ByteArrayInputStream(content));
                    if (image != null) {
                        graphics.drawImage(image, x + 4, y + 18, Math.max(1, Math.min(width - 8, pageWidth - x - 8)),
                                Math.max(1, Math.min(height - 22, pageHeight - y - 62)), null);
                        image.flush();
                    }
                } catch (Exception exception) {
                    throw new IllegalStateException("Positioned print image is invalid", exception);
                }
            }
        }
    }

    private static int mm(double value) {
        return (int) Math.round(value * 72D / 25.4D);
    }

    private String codeDataUri(PrintViews.ResolvedCodeBlock block) {
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(matrixImage(codeMatrix(block,
                    "QR".equalsIgnoreCase(block.kind()) ? 180 : 360, 120)), "png", output);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (Exception exception) {
            throw new IllegalStateException("Print code image could not be generated", exception);
        }
    }

    private static BitMatrix codeMatrix(PrintViews.ResolvedCodeBlock block, int width, int height) {
        try {
            var format = "QR".equalsIgnoreCase(block.kind()) ? BarcodeFormat.QR_CODE : BarcodeFormat.CODE_128;
            return new MultiFormatWriter().encode(safe(block.value()), format, width, height);
        } catch (Exception exception) {
            throw new IllegalStateException("QR/barcode value could not be encoded", exception);
        }
    }

    private static BufferedImage matrixImage(BitMatrix matrix) {
        var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (var y = 0; y < matrix.getHeight(); y++) {
            for (var x = 0; x < matrix.getWidth(); x++) {
                image.setRGB(x, y, matrix.get(x, y) ? Color.BLACK.getRGB() : Color.WHITE.getRGB());
            }
        }
        return image;
    }

    private List<String> wrap(Graphics2D graphics, String value, int maxWidth) {
        graphics.setFont(new Font("SansSerif", Font.PLAIN, 11));
        var metrics = graphics.getFontMetrics();
        var lines = new ArrayList<String>();
        for (var paragraph : value.replace("\r", "").split("\n", -1)) {
            var current = new StringBuilder();
            for (var offset = 0; offset < paragraph.length();) {
                var cp = paragraph.codePointAt(offset);
                var part = new String(Character.toChars(cp));
                if (!current.isEmpty() && metrics.stringWidth(current + part) > maxWidth) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                current.append(part);
                offset += Character.charCount(cp);
            }
            lines.add(current.toString());
        }
        if (lines.isEmpty()) lines.add("");
        return lines;
    }

    private BufferedImage image(int pageWidth, int pageHeight) {
        return new BufferedImage(Math.round(pageWidth * SCALE), Math.round(pageHeight * SCALE),
                BufferedImage.TYPE_INT_RGB);
    }

    private Graphics2D graphics(BufferedImage image) {
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.scale(SCALE, SCALE);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return graphics;
    }

    private static PDRectangle rectangle(String paperSize, String orientation) {
        var source = "A5".equals(paperSize) ? PDRectangle.A5 : PDRectangle.A4;
        return "LANDSCAPE".equals(orientation)
                ? new PDRectangle(source.getHeight(), source.getWidth()) : source;
    }
    private static void centered(Graphics2D graphics, String value, int width, int y) {
        var text = safe(value);
        graphics.drawString(text, Math.max(42, (width - graphics.getFontMetrics().stringWidth(text)) / 2), y);
    }
    private static String escape(String value) { return HtmlUtils.htmlEscape(safe(value)); }
    private static String safe(String value) { return value == null ? "" : value; }
    private static String truncate(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
    }
    private record PageState(BufferedImage image, int y) { }
    @FunctionalInterface
    private interface PageConsumer { void accept(BufferedImage image) throws Exception; }
}
