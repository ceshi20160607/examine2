package com.unique.examine.core.api;

/**
 * Event-owned bridge for creating one member inbox fact for a stable delivery identity.
 */
public interface IdempotentMemberMessageFacade {
    long deliver(Command command);

    record Command(
            String deliveryKey,
            long systemId,
            long tenantId,
            long recipientMemberId,
            String sourceType,
            String title,
            String body,
            AggregateRef target,
            String targetPath
    ) {
        public Command {
            deliveryKey = requireText(deliveryKey, "deliveryKey", 256);
            requirePositive(systemId, "systemId");
            requirePositive(tenantId, "tenantId");
            requirePositive(recipientMemberId, "recipientMemberId");
            sourceType = requireText(sourceType, "sourceType", 100);
            title = requireText(title, "title", 200);
            body = requireText(body, "body", 4000);
            if (target == null) {
                throw new IllegalArgumentException("target is required");
            }
            targetPath = requireText(targetPath, "targetPath", 500);
            if (!targetPath.startsWith("/systems/" + systemId + "/")) {
                throw new IllegalArgumentException("targetPath must stay inside the command system");
            }
        }

        private static void requirePositive(long value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }

        private static String requireText(String value, String name, int maxLength) {
            if (value == null || value.isBlank() || value.length() > maxLength) {
                throw new IllegalArgumentException(
                        name + " must contain 1 to " + maxLength + " characters");
            }
            return value.trim();
        }
    }
}
