package com.unique.unexamine.sys.module.manage.dto;

import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;

/** ModuleExport 的请求与返回模型。 */
public final class ModuleExportDto {
    private ModuleExportDto() {}

    /** 当前筛选和显式导出列与列表查询保持同一格式。 */
    public record ExportRequest(ModuleRecordSearchBO query,boolean displayValues) {}

    /** CSV 内容由 Controller 作为下载响应发送。 */
    public record ExportFile(String name,byte[] content) {}
}
