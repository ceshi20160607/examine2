package com.unique.examine.module.runtime.quickcreate;

import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class QuickCreateServiceTest {
    private static final RuntimeSession SESSION =
            new RuntimeSession(1, 7, 9, null, Set.of("system.runtime.access"));

    @Test
    void returnsOnlyReadyCreateModulesInNavigationOrderAndSilentlyOmitsDisappearedModules() {
        var runtime = new FakeRuntime(navigation(
                group("operations", module("alpha"), module("beta"), module("gamma")),
                group("support", module("delta"), module("echo"))));
        runtime.schemas.put("echo", schema("echo", "READY", List.of("VIEW", "CREATE")));
        runtime.schemas.put("gamma", schema("gamma", "UNAVAILABLE", List.of("CREATE")));
        runtime.schemas.put("beta", schema("beta", "READY", List.of("VIEW")));
        runtime.schemas.put("alpha", schema("alpha", "READY", List.of("CREATE")));
        runtime.denied.add("delta");

        var result = new QuickCreateService(runtime).modules(SESSION);

        assertThat(result.items())
                .extracting(
                        QuickCreateViews.ModuleItem::moduleCode,
                        QuickCreateViews.ModuleItem::moduleName,
                        QuickCreateViews.ModuleItem::schemaVersionId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("alpha", "alpha name", "alpha-schema"),
                        org.assertj.core.groups.Tuple.tuple("echo", "echo name", "echo-schema"));
        assertThat(runtime.schemaCalls)
                .containsExactly("alpha", "beta", "gamma", "delta", "echo");
    }

    private static RuntimeViews.Navigation navigation(RuntimeViews.Group... groups) {
        return new RuntimeViews.Navigation("100", "1", List.of(groups));
    }

    private static RuntimeViews.Group group(String code, RuntimeViews.Module... modules) {
        return new RuntimeViews.Group(
                code + "-id", code, code + " name", null, 0, List.of(modules));
    }

    private static RuntimeViews.Module module(String code) {
        return new RuntimeViews.Module(
                code + "-id", code, code + " name", null, 0, "module." + code + ".view", null);
    }

    private static RecordRuntimeViews.RecordSchema schema(
            String moduleCode,
            String state,
            List<String> actions
    ) {
        return new RecordRuntimeViews.RecordSchema(
                moduleCode + "-schema",
                moduleCode + "-snapshot",
                moduleCode + "-logical",
                moduleCode + "-checksum",
                state,
                "READY".equals(state) ? null : "unavailable",
                1,
                List.of(),
                actions,
                new RecordRuntimeViews.QueryLimits(50, 200, 3));
    }

    private static final class FakeRuntime implements QuickCreateRuntime {
        private final RuntimeViews.Navigation navigation;
        private final Map<String, RecordRuntimeViews.RecordSchema> schemas = new LinkedHashMap<>();
        private final Set<String> denied = new java.util.LinkedHashSet<>();
        private final List<String> schemaCalls = new ArrayList<>();

        private FakeRuntime(RuntimeViews.Navigation navigation) {
            this.navigation = navigation;
        }

        @Override
        public RuntimeViews.Navigation navigation(RuntimeSession session) {
            return navigation;
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode) {
            schemaCalls.add(moduleCode);
            if (denied.contains(moduleCode)) {
                throw new BusinessException("PERMISSION_DENIED", "denied", HttpStatus.FORBIDDEN);
            }
            return schemas.get(moduleCode);
        }
    }
}
