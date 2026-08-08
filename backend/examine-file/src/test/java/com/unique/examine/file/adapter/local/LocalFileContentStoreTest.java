package com.unique.examine.file.adapter.local;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileContentStoreTest {
    @TempDir
    Path root;

    @Test
    void contentSurvivesAcrossStoreInstancesAndCanBeDeleted() {
        var objectKey = "system/10/tenant/20/file/30";
        var bytes = "persistent content".getBytes(StandardCharsets.UTF_8);

        new LocalFileContentStore(root).put(objectKey, bytes);

        var restartedStore = new LocalFileContentStore(root);
        assertThat(restartedStore.read(objectKey)).contains(bytes);
        restartedStore.delete(objectKey);
        assertThat(restartedStore.read(objectKey)).isEmpty();
    }

    @Test
    void objectKeyCannotEscapeStorageRoot() {
        var store = new LocalFileContentStore(root);

        assertThatThrownBy(() -> store.put("../outside", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("escapes");
        assertThatThrownBy(() -> store.put("/absolute", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.put("nested\\outside", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void symbolicLinkSegmentsCannotRedirectContent() throws Exception {
        var store = new LocalFileContentStore(root);
        var target = Files.createDirectory(root.resolve("real"));
        try {
            Files.createSymbolicLink(root.resolve("link"), target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            return;
        }

        assertThatThrownBy(() -> store.put("link/escaped", new byte[]{1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("symbolic link");
    }
}
