package com.unique.examine.module.runtime.favorite;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.module.runtime.security.RuntimeSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteApiContractTest {
    @Test
    void freezesOwnerScopedRoutesHeadersAndTransactions() throws Exception {
        var root = FavoriteController.class.getAnnotation(RequestMapping.class);
        var create = FavoriteController.class.getMethod(
                "create", long.class, String.class, String.class, Object.class, HttpServletRequest.class);
        var delete = FavoriteController.class.getMethod(
                "delete", long.class, long.class, String.class, String.class,
                Object.class, HttpServletRequest.class);
        var deleteMapping = delete.getAnnotation(DeleteMapping.class);
        var createHeader = create.getParameters()[2].getAnnotation(RequestHeader.class);
        var deleteHeader = delete.getParameters()[3].getAnnotation(RequestHeader.class);

        assertThat(root.value()).containsExactly("/api/v1/systems/{systemId}/runtime/favorites");
        assertThat(deleteMapping.value()).containsExactly("/{favoriteId}");
        assertThat(createHeader.name()).isEqualTo("Idempotency-Key");
        assertThat(deleteHeader.name()).isEqualTo("Idempotency-Key");
        assertThat(FavoriteService.class.getMethod(
                "create", RuntimeSession.class, String.class, String.class, String.class, String.class)
                .getAnnotation(Transactional.class)).isNotNull();
        assertThat(FavoriteService.class.getMethod(
                "delete", RuntimeSession.class, long.class, String.class, String.class, String.class, String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void freezesItemPageAndDeleteJsonShapes() throws Exception {
        var mapper = new ObjectMapper();
        var module = mapper.readTree(mapper.writeValueAsString(new FavoriteViews.FavoriteItem(
                "10", 0, "MODULE", "work_order", null,
                "work_order", null, "2026-07-27T10:00:00")));
        var record = mapper.readTree(mapper.writeValueAsString(new FavoriteViews.FavoriteItem(
                "11", 2, "RECORD", "work_order", "123",
                "WO-123 Broken pump", "ACTIVE", "2026-07-27T10:01:00")));
        var page = mapper.readTree(mapper.writeValueAsString(new FavoriteViews.FavoritePage(
                List.of(), 2, 20, 41)));
        var deleted = mapper.readTree(mapper.writeValueAsString(
                new FavoriteViews.DeleteResponse("11", 3, true)));

        assertThat(module.fieldNames()).toIterable().containsExactly(
                "favoriteId", "version", "type", "moduleCode", "displayLabel", "updatedAt");
        assertThat(record.fieldNames()).toIterable().containsExactly(
                "favoriteId", "version", "type", "moduleCode", "recordId",
                "displayLabel", "status", "updatedAt");
        assertThat(page.fieldNames()).toIterable().containsExactly("items", "page", "size", "total");
        assertThat(deleted.fieldNames()).toIterable().containsExactly("favoriteId", "version", "deleted");
    }
}
