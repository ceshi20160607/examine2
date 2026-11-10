package com.unique.unexamine.sys.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleExportDto.ExportFile;
import com.unique.unexamine.sys.module.manage.dto.ModuleExportDto.ExportRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.ListView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.Column;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldDto;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 模块数据导出；沿用当前保存列表场景，以导出动作和字段许可逐页查询。 */
@Service
public class ModuleExportService {
    /** 后台任务保存实际导出列，下载时按当前许可重新核验。 */
    public record ExportResult(long recordCount,List<Column> columns) {}
    private static final int PAGE_SIZE=500;
    private static final int MAX_EXPORT_ROWS=50000;
    private final ModuleRecordListService lists;
    private final ObjectMapper json;
    private final BusinessEventPublisher eventPublisher;
    private final SystemManageService systems;
    private final IUnSysLogOptService logs;

    /** 使用现有列表查询及字段展示规则，并发布导出完成事件。 */
    public ModuleExportService(ModuleRecordListService lists, ObjectMapper json,
                               BusinessEventPublisher eventPublisher,SystemManageService systems,
                               IUnSysLogOptService logs) {
        this.lists=lists; this.json=json; this.eventPublisher=eventPublisher;
        this.systems=systems; this.logs=logs;
    }

    /** 同一数据库快照内逐页导出，避免分页间业务变动导致重复或遗漏记录。 */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExportFile export(String token,Long systemId,Long tenantId,Long moduleId,ExportRequest request) {
        var output=new ByteArrayOutputStream();
        write(token,systemId,tenantId,moduleId,request,output,MAX_EXPORT_ROWS,true);
        return new ExportFile("module-"+moduleId+"-export.csv",output.toByteArray());
    }

    /** 后台任务直接流式写文件，避免大批量导出占用响应内存。 */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ExportResult exportTo(String token,Long systemId,Long tenantId,Long moduleId,ExportRequest request,OutputStream output) {
        return write(token,systemId,tenantId,moduleId,request,output,1_000_000,false);
    }

    private ExportResult write(String token,Long systemId,Long tenantId,Long moduleId,ExportRequest request,
                       OutputStream output,int maxRows,boolean publishCompletion) {
        if (request==null || request.query()==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"导出查询不能为空");
        var input=request.query();
        var query=new ModuleRecordSearchBO();
        query.setSceneId(input.getSceneId()); query.setKeyword(input.getKeyword());
        query.setFilters(input.getFilters()); query.setSortFieldId(input.getSortFieldId());
        query.setDescending(input.getDescending()); query.setColumnFieldIds(input.getColumnFieldIds());
        query.setScope(input.getScope()); query.setPage(1); query.setLimit(PAGE_SIZE);
        var result=lists.export(token,systemId,tenantId,moduleId,query);
        if (result.page().getTotal()>maxRows)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                    publishCompletion?"当前筛选超过50000条，请使用后台导出":"当前筛选超过后台导出上限，请缩小范围");
        try (var csv=new OutputStreamWriter(output,StandardCharsets.UTF_8)) {
            csv.append('\uFEFF');
            for (int index=0;index<result.columns().size();index++) {
                if (index>0) csv.append(',');
                csv.append(cell(result.columns().get(index).name(),true));
            }
            csv.append("\r\n");
            appendRows(csv, result, request.displayValues());
            long pageCount=(result.page().getTotal()+PAGE_SIZE-1)/PAGE_SIZE;
            for (int page=2;page<=pageCount;page++) {
                query.setPage(page);
                var next=lists.export(token,systemId,tenantId,moduleId,query);
                if (!result.columns().equals(next.columns()) || next.page().getTotal()!=result.page().getTotal()
                        || next.page().getRecords().isEmpty())
                    throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"导出查询结果在分页过程中发生变化");
                appendRows(csv,next,request.displayValues());
            }
        } catch (IOException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"导出文件写入失败",ex);
        }
        if (publishCompletion) {
            var context=systems.context(token,systemId,tenantId);
            var log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(context.tenantId());
            log.setAccountId(context.accountId()); log.setActionCode("MODULE_EXPORT");
            log.setObjectType("MODULE"); log.setObjectId(moduleId);
            log.setDetailJson("{\"recordCount\":"+result.page().getTotal()+",\"columnCount\":"+result.columns().size()+"}");
            log.setCreateTime(java.time.LocalDateTime.now()); logs.addOrUpdate(log);
            eventPublisher.publish(new BusinessEventContext("EXPORT_COMPLETED", systemId, context.tenantId(), moduleId,
                    null, context.accountId(), null, Map.of(), Map.of("moduleId", moduleId,
                            "recordCount", result.page().getTotal(), "columnCount", result.columns().size())));
        }
        return new ExportResult(result.page().getTotal(),List.copyOf(result.columns()));
    }

    private void appendRows(OutputStreamWriter csv, ListView result, boolean displayValues) throws IOException {
        for (var row:result.page().getRecords()) {
            for (int index=0;index<result.columns().size();index++) {
                if (index>0) csv.append(',');
                String code=result.columns().get(index).code();
                Object value=displayValues && row.valueDisplay().containsKey(code)
                        ?row.valueDisplay().get(code):row.values().get(code);
                csv.append(cell(format(value),!(value instanceof Number)));
            }
            csv.append("\r\n");
        }
    }

    /** 字典及人员展示值转成人可读文本，结构化原值保留 JSON 形态。 */
    private String format(Object value) {
        if (value==null) return "";
        if (value instanceof ModuleFieldDto.MemberView member) return member.displayName();
        if (value instanceof Collection<?> members && members.stream().allMatch(item->item instanceof ModuleFieldDto.MemberView))
            return members.stream().map(item->((ModuleFieldDto.MemberView)item).displayName())
                    .collect(Collectors.joining("、"));
        if (value instanceof Map<?,?> map) {
            if (map.containsKey("displayName")) return String.valueOf(map.get("displayName"));
            if (map.containsKey("name")) return String.valueOf(map.get("name"));
            if (!map.isEmpty() && map.values().stream().allMatch(item->item instanceof String))
                return map.values().stream().map(String::valueOf).collect(Collectors.joining("、"));
        }
        if (value instanceof Map<?,?> || value instanceof Collection<?>) {
            try { return json.writeValueAsString(value); }
            catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"导出值无法编码",ex); }
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
