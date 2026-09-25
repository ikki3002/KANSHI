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

            // 2. Initialize Inventory Table (Week 6 Relational DB)
            String createInventoryTable = "CREATE TABLE IF NOT EXISTS inventory (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "sku TEXT UNIQUE NOT NULL, " +
                    "name TEXT NOT NULL, " +
                    "category TEXT NOT NULL, " +
                    "quantity INTEGER NOT NULL DEFAULT 0, " +
                    "unit_price REAL NOT NULL DEFAULT 0.0, " +
                    "location TEXT NOT NULL, " +
                    "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                    ");";
            stmt.execute(createInventoryTable);

            // Seed initial warehouse inventory items
            String seedInventory = "INSERT OR IGNORE INTO inventory (sku, name, category, quantity, unit_price, location) VALUES " +
                    "('BOX-SML-101', 'Standard Cardboard Box (Small)', 'Packaging', 150, 12.50, 'Aisle A-01'), " +
                    "('BOX-MED-102', 'Heavy Duty Corrugated Box (Med)', 'Packaging', 85, 18.00, 'Aisle A-02'), " +
                    "('PAL-EUR-201', 'Euro Pallet EPAL-1 Heavy Duty', 'Material Handling', 40, 35.00, 'Bay B-05'), " +
                    "('SEN-OPT-301', 'Optical Retroreflective Sensor M18', 'Automation Parts', 24, 120.00, 'Secure Shelf S-01'), " +
                    "('CON-BLT-401', 'Modular Conveyor Belt Segment 2m', 'Spares', 12, 245.00, 'Rack R-03');";
            stmt.execute(seedInventory);

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
