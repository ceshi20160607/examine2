package com.unique.unexamine.dataexchange.manage;

import com.unique.unexamine.backgroundjobs.manage.BackgroundJobExecution;
import com.unique.unexamine.backgroundjobs.manage.BackgroundJobHandler;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
public class ModuleImportBackgroundJobHandler implements BackgroundJobHandler {
    public static final String MODULE_IMPORT = "MODULE_IMPORT";

    private final ModuleImportService service;

    public ModuleImportBackgroundJobHandler(ModuleImportService service) {
        this.service = service;
    }

    @Override
    public Set<String> jobTypes() {
        return Set.of(MODULE_IMPORT);
    }

    @Override
    public String name(String jobType) {
        return "模块数据导入";
    }

    @Override
    public String description(String jobType) {
        return "按预演快照逐行执行模块数据导入，并保留可安全回滚的行级结果。";
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
