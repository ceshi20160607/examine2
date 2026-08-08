package com.unique.examine.module.runtime.importing;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ImportApiContractTest {
    @Test
    void controllerExposesTemplateXlsxHistoryPreviewReadCommitRollbackAndErrors() {
        var base = ImportController.class.getAnnotation(RequestMapping.class);
        assertThat(base.value()).containsExactly("/api/v1/systems/{systemId}/runtime/modules/{moduleCode}");
        var get = Arrays.stream(ImportController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(GetMapping.class))
                .filter(java.util.Objects::nonNull).flatMap(mapping -> Arrays.stream(mapping.value())).toList();
        var post = Arrays.stream(ImportController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(java.util.Objects::nonNull).flatMap(mapping -> Arrays.stream(mapping.value())).toList();

        assertThat(get).containsExactlyInAnyOrder("/imports/template", "/imports/template.xlsx", "/imports",
                "/imports/{batchId}", "/imports/{batchId}/errors.xlsx");
        assertThat(post).containsExactlyInAnyOrder("/imports:preview", "/imports:preview-xlsx",
                "/imports/{batchId}:commit", "/imports/{batchId}:rollback");
    }
}
