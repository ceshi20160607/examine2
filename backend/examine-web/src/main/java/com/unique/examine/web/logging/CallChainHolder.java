package com.unique.examine.web.logging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CallChainHolder {

    private static final ThreadLocal<List<String>> CHAIN = ThreadLocal.withInitial(ArrayList::new);

    private CallChainHolder() {
    }

    public static void push(String methodSig) {
        if (methodSig == null || methodSig.isBlank()) {
            return;
        }
        CHAIN.get().add(methodSig);
    }

    public static List<String> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(CHAIN.get()));
    }

    public static void clear() {
        CHAIN.remove();
    }
}

