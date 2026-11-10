package com.unique.unexamine.sys.module.manage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

/** 模块打印动作的请求与打印视图；模板版本由服务端选择并校验。 */
public final class ModulePrintDto {
    private ModulePrintDto() {}

    /** 打印时明确选择当前可用的模板编码。 */
    public record PrintRequest(@Schema(description = "业务记录主键") Long recordId,
                               @Schema(description = "打印模板编码") String templateCode) {}

    /** 前端可直接渲染或交给打印适配器的结构化打印数据。 */
    public record PrintView(@Schema(description = "模板版本主键") Long templateId,
                            @Schema(description = "模板编码") String templateCode,
                            @Schema(description = "模板名称") String templateName,
                            @Schema(description = "模板版本") Integer versionNo,
                            @Schema(description = "业务记录主键") Long recordId,
                            @Schema(description = "记录标题") String title,
                            @Schema(description = "纸张及页眉页脚配置") Map<String, Object> paperConfig,
                            @Schema(description = "按模板顺序返回的字段值") List<PrintField> fields,
                            @Schema(description = "字段位置与明细布局") Map<String,Object> bodyConfig) {}

    /** 一个模板字段对应一个当前授权后的业务值。 */
    public record PrintField(@Schema(description = "字段英文键") String fieldKey,
                             @Schema(description = "字段显示名称") String label,
                             @Schema(description = "当前授权后的字段值") Object value) {}
}
