package com.unique.unexamine.module.manage;

import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.module.base.service.IUnModuleRecordService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** 读取同一租户和模块内的记录及字段值，供详情和列表使用。 */
@Component
class ModuleRecordReader {
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService data;
    private final ObjectMapper json;
    private final ModuleRecordFieldValue valueSupport;

    ModuleRecordReader(IUnModuleRecordService records, IUnModuleRecordDataService data,
                       ObjectMapper json, ModuleRecordFieldValue valueSupport) {
        this.records = records;
        this.data = data;
        this.json = json;
        this.valueSupport = valueSupport;
    }

    UnModuleRecord record(long tenantId, long moduleId, long recordId) {
        UnModuleRecord row = records.getById(recordId);
        if (row == null || !row.getTenantId().equals(tenantId) || !row.getModuleId().equals(moduleId)
                || row.getRecordStatus() == 2) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
        return row;
    }

    ModuleRecordController.RecordView view(UnModuleRecord row) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (UnModuleRecordData item : data.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId, row.getId()))) {
            JsonNode value = switch (item.getValueType()) {
                case "number" -> json.valueToTree(item.getValueNumber());
                case "time" -> json.valueToTree(item.getValueTime());
                case "json" -> valueSupport.readJson(item.getValueJson());
                default -> json.valueToTree(item.getValueText());
            };
            values.put(item.getFieldName(), value);
        }
        return new ModuleRecordController.RecordView(row, values);
    }

}
