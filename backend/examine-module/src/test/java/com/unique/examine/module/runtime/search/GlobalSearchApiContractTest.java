package com.unique.examine.module.runtime.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalSearchApiContractTest {
    @Test
    void freezesTheGetRouteDefaultsAndReadOnlyBoundary() throws Exception {
        var root = GlobalSearchController.class.getAnnotation(RequestMapping.class);
        var search = GlobalSearchController.class.getMethod(
                "search",
                long.class,
                String.class,
                String.class,
                String.class,
                Object.class,
                HttpServletRequest.class);
        var mapping = search.getAnnotation(GetMapping.class);
        var parameters = search.getParameters();
        var transaction = GlobalSearchService.class.getMethod(
                "search", RuntimeSession.class, String.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(root.value())
                .containsExactly("/api/v1/systems/{systemId}/runtime/global-search");
        assertThat(mapping.value()).isEmpty();
        assertThat(parameters[1].getAnnotation(RequestParam.class).required()).isFalse();
        assertThat(parameters[2].getAnnotation(RequestParam.class).defaultValue()).isEqualTo("1");
        assertThat(parameters[3].getAnnotation(RequestParam.class).defaultValue()).isEqualTo("20");
        assertThat(Arrays.stream(parameters)
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(java.util.Objects::nonNull)).isEmpty();
        assertThat(transaction).isNotNull();
        assertThat(transaction.readOnly()).isTrue();
    }

    @Test
    void freezesTheSearchPageAndItemShapes() throws Exception {
        var mapper = new ObjectMapper();
        var item = mapper.readTree(mapper.writeValueAsString(new GlobalSearchViews.SearchItem(
                "work_order",
                "Work orders",
                "123",
                "WO-123",
                "WO-123 Broken pump",
                "ACTIVE",
                List.of("description"))));
        var page = mapper.readTree(mapper.writeValueAsString(
                new GlobalSearchViews.SearchPage(List.of(), 2, 20, 31)));

        assertThat(item.fieldNames()).toIterable().containsExactly(
                "moduleCode",
                "moduleName",
                "recordId",
                "recordNo",
                "displayLabel",
                "status",
                "matchedFieldCodes");
        assertThat(page.fieldNames()).toIterable().containsExactly("items", "page", "size", "total");
    }
}
