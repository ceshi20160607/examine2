package com.unique.examine.plat.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public final class PlatformFlowDashboardViews { private PlatformFlowDashboardViews() {}
    public record FlowDefinition(String id,String code,String name,String description,JsonNode draft,long draftVersion,String status,Integer activeVersionNumber,long version,String createdAt,String updatedAt) {}
    public record FlowVersion(String id,int versionNumber,JsonNode snapshot,String checksum,String publishedAt,String publishedBy) {}
    public record CheckResult(boolean valid,List<String> issues) { public CheckResult { issues=List.copyOf(issues); } }
    public record FlowInstance(String id,String definitionId,int definitionVersion,String status,JsonNode input,JsonNode result,String startedAt,String completedAt,long version) {}
    public record Dashboard(String id,String code,String name,String description,JsonNode draft,long draftVersion,String status,Integer activeVersionNumber,long version,String createdAt,String updatedAt) {}
    public record DashboardVersion(String id,int versionNumber,JsonNode snapshot,String checksum,String publishedAt,String publishedBy) {}
    public record Statistic(String code,String label,long value,String requiredPermission) {}
    public record Health(String service,String state) {}
    public record QuickEntry(String label,String path,String requiredPermission) {}
    public record DashboardRuntime(String code,String name,String description,int versionNumber,List<Statistic> statistics,List<Health> health,List<QuickEntry> quickEntries,String publishedAt) { public DashboardRuntime { statistics=List.copyOf(statistics);health=List.copyOf(health);quickEntries=List.copyOf(quickEntries); } }
}
