package com.unique.examine.web.flyway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;

/**
 * 手工已执行的 Flyway 版本：写入 schema_history(success=1)，不跑 DDL，只 migrate 后续版本。
 */
public final class FlywayManualMark {

    private static final Logger log = LoggerFactory.getLogger(FlywayManualMark.class);

    private static final Map<String, String[]> VERSION_META = new LinkedHashMap<>();

    static {
        VERSION_META.put("14", new String[]{"module field ref", "V14__module_field_ref.sql"});
        VERSION_META.put("23", new String[]{"module record data typed", "V23__module_record_data_typed.sql"});
    }

    private FlywayManualMark() {
    }

    /** @param markAppliedCsv 如 {@code 14} 或 {@code 14,23}，来自 examine.flyway.mark-applied */
    public static void markAppliedVersions(DataSource dataSource, String markAppliedCsv) {
        if (!StringUtils.hasText(markAppliedCsv)) {
            return;
        }
        for (String raw : markAppliedCsv.split(",")) {
            String version = raw.trim();
            if (!version.isEmpty()) {
                markVersionSuccess(dataSource, version);
            }
        }
    }

    private static void markVersionSuccess(DataSource dataSource, String version) {
        String[] meta = VERSION_META.get(version);
        if (meta == null) {
            log.warn("Flyway: unknown mark-applied version {}, skipped", version);
            return;
        }
        try (Connection conn = dataSource.getConnection()) {
            clearFailedVersion(conn, version);
            if (versionApplied(conn, version)) {
                log.debug("Flyway: V{} already success in schema_history", version);
                return;
            }
            int checksum = checksumOfMigration(meta[1]);
            insertApplied(conn, version, meta[0], meta[1], checksum);
            log.info("Flyway: V{} marked success (manual DDL done), will migrate V{}+", version, nextVersion(version));
        } catch (Exception e) {
            throw new IllegalStateException("Flyway mark V" + version + " failed: " + e.getMessage(), e);
        }
    }

    private static String nextVersion(String version) {
        try {
            return String.valueOf(Integer.parseInt(version) + 1);
        } catch (NumberFormatException e) {
            return "?";
        }
    }

    private static void clearFailedVersion(Connection conn, String version) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM flyway_schema_history WHERE version = ? AND success = 0")) {
            ps.setString(1, version);
            int n = ps.executeUpdate();
            if (n > 0) {
                log.info("Flyway: removed {} failed row(s) for V{}", n, version);
            }
        }
    }

    private static boolean versionApplied(Connection conn, String version) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = ? AND success = 1")) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private static void insertApplied(
            Connection conn, String version, String description, String script, int checksum) throws Exception {
        String sql = "INSERT INTO flyway_schema_history "
                + "(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) "
                + "SELECT COALESCE(MAX(installed_rank), 0) + 1, ?, ?, 'SQL', ?, ?, 'manual-mark', NOW(), 0, 1 "
                + "FROM flyway_schema_history";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, version);
            ps.setString(2, description);
            ps.setString(3, script);
            ps.setInt(4, checksum);
            ps.executeUpdate();
        }
    }

    static int checksumOfMigration(String scriptName) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource resource = resolver.getResource("classpath:db/migration/" + scriptName);
        if (!resource.exists()) {
            throw new IllegalStateException("Migration not on classpath: " + scriptName);
        }
        byte[] bytes = resource.getInputStream().readAllBytes();
        CRC32 crc = new CRC32();
        crc.update(bytes);
        return (int) crc.getValue();
    }
}
