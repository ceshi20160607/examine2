package com.unique.examine.plat.task;

import java.util.Set;

interface PlatformTaskAccess {
    LiveAuthorization current(long accountId);

    record LiveAuthorization(long epoch, Set<String> permissions) {
        public LiveAuthorization {
            if (epoch <= 0) throw new IllegalArgumentException("epoch is invalid");
            permissions = Set.copyOf(permissions);
        }
    }
}
