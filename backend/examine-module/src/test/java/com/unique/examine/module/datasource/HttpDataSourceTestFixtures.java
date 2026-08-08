package com.unique.examine.module.datasource;

import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourceVersion;
import com.unique.examine.module.datasource.domain.ModuleDataSource;
import com.unique.examine.module.datasource.port.DataSourceModuleCatalog;
import com.unique.examine.module.datasource.port.DataSourceRepository;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class HttpDataSourceTestFixtures {
    private HttpDataSourceTestFixtures() {
    }

    public static DataSourceVersion version() {
        var publishedAt = Instant.parse("2026-08-05T00:00:00Z");
        return new DataSourceVersion(
                501, 100, 10, 20, 1, "remote_orders", 91,
                "orders_module", "schema-1", "Remote orders", null,
                new DataSourceDraft(
                        List.of(new DataSourceDraft.OutputField("amount")),
                        List.of(), null, null,
                        DataSourceDraft.SourceKind.HTTP_JSON,
                        new DataSourceDraft.HttpJsonConnection(
                                "https://data.example.invalid/orders",
                                null, 3),
                        List.of(new DataSourceDraft.HttpJsonFieldProjection(
                                "amount", "amount",
                                DataSourceDraft.HttpJsonSourceType.DECIMAL))),
                "0".repeat(64), 30, publishedAt);
    }

    public static ModuleDataSource activeRoot(DataSourceVersion version) {
        return new ModuleDataSource(
                100, 10, 20, "remote_orders", 91, "Remote orders", null,
                DataSourceDraft.empty(), 2, version.id(), 1,
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
                    case "toString" -> "fixed HTTP data-source repository";
                    default -> throw new AssertionError(
                            "Unexpected repository call: " + method.getName());
                });
    }

    public static DataSourceModuleCatalog failingModuleCatalog() {
        return new DataSourceModuleCatalog() {
            @Override
            public Optional<PublishedModule> publishedModule(
                    long systemId,
                    long tenantId,
                    long moduleId
            ) {
                throw new AssertionError(
                        "HTTP publication must not reach the module catalog");
            }
        };
    }
}
