package com.unique.examine.module.manage.api;

import java.util.List;

public final class ConfigPreviewViews {
    private ConfigPreviewViews() { }

    public record PermissionPreview(String memberId, String tenantId, String permissionVersion,
                                    boolean root, List<PreviewRole> sourceRoles,
                                    List<PreviewDataScope> dataScopes, PreviewTree active,
                                    PreviewTree draft) { }

    public record PreviewRole(String id, String code, String name, String publishedVersion) { }

    public record PreviewDataScope(String roleId, String id, String code, String kind) { }

    public record PreviewTree(String versionId, String revision, List<PreviewGroup> groups) { }

    public record PreviewGroup(String id, String code, String name, int sortOrder,
                               List<PreviewModule> modules) { }

    public record PreviewModule(String id, String code, String name, int sortOrder,
                                List<String> fields, List<String> actions) { }
}
