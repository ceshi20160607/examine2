package com.unique.examine.work.api;

import com.unique.examine.work.configuration.WorkConfiguration;

import java.util.List;

public final class WorkConfigurationApiModels {
    private WorkConfigurationApiModels() {
    }

    public record DraftBody(WorkConfiguration.Snapshot snapshot) {
    }

    public record PublishBody(long version) {
    }

    public record RollbackBody(long targetRevision) {
    }

    public record ConfigurationView(
            String id,
            long revision,
            String status,
            WorkConfiguration.Snapshot snapshot,
            Long rollbackFromRevision,
            String createdBy,
            String createdAt,
            String publishedBy,
            String publishedAt,
            long version) {
        static ConfigurationView from(WorkConfiguration value) {
            return new ConfigurationView(
                    Long.toString(value.id()), value.revision(),
                    value.status().name(), value.snapshot(),
                    value.rollbackFromRevision(), Long.toString(value.createdBy()),
                    value.createdAt().toString(),
                    value.publishedBy() == null ? null
                            : Long.toString(value.publishedBy()),
                    value.publishedAt() == null ? null
                            : value.publishedAt().toString(),
                    value.version());
        }
    }

    public record History(List<ConfigurationView> items) {
        public History {
            items = List.copyOf(items);
        }
    }
}
