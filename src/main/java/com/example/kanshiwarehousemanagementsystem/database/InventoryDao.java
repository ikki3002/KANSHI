package com.example.kanshiwarehousemanagementsystem.database;

import com.example.kanshiwarehousemanagementsystem.model.Product;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for warehouse inventory operations.
 * Demonstrates Week 6 Relational DB CRUD, PreparedStatement parameterization, and SQL aggregation.
 */
public class InventoryDao {

    /**
     * Retrieves all products stored in the SQLite inventory ledger.
     */
    public List<Product> getAllProducts() {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT id, sku, name, category, quantity, unit_price, location FROM inventory ORDER BY name ASC;";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Product p = new Product(
                        rs.getInt("id"),
                        rs.getString("sku"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getInt("quantity"),
                        rs.getDouble("unit_price"),
                        rs.getString("location")
                );
                products.add(p);
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch inventory products: " + e.getMessage());
        }
        return products;
    }

    /**
     * Calculates the total units of stock currently in the warehouse.
     */
    public int getTotalStockCount() {
        String sql = "SELECT COALESCE(SUM(quantity), 0) AS total_units FROM inventory;";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("total_units");
            }
        } catch (SQLException e) {
            System.err.println("Failed to calculate total stock count: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Calculates the total warehouse asset valuation (sum of quantity * unit_price).
     */
    public double getTotalValuation() {
        String sql = "SELECT COALESCE(SUM(quantity * unit_price), 0.0) AS total_val FROM inventory;";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getDouble("total_val");
            }
        } catch (SQLException e) {
            System.err.println("Failed to calculate total valuation: " + e.getMessage());
        }
        return 0.0;
    }

    /**
     * Updates the stock count for a specific SKU by delta (+/-).
     */
    public boolean updateStockDelta(String sku, int delta) {
        String sql = "UPDATE inventory SET quantity = MAX(0, quantity + ?), updated_at = CURRENT_TIMESTAMP WHERE sku = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, delta);
            pstmt.setString(2, sku);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Failed to update stock for SKU " + sku + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Adds a new product to the warehouse ledger.
     */
    public boolean addProduct(Product product) {
        String sql = "INSERT INTO inventory (sku, name, category, quantity, unit_price, location) VALUES (?, ?, ?, ?, ?, ?);";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, product.getSku());
            pstmt.setString(2, product.getName());
            pstmt.setString(3, product.getCategory());
            pstmt.setInt(4, product.getQuantity());
            pstmt.setDouble(5, product.getUnitPrice());
            pstmt.setString(6, product.getLocation());

            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Failed to insert product: " + e.getMessage());
            return false;
        }
    }
}
