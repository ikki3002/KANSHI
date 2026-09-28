package com.example.warehousescadasystem.database;

import com.example.warehousescadasystem.model.Product;
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
        String sql = "UPDATE inventory SET sku = ?, name = ?, category = ?, quantity = ?, unit_price = ?, location = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, p.getSku());
            pstmt.setString(2, p.getName());
            pstmt.setString(3, p.getCategory());
            pstmt.setInt(4, p.getQuantity());
            pstmt.setDouble(5, p.getUnitPrice());
            pstmt.setString(6, p.getLocation());
            pstmt.setString(7, p.getStatus() != null ? p.getStatus() : "STORED");
            pstmt.setInt(8, p.getId());

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
    /**
     * Returns a map of bay numbers (1..totalBays) to the stored Product occupying that bay.
     * Considers a bay occupied if a product record has quantity > 0 and status is not DISPATCHED.
     */
    public java.util.Map<Integer, Product> getBayOccupancyMap(int totalBays) {
        java.util.Map<Integer, Product> map = new java.util.HashMap<>();
        List<Product> products = getAllProducts();
        for (Product p : products) {
            if (p.getLocation() != null) {
                int bay = parseBayNumber(p.getLocation());
                if (bay >= 1 && bay <= totalBays) {
                    boolean isOccupied = p.getQuantity() > 0 && !"DISPATCHED".equalsIgnoreCase(p.getStatus());
                    if (isOccupied) {
                        map.put(bay, p);
                    }
                }
            }
        }
        return map;
    }

    /**
     * Returns the count of occupied bays in the default 54-bay rack.
     */
    public int getOccupiedBayCount() {
        return getBayOccupancyMap(54).size();
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
     * Supports various formatting aliases (Bay-01, Bay-1, Bay 01, Bay 1).
     */
    public Product getProductByBay(int bayNumber) {
        String loc1 = String.format("Bay-%02d", bayNumber);
        String loc2 = String.format("Bay-%d", bayNumber);
        String loc3 = String.format("Bay %02d", bayNumber);
        String loc4 = String.format("Bay %d", bayNumber);
        String sql = "SELECT id, sku, name, category, quantity, unit_price, location, status FROM inventory " +
                     "WHERE location IN (?, ?, ?, ?) ORDER BY id DESC LIMIT 1;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, loc1);
            pstmt.setString(2, loc2);
            pstmt.setString(3, loc3);
            pstmt.setString(4, loc4);
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

        // Generate a bay-unique SKU:
        // If sku already ends with -B<bayNumber> or -<bayNumber>, keep it.
        // If sku ends with -B<otherBay>, replace it with -B<bayNumber>.
        // Otherwise, append -B<bayNumber>.
        String uniqueSku;
        String baySuffix = String.format("-B%02d", bayNumber);
        String altBaySuffix = "-" + bayNumber;
        if (sku.endsWith(baySuffix) || sku.endsWith(altBaySuffix)) {
            uniqueSku = sku;
        } else if (sku.matches(".*-B\\d+$")) {
            uniqueSku = sku.substring(0, sku.lastIndexOf("-B")) + baySuffix;
        } else {
            uniqueSku = String.format("%s-B%02d", sku, bayNumber);
        }

        Product existingInBay = getProductByBay(bayNumber);
        if (existingInBay != null) {
            existingInBay.setSku(uniqueSku);
            existingInBay.setName(name);
            existingInBay.setCategory(category);
            existingInBay.setQuantity(qty);
            existingInBay.setUnitPrice(unitPrice);
            existingInBay.setLocation(bayLocation);
            existingInBay.setStatus("STORED");
            return updateProduct(existingInBay);
        }

        Product existingSku = findProductBySku(uniqueSku);
        if (existingSku != null) {
            existingSku.setName(name);
            existingSku.setCategory(category);
            existingSku.setLocation(bayLocation);
            existingSku.setQuantity(qty);
            existingSku.setUnitPrice(unitPrice);
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
        String loc1 = String.format("Bay-%02d", bayNumber);
        String loc2 = String.format("Bay-%d", bayNumber);
        String loc3 = String.format("Bay %02d", bayNumber);
        String loc4 = String.format("Bay %d", bayNumber);
        String sql = "DELETE FROM inventory WHERE location IN (?, ?, ?, ?);";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, loc1);
            pstmt.setString(2, loc2);
            pstmt.setString(3, loc3);
            pstmt.setString(4, loc4);
            int rows = pstmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("Failed to clear bay " + bayNumber + ": " + e.getMessage());
            return false;
        }
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

    /**
     * Returns the list of occupied bay numbers ordered by FIFO timestamp (oldest first).
     */
    public List<Integer> getOccupiedBays(int limit) {
        List<Integer> bays = new ArrayList<>();
        String sql = "SELECT location FROM inventory WHERE status != 'DISPATCHED' AND (location LIKE 'Bay-%' OR location LIKE 'Bay %' OR location LIKE '%Bay%') AND quantity > 0 ORDER BY id ASC"
                + (limit > 0 ? " LIMIT " + limit : "") + ";";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int bay = parseBayNumber(rs.getString("location"));
                if (bay > 0 && !bays.contains(bay)) {
                    bays.add(bay);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to get occupied bays: " + e.getMessage());
        }
        if (bays.isEmpty()) {
            java.util.Map<Integer, Product> map = getBayOccupancyMap(54);
            List<Integer> mapBays = new ArrayList<>(map.keySet());
            java.util.Collections.sort(mapBays);
            if (limit > 0 && mapBays.size() > limit) {
                return new ArrayList<>(mapBays.subList(0, limit));
            }
            return mapBays;
        }
        return bays;
    }

    /**
     * Returns total number of occupied bays.
     */
    public int getTotalStoredCount() {
        int count = 0;
        String sql = "SELECT COUNT(*) FROM inventory WHERE status != 'DISPATCHED' AND (location LIKE 'Bay-%' OR location LIKE 'Bay %' OR location LIKE '%Bay%') AND quantity > 0;";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Failed to get total stored count: " + e.getMessage());
        }
        if (count == 0) {
            count = getBayOccupancyMap(54).size();
        }
        return count;
    }

    /**
     * Returns number of vacant bays out of total available bays.
     */
    public int getVacantCount(int totalBays) {
        return Math.max(0, totalBays - getTotalStoredCount());
    }

    public int parseBayNumber(String location) {
        if (location == null || location.trim().isEmpty()) return -1;
        String loc = location.trim();
        // Match standard bay formats: "Bay-01", "Bay 01", "Bay-1", "Bay 1", "Bay01", "B-01"
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?i)^(?:Bay[\\s\\-_]*|B[\\s\\-_]+)(\\d{1,2})$")
                .matcher(loc);
        if (m.matches()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        // Match word "Bay" followed by digits anywhere e.g. "Rack Bay 12"
        java.util.regex.Matcher m2 = java.util.regex.Pattern
                .compile("(?i)\\bBay[\\s\\-_]*(\\d{1,2})\\b")
                .matcher(loc);
        if (m2.find()) {
            try {
                return Integer.parseInt(m2.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return -1;
    }
}
