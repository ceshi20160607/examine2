package com.unique.unexamine.dataexchange.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.dataexchange.base.entity.ExchangeExportBatch;
import com.unique.unexamine.dataexchange.base.entity.ExchangeImportBatch;
import com.unique.unexamine.dataexchange.base.service.ExchangeExportBatchBaseService;
import com.unique.unexamine.dataexchange.base.service.ExchangeImportBatchBaseService;
import com.unique.unexamine.foundation.base.entity.CoreQuotaPolicy;
import com.unique.unexamine.foundation.base.service.CoreQuotaPolicyBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

/** 租户导入、导出任务数配额；执行批次就是实际用量，失败任务不会长期占用。 */
@Service
public class TenantExchangeQuotaService {
    private static final Set<String> IMPORT_EFFECTIVE = Set.of(
            "QUEUED", "RUNNING", "COMPLETED", "COMPLETED_WITH_ERRORS", "ROLLED_BACK", "ROLLBACK_PARTIAL");
    private static final Set<String> EXPORT_EFFECTIVE = Set.of("QUEUED", "RUNNING", "COMPLETED");

    private final CoreQuotaPolicyBaseService policies;
    private final ExchangeImportBatchBaseService imports;
    private final ExchangeExportBatchBaseService exports;

    public TenantExchangeQuotaService(CoreQuotaPolicyBaseService policies,
                                      ExchangeImportBatchBaseService imports,
                                      ExchangeExportBatchBaseService exports) {
        this.policies = policies;
        this.imports = imports;
        this.exports = exports;
    }

    /** 在批次写入事务内先锁配额策略；同租户提交顺序由该行决定。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public CoreQuotaPolicy lock(AuthenticatedContext context, String quotaCode) {
        return policies.selectOne(Wrappers.<CoreQuotaPolicy>lambdaQuery()
                .eq(CoreQuotaPolicy::getContextType, "SYSTEM")
                .eq(CoreQuotaPolicy::getSystemId, context.systemId())
                .eq(CoreQuotaPolicy::getTenantId, context.tenantId())
                .eq(CoreQuotaPolicy::getQuotaCode, quotaCode)
                .eq(CoreQuotaPolicy::getStatus, "ACTIVE")
                .last("FOR UPDATE"));
    }

    /** 配额以执行申请次数计，TOTAL 为累计，DAY/MONTH 按提交时间重置。 */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireCapacity(AuthenticatedContext context, CoreQuotaPolicy policy,
                                boolean importing, LocalDateTime submittedTime) {
        if (policy == null) return;
        LocalDateTime start = switch (policy.getPeriodType()) {
            case "DAY" -> submittedTime.toLocalDate().atStartOfDay();
            case "MONTH" -> submittedTime.withDayOfMonth(1).toLocalDate().atStartOfDay();
            case "TOTAL" -> null;
            default -> throw new DomainException("EXCHANGE_QUOTA_PERIOD_INVALID", "导入导出配额周期不受支持",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        };
        long used;
        if (importing) {
            var query = Wrappers.<ExchangeImportBatch>lambdaQuery()
                    .select(ExchangeImportBatch::getId)
                    .eq(ExchangeImportBatch::getSystemId, context.systemId())
                    .eq(ExchangeImportBatch::getTenantId, context.tenantId())
                    .in(ExchangeImportBatch::getStatus, IMPORT_EFFECTIVE);
            if (start != null) query.ge(ExchangeImportBatch::getStartTime, start);
            // 当前读可看到上一提交事务的批次，避免事务快照导致并发超额。
            used = imports.selectList(query.last("FOR UPDATE")).size();
        } else {
            var query = Wrappers.<ExchangeExportBatch>lambdaQuery()
                    .select(ExchangeExportBatch::getId)
                    .eq(ExchangeExportBatch::getSystemId, context.systemId())
                    .eq(ExchangeExportBatch::getTenantId, context.tenantId())
                    .in(ExchangeExportBatch::getStatus, EXPORT_EFFECTIVE);
            if (start != null) query.ge(ExchangeExportBatch::getStartTime, start);
            used = exports.selectList(query.last("FOR UPDATE")).size();
        }
        if (used >= policy.getHardLimit()) {
            throw new DomainException(importing ? "TENANT_IMPORT_QUOTA_EXCEEDED" : "TENANT_EXPORT_QUOTA_EXCEEDED",
                    importing ? "当前租户导入任务已达到配额" : "当前租户导出任务已达到配额",
                    HttpStatus.TOO_MANY_REQUESTS);
        }
    }
}
