package com.unique.templatebase.codegen;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证 Base 重生成只能清理与旧清单一致的生成文件。 */
class BaseCodeGeneratorCleanupTest {
    @TempDir
    Path projectRoot;

    @Test
    void refusesModifiedOldFileBeforeDeletingAnyModule() throws Exception {
        Fixture fixture = new Fixture(projectRoot);
        Path first = fixture.javaFile("alpha", "Existing.java");
        Path second = fixture.javaFile("beta", "Existing.java");
        fixture.writeManifest(first, second);
        Files.writeString(second, "handwritten change");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.clean(List.of(module("alpha", "Existing"), module("beta", "Existing"))));

        assertTrue(error.getMessage().contains("was modified"));
        assertTrue(Files.exists(first));
        assertTrue(Files.exists(second));
    }

    @Test
    void refusesUnknownJavaInsideGeneratedBase() throws Exception {
        Fixture fixture = new Fixture(projectRoot);
        Path known = fixture.javaFile("alpha", "Existing.java");
        fixture.writeManifest(known);
        Path unknown = fixture.javaFile("alpha", "Handwritten.java");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.clean(List.of(module("alpha", "Existing"))));

        assertTrue(error.getMessage().contains("untracked Base file"));
        assertTrue(Files.exists(known));
        assertTrue(Files.exists(unknown));
    }

    @Test
    void refusesUnknownXmlInsideGeneratedBase() throws Exception {
        Fixture fixture = new Fixture(projectRoot);
        Path known = fixture.javaFile("alpha", "Existing.java");
        fixture.writeManifest(known);
        Path unknown = fixture.xmlFile("alpha", "HandwrittenMapper.xml");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.clean(List.of(module("alpha", "Existing"))));

        assertTrue(error.getMessage().contains("untracked Base file"));
        assertTrue(Files.exists(known));
        assertTrue(Files.exists(unknown));
    }

    @Test
    void refusesMissingOldManifestFile() throws Exception {
        Fixture fixture = new Fixture(projectRoot);
        Path known = fixture.javaFile("alpha", "Existing.java");
        fixture.writeManifest(known);
        Files.delete(known);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> fixture.clean(List.of(module("alpha", "Existing"))));

        assertTrue(error.getMessage().contains("is missing"));
    }

    @Test
    void allowsNewTableWithoutOldManifestEntry() throws Exception {
        Fixture fixture = new Fixture(projectRoot);
        Path known = fixture.javaFile("alpha", "Existing.java");
        fixture.writeManifest(known);

        fixture.clean(List.of(new BaseCodeGenerator.ModuleConfig("alpha", "alpha", List.of(
                table("existing", "Existing"), table("new_table", "NewTable")))));

        assertFalse(Files.exists(known));
        assertFalse(Files.exists(known.getParent().getParent()));
    }

    @Test
    void allowsInitialGenerationWhenNoManifestAndNoBaseFilesExist() throws Exception {
        Fixture fixture = new Fixture(projectRoot);

        fixture.clean(List.of(module("alpha", "Existing")));
    }

    private static BaseCodeGenerator.ModuleConfig module(String name, String entity) {
        return new BaseCodeGenerator.ModuleConfig(name, name, List.of(table(name, entity)));
    }

    private static BaseCodeGenerator.TableConfig table(String name, String entity) {
        return new BaseCodeGenerator.TableConfig(name, entity, false, Map.of());
    }

    /** 独立临时项目仅模拟清理阶段，不连接数据库或更改真实项目 Base。 */
    private static final class Fixture {
        private static final ObjectMapper JSON = new ObjectMapper();
        private final Path projectRoot;
        private final Path sourceRoot;
        private final Path xmlRoot;
        private final Path manifestPath;

        private Fixture(Path projectRoot) throws IOException {
            this.projectRoot = projectRoot;
            this.sourceRoot = projectRoot.resolve("backend/src/main/java");
            this.xmlRoot = projectRoot.resolve("backend/src/main/resources/mapper");
            this.manifestPath = projectRoot.resolve("backend/base-codegen-manifest.json");
            Files.createDirectories(sourceRoot);
            Files.createDirectories(xmlRoot);
        }

        private Path javaFile(String module, String name) throws IOException {
            Path path = sourceRoot.resolve("example").resolve(module).resolve("base/entity").resolve(name);
            Files.createDirectories(path.getParent());
            Files.writeString(path, "generated " + name);
            return path;
        }

        private Path xmlFile(String module, String name) throws IOException {
            Path path = xmlRoot.resolve(module).resolve("base").resolve(name);
            Files.createDirectories(path.getParent());
            Files.writeString(path, "generated " + name);
            return path;
        }

        private void writeManifest(Path... files) throws Exception {
            List<Map<String, String>> entries = new ArrayList<>();
            for (Path file : files) {
                String relative = projectRoot.relativize(file).toString().replace('\\', '/');
                byte[] bytes = Files.readAllBytes(file);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                entries.add(Map.of("path", relative, "sha256", hash));
            }
            JSON.writeValue(manifestPath.toFile(), Map.of("schemaVersion", 1, "generatedFiles", entries));
        }

        private void clean(List<BaseCodeGenerator.ModuleConfig> modules) throws Exception {
            var config = new BaseCodeGenerator.GeneratorConfig(".", "example", "backend/src/main/java",
                    "backend/src/main/resources/mapper", "db/migration", "backend/base-codegen-manifest.json",
                    null, modules);
            BaseCodeGenerator.cleanGeneratedBase(config, projectRoot, sourceRoot, xmlRoot, manifestPath);
        }
    }
}
