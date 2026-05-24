import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/** java -cp mysql.jar FlywayQuery <jdbcUrl> <user> <pass> */
public class FlywayQuery {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: FlywayQuery <jdbcUrl> <user> <pass>");
            System.exit(1);
        }
        try (Connection c = DriverManager.getConnection(args[0], args[1], args[2]);
             Statement st = c.createStatement()) {
            System.out.println("=== flyway_schema_history ALL (last 30) ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT installed_rank, version, description, success, installed_on "
                            + "FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 30")) {
                while (rs.next()) {
                    System.out.printf("rank=%s ver=%s success=%s desc=%s%n",
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
                }
            }
            System.out.println("=== version 14 rows ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT installed_rank, version, success FROM flyway_schema_history WHERE version = '14'")) {
                while (rs.next()) {
                    System.out.printf("rank=%s ver=%s success=%s%n",
                            rs.getString(1), rs.getString(2), rs.getString(3));
                }
            }
            System.out.println("=== failed count ===");
            try (ResultSet rs = st.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0")) {
                if (rs.next()) {
                    System.out.println("failed_rows=" + rs.getInt(1));
                }
            }
        }
    }
}
