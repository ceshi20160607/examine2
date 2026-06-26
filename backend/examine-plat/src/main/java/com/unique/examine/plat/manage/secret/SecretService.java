package com.unique.examine.plat.manage.secret;

import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.task.AsyncTaskStatus;
import com.unique.examine.core.task.AsyncTaskView;
import com.unique.examine.plat.manage.secret.SecretModels.SecretRefVO;
import com.unique.examine.plat.manage.secret.SecretModels.SecretRotationJobVO;
import com.unique.examine.plat.manage.secret.SecretModels.SecretRotationPhaseVO;
import com.unique.examine.plat.manage.secret.SecretModels.SecretRotationRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * SecretRef metadata and rotation workflow service.
 */
@Service
public class SecretService {

    /**
     * Query SecretRef metadata without returning any secret material.
     *
     * @param secretRefId secret reference id
     * @return secret reference metadata
     */
    public SecretRefVO detail(String secretRefId) {
        return secretRef(secretRefId, inferRefType(secretRefId), "v1", "ACTIVE");
    }

    /**
     * Create a secret rotation job and return the task boundary.
     *
     * @param secretRefId secret reference id
     * @param request rotation request
     * @return rotation job
     */
    public SecretRotationJobVO createRotationJob(String secretRefId, SecretRotationRequest request) {
        RequestContext context = RequestContext.current();
        LocalDateTime now = LocalDateTime.now();
        String newVersion = safeText(request == null ? null : request.requestedVersion(), "v2");
        String auditLogId = auditLogId(context);
        SecretRefVO ref = secretRef(secretRefId, inferRefType(secretRefId), "v1", "ROTATING");
        AsyncTaskView task = new AsyncTaskView("task_secret_rotation_" + shortTrace(context.traceId()),
                "SECRET_ROTATION", request == null ? null : request.idempotencyKey(), AsyncTaskStatus.QUEUED,
                0, true, false, true, null, null, null, 0, 0, context.traceId(), auditLogId,
                "admin", now.toString());
        List<SecretRotationPhaseVO> phases = List.of(
                new SecretRotationPhaseVO("CREATE_NEW_VERSION", "QUEUED", null, now),
                new SecretRotationPhaseVO("DUAL_WRITE_VALIDATE", enabledStatus(request == null
                        || Boolean.TRUE.equals(request.dualWriteValidation())), null, now),
                new SecretRotationPhaseVO("SWITCH_ACTIVE_VERSION", enabledStatus(request == null
                        || Boolean.TRUE.equals(request.switchAfterValidation())), null, now),
                new SecretRotationPhaseVO("DISABLE_OLD_VERSION", "PENDING", null, now),
                new SecretRotationPhaseVO("ROLLBACK_ON_FAILURE", "READY", null, now));
        return new SecretRotationJobVO("srj_" + shortTrace(context.traceId()), ref, "QUEUED", newVersion, phases,
                safeText(request == null ? null : request.rollbackPlan(), "restore previous active version"),
                task, context.traceId(), auditLogId, null, now);
    }

    /**
     * Query a secret rotation job by id.
     *
     * @param jobId rotation job id
     * @return rotation job
     */
    public SecretRotationJobVO job(String jobId) {
        RequestContext context = RequestContext.current();
        LocalDateTime now = LocalDateTime.now();
        String auditLogId = auditLogId(context);
        SecretRefVO ref = secretRef("sec_idp_sso_oidc", "IDENTITY_PROVIDER", "v2", "ROTATING");
        AsyncTaskView task = new AsyncTaskView("task_" + jobId, "SECRET_ROTATION", null,
                AsyncTaskStatus.RUNNING, 40, true, false, true, null, null, null, 1, 0,
                context.traceId(), auditLogId, "admin", now.minusMinutes(5).toString());
        List<SecretRotationPhaseVO> phases = List.of(
                new SecretRotationPhaseVO("CREATE_NEW_VERSION", "SUCCESS", null, now.minusMinutes(4)),
                new SecretRotationPhaseVO("DUAL_WRITE_VALIDATE", "RUNNING", null, now.minusMinutes(1)),
                new SecretRotationPhaseVO("SWITCH_ACTIVE_VERSION", "PENDING", null, now),
                new SecretRotationPhaseVO("DISABLE_OLD_VERSION", "PENDING", null, now),
                new SecretRotationPhaseVO("ROLLBACK_ON_FAILURE", "READY", null, now));
        return new SecretRotationJobVO(jobId, ref, "RUNNING", "v2", phases,
                "restore previous active version", task, context.traceId(), auditLogId, null,
                now.minusMinutes(5));
    }

    /**
     * Build a SecretRef metadata view for other manage services.
     *
     * @param secretRefId secret reference id
     * @param refType reference type
     * @return secret metadata
     */
    public SecretRefVO secretRef(String secretRefId, String refType) {
        return secretRef(secretRefId, refType, "v1", "ACTIVE");
    }

    private SecretRefVO secretRef(String secretRefId, String refType, String version, String rotationStatus) {
        LocalDateTime now = LocalDateTime.now();
        String resolvedId = safeText(secretRefId, "sec_unconfigured");
        return new SecretRefVO(resolvedId, safeText(refType, "GENERIC"), version, now.plusDays(90),
                rotationStatus, now.minusDays(1), displayName(resolvedId));
    }

    private String inferRefType(String secretRefId) {
        if (secretRefId != null && secretRefId.contains("openapi")) {
            return "OPENAPI";
        }
        if (secretRefId != null && secretRefId.contains("cert")) {
            return "CERTIFICATE";
        }
        if (secretRefId != null && secretRefId.contains("model")) {
            return "MODEL_CREDENTIAL";
        }
        return "IDENTITY_PROVIDER";
    }

    private String displayName(String secretRefId) {
        if (secretRefId.contains("openapi")) {
            return "OpenAPI application credential";
        }
        if (secretRefId.contains("cert")) {
            return "Identity provider certificate";
        }
        if (secretRefId.contains("model")) {
            return "Model credential";
        }
        return "Identity provider client secret";
    }

    private String enabledStatus(boolean enabled) {
        return enabled ? "PENDING" : "SKIPPED";
    }

    private String auditLogId(RequestContext context) {
        return context.auditLogId() == null || context.auditLogId().isBlank()
                ? "aud_" + context.traceId()
                : context.auditLogId();
    }

    private String shortTrace(String traceId) {
        return traceId == null || traceId.length() <= 8 ? "trace" : traceId.substring(traceId.length() - 8);
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
