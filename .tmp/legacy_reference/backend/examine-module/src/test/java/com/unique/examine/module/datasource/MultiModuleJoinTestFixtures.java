package com.unique.examine.module.datasource;

import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceRepository;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class MultiModuleJoinTestFixtures {
    private MultiModuleJoinTestFixtures() {
    }

    public static DataSourceVersion version() {
        var now = Instant.parse("2026-08-07T00:00:00Z");
        var plan = new DataSourceDraft.MultiModuleJoin(
                List.of(
                        new DataSourceDraft.JoinInput("customers", 101, 1001),
                        new DataSourceDraft.JoinInput("orders", 202, 2002)),
                List.of(new DataSourceDraft.JoinEdge(
                        "customers", "id", "orders", "customerId",
                        DataSourceDraft.JoinType.LEFT,
                        DataSourceDraft.JoinCardinality.ONE_TO_MANY)),
                List.of(
                        new DataSourceDraft.JoinProjection(
                                "customers", "id", "customers__id", 111,
                                "Customer ID", "NUMBER", "NUMBER",
                                true, false, true),
                        new DataSourceDraft.JoinProjection(
                                "orders", "amount", "orders__amount", 222,
                                "Amount", "MONEY", "NUMBER",
                                true, false, true)),
                DataSourceDraft.JoinFailureMode.ALLOW_PARTIAL_LEFT, 3, 50);
        var snapshot = new DataSourceDraft(
                List.of(), List.of(), null, null,
                DataSourceDraft.SourceKind.MULTI_MODULE_JOIN,
                null, List.of(), null, List.of(), plan);
        return new DataSourceVersion(
                9001, 900, 10, 20, 1, "customer_orders", 11,
                "customers_module", "schema-11", "Customer orders", null,
                snapshot, "a".repeat(64), 7, now);
    }

    public static ModuleDataSource activeRoot(DataSourceVersion version) {
        return new ModuleDataSource(
                version.dataSourceId(), version.systemId(), version.tenantId(),
                version.code(), version.moduleId(), version.name(), null,
                version.snapshot(), 2, version.id(), version.versionNumber(),
                version.publishedAt(), version.publishedAt(), 2);
    }

    public static DataSourceRepository repository(
            ModuleDataSource root,
            DataSourceVersion version
    ) {
        return (DataSourceRepository) Proxy.newProxyInstance(
                DataSourceRepository.class.getClassLoader(),
                new Class<?>[]{DataSourceRepository.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "findById" -> Optional.of(root);
                    case "findActiveVersion", "findVersionById" ->
                            Optional.of(version);
                    case "toString" -> "fixed join data-source repository";
                    default -> throw new AssertionError(
                            "Unexpected repository call: " + method.getName());
                });
    }
}
