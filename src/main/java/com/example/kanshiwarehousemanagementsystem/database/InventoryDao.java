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
 * Demonstrates Week 6 Relational DB CRUD, PreparedStatement parameterization, SQL aggregation,
 * and Advanced OOP (Inheriting from BaseDao<Product> and implementing CrudDao<Product>).
 */
public class InventoryDao extends BaseDao<Product> {

    @Override
    protected Product mapResultSet(ResultSet rs) throws SQLException {
        Product p = new Product(
                rs.getInt("id"),
                rs.getString("sku"),
                rs.getString("name"),
                rs.getString("category"),
                rs.getInt("quantity"),
                rs.getDouble("unit_price"),
                rs.getString("location")
        );
        try {
            String st = rs.getString("status");
            if (st != null && !st.isEmpty()) {
                p.setStatus(st);
            }
        } catch (SQLException ignored) {}
        return p;
    }

    @Override
    public List<Product> getAll() {
        return getAllProducts();
    }

    @Override
    public Product getById(int id) {
        String sql = "SELECT id, sku, name, category, quantity, unit_price, location, status FROM inventory WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch product by id " + id + ": " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean add(Product entity) {
        return addProduct(entity);
    }

    @Override
    public boolean update(Product entity) {
        return updateProduct(entity);
    }

    @Override
    public boolean delete(int id) {
        return deleteProduct(id);
    }

    /**
     * Retrieves all products stored in the SQLite inventory ledger.
     */
    public List<Product> getAllProducts() {
        List<Product> products = new ArrayList<>();
        String sql = "SELECT id, sku, name, category, quantity, unit_price, location, status FROM inventory ORDER BY name ASC;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                products.add(mapResultSet(rs));
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
        String sql = "INSERT INTO inventory (sku, name, category, quantity, unit_price, location, status) VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, product.getSku());
            pstmt.setString(2, product.getName());
            pstmt.setString(3, product.getCategory());
            pstmt.setInt(4, product.getQuantity());
            pstmt.setDouble(5, product.getUnitPrice());
            pstmt.setString(6, product.getLocation());
            pstmt.setString(7, product.getStatus() != null ? product.getStatus() : "STORED");

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
        String sql = "UPDATE inventory SET name = ?, category = ?, quantity = ?, unit_price = ?, location = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, p.getName());
            pstmt.setString(2, p.getCategory());
            pstmt.setInt(3, p.getQuantity());
            pstmt.setDouble(4, p.getUnitPrice());
            pstmt.setString(5, p.getLocation());
            pstmt.setString(6, p.getStatus() != null ? p.getStatus() : "STORED");
            pstmt.setInt(7, p.getId());

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
        String sql = "SELECT id, sku, name, category, quantity, unit_price, location, status FROM inventory WHERE sku = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, sku);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
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

    /**
     * Returns a map of bay numbers (1..totalBays) to the stored Product occupying that bay.
     */
    public java.util.Map<Integer, Product> getBayOccupancyMap(int totalBays) {
        java.util.Map<Integer, Product> map = new java.util.HashMap<>();
        List<Product> products = getAllProducts();
        for (Product p : products) {
            if (p.getLocation() != null) {
                int bay = parseBayNumber(p.getLocation());
                if (bay >= 1 && bay <= totalBays && p.getQuantity() > 0) {
                    map.put(bay, p);
                }
            }
        }
        return map;
    }

    /**
     * Finds the lowest-numbered available/empty bay in the rack (1..totalBays).
     * Returns -1 if the rack is 100% full.
     */
    public int findNextAvailableBay(int totalBays) {
        java.util.Map<Integer, Product> occupancy = getBayOccupancyMap(totalBays);
        for (int i = 1; i <= totalBays; i++) {
            if (!occupancy.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Retrieves the product located at a specific bay number.
     */
    public Product getProductByBay(int bayNumber) {
        String bayLocation = String.format("Bay-%02d", bayNumber);
        String sql = "SELECT id, sku, name, category, quantity, unit_price, location, status FROM inventory WHERE location = ? OR location = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, bayLocation);
            pstmt.setString(2, "Bay " + bayNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch product for bay " + bayNumber + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Stores or updates a product in a high-bay storage cell.
     * Ensures bay-specific uniqueness while keeping the product type/name consistent.
     */
    public boolean storeProductInBay(String sku, String name, String category, int qty, double unitPrice, int bayNumber) {
        String bayLocation = String.format("Bay-%02d", bayNumber);
        Product existingInBay = getProductByBay(bayNumber);
        if (existingInBay != null) {
            existingInBay.setName(name);
            existingInBay.setCategory(category);
            existingInBay.setQuantity(qty);
            existingInBay.setUnitPrice(unitPrice);
            existingInBay.setLocation(bayLocation);
            existingInBay.setStatus("STORED");
            return updateProduct(existingInBay);
        }

        String uniqueSku = sku.contains("-B") ? sku : String.format("%s-B%02d", sku, bayNumber);
        Product existingSku = findProductBySku(uniqueSku);
        if (existingSku != null) {
            existingSku.setName(name);
            existingSku.setCategory(category);
            existingSku.setLocation(bayLocation);
            existingSku.setQuantity(qty);
            existingSku.setStatus("STORED");
            return updateProduct(existingSku);
        } else {
            Product newP = new Product(0, uniqueSku, name, category, qty, unitPrice, bayLocation, "STORED");
            return addProduct(newP);
        }
    }

    /**
     * Clears or removes product from a high-bay storage cell upon retrieval.
     */
    public boolean clearBay(int bayNumber) {
        Product p = getProductByBay(bayNumber);
        if (p != null) {
            return deleteProduct(p.getId());
        }
        return false;
    }

    /**
     * Returns total stored units for a product type currently in rack bays.
     */
    public int getAvailableCountByProductType(String productType) {
        String sql = "SELECT COALESCE(SUM(quantity), 0) AS total FROM inventory WHERE (name = ? OR category = ? OR sku LIKE ?) AND status = 'STORED' AND location LIKE 'Bay-%';";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, productType);
            pstmt.setString(2, productType);
            pstmt.setString(3, productType + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("total");
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get available count for " + productType + ": " + e.getMessage());
        }
        return 0;
    }

    /**
     * Returns the ordered list of bay numbers where a product type is currently stored.
     */
    public List<Integer> getBaysForProductType(String productType) {
        List<Integer> bays = new ArrayList<>();
        String sql = "SELECT location FROM inventory WHERE (name = ? OR category = ? OR sku LIKE ?) AND status = 'STORED' AND location LIKE 'Bay-%' ORDER BY id ASC;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, productType);
            pstmt.setString(2, productType);
            pstmt.setString(3, productType + "%");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int bay = parseBayNumber(rs.getString("location"));
                    if (bay > 0) {
                        bays.add(bay);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get bays for " + productType + ": " + e.getMessage());
        }
        return bays;
    }

    /**
     * Returns a list of distinct product types currently stored in the high-bay warehouse.
     */
    public List<String> getDistinctStoredProductTypes() {
        List<String> types = new ArrayList<>();
        String sql = "SELECT DISTINCT name FROM inventory WHERE status = 'STORED' AND location LIKE 'Bay-%' AND quantity > 0 ORDER BY name ASC;";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("name");
                if (name != null && !name.trim().isEmpty()) {
                    types.add(name);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get distinct stored product types: " + e.getMessage());
        }
        return types;
    }

    private int parseBayNumber(String location) {
        if (location == null) return -1;
        String cleaned = location.replaceAll("[^0-9]", "");
        if (!cleaned.isEmpty()) {
            try {
                return Integer.parseInt(cleaned);
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }
}
