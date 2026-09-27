package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.database.DatabaseManager;
import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.model.Product;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryDaoTest {

    private static InventoryDao inventoryDao;

    @org.junit.jupiter.api.BeforeAll
    public static void setUp() {
        DatabaseManager.initializeDatabase();
        DatabaseManager.purgeWarehouseData();
        inventoryDao = new InventoryDao();

        // Seed test fixture inventory items
        inventoryDao.addProduct(new Product(0, "BOX-SML-101", "Standard Cardboard Box (Small)", "Packaging", 150, 12.50, "Bay-01"));
        inventoryDao.addProduct(new Product(0, "BOX-MED-102", "Heavy Duty Corrugated Box (Med)", "Packaging", 85, 18.00, "Bay-05"));
        inventoryDao.addProduct(new Product(0, "PAL-EUR-201", "Euro Pallet EPAL-1 Heavy Duty", "Material Handling", 40, 35.00, "Bay-12"));
        inventoryDao.addProduct(new Product(0, "SEN-OPT-301", "Optical Retroreflective Sensor M18", "Automation Parts", 24, 120.00, "Bay-21"));
        inventoryDao.addProduct(new Product(0, "CON-BLT-401", "Modular Conveyor Belt Segment 2m", "Spares", 12, 245.00, "Bay-33"));
    }

    @org.junit.jupiter.api.AfterAll
    public static void tearDown() {
        DatabaseManager.purgeWarehouseData();
    }

    @Test
    public void testGetAllProducts() {
        List<Product> products = inventoryDao.getAllProducts();
        assertNotNull(products);
        assertFalse(products.isEmpty(), "Inventory table should contain seeded products");
    }

    @Test
    public void testStockCountAndValuation() {
        int totalUnits = inventoryDao.getTotalStockCount();
        assertTrue(totalUnits >= 300, "Total stock should be at least 300 units");

        double totalValuation = inventoryDao.getTotalValuation();
        assertTrue(totalValuation > 5000.0, "Total valuation should exceed $5,000.00");
    }

    @Test
    public void testUpdateStockDelta() {
        int initialCount = inventoryDao.getTotalStockCount();
        boolean updated = inventoryDao.updateStockDelta("BOX-SML-101", 5);
        assertTrue(updated, "Updating stock delta should succeed");

        int updatedCount = inventoryDao.getTotalStockCount();
        assertEquals(initialCount + 5, updatedCount, "Stock should increment by exactly 5 units");

        // Revert delta
        inventoryDao.updateStockDelta("BOX-SML-101", -5);
    }

    @Test
    public void testDistinctProductAndLowStockCount() {
        int distinctSkus = inventoryDao.getDistinctProductCount();
        assertTrue(distinctSkus >= 5, "There should be at least 5 distinct product SKUs in seed data");

        int lowStockCount = inventoryDao.getLowStockCount(15);
        assertTrue(lowStockCount >= 1, "There should be at least one product with <= 15 units (e.g. CON-BLT-401 has 12)");

        int currentQty = inventoryDao.getStockQuantity("BOX-SML-101");
        assertTrue(currentQty > 0, "BOX-SML-101 should have positive quantity");
    }

    @Test
    public void testProductCrudLifecycle() {
        String testSku = "TEST-SKU-999";
        Product testProduct = new Product(0, testSku, "Test Optical Sensor", "Automation Parts", 45, 120.50, "Bay-T1-A");

        // 1. Uniqueness check before insert
        assertTrue(inventoryDao.isSkuUnique(testSku, 0), "SKU should be unique before insertion");

        // 2. Insert
        boolean added = inventoryDao.addProduct(testProduct);
        assertTrue(added, "Adding new product should succeed");

        // 3. Find by SKU
        Product fetched = inventoryDao.findProductBySku(testSku);
        assertNotNull(fetched, "Product should be retrievable by SKU");
        assertEquals("Test Optical Sensor", fetched.getName());
        assertEquals(45, fetched.getQuantity());
        assertEquals(120.50, fetched.getUnitPrice(), 0.001);

        // 4. Uniqueness check after insert
        assertFalse(inventoryDao.isSkuUnique(testSku, 0), "SKU should no longer be unique for a new record");
        assertTrue(inventoryDao.isSkuUnique(testSku, fetched.getId()), "SKU should be unique when excluding its own ID");

        // 5. Update
        fetched.setName("Updated Optical Sensor V2");
        fetched.setQuantity(60);
        fetched.setUnitPrice(135.00);
        boolean updated = inventoryDao.updateProduct(fetched);
        assertTrue(updated, "Updating product should succeed");

        Product afterUpdate = inventoryDao.findProductBySku(testSku);
        assertNotNull(afterUpdate);
        assertEquals("Updated Optical Sensor V2", afterUpdate.getName());
        assertEquals(60, afterUpdate.getQuantity());
        assertEquals(135.00, afterUpdate.getUnitPrice(), 0.001);

        // 6. Delete
        boolean deleted = inventoryDao.deleteProduct(fetched.getId());
        assertTrue(deleted, "Deleting product should succeed");

        Product afterDelete = inventoryDao.findProductBySku(testSku);
        assertNull(afterDelete, "Product should no longer exist after deletion");
    }

    @Test
    public void testExportInventoryToJson() throws IOException {
        File tempJson = File.createTempFile("inventory_test_export_", ".json");
        tempJson.deleteOnExit();

        boolean exported = inventoryDao.exportInventoryToJson(tempJson);
        assertTrue(exported, "Export to JSON should return true");
        assertTrue(tempJson.exists(), "Export file should exist on disk");
        assertTrue(tempJson.length() > 50, "Export file should contain non-trivial JSON payload");

        String content = Files.readString(tempJson.toPath());
        assertTrue(content.contains("BOX-SML-101"), "JSON export should contain seeded SKU");
        assertTrue(content.contains("sku"), "JSON export should contain JSON key 'sku'");
    }
}

