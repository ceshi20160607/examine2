package com.unique.examine.plat.task;

/** Atomic persistence boundary for one idempotent platform task write. */
interface PlatformTaskStore {
    PlatformTask createOrReplay(PlatformTask candidate);
}
