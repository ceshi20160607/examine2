package com.unique.examine.core.api;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Event-owned boundary for delivering one platform account-recovery email.
 *
 * <p>The raw recovery token is intentionally transient. Implementations must
 * not log it or persist it in an outbox, retry payload, or delivery record.</p>
 */
public interface AccountRecoveryMailFacade {
    DeliveryReceipt deliver(Command command);

    enum Status {
        SENT,
        UNAVAILABLE,
        FAILED
    }

    record Command(
            String recipientEmail,
            String rawRecoveryToken,
            Instant expiresAt,
            String requestId
    ) {
        private static final Pattern RECOVERY_TOKEN =
                Pattern.compile("[A-Za-z0-9_-]{32,512}");

        public Command {
            recipientEmail = requireEmail(recipientEmail);
            if (rawRecoveryToken == null
                    || !RECOVERY_TOKEN.matcher(rawRecoveryToken).matches()) {
                throw new IllegalArgumentException(
                        "Recovery token must be a bounded base64url value");
            }
            if (expiresAt == null) {
                throw new IllegalArgumentException(
                        "Recovery expiration is required");
            }
            requestId = requireText(requestId, "requestId", 128);
        }

        @Override
        public String toString() {
            return "Command[recipientEmail=[redacted], rawRecoveryToken=[redacted], "
                    + "expiresAt=" + expiresAt + ", requestId=" + requestId + "]";
        }

        private static String requireEmail(String value) {
            var email = requireText(value, "recipientEmail", 254);
            var at = email.indexOf('@');
            if (at < 1 || at != email.lastIndexOf('@') || at == email.length() - 1
                    || email.indexOf('\r') >= 0 || email.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("Recipient email is invalid");
            }
            return email;
        }

        private static String requireText(String value, String name, int maxLength) {
            if (value == null || value.isBlank() || value.length() > maxLength) {
                throw new IllegalArgumentException(
                        name + " must contain 1 to " + maxLength + " characters");
            }
            var text = value.strip();
            if (text.indexOf('\r') >= 0 || text.indexOf('\n') >= 0) {
                throw new IllegalArgumentException(name + " contains a line break");
            }
            return text;
        }
    }

    record DeliveryReceipt(String requestId, Status status) {
        public DeliveryReceipt {
            if (requestId == null || requestId.isBlank()
                    || requestId.length() > 128
                    || requestId.indexOf('\r') >= 0 || requestId.indexOf('\n') >= 0
                    || status == null) {
                throw new IllegalArgumentException(
                        "Account recovery delivery receipt is incomplete");
            }
            requestId = requestId.strip();
        }
    }
}
