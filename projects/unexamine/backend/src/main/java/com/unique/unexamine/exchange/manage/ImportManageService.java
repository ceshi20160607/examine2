package com.unique.unexamine.exchange.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.exchange.base.entity.ImportJob;
import com.unique.unexamine.exchange.base.service.ImportJobBaseService;
import com.unique.unexamine.moduleconfig.manage.ModuleManageService;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.manage.RecordManageService;
import com.unique.unexamine.system.manage.SystemPermissionService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 已发布模块的 Excel 模板、预检查和整批确认；按稳定记录编号更新时仍走原记录业务服务。 */
@Service
public class ImportManageService {
    private static final int MAX_ROWS = 1000;
    private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    private final SystemPermissionService access;
    private final ModuleManageService modules;
    private final RecordManageService recordViews;
    private final BusinessRecordBaseService records;
    private final ImportJobBaseService jobs;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final Path root;

    public ImportManageService(SystemPermissionService access, ModuleManageService modules,
                               RecordManageService recordViews, BusinessRecordBaseService records,
                               ImportJobBaseService jobs, ObjectMapper json, PlatformTransactionManager transactionManager,
                               @Value("${APP_IMPORT_ROOT:../data/imports}") String rootPath) {
        this.access = access;
        this.modules = modules;
        this.recordViews = recordViews;
        this.records = records;
        this.jobs = jobs;
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
        this.root = Path.of(rootPath).toAbsolutePath().normalize();
    }

    /** 模板显示字段名称，隐藏第二行保存稳定字段编码；第一列记录编号仅用于匹配，不允许覆盖。 */
    public byte[] template(Long accountId, Long systemId, Long moduleId) {
        access.requireModule(accountId, systemId, moduleId, "IMPORT");
        List<ModuleManageService.FieldSnapshot> fields = modules.runtime(accountId, systemId, moduleId).snapshot().fields();
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("导入模板");
            Row labels = sheet.createRow(0);
            Row keys = sheet.createRow(1);
            labels.createCell(0).setCellValue("记录编号（更新时填写）");
            keys.createCell(0).setCellValue("record_no");
            keys.setZeroHeight(true);
            sheet.setColumnWidth(0, 24 * 256);
            for (int index = 0; index < fields.size(); index++) {
                ModuleManageService.FieldSnapshot field = fields.get(index);
                labels.createCell(index + 1).setCellValue(field.label() + (field.required() ? " *" : ""));
                keys.createCell(index + 1).setCellValue(field.key());
                sheet.setColumnWidth(index + 1, 22 * 256);
            }
            sheet.createFreezePane(0, 1);
            book.write(out);
            return out.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("导入模板生成失败", exception); }
    }

    /** 只保存预检查结果和源文件，不写业务记录。含错误的任务可下载错误行。 */
    public ImportView preview(Long accountId, Long systemId, Long moduleId, String mode, MultipartFile part) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "IMPORT");
        if (!List.of("INSERT", "UPSERT").contains(mode)) throw bad("导入模式无效");
        if ("UPSERT".equals(mode)) access.requireModule(accountId, systemId, moduleId, "UPDATE");
        if (part == null || part.isEmpty() || part.getSize() > MAX_FILE_BYTES
                || part.getOriginalFilename() == null || !part.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            throw bad("请上传不超过 2 MB 的 .xlsx 文件");
        }
        byte[] bytes;
        try { bytes = part.getBytes(); }
        catch (IOException exception) { throw bad("无法读取 Excel 文件"); }
        if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) throw bad("Excel 文件大小无效");
        ModuleManageService.PublishedModule published = modules.runtime(accountId, systemId, moduleId);
        List<ImportRow> rows = parseAndCheck(accountId, systemId, moduleId, context.tenant().getId(),
                mode, bytes, published.snapshot().fields());
        String key = UUID.randomUUID().toString().replace("-", "") + ".xlsx";
        Path path = root.resolve(key);
        try {
            Files.createDirectories(root);
            Files.write(path, bytes);
            ImportJob job = new ImportJob();
            job.setSystemId(systemId);
            job.setTenantId(context.tenant().getId());
            job.setModuleId(moduleId);
            job.setModuleVersionId(published.versionId());
            job.setRequestedByAccountId(accountId);
            job.setMode(mode);
            job.setSourceKey(key);
            job.setSourceName(part.getOriginalFilename().replace('\\', '/').replaceAll("^.*/", ""));
            job.setSourceSha256(sha256(bytes));
            job.setPreviewJson(write(rows));
            job.setTotalRows(rows.size());
            int valid = (int) rows.stream().filter(row -> row.message() == null).count();
            job.setValidRows(valid);
            job.setImportedRows(0);
            job.setStatus(valid == rows.size() ? "READY" : "INVALID");
            jobs.insert(job);
            return view(job, rows);
        } catch (IOException | RuntimeException exception) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { /* 失败时后续清理孤立文件。 */ }
            if (exception instanceof RuntimeException runtime) throw runtime;
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "导入文件保存失败");
        }
    }

    /** 整批写入同一事务；失败时业务写入全回滚，任务另行标记失败便于排查。 */
    public ImportView commit(Long accountId, Long systemId, Long moduleId, Long jobId) {
        ImportJob job = requireJob(accountId, systemId, moduleId, jobId);
        if (!"READY".equals(job.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT, "导入预检查未通过或已确认");
        try {
            transactions.executeWithoutResult(status -> {
                // 同一任务只允许确认一次；锁定任务后再次检查状态，防止两个请求重复导入。
                ImportJob locked = jobs.selectOne(new LambdaQueryWrapper<ImportJob>()
                        .eq(ImportJob::getId, jobId).last("FOR UPDATE"));
                if (locked == null || !"READY".equals(locked.getStatus())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "导入预检查未通过或已确认");
                }
                if ("UPSERT".equals(job.getMode())) access.requireModule(accountId, systemId, moduleId, "UPDATE");
                ModuleManageService.PublishedModule published = modules.runtime(accountId, systemId, moduleId);
                if (!published.versionId().equals(job.getModuleVersionId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "模块已发布新版本，请重新预检查");
                }
                byte[] bytes = readSource(job);
                if (!sha256(bytes).equals(job.getSourceSha256())) throw new ResponseStatusException(HttpStatus.CONFLICT, "源文件已变化");
                List<ImportRow> current = parseAndCheck(accountId, systemId, moduleId, job.getTenantId(),
                        job.getMode(), bytes, published.snapshot().fields());
                List<ImportRow> previous = readRows(job.getPreviewJson());
                if (current.size() != previous.size() || current.stream().anyMatch(row -> row.message() != null)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "数据已变化，请重新预检查");
                }
                for (int index = 0; index < current.size(); index++) {
                    ImportRow row = current.get(index);
                    ImportRow planned = previous.get(index);
                    if (!row.action().equals(planned.action()) || !row.recordNo().equals(planned.recordNo())
                            || ("UPDATE".equals(row.action()) && !row.expectedVersion().equals(planned.expectedVersion()))) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "匹配记录已变化，请重新预检查");
                    }
                    if ("UPDATE".equals(row.action())) {
                        recordViews.update(accountId, systemId, moduleId, row.recordId(), planned.expectedVersion(), row.values());
                    } else {
                        recordViews.create(accountId, systemId, moduleId, row.values());
                    }
                }
                job.setImportedRows(current.size());
                job.setStatus("IMPORTED");
                job.setFinishTime(LocalDateTime.now());
                jobs.updateById(job);
            });
        } catch (RuntimeException exception) {
            ImportJob persisted = jobs.selectById(jobId);
            if (persisted != null && "READY".equals(persisted.getStatus())) {
                persisted.setStatus("FAILED");
                persisted.setFailureReason(exception instanceof ResponseStatusException response && response.getReason() != null
                        ? response.getReason() : "导入失败，所有业务写入已回滚");
                persisted.setFinishTime(LocalDateTime.now());
                jobs.updateById(persisted);
            }
            throw exception;
        }
        return view(jobs.selectById(jobId), readRows(job.getPreviewJson()));
    }

    /** 提交人可查看最近的预检查与导入历史。 */
    public List<ImportView> history(Long accountId, Long systemId, Long moduleId) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "IMPORT");
        return jobs.selectList(new LambdaQueryWrapper<ImportJob>()
                .eq(ImportJob::getSystemId, systemId).eq(ImportJob::getTenantId, context.tenant().getId())
                .eq(ImportJob::getModuleId, moduleId).eq(ImportJob::getRequestedByAccountId, accountId)
                .orderByDesc(ImportJob::getId).last("LIMIT 20"))
                .stream().map(job -> view(job, List.of())).toList();
    }

    /** 错误文件首表可修正后重传，第二张表保留原行号和原因；原值与当时字段标题取自源文件。 */
    public byte[] errors(Long accountId, Long systemId, Long moduleId, Long jobId) {
        ImportJob job = requireJob(accountId, systemId, moduleId, jobId);
        List<ImportRow> failed = readRows(job.getPreviewJson()).stream().filter(row -> row.message() != null).toList();
        if (failed.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "没有错误行");
        try (XSSFWorkbook sourceBook = new XSSFWorkbook(new ByteArrayInputStream(readSource(job)));
             XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sourceSheet = sourceBook.getSheetAt(0);
            Row sourceHeader = sourceSheet.getRow(0);
            Row sourceKeys = sourceSheet.getRow(1);
            DataFormatter formatter = new DataFormatter();
            Sheet sheet = book.createSheet("修正后重新上传");
            Row header = sheet.createRow(0);
            Row keys = sheet.createRow(1);
            keys.setZeroHeight(true);
            for (int i = 0; i < sourceHeader.getLastCellNum(); i++) {
                header.createCell(i).setCellValue(formatter.formatCellValue(sourceHeader.getCell(i)));
                keys.createCell(i).setCellValue(formatter.formatCellValue(sourceKeys.getCell(i)));
            }
            Sheet reasons = book.createSheet("错误原因");
            Row reasonHeader = reasons.createRow(0);
            reasonHeader.createCell(0).setCellValue("原行号");
            reasonHeader.createCell(1).setCellValue("错误原因");
            for (int i = 0; i < failed.size(); i++) {
                ImportRow failedRow = failed.get(i);
                Row source = sourceSheet.getRow(failedRow.line() - 1);
                Row row = sheet.createRow(i + 2);
                for (int j = 0; j < sourceHeader.getLastCellNum(); j++) {
                    row.createCell(j).setCellValue(source == null ? "" : formatter.formatCellValue(source.getCell(j)));
                }
                Row reason = reasons.createRow(i + 1);
                reason.createCell(0).setCellValue(failedRow.line());
                reason.createCell(1).setCellValue(failedRow.message());
            }
            book.write(out);
            return out.toByteArray();
        } catch (IOException exception) { throw new IllegalStateException("错误行文件生成失败", exception); }
    }

    private List<ImportRow> parseAndCheck(Long accountId, Long systemId, Long moduleId, Long tenantId,
                                          String mode, byte[] bytes, List<ModuleManageService.FieldSnapshot> fields) {
        try (XSSFWorkbook book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets() < 1) throw bad("Excel 缺少工作表");
            Sheet sheet = book.getSheetAt(0);
            Row keys = sheet.getRow(1);
            DataFormatter formatter = new DataFormatter();
            if (keys == null || keys.getLastCellNum() != fields.size() + 1
                    || !"record_no".equals(formatter.formatCellValue(keys.getCell(0)))) throw bad("Excel 模板与模块字段不一致");
            for (int index = 0; index < fields.size(); index++) {
                if (!fields.get(index).key().equals(formatter.formatCellValue(keys.getCell(index + 1)))) {
                    throw bad("Excel 模板与当前发布字段不一致");
                }
            }
            if (sheet.getLastRowNum() > MAX_ROWS + 1) throw bad("单次导入不能超过 1000 行");
            List<ImportRow> results = new ArrayList<>();
            Set<String> seenNumbers = new HashSet<>();
            for (int line = 2; line <= sheet.getLastRowNum(); line++) {
                Row source = sheet.getRow(line);
                if (source == null || blank(source, fields.size(), formatter)) continue;
                String number = formatter.formatCellValue(source.getCell(0)).trim();
                Map<String, JsonNode> values = new LinkedHashMap<>();
                String message = null;
                String action = "CREATE";
                Long recordId = null;
                Integer expectedVersion = null;
                try {
                    if (source.getCell(0) != null && source.getCell(0).getCellType() == CellType.FORMULA) {
                        throw bad("记录编号不能使用公式");
                    }
                    if (!number.isEmpty() && !seenNumbers.add(number)) throw bad("同一文件中记录编号重复");
                    if ("INSERT".equals(mode) && !number.isEmpty()) throw bad("只新增模式不能填写记录编号");
                    for (int index = 0; index < fields.size(); index++) {
                        ModuleManageService.FieldSnapshot field = fields.get(index);
                        JsonNode value = typed(source.getCell(index + 1), field, formatter);
                        if (value != null) values.put(field.key(), value);
                    }
                    recordViews.validateImportedValues(accountId, systemId, moduleId, values);
                    if (!number.isEmpty()) {
                        BusinessRecord matched = records.selectOne(new LambdaQueryWrapper<BusinessRecord>()
                                .eq(BusinessRecord::getTenantId, tenantId).eq(BusinessRecord::getModuleId, moduleId)
                                .eq(BusinessRecord::getRecordNo, number));
                        if (matched == null) throw bad("记录编号不存在");
                        RecordManageService.RecordView visible = recordViews.detail(accountId, systemId, moduleId, matched.getId());
                        if (!List.of("ACTIVE", "REJECTED").contains(visible.recordStatus())) throw bad("审批中的记录不能更新");
                        action = "UPDATE";
                        recordId = visible.id();
                        expectedVersion = visible.version();
                    }
                } catch (ResponseStatusException exception) {
                    message = exception.getReason() == null ? "此行数据或权限无效" : exception.getReason();
                } catch (RuntimeException exception) {
                    message = "此行字段格式无效";
                }
                results.add(new ImportRow(line + 1, number, action, recordId, expectedVersion, values, message));
            }
            if (results.isEmpty()) throw bad("Excel 没有数据行");
            return results;
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ResponseStatusException response) throw response;
            throw bad("无法解析 .xlsx 文件");
        }
    }

    private boolean blank(Row row, int fields, DataFormatter formatter) {
        for (int index = 0; index <= fields; index++) {
            if (!formatter.formatCellValue(row.getCell(index)).isBlank()) return false;
        }
        return true;
    }

    private JsonNode typed(Cell cell, ModuleManageService.FieldSnapshot field, DataFormatter formatter) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return null;
        if (cell.getCellType() == CellType.FORMULA) throw bad(field.label() + "不能使用公式");
        String text = formatter.formatCellValue(cell).trim();
        if (text.isEmpty()) return null;
        try {
            return switch (field.type()) {
                case "NUMBER", "MONEY", "PERCENT" -> {
                    BigDecimal number = "PERCENT".equals(field.type()) && text.endsWith("%")
                            ? new BigDecimal(text.substring(0, text.length() - 1).trim())
                            : cell.getCellType() == CellType.NUMERIC
                            ? BigDecimal.valueOf(cell.getNumericCellValue()) : new BigDecimal(text);
                    yield json.valueToTree(number);
                }
                case "DATE" -> json.valueToTree(cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toLocalDate().toString() : LocalDate.parse(text).toString());
                case "DATETIME" -> json.valueToTree(cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)
                        ? cell.getLocalDateTimeCellValue().toString() : LocalDateTime.parse(text.replace(' ', 'T')).toString());
                case "BOOLEAN" -> {
                    if (List.of("true", "1", "是").contains(text.toLowerCase())) yield json.valueToTree(true);
                    if (List.of("false", "0", "否").contains(text.toLowerCase())) yield json.valueToTree(false);
                    throw bad(field.label() + "应填写是或否");
                }
                default -> json.valueToTree(text);
            };
        } catch (NumberFormatException | DateTimeParseException exception) {
            throw bad(field.label() + "格式无效");
        }
    }

    private ImportJob requireJob(Long accountId, Long systemId, Long moduleId, Long jobId) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "IMPORT");
        ImportJob job = jobs.selectById(jobId);
        if (job == null || !systemId.equals(job.getSystemId()) || !context.tenant().getId().equals(job.getTenantId())
                || !moduleId.equals(job.getModuleId()) || !accountId.equals(job.getRequestedByAccountId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导入任务不存在");
        }
        return job;
    }

    private byte[] readSource(ImportJob job) {
        try { return Files.readAllBytes(root.resolve(job.getSourceKey())); }
        catch (IOException exception) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导入源文件不可用"); }
    }

    private String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("摘要算法不可用", exception); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("预检查结果无法保存", exception); }
    }

    private List<ImportRow> readRows(String raw) {
        try { return json.readValue(raw, new TypeReference<>() {}); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("预检查结果无法读取", exception); }
    }

    private ImportView view(ImportJob job, List<ImportRow> rows) {
        return new ImportView(job.getId(), job.getMode(), job.getStatus(), job.getTotalRows(), job.getValidRows(),
                job.getImportedRows(), job.getFailureReason(), job.getCreateTime(), job.getFinishTime(), rows);
    }

    private ResponseStatusException bad(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }

    /** 每行保留预检查时的动作、可见记录版本和字段值，确认时重新计算并比对。 */
    public record ImportRow(int line, String recordNo, String action, Long recordId, Integer expectedVersion,
                            Map<String, JsonNode> values, String message) {}
    /** 页面可查看的导入历史与预检查逐行反馈。 */
    public record ImportView(Long id, String mode, String status, Integer totalRows, Integer validRows,
                             Integer importedRows, String failureReason, LocalDateTime createTime,
                             LocalDateTime finishTime, List<ImportRow> rows) {}
}
