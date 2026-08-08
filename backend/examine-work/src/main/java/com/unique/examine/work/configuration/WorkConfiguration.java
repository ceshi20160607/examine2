package com.unique.examine.work.configuration;

import com.unique.examine.work.domain.WorkDomainException;

import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record WorkConfiguration(
        long id,
        long systemId,
        long tenantId,
        long revision,
        Status status,
        Snapshot snapshot,
        Long rollbackFromRevision,
        long createdBy,
        Instant createdAt,
        Long publishedBy,
        Instant publishedAt,
        long version
) {
    public enum Status { DRAFT, PUBLISHED, RETIRED }

    public enum ObjectType { PROJECT_TASK, ORDINARY_TASK, DAILY_REPORT }

    public enum FieldType {
        TEXT, NUMBER, BOOLEAN, DATE, DATETIME, SELECT, MULTI_SELECT
    }

    public enum KanbanRole { NONE, COLUMN, GROUP, NUMERIC }

    public record Field(
            String code,
            String name,
            FieldType type,
            boolean required,
            String dictionaryCode,
            String readPermission,
            String editPermission,
            boolean cardVisible,
            KanbanRole kanbanRole
    ) {
        public Field {
            code = token(code, "field code", 64);
            name = text(name, "field name", 128);
            type = Objects.requireNonNull(type, "type");
            kanbanRole = kanbanRole == null ? KanbanRole.NONE : kanbanRole;
            dictionaryCode = optionalToken(dictionaryCode, "dictionary code", 64);
            readPermission = optionalToken(readPermission, "read permission", 128);
            editPermission = optionalToken(editPermission, "edit permission", 128);
            if (Set.of(FieldType.SELECT, FieldType.MULTI_SELECT).contains(type)) {
                if (dictionaryCode == null) {
                    throw invalid("WORK_CONFIG_DICTIONARY_REQUIRED",
                            "Selection field " + code + " must bind a dictionary");
                }
            } else if (dictionaryCode != null) {
                throw invalid("WORK_CONFIG_DICTIONARY_INVALID",
                        "Only selection fields may bind a dictionary");
            }
            if (kanbanRole == KanbanRole.COLUMN && type != FieldType.SELECT) {
                throw invalid("WORK_CONFIG_KANBAN_INVALID",
                        "Kanban column field must be a single select field");
            }
            if (kanbanRole == KanbanRole.GROUP
                    && type != FieldType.SELECT
                    && type != FieldType.MULTI_SELECT) {
                throw invalid("WORK_CONFIG_KANBAN_INVALID",
                        "Kanban group field must be a selection field");
            }
            if (kanbanRole == KanbanRole.NUMERIC && type != FieldType.NUMBER) {
                throw invalid("WORK_CONFIG_KANBAN_INVALID",
                        "Kanban numeric field must be numeric");
            }
        }

        public boolean readable(Set<String> permissions) {
            return readPermission == null || permissions.contains(readPermission);
        }

        public boolean editable(Set<String> permissions) {
            return editPermission == null || permissions.contains(editPermission);
        }
    }

    public record Snapshot(Map<ObjectType, List<Field>> fields) {
        public Snapshot {
            if (fields == null) {
                throw invalid("WORK_CONFIG_INVALID", "Work configuration fields are required");
            }
            var copy = new EnumMap<ObjectType, List<Field>>(ObjectType.class);
            for (var type : ObjectType.values()) {
                var values = List.copyOf(fields.getOrDefault(type, List.of()));
                if (values.size() > 100) {
                    throw invalid("WORK_CONFIG_LIMIT_EXCEEDED",
                            "At most 100 fields may be configured per work object");
                }
                requireUnique(values, type);
                requireKanban(values, type);
                copy.put(type, values);
            }
            fields = Map.copyOf(copy);
        }

        public List<Field> fields(ObjectType type) {
            return fields.getOrDefault(type, List.of());
        }

        private static void requireUnique(List<Field> fields, ObjectType type) {
            var codes = new HashSet<String>();
            for (var field : fields) {
                if (!codes.add(field.code())) {
                    throw invalid("WORK_CONFIG_FIELD_DUPLICATE",
                            "Duplicate field code in " + type + ": " + field.code());
                }
            }
        }

        private static void requireKanban(List<Field> fields, ObjectType type) {
            if (type == ObjectType.DAILY_REPORT && fields.stream()
                    .anyMatch(field -> field.kanbanRole() != KanbanRole.NONE)) {
                throw invalid("WORK_CONFIG_KANBAN_INVALID",
                        "Daily reports cannot define Kanban fields");
            }
            for (var role : List.of(
                    KanbanRole.COLUMN, KanbanRole.GROUP, KanbanRole.NUMERIC)) {
                if (fields.stream().filter(field -> field.kanbanRole() == role).count() > 1) {
                    throw invalid("WORK_CONFIG_KANBAN_INVALID",
                            "Only one " + role + " field may be configured for " + type);
                }
            }
        }
    }

    public WorkConfiguration {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || revision <= 0
                || status == null || snapshot == null || createdBy <= 0
                || createdAt == null || version <= 0) {
            throw new IllegalArgumentException("Work configuration state is incomplete");
        }
        if (rollbackFromRevision != null && rollbackFromRevision <= 0) {
            throw new IllegalArgumentException("Rollback revision must be positive");
        }
        if (status == Status.DRAFT && (publishedBy != null || publishedAt != null)
                || status != Status.DRAFT && (publishedBy == null || publishedAt == null)) {
            throw new IllegalArgumentException("Work configuration publication facts are inconsistent");
        }
    }

    public WorkConfiguration publish(long actorId, Instant now) {
        if (status != Status.DRAFT) {
            throw invalid("WORK_CONFIG_STATE_INVALID", "Only a draft can be published");
        }
        return new WorkConfiguration(id, systemId, tenantId, revision,
                Status.PUBLISHED, snapshot, rollbackFromRevision, createdBy,
                createdAt, actorId, now, version + 1);
    }

    public WorkConfiguration retire() {
        if (status != Status.PUBLISHED) return this;
        return new WorkConfiguration(id, systemId, tenantId, revision,
                Status.RETIRED, snapshot, rollbackFromRevision, createdBy,
                createdAt, publishedBy, publishedAt, version + 1);
    }

    private static String token(String value, String name, int maximum) {
        if (value == null || value.length() > maximum
                || !value.matches("^[a-z][a-z0-9_.-]{0," + (maximum - 1) + "}$")) {
            throw invalid("WORK_CONFIG_INVALID", name + " is invalid");
        }
        return value;
    }

    private static String optionalToken(String value, String name, int maximum) {
        if (value == null || value.isBlank()) return null;
        return token(value.strip(), name, maximum);
    }

    private static String text(String value, String name, int maximum) {
        if (value == null || value.isBlank()
                || value.strip().codePointCount(0, value.strip().length()) > maximum) {
            throw invalid("WORK_CONFIG_INVALID", name + " is invalid");
        }
        return value.strip();
    }

    static WorkDomainException invalid(String code, String message) {
        return new WorkDomainException(code, message);
    }
}
