package com.unique.examine.ai.provider;

import com.unique.examine.ai.domain.AiProvider;

import java.util.Objects;

public interface AiProviderClient {
    Completion complete(AiProvider provider, Request request);

    enum Phase {
        PLAN,
        SUMMARY
    }

    record Request(
            Phase phase,
            String systemPrompt,
            String userContent,
            int maxOutputTokens
    ) {
        public Request {
            phase = Objects.requireNonNull(phase, "phase");
            if (systemPrompt == null || systemPrompt.isBlank()
                    || userContent == null || userContent.isBlank()
                    || systemPrompt.length() > 32_000
                    || userContent.length() > 128_000
                    || maxOutputTokens < 1 || maxOutputTokens > 4_096) {
                throw new IllegalArgumentException("AI provider request is invalid");
            }
        }
    }

    record Completion(
            String content,
            int promptTokens,
            int completionTokens,
            long latencyMs,
            String responseHash
    ) {
        public Completion {
            if (content == null || content.isBlank() || content.length() > 128_000
                    || promptTokens < 0 || completionTokens < 0 || latencyMs < 0
                    || responseHash == null
                    || !responseHash.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("AI provider completion is invalid");
            }
        }

        public int totalTokens() {
            return Math.addExact(promptTokens, completionTokens);
        }
    }

    final class ProviderFailure extends RuntimeException {
        private final String code;
        private final boolean retryable;

        public ProviderFailure(String code, boolean retryable) {
            super("AI provider request failed");
            if (code == null || !code.matches("^[A-Z][A-Z0-9_]{1,63}$")) {
                throw new IllegalArgumentException("AI provider failure code is invalid");
            }
            this.code = code;
            this.retryable = retryable;
        }

        public String code() {
            return code;
        }

        public boolean retryable() {
            return retryable;
        }

        @Override
        public String toString() {
            return "ProviderFailure[code=" + code + ",retryable=" + retryable + "]";
        }
    }
}
