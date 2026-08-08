package com.unique.examine.module.runtime.notification;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.ResultNotificationFacade;
import com.unique.examine.module.runtime.exporting.ExportRepository;
import com.unique.examine.module.report.exporting.ReportExportStore;
import com.unique.examine.module.runtime.importing.ImportRepository;
import com.unique.examine.module.runtime.printing.PrintRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class JobResultNotifier {
    private final ResultNotificationFacade notifications;

    public JobResultNotifier(ResultNotificationFacade notifications) {
        this.notifications = notifications;
    }

    public void importSucceeded(ImportRepository.BatchRecord batch) {
        schedule(() -> command(batch.systemId(), batch.tenantId(), batch.memberId(),
                "MODULE_IMPORT_SUCCEEDED", Map.of(
                        "moduleCode", batch.moduleCode(),
                        "newRows", Integer.toString(batch.newRows()),
                        "updateRows", Integer.toString(batch.updateRows())),
                new AggregateRef("MODULE_IMPORT_BATCH", Long.toString(batch.id())),
                path(batch.systemId(), batch.moduleCode(), "import", batch.id(), null),
                "job-result:import:" + batch.id() + ":SUCCEEDED"));
    }

    public void importFailed(ImportRepository.BatchRecord batch, String errorMessage) {
        schedule(() -> command(batch.systemId(), batch.tenantId(), batch.memberId(),
                "MODULE_IMPORT_FAILED", Map.of("moduleCode", batch.moduleCode(),
                        "errorMessage", safe(errorMessage)),
                new AggregateRef("MODULE_IMPORT_BATCH", Long.toString(batch.id())),
                path(batch.systemId(), batch.moduleCode(), "import", batch.id(), null),
                "job-result:import:" + batch.id() + ":FAILED"));
    }

    public void exportSucceeded(ExportRepository.TaskRecord task, int rows) {
        schedule(() -> command(task.systemId(), task.tenantId(), task.memberId(),
                "MODULE_EXPORT_SUCCEEDED", Map.of("moduleCode", task.moduleCode(), "rows", Integer.toString(rows)),
                new AggregateRef("MODULE_EXPORT_TASK", Long.toString(task.id())),
                path(task.systemId(), task.moduleCode(), "export", task.id(), null),
                "job-result:export:" + task.id() + ":SUCCEEDED"));
    }

    public void exportFailed(ExportRepository.TaskRecord task, String errorMessage) {
        schedule(() -> command(task.systemId(), task.tenantId(), task.memberId(),
                "MODULE_EXPORT_FAILED", Map.of("moduleCode", task.moduleCode(),
                        "errorMessage", safe(errorMessage)),
                new AggregateRef("MODULE_EXPORT_TASK", Long.toString(task.id())),
                path(task.systemId(), task.moduleCode(), "export", task.id(), null),
                "job-result:export:" + task.id() + ":FAILED"));
    }

    public void reportExportSucceeded(ReportExportStore.Run run) {
        schedule(() -> command(run.systemId(), run.tenantId(),
                run.requestedByMemberId(), "MODULE_EXPORT_SUCCEEDED",
                Map.of("moduleCode", run.reportCode(), "rows",
                        Integer.toString(run.processedRows())),
                new AggregateRef("REPORT_EXPORT_RUN", Long.toString(run.id())),
                reportPath(run.systemId(), run.reportCode(), run.id()),
                "job-result:report-export:" + run.id() + ":SUCCEEDED"));
    }

    public void reportExportFailed(
            ReportExportStore.Run run,
            String errorMessage
    ) {
        schedule(() -> command(run.systemId(), run.tenantId(),
                run.requestedByMemberId(), "MODULE_EXPORT_FAILED",
                Map.of("moduleCode", run.reportCode(), "errorMessage",
                        safe(errorMessage)),
                new AggregateRef("REPORT_EXPORT_RUN", Long.toString(run.id())),
                reportPath(run.systemId(), run.reportCode(), run.id()),
                "job-result:report-export:" + run.id() + ":FAILED"));
    }

    public void printSucceeded(PrintRepository.TaskRecord task) {
        schedule(() -> command(task.systemId(), task.tenantId(), task.memberId(),
                "MODULE_PRINT_SUCCEEDED", Map.of("recordNo", task.recordNo(),
                        "templateName", task.templateName()),
                new AggregateRef("MODULE_PRINT_TASK", Long.toString(task.id())),
                path(task.systemId(), task.moduleCode(), "print", task.id(), task.recordId()),
                "job-result:print:" + task.id() + ":SUCCEEDED"));
    }

    public void printFailed(PrintRepository.TaskRecord task, String errorMessage) {
        schedule(() -> command(task.systemId(), task.tenantId(), task.memberId(),
                "MODULE_PRINT_FAILED", Map.of("recordNo", task.recordNo(),
                        "templateName", task.templateName(), "errorMessage", safe(errorMessage)),
                new AggregateRef("MODULE_PRINT_TASK", Long.toString(task.id())),
                path(task.systemId(), task.moduleCode(), "print", task.id(), task.recordId()),
                "job-result:print:" + task.id() + ":FAILED"));
    }

    private ResultNotificationFacade.Command command(long systemId, long tenantId, long memberId,
                                                      String templateCode, Map<String, String> variables,
                                                      AggregateRef target, String targetPath, String dedupeKey) {
        return new ResultNotificationFacade.Command(systemId, tenantId, memberId, memberId, templateCode,
                variables, target, targetPath, dedupeKey);
    }

    private void schedule(Supplier<ResultNotificationFacade.Command> command) {
        try {
            var value = command.get();
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatchSafely(value);
                    }
                });
            } else {
                dispatchSafely(value);
            }
        } catch (RuntimeException ignored) {
            // Notification construction and delivery never change the completed business result.
        }
    }

    private void dispatchSafely(ResultNotificationFacade.Command command) {
        try {
            notifications.dispatch(command);
        } catch (RuntimeException ignored) {
            // Durable job completion remains authoritative if the event owner is temporarily unavailable.
        }
    }

    private static String path(long systemId, String moduleCode, String panel, long taskId, Long recordId) {
        var result = "/systems/" + systemId + "/workbench?module=" + encode(moduleCode)
                + "&panel=" + panel + "&task=" + taskId;
        if (recordId != null) result += "&record=" + recordId + "&mode=detail";
        return result;
    }

    private static String reportPath(
            long systemId,
            String reportCode,
            long exportId
    ) {
        return "/systems/" + systemId + "/reports?report="
                + encode(reportCode) + "&export=" + exportId;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "任务执行失败，请打开结果查看详情";
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
