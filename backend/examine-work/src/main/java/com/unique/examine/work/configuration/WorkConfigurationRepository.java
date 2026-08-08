package com.unique.examine.work.configuration;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface WorkConfigurationRepository {
    long nextId();

    long nextRevision(long systemId, long tenantId);

    WorkConfiguration insert(WorkConfiguration configuration);

    WorkConfiguration update(WorkConfiguration configuration, long expectedVersion);

    Optional<WorkConfiguration> active(long systemId, long tenantId);

    Optional<WorkConfiguration> findByRevision(
            long systemId, long tenantId, long revision);

    Optional<WorkConfiguration> findById(long systemId, long tenantId, long id);

    List<WorkConfiguration> history(long systemId, long tenantId);

    boolean enabledDictionary(long systemId, String dictionaryCode);

    boolean enabledDictionaryItems(
            long systemId, String dictionaryCode, Set<String> itemCodes);

    void saveRuntimeValues(
            long systemId, long tenantId, WorkConfiguration.ObjectType objectType,
            long objectId, long configurationRevision, JsonNode values, long actorId);

    Optional<RuntimeValues> runtimeValues(
            long systemId, long tenantId, WorkConfiguration.ObjectType objectType,
            long objectId);

    record RuntimeValues(long configurationRevision, JsonNode values) {
    }
}
