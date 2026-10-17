package com.unique.unexamine.platform.manage.registration;

import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RegistrationService {
    private final RegistrationTransactionService transactionService;
    private final AuditRecorder auditRecorder;

    public RegistrationService(RegistrationTransactionService transactionService, AuditRecorder auditRecorder) {
        this.transactionService = transactionService;
        this.auditRecorder = auditRecorder;
    }

    public RegistrationResult register(RegistrationRequest request, String traceId) {
        try {
            return transactionService.create(request, traceId);
        } catch (DomainException exception) {
            if (exception.status() == HttpStatus.CONFLICT) {
                recordConflict(traceId, request, exception.code());
            }
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            recordConflict(traceId, request, "UNIQUE_VALUE_CONFLICT");
            throw exception;
        }
    }

    /** 失败审计使用用户选择的注册方式，避免把平台账号失败记成系统创建失败。 */
    private void recordConflict(String traceId, RegistrationRequest request, String reason) {
        String event = request.systemName() == null || request.systemName().isBlank()
                ? "ACCOUNT_REGISTERED" : "ACCOUNT_REGISTER_SYSTEM_CREATED";
        auditRecorder.recordFailure(traceId, null, event, "RESOURCE_CONFLICT",
                Map.of("reason", reason));
    }
}
