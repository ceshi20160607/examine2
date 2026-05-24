import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

/** 打印 V14 migration checksum，供 flyway_mark_v14_applied.sql 手工替换 */
public class FlywayMarkV14 {
    public static void main(String[] args) throws Exception {
        Path p = Path.of(args.length > 0 ? args[0]
                : "backend/examine-web/src/main/resources/db/migration/V14__module_field_ref.sql");
        byte[] bytes = Files.readAllBytes(p);
        CRC32 crc = new CRC32();
        crc.update(bytes);
        System.out.println("V14 checksum=" + (int) crc.getValue());
    }
}
