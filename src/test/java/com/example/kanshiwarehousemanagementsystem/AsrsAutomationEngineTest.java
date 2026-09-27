package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.database.DatabaseManager;
import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.model.Product;
import com.example.kanshiwarehousemanagementsystem.service.modbus.FactoryIOService;
import com.example.kanshiwarehousemanagementsystem.service.scada.AsrsAutomationEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AsrsAutomationEngineTest {

    private static InventoryDao inventoryDao;
    private static FactoryIOService ioService;
    private static AsrsAutomationEngine engine;

    @BeforeAll
    static void setUp() {
        DatabaseManager.initializeDatabase();
        inventoryDao = new InventoryDao();
        ioService = new FactoryIOService();
        engine = new AsrsAutomationEngine(ioService, inventoryDao);
    }

    @AfterAll
    static void tearDown() {
        if (engine != null) {
            engine.stop();
        }
    }

    @Test
    void testEngineInitialState() {
        assertEquals(AsrsAutomationEngine.AsrsState.IDLE, engine.getCurrentState());
        assertFalse(engine.isAutoMode());
        assertEquals(0, engine.getCurrentTargetPosition());
        assertEquals(0, engine.getActiveBay());
    }

    @Test
    void testAutoModeToggle() {
        engine.setAutoMode(true);
        assertTrue(engine.isAutoMode());
        engine.setAutoMode(false);
        assertFalse(engine.isAutoMode());
    }

    @Test
    void testEmergencyStopHaltsEngine() {
        engine.setAutoMode(true);
        engine.emergencyStop();
        assertFalse(engine.isAutoMode());
        assertEquals(AsrsAutomationEngine.AsrsState.FAULT, engine.getCurrentState());
    }

    @Test
    void testBayStorageAndOccupancyMapping() {
        // Test storing in Bay 42
        int testBay = 42;
        String testSku = "TEST-BOX-42";
        inventoryDao.storeProductInBay(testSku, "Automated Test Pallet", "Packaging", 1, 99.00, testBay);

        Product stored = inventoryDao.getProductByBay(testBay);
        assertNotNull(stored);
        assertEquals(testSku, stored.getSku());

        Map<Integer, Product> occupancy = inventoryDao.getBayOccupancyMap(54);
        assertTrue(occupancy.containsKey(testBay));
        assertEquals(testSku, occupancy.get(testBay).getSku());

        // Test clearing the bay upon retrieval
        boolean cleared = inventoryDao.clearBay(testBay);
        assertTrue(cleared);

        Product afterClear = inventoryDao.getProductByBay(testBay);
        assertNull(afterClear);
    }

    @Test
    void testNextAvailableBay() {
        int nextBay = inventoryDao.findNextAvailableBay(54);
        assertTrue(nextBay >= 1 && nextBay <= 54, "Next available bay should be between 1 and 54");
    }
}
