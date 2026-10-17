package com.unique.unexamine.exchange.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.exchange.base.entity.ExchangeJob;
import com.unique.unexamine.exchange.base.service.ExchangeJobBaseService;
import com.unique.unexamine.moduleconfig.manage.ModuleManageService;
import com.unique.unexamine.runtimedata.manage.RecordManageService;
import com.unique.unexamine.system.manage.SystemPermissionService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 模块筛选数据异步导出；每一页调用同一记录查询服务重新计算当前请求人的权限范围。 */
@Service
public class ExportManageService {
    private static final int PAGE_SIZE = 50;
    private static final int MAX_ROWS = 10_000;
    private final SystemPermissionService access;
    private final ModuleManageService modules;
    private final RecordManageService records;
    private final ExchangeJobBaseService jobs;
    private final ObjectMapper json;
    private final Path root;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(16), job -> {
        Thread thread = new Thread(job, "unexamine-export");
        thread.setDaemon(true);
        return thread;
    });

    public ExportManageService(SystemPermissionService access, ModuleManageService modules,
                               RecordManageService records, ExchangeJobBaseService jobs, ObjectMapper json,
                               @Value("${APP_EXPORT_ROOT:../data/exports}") String rootPath) {
        this.access = access;
        this.modules = modules;
        this.records = records;
        this.jobs = jobs;
        this.json = json;
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    /** 提交时固化筛选条件和列名；任务只在已发布模块范围内创建。 */
    public JobView submit(Long accountId, Long systemId, Long moduleId, ExportRequest request) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "EXPORT");
        if (request == null) throw bad("缺少导出条件");
        ModuleManageService.PublishedModule published = modules.runtime(accountId, systemId, moduleId);
        if (!published.canExport()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导出动作未发布或无权限");
        List<ModuleManageService.FieldSnapshot> available = published.snapshot().fields();
        List<String> keys = request.columnKeys() == null || request.columnKeys().isEmpty()
                ? available.stream().map(ModuleManageService.FieldSnapshot::key).toList() : request.columnKeys();
        if (keys.isEmpty() || keys.size() > 100 || keys.contains(null)
                || keys.size() != Set.copyOf(keys).size()) throw bad("导出字段选择无效");
        List<ColumnSnapshot> columns = new ArrayList<>();
        for (String key : keys) {
            ModuleManageService.FieldSnapshot field = available.stream().filter(item -> item.key().equals(key))
                    .findFirst().orElse(null);
            if (field == null) throw bad("导出字段未发布");
            columns.add(new ColumnSnapshot(key, field.label()));
        }
        String filterKey = request.filterKey() == null ? "" : request.filterKey().trim();
        String filterValue = request.filterValue() == null ? "" : request.filterValue().trim();
        String search = request.search() == null ? "" : request.search().trim();
        if (search.length() > 255 || filterValue.length() > 255) throw bad("筛选条件过长");
        if (!filterKey.isEmpty() && available.stream().noneMatch(item -> item.key().equals(filterKey) && item.searchable())) {
            throw bad("筛选字段不可用");
        }
        if (filterKey.isEmpty() != filterValue.isEmpty()) throw bad("筛选字段和值必须同时填写");
        ExchangeJob job = new ExchangeJob();
        job.setSystemId(systemId);
        job.setTenantId(context.tenant().getId());
        job.setModuleId(moduleId);
        job.setRequestedByAccountId(accountId);
        job.setFilterJson(write(new FilterSnapshot(search, filterKey, filterValue)));
        job.setColumnsJson(write(columns));
        job.setStatus("QUEUED");
        job.setRowCount(0);
        jobs.insert(job);
        enqueue(job.getId());
        return view(jobs.selectById(job.getId()));
    }

    /** 当前请求人只可查看自己提交的任务，且必须仍有模块查看权限。 */
    public List<JobView> list(Long accountId, Long systemId, Long moduleId) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "EXPORT");
        return jobs.selectList(new LambdaQueryWrapper<ExchangeJob>()
                .eq(ExchangeJob::getSystemId, systemId).eq(ExchangeJob::getTenantId, context.tenant().getId())
                .eq(ExchangeJob::getModuleId, moduleId).eq(ExchangeJob::getRequestedByAccountId, accountId)
                .orderByDesc(ExchangeJob::getId).last("LIMIT 20"))
                .stream().map(this::view).toList();
    }

    /** 下载前再次核对任务提交人、系统/租户/模块及模块菜单查看权限。 */
    public ExportFile download(Long accountId, Long systemId, Long moduleId, Long jobId) {
        ExchangeJob job = requireJob(accountId, systemId, moduleId, jobId);
        if (!"SUCCEEDED".equals(job.getStatus()) || job.getResultKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "导出任务尚未完成");
        }
        Path path = root.resolve(job.getResultKey());
        if (!Files.isRegularFile(path)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导出文件已不可用");
        return new ExportFile(path, "module-" + moduleId + "-export-" + jobId + ".csv");
    }

    /** 服务重启后重新提交未完成任务；处理时仍重新验证权限。 */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeUnfinished() {
        for (ExchangeJob job : jobs.selectList(new LambdaQueryWrapper<ExchangeJob>()
                .in(ExchangeJob::getStatus, "QUEUED", "RUNNING"))) enqueue(job.getId());
    }

    @PreDestroy
    public void close() { executor.shutdown(); }

    private void enqueue(Long jobId) {
        try { executor.execute(() -> process(jobId)); }
        catch (RuntimeException exception) {
            ExchangeJob job = jobs.selectById(jobId);
            job.setStatus("FAILED");
            job.setFailureReason("导出队列已满，请稍后重试");
            job.setFinishTime(LocalDateTime.now());
            jobs.updateById(job);
        }
    }

    private void process(Long jobId) {
        ExchangeJob job = jobs.selectById(jobId);
        if (job == null || !Set.of("QUEUED", "RUNNING").contains(job.getStatus())) return;
        String key = UUID.randomUUID().toString().replace("-", "") + ".csv";
        Path temporary = root.resolve(key + ".tmp");
        Path output = root.resolve(key);
        try {
            job.setStatus("RUNNING");
            job.setStartTime(LocalDateTime.now());
            jobs.updateById(job);
            FilterSnapshot filter = json.readValue(job.getFilterJson(), FilterSnapshot.class);
            List<ColumnSnapshot> columns = json.readValue(job.getColumnsJson(), new TypeReference<>() {});
            Files.createDirectories(root);
            int count = 0;
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write('\ufeff');
                writeRow(writer, new String[]{"记录编号", "标题", "审批状态"}, columns.stream().map(ColumnSnapshot::label).toList());
                for (int page = 1; ; page++) {
                    access.requireModule(job.getRequestedByAccountId(), job.getSystemId(), job.getModuleId(), "EXPORT");
                    RecordManageService.PageView result = records.list(job.getRequestedByAccountId(), job.getSystemId(),
                            job.getModuleId(), page, PAGE_SIZE, filter.search(), filter.filterKey(), filter.filterValue());
                    if (count + result.items().size() > MAX_ROWS) throw bad("导出超过 10000 行，请缩小筛选范围");
                    for (RecordManageService.RecordView record : result.items()) {
                        List<String> values = columns.stream().map(column -> cell(record.values().get(column.key()))).toList();
                        writeRow(writer, new String[]{record.recordNo(), record.title(), record.recordStatus()}, values);
                        count++;
                    }
                    if (count >= result.total() || result.items().isEmpty()) break;
                }
            }
            Files.move(temporary, output);
            job.setResultKey(key);
            job.setRowCount(count);
            job.setStatus("SUCCEEDED");
            job.setFailureReason(null);
        } catch (Exception exception) {
            removeQuietly(temporary);
            removeQuietly(output);
            job.setResultKey(null);
            job.setStatus("FAILED");
            job.setFailureReason(exception instanceof ResponseStatusException response && response.getReason() != null
                    ? response.getReason() : "导出失败，请检查权限和筛选条件后重试");
        } finally {
            job.setFinishTime(LocalDateTime.now());
            jobs.updateById(job);
        }
    }

    private void writeRow(BufferedWriter writer, String[] prefix, List<String> values) throws IOException {
        List<String> all = new ArrayList<>(List.of(prefix));
        all.addAll(values);
        for (int index = 0; index < all.size(); index++) {
            if (index > 0) writer.write(',');
            String value = all.get(index) == null ? "" : all.get(index);
            String leading = value.stripLeading();
            if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0) value = "'" + value;
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
        writer.write("\r\n");
    }

    private String cell(JsonNode value) {
        if (value == null || value.isNull()) return "";
        return value.isTextual() ? value.asText() : value.toString();
    }

    private ExchangeJob requireJob(Long accountId, Long systemId, Long moduleId, Long jobId) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "EXPORT");
        ExchangeJob job = jobs.selectById(jobId);
        if (job == null || !systemId.equals(job.getSystemId()) || !context.tenant().getId().equals(job.getTenantId())
                || !moduleId.equals(job.getModuleId()) || !accountId.equals(job.getRequestedByAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导出任务不存在");
        }
        return job;
    }

    private JobView view(ExchangeJob job) {
        return new JobView(job.getId(), job.getStatus(), job.getRowCount(), job.getFailureReason(),
                job.getCreateTime(), job.getFinishTime());
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("导出配置无法保存", exception); }
    }

    private void removeQuietly(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException ignored) { /* 失败任务不向用户暴露未完成结果。 */ }
    }

    private ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }

    /** 页面提交的筛选与发布字段编码。 */
    public record ExportRequest(String search, String filterKey, String filterValue, List<String> columnKeys) {}
    /** 任务提交时冻结字段显示名，避免草稿修改后列标题漂移。 */
    public record ColumnSnapshot(String key, String label) {}
    /** 任务提交时冻结当前搜索和字段筛选。 */
    public record FilterSnapshot(String search, String filterKey, String filterValue) {}
    /** 页面只读取任务状态与失败原因，不接触本地路径。 */
    public record JobView(Long id, String status, Integer rowCount, String failureReason,
                          LocalDateTime createTime, LocalDateTime finishTime) {}
    /** 下载文件的已授权本地路径和用户文件名。 */
    public record ExportFile(Path path, String name) {}
}
