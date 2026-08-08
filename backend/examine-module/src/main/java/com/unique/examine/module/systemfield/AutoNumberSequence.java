package com.unique.examine.module.systemfield;

@FunctionalInterface
public interface AutoNumberSequence {
    long next(long systemId, long tenantId, long moduleId, long fieldId);
}
