package com.unique.examine.plat.platform;
import com.fasterxml.jackson.databind.JsonNode;
public final class PlatformFlowDashboardRequests { private PlatformFlowDashboardRequests() {}
    public record Create(String code,String name,String description,JsonNode draft) {}
    public record Save(String name,String description,JsonNode draft,Long expectedVersion) {}
    public record Publish(Long expectedVersion) {}
    public record Restore(Long expectedVersion) {}
    public record Start(JsonNode input) {}
}
