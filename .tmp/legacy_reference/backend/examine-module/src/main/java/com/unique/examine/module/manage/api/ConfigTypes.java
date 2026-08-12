package com.unique.examine.module.manage.api;

import java.util.EnumSet;
import java.util.Set;

public final class ConfigTypes {
    private ConfigTypes() {
    }

    public enum DesiredStatus { ENABLED, DISABLED, ARCHIVED }

    public enum FieldPermissionMode { INHERIT, STAGED, ENFORCED }

    public enum DictionaryType { LIST, TREE, CASCADE, STATUS, TAG, FIELD_OPTION }

    public enum FieldType {
        TEXT, TEXTAREA, PHONE, EMAIL, URL, IDENTITY, NUMBER, PERCENT, MONEY,
        DATE, DATETIME, DATE_RANGE, TIME, TIME_RANGE, RADIO, MULTI_SELECT, CASCADE,
        SWITCH, MEMBER, DEPARTMENT, TENANT, ATTACHMENT, IMAGE, FILE_GROUP,
        AUTO_NUMBER, RELATION, REFERENCE, SUBTABLE, ADDRESS, GEO, RATING, PROGRESS,
        TAG, BARCODE, SIGNATURE, RICH_TEXT, JSON, SECRET, STATUS, FORMULA, SUMMARY,
        CALCULATED, LOOKUP, AGGREGATE, AI_FILL, CREATED_BY, CREATED_AT, UPDATED_BY, UPDATED_AT
    }

    public enum IndexMode { NONE, FILTER, SORT, UNIQUE, STATISTIC }

    public enum PageType { LIST, FORM, DETAIL }

    public enum ComponentType { FIELD, SECTION, TABS, TAB, ACTION, TEXT, DIVIDER }

    public enum ActionType { CREATE, UPDATE, DELETE, CUSTOM, APPROVAL, IMPORT, EXPORT, PRINT }

    public enum ActionPlacement { TOOLBAR, ROW, DETAIL, BATCH }

    public enum RuleType {
        FIELD_VISIBILITY, FIELD_REQUIRED, FIELD_READ_ONLY,
        ACTION_ENABLED, DELETE_ALLOWED, APPROVAL_REQUIRED
    }

    public enum ConditionJoin { AND, OR }

    public enum ConditionOperator {
        EQ, NE, GT, GTE, LT, LTE, IN, NOT_IN, EMPTY, NOT_EMPTY, CONTAINS, BETWEEN
    }

    public enum RuleEffect {
        VISIBLE, REQUIRED, READ_ONLY, ACTION_ENABLED, DELETE_ALLOWED, APPROVAL_REQUIRED
    }

    public static final Set<FieldType> DICTIONARY_FIELDS = EnumSet.of(
            FieldType.RADIO, FieldType.MULTI_SELECT, FieldType.CASCADE, FieldType.STATUS
    );
    public static final Set<FieldType> RELATION_FIELDS = EnumSet.of(
            FieldType.RELATION, FieldType.REFERENCE, FieldType.SUBTABLE, FieldType.LOOKUP,
            FieldType.AGGREGATE, FieldType.SUMMARY
    );
    public static final Set<FieldType> NUMERIC_FIELDS = EnumSet.of(
            FieldType.NUMBER, FieldType.PERCENT, FieldType.MONEY, FieldType.RATING, FieldType.PROGRESS,
            FieldType.SUMMARY, FieldType.CALCULATED, FieldType.AGGREGATE
    );
    public static final Set<FieldType> ORDERED_FIELDS = EnumSet.of(
            FieldType.NUMBER, FieldType.PERCENT, FieldType.MONEY, FieldType.RATING, FieldType.PROGRESS,
            FieldType.DATE, FieldType.DATETIME, FieldType.TIME, FieldType.CREATED_AT, FieldType.UPDATED_AT
    );
    public static final Set<FieldType> TEXT_FIELDS = EnumSet.of(
            FieldType.TEXT, FieldType.TEXTAREA, FieldType.PHONE, FieldType.EMAIL, FieldType.URL,
            FieldType.IDENTITY, FieldType.ADDRESS, FieldType.BARCODE, FieldType.RICH_TEXT,
            FieldType.SECRET, FieldType.AUTO_NUMBER
    );
    public static final Set<FieldType> SYSTEM_FIELDS = EnumSet.of(
            FieldType.CREATED_BY, FieldType.CREATED_AT, FieldType.UPDATED_BY, FieldType.UPDATED_AT
    );
}
