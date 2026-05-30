import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

/** One-off: java -cp mysql-connector.jar RunSqlFile <jdbcUrl> <user> <pass> <sqlFile> */
public class RunSqlFile {
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
                        boolean hasResult = st.execute(q.substring(0, q.length() - 1));
                        if (hasResult) {
                            printResult(st.getResultSet());
                        }
                    }
                }
            }
        }
        System.out.println("OK: " + args[3]);
    }

    private static void printResult(ResultSet rs) throws Exception {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        while (rs.next()) {
            StringBuilder row = new StringBuilder();
            for (int i = 1; i <= cols; i++) {
                if (i > 1) {
                    row.append('\t');
                }
                row.append(md.getColumnLabel(i)).append('=').append(rs.getString(i));
            }
            System.out.println(row);
        }
    }
}
