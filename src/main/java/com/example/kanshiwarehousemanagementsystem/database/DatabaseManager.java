package com.example.kanshiwarehousemanagementsystem.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection and schema initialization.
 * Demonstrates Week 6 Relational Database concepts.
 */
public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:kanshi.db";

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Initializes required SQLite tables and default seed user.
     */
    public static void initializeDatabase() {
        String createUsersTable = "CREATE TABLE IF NOT EXISTS users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "email TEXT UNIQUE NOT NULL, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL, " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            stmt.execute(createUsersTable);

            // Migration check: ensure email column exists if upgrading an existing older table
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN email TEXT;");
            } catch (SQLException ignored) {
                // Column already exists
            }

            // Seed standard demo user
            String seedUser = "INSERT OR IGNORE INTO users (email, username, password) " +
                    "VALUES ('user@gmail.com', 'user', 'User@123');";
            stmt.execute(seedUser);

            // Also seed admin user
            String seedAdmin = "INSERT OR IGNORE INTO users (email, username, password) " +
                    "VALUES ('admin@gmail.com', 'admin', 'Admin@123');";
            stmt.execute(seedAdmin);

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
