package lk.ijse.firstsemfinalproject25.util;

import java.sql.ResultSet;

public class IdGenerator {

    public static String nextId(String table, String column, String prefix) throws Exception {
        String sql = "SELECT " + column + " FROM " + table
                + " ORDER BY CAST(SUBSTRING(" + column + ", " + (prefix.length() + 1) + ") AS UNSIGNED) DESC LIMIT 1";
        ResultSet rs = CrudUtil.execute(sql);
        if (rs.next()) {
            int number = Integer.parseInt(rs.getString(1).substring(prefix.length()));
            return String.format("%s%03d", prefix, number + 1);
        }
        return prefix + "001";
    }
}
