package com.unique.examine.plat.vnext.manage.registration;

import com.unique.examine.plat.vnext.manage.auth.ClientRequest;
import com.unique.examine.plat.vnext.manage.auth.IssuedSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** Non-transactional coordinator. Unique-key losers read the winner after create rollback. */
@Service
public class VNextRegistrationService {
    private final RegistrationCommandFactory commandFactory;
    private final VNextRegistrationTransaction transaction;
    private final RegistrationIdempotencyStore idempotencyStore;
    private final RegistrationIdentityService identityService;
    private final RegistrationSystemMembershipService systemMembershipService;

    VNextRegistrationService(
            RegistrationCommandFactory commandFactory,
            VNextRegistrationTransaction transaction,
            RegistrationIdempotencyStore idempotencyStore,
            RegistrationIdentityService identityService,
            RegistrationSystemMembershipService systemMembershipService
    ) {
        this.commandFactory = commandFactory;
        this.transaction = transaction;
        this.idempotencyStore = idempotencyStore;
        this.identityService = identityService;
        this.systemMembershipService = systemMembershipService;
    }

    public IssuedSession register(
            String username, String displayName, String password,
            String systemName, String systemCode, String idempotencyKey, ClientRequest client
    ) {
        var command = commandFactory.create(
                username, displayName, password, systemName, systemCode, idempotencyKey
        );
        try {
            return transaction.create(command, client);
        } catch (RegistrationException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            return resolveAfterRollback(command, client);
        } catch (RuntimeException exception) {
            throw RegistrationErrors.failed();
        }
    }

    private IssuedSession resolveAfterRollback(RegistrationCommand command, ClientRequest client) {
        var idempotency = idempotencyStore.find(command.idempotencyKey());
        if (idempotency != null) {
            if (!"COMPLETED".equals(idempotency.getStatus())
                    || !command.requestHash().equals(idempotency.getRequestHash())) {
                throw RegistrationErrors.replayMismatch();
            }
            var stored = idempotencyStore.read(idempotency);
            if (!identityService.passwordMatches(stored.receipt().accountId(), command.password())) {
                throw RegistrationErrors.replayMismatch();
            }
            try {
                return transaction.replay(idempotency, stored, client);
            } catch (RegistrationException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw RegistrationErrors.failed();
            }
        }
        if (identityService.find(command.usernameNormalized()) != null) {
            throw RegistrationErrors.usernameConflict();
        }
        if (systemMembershipService.findSystem(command.systemCode()) != null) {
            throw RegistrationErrors.systemCodeConflict();
        }
        throw RegistrationErrors.failed();
    }
}
