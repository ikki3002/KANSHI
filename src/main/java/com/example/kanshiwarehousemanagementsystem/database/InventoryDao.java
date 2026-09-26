package com.example.kanshiwarehousemanagementsystem.database;

import com.example.kanshiwarehousemanagementsystem.model.Product;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

    /**
     * Counts products whose stock level is at or below the warning threshold.
     */
    public int getLowStockCount(int threshold) {
        String sql = "SELECT COUNT(*) AS low_count FROM inventory WHERE quantity <= ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, threshold);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("low_count");
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to count low stock products: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Counts total distinct product SKUs in the catalog.
     */
    public int getDistinctProductCount() {
        String sql = "SELECT COUNT(DISTINCT sku) AS sku_count FROM inventory;";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getInt("sku_count");
            }
        } catch (SQLException e) {
            System.err.println("Failed to count distinct SKUs: " + e.getMessage());
        }
        return 0;
    }

    /**
     * Retrieves current quantity for a specific SKU.
     */
    public int getStockQuantity(String sku) {
        String sql = "SELECT quantity FROM inventory WHERE sku = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sku);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("quantity");
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get stock for SKU " + sku + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Updates an existing product in the SQLite inventory ledger.
     */
    public boolean updateProduct(Product p) {
        String sql = "UPDATE inventory SET name = ?, category = ?, quantity = ?, unit_price = ?, location = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, p.getName());
            pstmt.setString(2, p.getCategory());
            pstmt.setInt(3, p.getQuantity());
            pstmt.setDouble(4, p.getUnitPrice());
            pstmt.setString(5, p.getLocation());
            pstmt.setInt(6, p.getId());

            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Failed to update product ID " + p.getId() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a product from the inventory ledger by ID.
     */
    public boolean deleteProduct(int id) {
        String sql = "DELETE FROM inventory WHERE id = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Failed to delete product ID " + id + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a SKU is unique across all products (excluding the specified ID during edits).
     */
    public boolean isSkuUnique(String sku, int excludeId) {
        String sql = "SELECT COUNT(*) AS c FROM inventory WHERE sku = ? AND id != ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sku);
            pstmt.setInt(2, excludeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("c") == 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to check SKU uniqueness: " + e.getMessage());
        }
        return false;
    }

    /**
     * Finds a single product by SKU.
     */
    public Product findProductBySku(String sku) {
        String sql = "SELECT id, sku, name, category, quantity, unit_price, location FROM inventory WHERE sku = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sku);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Product(
                            rs.getInt("id"),
                            rs.getString("sku"),
                            rs.getString("name"),
                            rs.getString("category"),
                            rs.getInt("quantity"),
                            rs.getDouble("unit_price"),
                            rs.getString("location")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to find product by SKU: " + e.getMessage());
        }
        return null;
    }

    /**
     * Exports the complete inventory list to a formatted JSON file (Week 7 syllabus).
     */
    public boolean exportInventoryToJson(File destinationFile) {
        List<Product> products = getAllProducts();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try (FileWriter writer = new FileWriter(destinationFile)) {
            gson.toJson(products, writer);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to export inventory to JSON: " + e.getMessage());
            return false;
        }
    }
}
