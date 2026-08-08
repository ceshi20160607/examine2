package com.unique.examine.core.api;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Injected outbound HTTP boundary. Feature modules own payload semantics and
 * retry policy; the production adapter owns the actual network connection.
 */
public interface OutboundHttpTransport {
    Response post(Request request) throws TransportException;

    record Request(
            URI uri,
            Map<String, String> headers,
            byte[] body,
            Duration timeout
    ) {
        public Request {
            Objects.requireNonNull(uri, "uri");
            headers = headers == null
                    ? Map.of()
                    : Map.copyOf(new LinkedHashMap<>(headers));
            body = body == null ? new byte[0] : body.clone();
            if (timeout == null
                    || timeout.isZero()
                    || timeout.isNegative()
                    || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
                throw new IllegalArgumentException(
                        "Outbound HTTP timeout must be between 1 and 30 seconds");
            }
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    record Response(
            int statusCode,
            byte[] body,
            Duration duration
    ) {
        public Response {
            if (statusCode < 100 || statusCode > 599) {
                throw new IllegalArgumentException(
                        "Outbound HTTP status is invalid");
            }
            body = body == null ? new byte[0] : body.clone();
            if (duration == null || duration.isNegative()) {
                throw new IllegalArgumentException(
                        "Outbound HTTP duration is invalid");
            }
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    final class TransportException extends Exception {
        private final Kind kind;

        public TransportException(
                Kind kind,
                String message,
                Throwable cause
        ) {
            super(message, cause);
            this.kind = Objects.requireNonNull(kind, "kind");
        }

        public TransportException(Kind kind, String message) {
            this(kind, message, null);
        }

        public Kind kind() {
            return kind;
        }

        public enum Kind {
            TIMEOUT,
            IO,
            TLS,
            UNSAFE_TARGET,
            RESPONSE_TOO_LARGE
        }
    }
}
