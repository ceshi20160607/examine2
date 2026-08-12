package com.unique.examine.module.runtime.filefield;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.rowset.RowSetMetaDataImpl;
import javax.sql.rowset.RowSetProvider;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordFileFieldBindingServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolvesScopedImageMetadataAndMaintainsDeletionProtectingFieldReference() throws Exception {
        var jdbc = new AssetJdbc();
        var service = new RecordFileFieldBindingService(jdbc);
        var session = session(Set.of("file.read", "file.reference"));
        var schema = mapper.readTree("{\"maxFiles\":2,\"imageOnly\":true,\"allowedExtensions\":[\"png\"]}");

        var values = service.resolve(session, "photos", "IMAGE", schema,
                mapper.readTree("[{\"fileId\":\"91\"}]") );
        service.synchronize(session, 501, 701, values.stream()
                .map(RecordFileFieldBindingService.AssetValue::fileId).toList(), LocalDateTime.now());

        assertThat(values).singleElement().satisfies(value -> {
            assertThat(value.originalName()).isEqualTo("evidence.png");
            assertThat(value.mediaType()).isEqualTo("image/png");
            assertThat(value.sha256()).hasSize(64);
        });
        assertThat(jdbc.updates).anyMatch(call -> call.contains("INSERT IGNORE INTO un_file_reference"))
                .anyMatch(call -> call.contains("|91|MODULE_RECORD_FIELD|501:701|"));
    }

    @Test
    void rejectsMissingReferencePermissionAndNonImageSignature() throws Exception {
        var schema = mapper.readTree("{\"maxFiles\":1}");
        assertThatThrownBy(() -> new RecordFileFieldBindingService(new AssetJdbc()).resolve(
                session(Set.of("file.read")), "signature", "SIGNATURE", schema, mapper.readTree("[91]")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FIELD_FORBIDDEN"));

        var jdbc = new AssetJdbc("evidence.pdf", "application/pdf");
        assertThatThrownBy(() -> new RecordFileFieldBindingService(jdbc).resolve(
                session(Set.of("file.read", "file.reference")), "signature", "SIGNATURE", schema,
                mapper.readTree("[91]")))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("FILE_FIELD_VALUE_INVALID"));
    }

    @Test
    void ordinaryFieldPatchKeepsOmittedSignatureReferenceWhileExplicitEmptyClearsIt() {
        var fields = Map.of("signature", 701L, "attachments", 702L);
        var existingDesired = Map.of(701L, List.of(91L), 702L, List.of(92L));

        var ordinaryPatch = RecordFileFieldBindingService.touchedBindings(
                fields, Set.of("title"), existingDesired);
        var explicitClear = RecordFileFieldBindingService.touchedBindings(
                fields, Set.of("title", "signature"), Map.of());

        assertThat(ordinaryPatch).isEmpty();
        assertThat(explicitClear).containsOnlyKeys(701L);
        assertThat(explicitClear.get(701L)).isEmpty();
    }

    private static RuntimeSession session(Set<String> permissions) {
        return new RuntimeSession(1, 10, 20, 30L, permissions);
    }

    private static final class AssetJdbc extends JdbcTemplate {
        private final String name;
        private final String mediaType;
        private final List<String> updates = new ArrayList<>();

        private AssetJdbc() { this("evidence.png", "image/png"); }
        private AssetJdbc(String name, String mediaType) { this.name = name; this.mediaType = mediaType; }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
            try {
                var metadata = new RowSetMetaDataImpl();
                metadata.setColumnCount(5);
                column(metadata, 1, "id", Types.BIGINT);
                column(metadata, 2, "original_name", Types.VARCHAR);
                column(metadata, 3, "media_type", Types.VARCHAR);
                column(metadata, 4, "size_bytes", Types.BIGINT);
                column(metadata, 5, "sha256", Types.VARCHAR);
                var rows = RowSetProvider.newFactory().createCachedRowSet();
                rows.setMetaData(metadata);
                rows.moveToInsertRow();
                rows.updateLong("id", 91);
                rows.updateString("original_name", name);
                rows.updateString("media_type", mediaType);
                rows.updateLong("size_bytes", 128);
                rows.updateString("sha256", "a".repeat(64));
                rows.insertRow();
                rows.moveToCurrentRow();
                rows.beforeFirst();
                var result = new ArrayList<T>();
                var index = 0;
                while (rows.next()) result.add(mapper.mapRow(rows, index++));
                return result;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public int update(String sql, Object... args) {
            updates.add(sql + "|" + String.join("|", java.util.Arrays.stream(args).map(String::valueOf).toList()));
            return 1;
        }

        private static void column(RowSetMetaDataImpl metadata, int index, String name, int type) throws Exception {
            metadata.setColumnName(index, name);
            metadata.setColumnLabel(index, name);
            metadata.setColumnType(index, type);
        }
    }
}
