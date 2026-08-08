package com.unique.examine.core.api;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Event-owned, cross-domain port for deterministic member result notifications.
 */
public interface ResultNotificationFacade {
    DeliveryReceipt dispatch(Command command);

    record Command(
            long systemId,
            long tenantId,
            long senderMemberId,
            long recipientMemberId,
            String templateCode,
            Map<String, String> variables,
            AggregateRef target,
            String targetPath,
            String dedupeKey
    ) {
        public Command {
            requirePositive(systemId, "systemId");
            requirePositive(tenantId, "tenantId");
            requirePositive(senderMemberId, "senderMemberId");
            requirePositive(recipientMemberId, "recipientMemberId");
            templateCode = requireText(templateCode, "templateCode", 100);
            targetPath = requireText(targetPath, "targetPath", 500);
            dedupeKey = requireText(dedupeKey, "dedupeKey", 200);
            if (target == null) {
                throw new IllegalArgumentException("target is required");
            }
            if (!targetPath.startsWith("/systems/" + systemId + "/")) {
                throw new IllegalArgumentException("targetPath must stay inside the command system");
            }
            if (variables == null || variables.size() > 32) {
                throw new IllegalArgumentException("variables must contain at most 32 entries");
            }
            var copy = new LinkedHashMap<String, String>();
            int total = 0;
            for (var entry : variables.entrySet()) {
                var key = requireText(entry.getKey(), "variable key", 64);
                if (!key.matches("^[a-z][A-Za-z0-9]*$")) {
                    throw new IllegalArgumentException("variable key is invalid: " + key);
                }
                var value = entry.getValue() == null ? "" : entry.getValue();
                if (value.length() > 1000) {
                    throw new IllegalArgumentException("variable value exceeds 1000 characters: " + key);
                }
                total += key.length() + value.length();
                copy.put(key, value);
            }
            if (total > 8000) {
                throw new IllegalArgumentException("variables exceed the bounded payload size");
            }
            variables = Map.copyOf(copy);
        }

        private static void requirePositive(long value, String name) {
            if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        }

        private static String requireText(String value, String name, int maxLength) {
            if (value == null || value.isBlank() || value.length() > maxLength) {
                throw new IllegalArgumentException(name + " must contain 1 to " + maxLength + " characters");
            }
            return value.trim();
        }
    }

    record DeliveryReceipt(long deliveryLogId, Long messageId, String status, boolean replay) {
        public DeliveryReceipt {
            if (deliveryLogId <= 0 || status == null || status.isBlank()) {
                throw new IllegalArgumentException("Delivery receipt is incomplete");
            }
        }
    }
}
