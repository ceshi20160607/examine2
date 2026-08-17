package com.unique.unexamine.platform.manage.registration;

import com.unique.unexamine.audit.manage.AuditRecorder;
import org.springframework.dao.DataIntegrityViolationException;
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
        } catch (DataIntegrityViolationException exception) {
            auditRecorder.recordFailure(traceId, null, "ACCOUNT_REGISTER_SYSTEM_CREATED", "RESOURCE_CONFLICT",
                    Map.of("reason", "UNIQUE_VALUE_CONFLICT"));
            throw exception;
        }
    }
}
