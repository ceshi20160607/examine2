package com.unique.unexamine.dataexchange.manage;

import com.unique.unexamine.backgroundjobs.manage.BackgroundJobExecution;
import com.unique.unexamine.backgroundjobs.manage.BackgroundJobHandler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ModuleExportBackgroundJobHandler implements BackgroundJobHandler {
    public static final String MODULE_EXPORT = "MODULE_EXPORT";

    private final ModuleExportService service;

    public ModuleExportBackgroundJobHandler(ModuleExportService service) {
        this.service = service;
    }

    @Override
    public Set<String> jobTypes() {
        return Set.of(MODULE_EXPORT);
    }

    @Override
    public String name(String jobType) {
        return "模块数据导出";
    }

    @Override
    public String description(String jobType) {
        return "按提交时的筛选、字段和权限快照异步生成受控 CSV 文件。";
    }

    @Override
    public long estimateTotal(String jobType, Map<String, Object> parameters) {
        return service.estimateRows(parameters.get("batchId"));
    }

    @Override
    public Map<String, Object> execute(
            String jobType, Map<String, Object> parameters, BackgroundJobExecution execution) {
        return service.executeQueued(parameters, execution);
    }
}
