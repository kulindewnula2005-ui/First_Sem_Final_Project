package lk.ijse.firstsemfinalproject25.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static DBConnection dbConnection;
    private Connection connection;

    private final String URL = "jdbc:mysql://localhost:3306/firstsemfinalproject25";
    private final String USER = "root";
    private final String PASSWORD = "ijse@1234";

    private DBConnection() throws Exception {
        connection = DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static DBConnection getInstance() throws Exception {
        return dbConnection == null ? new DBConnection() : dbConnection;
    }

    public Connection getConnection() {
        return connection;
    }
}
