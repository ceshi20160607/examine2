package com.unique.examine.module.runtime.recent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentApiContractTest {
    @Test
    void freezesTouchAndListRoutesWithoutIdempotencyHeader() throws Exception {
        var root = RecentController.class.getAnnotation(RequestMapping.class);
        var touch = RecentController.class.getMethod(
                "touch", long.class, String.class, Object.class, HttpServletRequest.class);
        var mapping = touch.getAnnotation(PostMapping.class);

        assertThat(root.value()).containsExactly("/api/v1/systems/{systemId}/runtime");
        assertThat(mapping.value()).containsExactly("/recent-records:touch");
        assertThat(Arrays.stream(touch.getParameters())
                .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                .filter(java.util.Objects::nonNull)).isEmpty();
        assertThat(RecentService.class.getMethod(
                "touch", RuntimeSession.class, String.class).getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void freezesTouchItemAndPagedListShapes() throws Exception {
        var mapper = new ObjectMapper();
        var item = mapper.readTree(mapper.writeValueAsString(new RecentViews.RecentItem(
                "10", "work_order", "123", "WO-123 Broken pump",
                "ACTIVE", 4, "2026-07-27T17:00:00")));
        var page = mapper.readTree(mapper.writeValueAsString(
                new RecentViews.RecentPage(List.of(), 2, 20, 31)));

        assertThat(item.fieldNames()).toIterable().containsExactly(
                "recentId", "moduleCode", "recordId", "displayLabel",
                "status", "accessCount", "lastAccessedAt");
        assertThat(page.fieldNames()).toIterable().containsExactly("items", "page", "size", "total");
    }
}
