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
                recordConflict(traceId, exception.code());
            }
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            recordConflict(traceId, "UNIQUE_VALUE_CONFLICT");
            throw exception;
        }
    }

    private void recordConflict(String traceId, String reason) {
        auditRecorder.recordFailure(traceId, null, "ACCOUNT_REGISTER_SYSTEM_CREATED", "RESOURCE_CONFLICT",
                Map.of("reason", reason));
    }
}
