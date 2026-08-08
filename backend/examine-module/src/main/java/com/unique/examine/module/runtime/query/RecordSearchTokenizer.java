package com.unique.examine.module.runtime.query;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class RecordSearchTokenizer {
    private static final int MAX_INDEX_TOKENS = 256;
    private static final int MAX_PREFIX_LENGTH = 20;

    private RecordSearchTokenizer() { }

    public static List<String> indexTokens(String value) {
        var result = new LinkedHashSet<String>();
        for (var word : words(value)) {
            if (word.length() < 2) {
                continue;
            }
            result.add(word);
            for (var length = 2; length <= Math.min(word.length(), MAX_PREFIX_LENGTH); length++) {
                result.add(word.substring(0, length));
                if (result.size() >= MAX_INDEX_TOKENS) {
                    return List.copyOf(result);
                }
            }
        }
        return List.copyOf(result);
    }

    public static List<String> queryTokens(String value) {
        var result = new ArrayList<String>();
        for (var word : words(value)) {
            if (word.length() < 2) {
                throw new IllegalArgumentException("Each search token must contain at least two characters");
            }
            if (!result.contains(word)) {
                result.add(word);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("Search text must contain a searchable token");
        }
        return List.copyOf(result);
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .trim();
    }

    public static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static List<String> words(String value) {
        var normalized = normalize(value);
        if (normalized.isEmpty()) {
            return List.of();
        }
        var words = new ArrayList<String>();
        for (var candidate : normalized.split("[^\\p{L}\\p{N}]+")) {
            if (!candidate.isBlank()) {
                words.add(candidate);
            }
        }
        return words;
    }
}
