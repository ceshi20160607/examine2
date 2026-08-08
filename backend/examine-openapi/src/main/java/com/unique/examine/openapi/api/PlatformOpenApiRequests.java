package com.unique.examine.openapi.api;
import java.util.List;import java.util.Set;
public final class PlatformOpenApiRequests {private PlatformOpenApiRequests(){}
    public record Create(String serviceAccountId,String name,Set<String> scopes,List<String> ipAllowlist,Integer rateLimitPerMinute,String secretRef){}
    public record Policy(String serviceAccountId,String name,Set<String> scopes,List<String> ipAllowlist,Integer rateLimitPerMinute,Long version){}
    public record Rotate(String secretRef,Long version){}
    public record Status(Long version,String reason){}
}
