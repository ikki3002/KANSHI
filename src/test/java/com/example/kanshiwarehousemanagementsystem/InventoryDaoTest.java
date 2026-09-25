package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.database.DatabaseManager;
import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.model.Product;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryDaoTest {

    private static InventoryDao inventoryDao;

    @BeforeAll
    public static void setUp() {
        DatabaseManager.initializeDatabase();
        inventoryDao = new InventoryDao();
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
}
