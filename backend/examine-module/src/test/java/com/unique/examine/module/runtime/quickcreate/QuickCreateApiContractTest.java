package com.unique.examine.module.runtime.quickcreate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuickCreateApiContractTest {
    @Test
    void freezesTheGetRouteAndReadOnlyBoundary() throws Exception {
        var root = QuickCreateController.class.getAnnotation(RequestMapping.class);
        var modules = QuickCreateController.class.getMethod(
                "modules", long.class, Object.class, HttpServletRequest.class);
        var mapping = modules.getAnnotation(GetMapping.class);
        var transaction = QuickCreateService.class.getMethod(
                "modules", RuntimeSession.class).getAnnotation(Transactional.class);

        assertThat(root.value())
                .containsExactly("/api/v1/systems/{systemId}/runtime/quick-create-modules");
        assertThat(mapping.value()).isEmpty();
        assertThat(Arrays.stream(modules.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(java.util.Objects::nonNull)).isEmpty();
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
    }

    @Test
    void freezesTheModuleListAndItemShapes() throws Exception {
        var mapper = new ObjectMapper();
        var item = mapper.readTree(mapper.writeValueAsString(
                new QuickCreateViews.ModuleItem("work_order", "Work orders", "101")));
        var result = mapper.readTree(mapper.writeValueAsString(
                new QuickCreateViews.ModuleList(List.of())));

        assertThat(item.fieldNames()).toIterable()
                .containsExactly("moduleCode", "moduleName", "schemaVersionId");
        assertThat(result.fieldNames()).toIterable().containsExactly("items");
    }
}
