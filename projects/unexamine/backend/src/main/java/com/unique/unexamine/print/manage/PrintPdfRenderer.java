package com.unique.unexamine.print.manage;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Deterministic, dependency-free PDF writer using the standard Adobe-GB1 CJK font mapping. */
@Component
public class PrintPdfRenderer {
    public byte[] render(String documentTitle, String pageSize, String orientation,
                         List<PrintModels.PreviewPage> pages) {
        int width = "A5".equals(pageSize) ? 420 : 595;
        int height = "A5".equals(pageSize) ? 595 : 842;
        if ("LANDSCAPE".equals(orientation)) {
            int swap = width;
            width = height;
            height = swap;
        }

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
            byte[] stream = content(documentTitle, pages.get(index), width, height)
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

    private String content(String documentTitle, PrintModels.PreviewPage page, int width, int height) {
        StringBuilder content = new StringBuilder();
        text(content, 18, 42, height - 46, documentTitle);
        text(content, 11, 42, height - 70, safe(page.header()));
        text(content, 10, 42, height - 92,
                "单号：" + safe(page.recordNumber()) + "    标题：" + safe(page.recordTitle()));
        int y = height - 124;
        for (PrintModels.PageField field : page.fields()) {
            String prefix = field.detail() ? "明细 · " : "";
            text(content, 10, 52, y, prefix + field.name() + "（" + field.code() + "）：" + safe(field.value()));
            y -= 22;
        }
        if (page.signatureLabel() != null && !page.signatureLabel().isBlank()) {
            text(content, 10, Math.max(42, width - 220), 72, page.signatureLabel() + "：________________");
        }
        text(content, 9, 42, 36, safe(page.footer()));
        text(content, 9, Math.max(42, width - 110), 36,
                "第 " + page.pageNumber() + " / " + page.pageCount() + " 页");
        return content.toString();
    }

    private void text(StringBuilder content, int size, int x, int y, String value) {
        if (value == null || value.isBlank()) return;
        content.append("BT /F1 ").append(size).append(" Tf ")
                .append(x).append(' ').append(y).append(" Td <")
                .append(hex(limit(value, 120))).append("> Tj ET\n");
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private String limit(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum - 1) + "…";
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
