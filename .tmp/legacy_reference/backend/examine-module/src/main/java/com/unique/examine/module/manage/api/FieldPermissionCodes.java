package com.unique.examine.module.manage.api;

import java.util.regex.Pattern;

public final class FieldPermissionCodes {
    private static final Pattern GENERIC = Pattern.compile(
            "^module\\.[a-z][a-z0-9_]{1,63}\\.field\\.[a-z][a-z0-9_]{1,63}\\.(read|write)$");

    private FieldPermissionCodes() {
    }

    public static String read(String moduleCode, String fieldCode) {
        return prefix(moduleCode, fieldCode) + ".read";
    }

    public static String write(String moduleCode, String fieldCode) {
        return prefix(moduleCode, fieldCode) + ".write";
    }

    public static String sensitiveRead(String moduleCode, String fieldCode) {
        return prefix(moduleCode, fieldCode) + ".sensitive.read";
    }

    public static String sensitiveQuery(String moduleCode, String fieldCode) {
        return prefix(moduleCode, fieldCode) + ".sensitive.query";
    }

    public static boolean isGeneric(String permissionCode) {
        return permissionCode != null && GENERIC.matcher(permissionCode).matches();
    }

    private static String prefix(String moduleCode, String fieldCode) {
        return "module." + moduleCode + ".field." + fieldCode;
    }
}
