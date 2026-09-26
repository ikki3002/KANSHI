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

            // Seed initial warehouse inventory items
            String seedInventory = "INSERT OR IGNORE INTO inventory (sku, name, category, quantity, unit_price, location) VALUES " +
                    "('BOX-SML-101', 'Standard Cardboard Box (Small)', 'Packaging', 150, 12.50, 'Aisle A-01'), " +
                    "('BOX-MED-102', 'Heavy Duty Corrugated Box (Med)', 'Packaging', 85, 18.00, 'Aisle A-02'), " +
                    "('PAL-EUR-201', 'Euro Pallet EPAL-1 Heavy Duty', 'Material Handling', 40, 35.00, 'Bay B-05'), " +
                    "('SEN-OPT-301', 'Optical Retroreflective Sensor M18', 'Automation Parts', 24, 120.00, 'Secure Shelf S-01'), " +
                    "('CON-BLT-401', 'Modular Conveyor Belt Segment 2m', 'Spares', 12, 245.00, 'Rack R-03');";
            stmt.execute(seedInventory);

            // 3. Initialize Relational Invoices & Items tables
            stmt.execute(createInvoicesTable);
            stmt.execute(createInvoiceItemsTable);

            // Seed initial relational invoice if none exists
            String checkInvoiceSql = "SELECT COUNT(*) FROM invoices;";
            try (var rs = stmt.executeQuery(checkInvoiceSql)) {
                if (rs.next() && rs.getInt(1) == 0) {
                    stmt.execute("INSERT INTO invoices (invoice_number, user_id, customer_name, total_amount, status) " +
                            "VALUES ('INV-2026-001', 2, 'Global Logistics Corp', 305.00, 'PAID');");
                    stmt.execute("INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, subtotal) VALUES " +
                            "(1, 1, 10, 12.50, 125.00), " +
                            "(1, 3, 5, 36.00, 180.00);");
                }
            }

        } catch (SQLException e) {
            System.err.println("Database initialization failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
