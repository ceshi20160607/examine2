package com.unique.examine.core.api;

/**
 * Event-owned member-message bridge used by transactional cross-domain interactions.
 */
public interface MemberMessageFacade {
    long send(Command command);

    record Command(
            long systemId,
            long tenantId,
            long senderMemberId,
            long recipientMemberId,
            String templateCode,
            String title,
            String body,
            AggregateRef target
    ) {
        public Command {
            requirePositive(systemId, "systemId");
            requirePositive(tenantId, "tenantId");
            requirePositive(senderMemberId, "senderMemberId");
            requirePositive(recipientMemberId, "recipientMemberId");
            templateCode = requireText(templateCode, "templateCode");
            title = requireText(title, "title");
            body = requireText(body, "body");
            if (target == null) {
                throw new IllegalArgumentException("target is required");
            }
        }

        private static void requirePositive(long value, String name) {
            if (value <= 0) {
                throw new IllegalArgumentException(name + " must be positive");
            }
        }

        private static String requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
            return value.trim();
        }
    }
}
