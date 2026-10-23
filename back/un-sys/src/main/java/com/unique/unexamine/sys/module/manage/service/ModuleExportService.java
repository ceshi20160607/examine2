package com.unique.unexamine.sys.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 模块小批量数据导出；沿用当前保存列表场景，但以导出动作和字段许可重新查询。 */
@Service
public class ModuleExportService {
    private static final int SYNCHRONOUS_LIMIT=500;
    private final ModuleRecordListService lists;
    private final ObjectMapper json;

    /** 当前筛选和显式导出列与列表查询保持同一格式。 */
    public record ExportRequest(ModuleRecordListService.Query query,boolean displayValues) {}
    /** CSV 内容由 Controller 作为下载响应发送。 */
    public record ExportFile(String name,byte[] content) {}

    /** 使用现有列表查询及字段展示规则，不建立平行的数据筛选实现。 */
    public ModuleExportService(ModuleRecordListService lists,ObjectMapper json) {
        this.lists=lists; this.json=json;
    }

    /** 小批量直接下载；超限明确拒绝，避免长请求和内存累积。 */
    public ExportFile export(String token,Long systemId,Long tenantId,Long moduleId,ExportRequest request) {
        if (request==null || request.query()==null) throw new IllegalArgumentException("导出查询不能为空");
        var input=request.query();
        var query=new ModuleRecordListService.Query(input.sceneId(),input.search(),input.filters(),
                input.sortFieldId(),input.descending(),input.columnFieldIds(),1,SYNCHRONOUS_LIMIT,input.scope());
        var result=lists.export(token,systemId,tenantId,moduleId,query);
        if (result.total()>SYNCHRONOUS_LIMIT)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"当前筛选超过500条，暂不支持同步导出");
        StringBuilder csv=new StringBuilder("\uFEFF");
        for (int index=0;index<result.columns().size();index++) {
            if (index>0) csv.append(',');
            csv.append(cell(result.columns().get(index).name(),true));
        }
        csv.append("\r\n");
        for (var row:result.records()) {
            for (int index=0;index<result.columns().size();index++) {
                if (index>0) csv.append(',');
                String code=result.columns().get(index).code();
                Object value=request.displayValues() && row.valueDisplay().containsKey(code)
                        ?row.valueDisplay().get(code):row.values().get(code);
                csv.append(cell(format(value),!(value instanceof Number)));
            }
            csv.append("\r\n");
        }
        return new ExportFile("module-"+moduleId+"-export.csv",csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** 字典及人员展示值转成人可读文本，结构化原值保留 JSON 形态。 */
    private String format(Object value) {
        if (value==null) return "";
        if (value instanceof ModuleFieldService.MemberView member) return member.displayName();
        if (value instanceof Collection<?> members && members.stream().allMatch(item->item instanceof ModuleFieldService.MemberView))
            return members.stream().map(item->((ModuleFieldService.MemberView)item).displayName())
                    .collect(Collectors.joining("、"));
        if (value instanceof Map<?,?> map) {
            if (map.containsKey("displayName")) return String.valueOf(map.get("displayName"));
            if (map.containsKey("name")) return String.valueOf(map.get("name"));
            if (!map.isEmpty() && map.values().stream().allMatch(item->item instanceof String))
                return map.values().stream().map(String::valueOf).collect(Collectors.joining("、"));
        }
        if (value instanceof Map<?,?> || value instanceof Collection<?>) {
            try { return json.writeValueAsString(value); }
            catch (JsonProcessingException ex) { throw new IllegalStateException("导出值无法编码",ex); }
        }
        return value.toString();
    }

    /** CSV 特殊字符转义，避免业务文本被电子表格解释为公式。 */
    private String cell(String value,boolean guardFormula) {
        String safe=value;
        if (guardFormula && !safe.isEmpty() && "=+-@".indexOf(safe.charAt(0))>=0) safe="'"+safe;
        return '"'+safe.replace("\"","\"\"")+'"';
    }
}
