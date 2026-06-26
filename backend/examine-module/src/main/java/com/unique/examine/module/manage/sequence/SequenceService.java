package com.unique.examine.module.manage.sequence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.module.base.entity.ModuleDynamicSequence;
import com.unique.examine.module.base.service.ModuleDynamicSequenceBaseService;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver;
import com.unique.examine.module.manage.common.ModuleSystemContextResolver.ModuleSystemContext;
import com.unique.examine.module.manage.sequence.SequenceModels.SequenceAllocateRequest;
import com.unique.examine.module.manage.sequence.SequenceModels.SequenceAllocationResult;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.LongStream;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 自动编号数据库原子分配服务。
 */
@Service
public class SequenceService {

    private static final String DEFAULT_SEQUENCE_TYPE = "RUNTIME_RECORD";
    private static final String DEFAULT_PREFIX = "REC-";
    private static final int DEFAULT_WIDTH = 6;
    private static final int DEFAULT_COUNT = 1;
    private static final int MAX_ALLOCATE_COUNT = 1000;

    private final ModuleSystemContextResolver contextResolver;
    private final ModuleDynamicSequenceBaseService sequenceBaseService;
    private final JdbcTemplate jdbcTemplate;

    public SequenceService(ModuleSystemContextResolver contextResolver,
                           ModuleDynamicSequenceBaseService sequenceBaseService,
                           JdbcTemplate jdbcTemplate) {
        this.contextResolver = contextResolver;
        this.sequenceBaseService = sequenceBaseService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 通过数据库原子更新分配编号号段。
     *
     * @param systemId system id
     * @param moduleId module id
     * @param request allocation request
     * @return allocation result
     */
    @Transactional(rollbackFor = Exception.class)
    public SequenceAllocationResult allocate(String systemId, String moduleId, SequenceAllocateRequest request) {
        ModuleSystemContext context = contextResolver.resolve(systemId);
        Long parsedModuleId = contextResolver.parseRequiredId(moduleId, "模块ID格式不正确");
        ensureTenantRequestAllowed(context, request);
        String sequenceType = safeText(Objects.isNull(request) ? null : request.sequenceType(), DEFAULT_SEQUENCE_TYPE);
        int count = allocateCount(Objects.isNull(request) ? null : request.count());
        int width = positive(Objects.isNull(request) ? null : request.width(), DEFAULT_WIDTH);
        String prefix = safeText(Objects.isNull(request) ? null : request.prefix(), DEFAULT_PREFIX);

        ensureSequenceRow(context, parsedModuleId, sequenceType, prefix);
        int updated = jdbcTemplate.update("""
                UPDATE un_module_dynamic_sequence
                   SET current_no = LAST_INSERT_ID(current_no + ?),
                       prefix_rule = ?,
                       updated_at = NOW()
                 WHERE system_id = ?
                   AND tenant_id = ?
                   AND module_id = ?
                   AND sequence_type = ?
                """, count, prefix, context.systemId(), context.tenantId(), parsedModuleId, sequenceType);
        if (updated != 1) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "自动编号分配失败，请重试");
        }
        Long last = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        if (Objects.isNull(last) || last <= 0) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "自动编号结果为空，请重试");
        }
        long first = last - count + 1;
        List<String> allocatedNos = LongStream.rangeClosed(first, last)
                .mapToObj(value -> prefix + pad(value, width))
                .toList();
        RequestContext requestContext = RequestContext.current();
        return new SequenceAllocationResult(String.valueOf(context.systemId()), String.valueOf(context.tenantId()),
                String.valueOf(parsedModuleId), sequenceType, first, last, last, allocatedNos, true,
                "DB_ATOMIC_UPDATE_LAST_INSERT_ID", requestContext.traceId(), auditLogId(requestContext),
                LocalDateTime.now());
    }

    private void ensureSequenceRow(ModuleSystemContext context, Long moduleId, String sequenceType, String prefix) {
        ModuleDynamicSequence existing = sequenceBaseService.getOne(
                new LambdaQueryWrapper<ModuleDynamicSequence>()
                        .eq(ModuleDynamicSequence::getSystemId, context.systemId())
                        .eq(ModuleDynamicSequence::getTenantId, context.tenantId())
                        .eq(ModuleDynamicSequence::getModuleId, moduleId)
                        .eq(ModuleDynamicSequence::getSequenceType, sequenceType)
                        .last("LIMIT 1"), false);
        if (Objects.nonNull(existing)) {
            return;
        }
        ModuleDynamicSequence sequence = new ModuleDynamicSequence();
        sequence.setSystemId(context.systemId());
        sequence.setTenantId(context.tenantId());
        sequence.setModuleId(moduleId);
        sequence.setSequenceType(sequenceType);
        sequence.setPrefixRule(prefix);
        sequence.setCurrentNo(0L);
        sequence.setUpdatedAt(LocalDateTime.now());
        try {
            sequenceBaseService.saveEntity(sequence);
        } catch (DuplicateKeyException ignored) {
            // Another request created the row first. The following atomic update will use that row.
        }
    }

    private void ensureTenantRequestAllowed(ModuleSystemContext context, SequenceAllocateRequest request) {
        if (Objects.isNull(request) || !StringUtils.hasText(request.tenantId())) {
            return;
        }
        Long requestedTenantId = contextResolver.parseRequiredId(request.tenantId(), "租户ID格式不正确");
        if (!Objects.equals(requestedTenantId, context.tenantId())) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "不能为当前租户之外的范围分配编号");
        }
    }

    private int allocateCount(Integer value) {
        int count = positive(value, DEFAULT_COUNT);
        if (count > MAX_ALLOCATE_COUNT) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "单次编号分配不能超过1000个");
        }
        return count;
    }

    private int positive(Integer value, int fallback) {
        return Objects.isNull(value) || value <= 0 ? fallback : value;
    }

    private String pad(long value, int width) {
        return String.format("%0" + width + "d", value);
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
