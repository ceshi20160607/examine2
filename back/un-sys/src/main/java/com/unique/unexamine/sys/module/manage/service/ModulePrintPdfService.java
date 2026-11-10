package com.unique.unexamine.sys.module.manage.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintField;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 按同一打印动作的授权结果生成 PDF；固定位置用于套打，明细内容自动换页。 */
@Service
public class ModulePrintPdfService {
    private static final float POINTS_PER_MM=72f/25.4f;
    private static final float FONT_SIZE=10f;
    private static final float LINE_HEIGHT=15f;
    private final ModulePrintService prints;
    private final ObjectMapper json;
    private final SystemManageService systems;
    private final IUnSysLogOptService logs;
    private final Path fontPath;

    /** 字体文件由部署配置提供，避免把系统私有字体打包到工程。 */
    public ModulePrintPdfService(ModulePrintService prints,ObjectMapper json,SystemManageService systems,
            IUnSysLogOptService logs,
            @Value("${un.print.font-path:C:/Windows/Fonts/simhei.ttf}") String fontPath) {
        this.prints=prints; this.json=json; this.systems=systems; this.logs=logs;
        this.fontPath=Path.of(fontPath).toAbsolutePath().normalize();
    }

    /** 先执行 PRINT 菜单与字段许可，再用同一视图生成可下载文件。 */
    @Transactional(rollbackFor=Exception.class)
    public byte[] pdf(String token,Long systemId,Long tenantId,Long moduleId,PrintRequest request) {
        PrintView view=prints.print(token,systemId,tenantId,moduleId,request);
        if (!Files.isRegularFile(fontPath))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"打印字体不可用，请配置 un.print.font-path");
        try (var document=new PDDocument();var output=new ByteArrayOutputStream()) {
            var font=PDType0Font.load(document,fontPath.toFile());
            var paper=paper(view.paperConfig());
            var canvas=new Canvas(document,font,paper.width(),paper.height(),paper.margin(),
                    String.valueOf(view.paperConfig().getOrDefault("header","")),
                    String.valueOf(view.paperConfig().getOrDefault("footer","")));
            canvas.newPage();
            JsonNode positions=json.valueToTree(view.bodyConfig()).path("positions");
            if (!positions.isObject() || positions.isEmpty())
                canvas.line(view.templateName()+"  #"+view.recordId(),FONT_SIZE+3);
            float flowTop=canvas.cursor();
            for (PrintField field:view.fields()) {
                JsonNode box=positions.path(field.fieldKey());
                if (!box.isObject()) continue;
                float x=mm(box.path("xMm").floatValue()),y=mm(box.path("yMm").floatValue());
                float width=mm(box.path("widthMm").floatValue()),height=mm(box.path("heightMm").floatValue());
                List<String> lines=wrap(field.label()+"："+plain(field.value()),font,width);
                if (lines.size()*LINE_HEIGHT>height)
                    throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"套打字段超出位置高度："+field.label());
                for (int index=0;index<lines.size();index++)
                    canvas.text(x,paper.height()-y-FONT_SIZE-index*LINE_HEIGHT,lines.get(index),FONT_SIZE);
                flowTop=Math.min(flowTop,paper.height()-y-height-LINE_HEIGHT);
            }
            canvas.cursor(flowTop);
            for (PrintField field:view.fields()) {
                if (positions.path(field.fieldKey()).isObject()) continue;
                canvas.line(field.label()+"：",FONT_SIZE);
                if (field.value() instanceof List<?> list) {
                    int index=1;
                    for (Object row:list) {
                        for (String line:wrap(index+". "+plain(row),font,paper.width()-2*paper.margin()-12))
                            canvas.line("  "+line,FONT_SIZE);
                        index++;
                    }
                } else {
                    for (String line:wrap(plain(field.value()),font,paper.width()-2*paper.margin()-12))
                        canvas.line("  "+line,FONT_SIZE);
                }
                canvas.space(6);
            }
            canvas.closePage();
            document.save(output);
            var context=systems.context(token,systemId,tenantId);
            var log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(context.tenantId());
            log.setAccountId(context.accountId()); log.setActionCode("MODULE_PRINT_PDF");
            log.setObjectType("MODULE_RECORD"); log.setObjectId(view.recordId());
            log.setDetailJson("{\"templateId\":"+view.templateId()+",\"pageCount\":"+document.getNumberOfPages()+"}");
            log.setCreateTime(java.time.LocalDateTime.now()); logs.addOrUpdate(log);
            return output.toByteArray();
        } catch (BaseException ex) { throw ex; }
        catch (IOException | IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"打印 PDF 生成失败，请检查字体与模板内容",ex);
        }
    }

    private record Paper(float width,float height,float margin) {}

    /** 纸张配置已经在发布时校验，运行态仍只接受明确的尺寸和方向。 */
    private Paper paper(Map<String,Object> value) {
        String size=String.valueOf(value.get("size"));
        float width=switch (size) { case "A4" -> 210; case "A5" -> 148; case "LETTER" -> 216; default -> throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"打印纸张无效"); };
        float height=switch (size) { case "A4" -> 297; case "A5" -> 210; default -> 279; };
        if ("LANDSCAPE".equals(value.get("orientation"))) { float swap=width; width=height; height=swap; }
        float margin=((Number)value.get("marginMm")).floatValue();
        return new Paper(mm(width),mm(height),mm(margin));
    }

    private float mm(float value) { return value*POINTS_PER_MM; }

    /** 字段展示值保持当前授权结果，结构化明细逐行使用 JSON 文本。 */
    private String plain(Object value) {
        if (value==null) return "";
        if (value instanceof String text) return text;
        if (value instanceof Map<?,?> map && map.containsKey("displayName")) return String.valueOf(map.get("displayName"));
        if (value instanceof Map<?,?> map && map.containsKey("name")) return String.valueOf(map.get("name"));
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"打印值无法编码",ex); }
    }

    /** 按实际字体宽度换行，不能把超过纸张边界的文字静默截断。 */
    private List<String> wrap(String source,PDType0Font font,float maxWidth) throws IOException {
        List<String> result=new ArrayList<>();
        for (String paragraph:source.replace("\r","").split("\n",-1)) {
            StringBuilder line=new StringBuilder();
            for (int offset=0;offset<paragraph.length();) {
                int code=paragraph.codePointAt(offset); offset+=Character.charCount(code);
                String character=new String(Character.toChars(code));
                if (!line.isEmpty() && font.getStringWidth(line.toString()+character)*FONT_SIZE/1000f>maxWidth) {
                    result.add(line.toString()); line.setLength(0);
                }
                line.append(character);
            }
            result.add(line.toString());
        }
        return result;
    }

    /** 单份 PDF 按需新建页面，页眉页脚与字段使用同一嵌入字体。 */
    private static final class Canvas {
        private final PDDocument document;
        private final PDType0Font font;
        private final float width,height,margin;
        private final String header,footer;
        private PDPageContentStream stream;
        private float cursor;
        private int pages;

        private Canvas(PDDocument document,PDType0Font font,float width,float height,float margin,String header,String footer) {
            this.document=document; this.font=font; this.width=width; this.height=height;
            this.margin=margin; this.header=header; this.footer=footer;
        }

        private void newPage() throws IOException {
            if (stream!=null) closePage();
            if (++pages>200) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"打印内容超过200页");
            var page=new PDPage(new PDRectangle(width,height)); document.addPage(page);
            stream=new PDPageContentStream(document,page); cursor=height-margin;
            if (!header.isBlank()) { text(margin,cursor,header,FONT_SIZE); cursor-=LINE_HEIGHT*2; }
        }

        private void line(String value,float size) throws IOException {
            if (cursor<margin+LINE_HEIGHT*2) newPage();
            text(margin,cursor,value,size); cursor-=LINE_HEIGHT;
        }

        private void text(float x,float y,String value,float size) throws IOException {
            if (value.isEmpty()) return;
            stream.beginText(); stream.setFont(font,size); stream.newLineAtOffset(x,y);
            stream.showText(value); stream.endText();
        }

        private void closePage() throws IOException {
            if (stream==null) return;
            String line=footer.isBlank()?"第 "+pages+" 页":footer+"  ·  第 "+pages+" 页";
            text(margin,Math.max(12,margin/2),line,8);
            stream.close(); stream=null;
        }

        private float cursor() { return cursor; }
        private void cursor(float value) { cursor=value; }
        private void space(float points) { cursor-=points; }
    }
}
