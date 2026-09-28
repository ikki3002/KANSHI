package com.example.warehousescadasystem.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Manages the SQLite database connection and schema initialization.
 * Demonstrates Week 6 Relational Database concepts.
 */
public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:warehouse_scada.db";

    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");
        }
        return conn;
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

        String createInventoryTable = "CREATE TABLE IF NOT EXISTS inventory (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "sku TEXT UNIQUE NOT NULL, " +
                "name TEXT NOT NULL, " +
                "category TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL DEFAULT 0, " +
                "unit_price REAL NOT NULL DEFAULT 0.0, " +
                "location TEXT NOT NULL, " +
                "status TEXT NOT NULL DEFAULT 'STORED', " +
                "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ");";

        // Invoices Table (1-to-Many relationship with users via user_id FK)
        String createInvoicesTable = "CREATE TABLE IF NOT EXISTS invoices (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "invoice_number TEXT UNIQUE NOT NULL, " +
                "user_id INTEGER NOT NULL, " +
                "customer_name TEXT NOT NULL, " +
                "total_amount REAL NOT NULL DEFAULT 0.0, " +
                "status TEXT NOT NULL DEFAULT 'PAID', " +
                "created_at DATETIME DEFAULT CURRENT_TIMESTAMP, " +
                "FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE" +
                ");";

        // Invoice Items Table (Many-to-Many line items bridge table with FKs to invoices and inventory)
        String createInvoiceItemsTable = "CREATE TABLE IF NOT EXISTS invoice_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "invoice_id INTEGER NOT NULL, " +
                "product_id INTEGER NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "unit_price REAL NOT NULL, " +
                "subtotal REAL NOT NULL, " +
                "FOREIGN KEY (invoice_id) REFERENCES invoices(id) ON DELETE CASCADE, " +
                "FOREIGN KEY (product_id) REFERENCES inventory(id) ON DELETE RESTRICT" +
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
            stmt.execute(createInventoryTable);
            try {
                stmt.execute("ALTER TABLE inventory ADD COLUMN status TEXT NOT NULL DEFAULT 'STORED';");
            } catch (SQLException ignored) {
                // Column already exists
            }

            // 3. Initialize Relational Invoices & Items tables (Clean Slate - no dummy records)
            stmt.execute(createInvoicesTable);
            stmt.execute(createInvoiceItemsTable);

            // 4. Initialize Daily Transaction Log for Calendar Widget
            String createDailyTransactionsTable = "CREATE TABLE IF NOT EXISTS daily_transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "date TEXT UNIQUE NOT NULL, " +
                    "putaway_count INTEGER NOT NULL DEFAULT 0, " +
                    "dispatch_count INTEGER NOT NULL DEFAULT 0, " +
                    "invoice_count INTEGER NOT NULL DEFAULT 0, " +
                    "adjustment_count INTEGER NOT NULL DEFAULT 0" +
                    ");";
            stmt.execute(createDailyTransactionsTable);

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Purges all inventory items, invoices, and invoice line items for a clean slate.
     * Preserves authenticated user accounts.
     */
    public static void purgeWarehouseData() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM invoice_items;");
            stmt.execute("DELETE FROM invoices;");
            stmt.execute("DELETE FROM inventory;");
            stmt.execute("DELETE FROM sqlite_sequence WHERE name IN ('invoice_items', 'invoices', 'inventory');");
        } catch (SQLException e) {
            System.err.println("Failed to purge warehouse data: " + e.getMessage());
        }
    }
}
