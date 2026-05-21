import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.regex.Pattern;

/** One-off: java -cp mysql-connector.jar RunSqlFile <jdbcUrl> <user> <pass> <sqlFile> */
public class RunSqlFile {
    private static final Pattern GO = Pattern.compile(";\\s*(?:\\r?\\n|$)");

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: RunSqlFile <jdbcUrl> <user> <password> <sqlFile>");
            System.exit(1);
        }
        String sql = Files.readString(Path.of(args[3]));
        StringBuilder stmt = new StringBuilder();
        try (Connection c = DriverManager.getConnection(args[0], args[1], args[2]);
             Statement st = c.createStatement()) {
            for (String line : sql.split("\\R")) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("--")) {
                    continue;
                }
                stmt.append(line).append('\n');
                if (t.endsWith(";")) {
                    String q = stmt.toString().trim();
                    stmt.setLength(0);
                    if (!q.isEmpty()) {
                        st.execute(q.substring(0, q.length() - 1));
                    }
                }
            }
        }
        System.out.println("OK: " + args[3]);
    }
}
