import java.sql.*;
class C { public static void main(String[] a) throws Exception {
  try (var c = DriverManager.getConnection(a[0],a[1],a[2]); var s = c.createStatement()) {
    var r = s.executeQuery("SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='un_module_record_data' ORDER BY ORDINAL_POSITION");
    System.out.print("cols:");
    while(r.next()) System.out.print(" "+r.getString(1));
    System.out.println();
  }
}}
