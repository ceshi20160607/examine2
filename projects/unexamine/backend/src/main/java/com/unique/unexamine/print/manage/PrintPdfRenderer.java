package com.unique.unexamine.print.manage;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 使用标准 Adobe-GB1 中文字体映射生成可下载的业务打印 PDF。 */
@Component
public class PrintPdfRenderer {
    public byte[] render(String documentTitle, String pageSize, String orientation,
                         Integer offsetXmm, Integer offsetYmm, List<PrintModels.PreviewPage> pages) {
        int width = pageWidth(pageSize, orientation);
        int height = pageHeight(pageSize, orientation);

        List<String> objects = new ArrayList<>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int index = 0; index < pages.size(); index++) kids.append(4 + index * 2).append(" 0 R ");
        objects.add("<< /Type /Pages /Count " + pages.size() + " /Kids [" + kids + "] >>");
        objects.add("<< /Type /Font /Subtype /Type0 /BaseFont /STSong-Light /Encoding /UniGB-UCS2-H "
                + "/DescendantFonts [<< /Type /Font /Subtype /CIDFontType0 /BaseFont /STSong-Light "
                + "/CIDSystemInfo << /Registry (Adobe) /Ordering (GB1) /Supplement 4 >> >>] >>");

        for (int index = 0; index < pages.size(); index++) {
            int contentObject = 5 + index * 2;
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + width + " " + height + "] "
                    + "/Resources << /Font << /F1 3 0 R >> >> /Contents " + contentObject + " 0 R >>");
            byte[] stream = content(documentTitle, pages.get(index), width, height,
                            millimetres(offsetXmm), millimetres(offsetYmm))
                    .getBytes(StandardCharsets.US_ASCII);
            objects.add("<< /Length " + stream.length + " >>\nstream\n"
                    + new String(stream, StandardCharsets.US_ASCII) + "\nendstream");
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        write(output, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            write(output, (index + 1) + " 0 obj\n" + objects.get(index) + "\nendobj\n");
        }
        int xref = output.size();
        write(output, "xref\n0 " + (objects.size() + 1) + "\n0000000000 65535 f \n");
        for (int index = 1; index < offsets.size(); index++) {
            write(output, String.format("%010d 00000 n \n", offsets.get(index)));
        }
        write(output, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n"
                + "startxref\n" + xref + "\n%%EOF\n");
        return output.toByteArray();
    }

    /** 套打偏移以毫米配置；正数向右、向下，预览和 PDF 使用同一方向。 */
    private String content(String documentTitle, PrintModels.PreviewPage page, int width, int height,
                           int offsetX, int offsetY) {
        StringBuilder content = new StringBuilder();
        List<String> titleLines = wrap(documentTitle, textUnits(width - 84, 18));
        List<String> headerLines = wrap(safe(page.header()), textUnits(width - 84, 11));
        List<String> recordLines = wrap(recordHeading(page.recordNumber(), page.recordTitle()),
                textUnits(width - 84, 10));
        int titleExtra = (titleLines.size() - 1) * 22;
        int headerExtra = (headerLines.size() - 1) * 14;
        int recordExtra = (recordLines.size() - 1) * 14;
        for (int index = 0; index < titleLines.size(); index++) {
            text(content, 18, 42 + offsetX, height - 46 - offsetY - index * 22, titleLines.get(index));
        }
        for (int index = 0; index < headerLines.size(); index++) {
            text(content, 11, 42 + offsetX, height - 70 - offsetY - titleExtra - index * 14,
                    headerLines.get(index));
        }
        for (int index = 0; index < recordLines.size(); index++) {
            text(content, 10, 42 + offsetX, height - 92 - offsetY - titleExtra - headerExtra - index * 14,
                    recordLines.get(index));
        }
        int y = height - 124 - offsetY - titleExtra - headerExtra - recordExtra;
        for (PrintModels.PageField field : page.fields()) {
            for (String line : wrap(fieldText(field), fieldUnits(width))) {
                text(content, 10, 52 + offsetX, y, line);
                y -= 14;
            }
            y -= 8;
        }
        if (page.detailTables() != null) {
            for (PrintModels.DetailTable table : page.detailTables()) {
                text(content, 11, 52 + offsetX, y, table.name());
                y -= 24;
                int count = table.columns().size();
                if (count == 0) continue;
                int columnWidth = Math.max(1, (width - 104) / count);
                int tableHeaderLines = table.columns().stream()
                        .mapToInt(column -> wrap(column.name(), columnUnits(columnWidth)).size())
                        .max().orElse(1);
                for (int index = 0; index < count; index++) {
                    List<String> lines = wrap(table.columns().get(index).name(), columnUnits(columnWidth));
                    for (int line = 0; line < lines.size(); line++) {
                        text(content, 9, 52 + offsetX + index * columnWidth, y - line * 12,
                                lines.get(line));
                    }
                }
                y -= tableHeaderLines * 12 + 6;
                content.append("0.7 w ").append(52 + offsetX).append(' ').append(y + 9)
                        .append(" m ").append(width - 52 + offsetX).append(' ').append(y + 9).append(" l S\n");
                for (PrintModels.DetailRow row : table.rows()) {
                    List<List<String>> cells = new ArrayList<>();
                    for (int index = 0; index < Math.min(count, row.values().size()); index++) {
                        List<String> lines = wrap(safe(row.values().get(index)), columnUnits(columnWidth));
                        cells.add(lines);
                        for (int line = 0; line < lines.size(); line++) {
                            text(content, 9, 52 + offsetX + index * columnWidth, y - line * 12,
                                    lines.get(line));
                        }
                    }
                    y -= cells.stream().mapToInt(List::size).max().orElse(1) * 12 + 10;
                }
                y -= 8;
            }
        }
        List<String> footerLines = wrap(safe(page.footer()), textUnits(width - 160, 9));
        int footerExtra = (footerLines.size() - 1) * 12;
        if (page.signatureLabel() != null && !page.signatureLabel().isBlank()) {
            List<String> signatureLines = wrap(page.signatureLabel() + "：________________",
                    signatureUnits(offsetX));
            for (int index = 0; index < signatureLines.size(); index++) {
                text(content, 10, Math.max(42, width - 220 + offsetX),
                        72 - offsetY + footerExtra + index * 12, signatureLines.get(index));
            }
        }
        for (int index = 0; index < footerLines.size(); index++) {
            text(content, 9, 42 + offsetX, 36 - offsetY + index * 12, footerLines.get(index));
        }
        text(content, 9, Math.max(42, width - 110 + offsetX), 36 - offsetY,
                "第 " + page.pageNumber() + " / " + page.pageCount() + " 页");
        return content.toString();
    }

    private int millimetres(Integer value) {
        return value == null ? 0 : (int) Math.round(value * 72d / 25.4d);
    }

    private void text(StringBuilder content, int size, int x, int y, String value) {
        if (value == null || value.isBlank()) return;
        content.append("BT /F1 ").append(size).append(" Tf ")
                .append(x).append(' ').append(y).append(" Td <")
                .append(hex(value)).append("> Tj ET\n");
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /** 分页和 PDF 统一按实际纸张与字号估算每行宽度，保留全部字段内容。 */
    int pageWidth(String size, String orientation) {
        return "LANDSCAPE".equals(orientation) ? ("A5".equals(size) ? 595 : 842)
                : ("A5".equals(size) ? 420 : 595);
    }

    int pageHeight(String size, String orientation) {
        return "LANDSCAPE".equals(orientation) ? ("A5".equals(size) ? 420 : 595)
                : ("A5".equals(size) ? 595 : 842);
    }

    int fieldUnits(int width) {
        return Math.max(10, (width - 104) / 5);
    }

    int columnUnits(int columnWidth) {
        return Math.max(4, columnWidth * 2 / 9 - 2);
    }

    /** 固定页眉、页脚和签名的实际行数决定正文可用高度。 */
    int bodyCapacity(String documentTitle, String pageSize, String orientation, String header,
                     String recordNumber, String recordTitle, String footer, String signature, int offsetXmm) {
        int width = pageWidth(pageSize, orientation);
        int topExtra = (wrap(documentTitle, textUnits(width - 84, 18)).size() - 1) * 22
                + (wrap(safe(header), textUnits(width - 84, 11)).size() - 1) * 14
                + (wrap(recordHeading(recordNumber, recordTitle), textUnits(width - 84, 10)).size() - 1) * 14;
        int footerExtra = (wrap(safe(footer), textUnits(width - 160, 9)).size() - 1) * 12;
        int signatureTop = signature == null || signature.isBlank() ? 0
                : 72 + footerExtra + (wrap(signature + "：________________",
                signatureUnits(millimetres(offsetXmm))).size() - 1) * 12;
        int bottom = Math.max(110, Math.max(36 + footerExtra + 28, signatureTop + 28));
        return pageHeight(pageSize, orientation) - 124 - topExtra - bottom;
    }

    private int textUnits(int width, int fontSize) {
        return Math.max(4, width * 2 / fontSize);
    }

    private int signatureUnits(int offsetX) {
        return textUnits(220 - Math.max(0, offsetX), 10);
    }

    private String recordHeading(String number, String title) {
        return "单号：" + safe(number) + "    标题：" + safe(title);
    }

    String fieldText(PrintModels.PageField field) {
        return (field.detail() ? "明细 · " : "") + field.name() + "（" + field.code() + "）：" + safe(field.value());
    }

    /** 中文按双宽、英文按单宽换行；明确的换行也保留。 */
    List<String> wrap(String value, int units) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int used = 0;
        for (int offset = 0; offset < value.length();) {
            int point = value.codePointAt(offset);
            offset += Character.charCount(point);
            if (point == '\n') {
                lines.add(line.toString());
                line.setLength(0);
                used = 0;
                continue;
            }
            int width = point < 128 ? 1 : 2;
            if (used + width > units && !line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
                used = 0;
            }
            line.appendCodePoint(point);
            used += width;
        }
        lines.add(line.toString());
        return lines;
    }

    private String hex(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_16BE);
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) result.append(String.format("%02X", item & 0xff));
        return result.toString();
    }

    private void write(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }
}
