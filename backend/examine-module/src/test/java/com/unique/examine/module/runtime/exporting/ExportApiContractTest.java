package com.unique.examine.module.runtime.exporting;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ExportApiContractTest {
    @Test
    void controllerExposesCreateHistoryReadAndResult() {
        var base = ExportController.class.getAnnotation(RequestMapping.class);
        assertThat(base.value()).containsExactly(
                "/api/v1/systems/{systemId}/runtime/modules/{moduleCode}/exports");
        var gets = Arrays.stream(ExportController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(java.util.Objects::nonNull).toList();
        var get = gets.stream().flatMap(mapping -> Arrays.stream(mapping.value())).toList();
        var post = Arrays.stream(ExportController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull).toList();

        assertThat(gets).hasSize(3);
        assertThat(get).containsExactlyInAnyOrder("/{exportId}", "/{exportId}/result.xlsx");
        assertThat(post).hasSize(1);
        assertThat(post.getFirst().value()).isEmpty();
    }
}
