package com.unique.unexamine.sys.module.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintField;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证固定套打位置和长明细可以生成多页 PDF。 */
class ModulePrintPdfServiceTest {
    @Test void producesMultipagePdfWithFixedField() throws Exception {
        Path font=Path.of("C:/Windows/Fonts/simhei.ttf");
        Assumptions.assumeTrue(Files.isRegularFile(font));
        var prints=mock(ModulePrintService.class);
        var details=IntStream.range(0,90).mapToObj(index->Map.of("序号",index,"名称","明细"+index)).toList();
        var view=new PrintView(9L,"contract","合同模板",1,15L,"业务记录",
                Map.of("size","A4","orientation","PORTRAIT","marginMm",15,"header","合同预览"),
                List.of(new PrintField("name","标题","测试合同"),new PrintField("details","明细",details)),
                Map.of("fieldKeys",List.of("name","details"),"positions",
                        Map.of("name",Map.of("xMm",20,"yMm",45,"widthMm",100,"heightMm",12))));
        when(prints.print(any(),eq(1L),eq(2L),eq(3L),any(PrintRequest.class))).thenReturn(view);
        var systems=mock(com.unique.unexamine.sys.manage.service.SystemManageService.class);
        when(systems.context(any(),eq(1L),eq(2L))).thenReturn(
                new com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext(1L,2L,3L,4L,5L,true,true));
        var service=new ModulePrintPdfService(prints,new ObjectMapper(),systems,
                mock(com.unique.unexamine.sys.base.service.IUnSysLogOptService.class),font.toString());

        byte[] bytes=service.pdf("token",1L,2L,3L,new PrintRequest(15L,"contract"));

        assertTrue(bytes.length>1000);
        assertEquals("%PDF",new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII));
        try (var document=Loader.loadPDF(bytes)) {
            assertTrue(document.getNumberOfPages()>1);
            String content=new org.apache.pdfbox.text.PDFTextStripper().getText(document);
            assertTrue(content.contains("测试合同"));
            assertTrue(content.contains("明细89"));
        }
    }
}
