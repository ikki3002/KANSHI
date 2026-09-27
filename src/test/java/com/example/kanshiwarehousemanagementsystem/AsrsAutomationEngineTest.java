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
        inventoryDao.clearBay(42);
        ioService = new FactoryIOService();
        engine = new AsrsAutomationEngine(ioService, inventoryDao);
    }

    @AfterAll
    static void tearDown() {
        if (engine != null) {
            engine.stop();
        }
        if (inventoryDao != null) {
            inventoryDao.clearBay(42);
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
        engine.stop(); // Reset state to IDLE
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

    @Test
    void testProductTypeQueriesAndBulkUnloadValidation() {
        String prodA = "Product A (Standard Box)";
        String prodB = "Product B (Heavy Crate)";

        // Store 2 units of Product A and 1 of Product B
        inventoryDao.storeProductInBay("BOX-A", prodA, "Packaging", 1, 15.0, 10);
        inventoryDao.storeProductInBay("BOX-A", prodA, "Packaging", 1, 15.0, 11);
        inventoryDao.storeProductInBay("BOX-B", prodB, "Machinery", 1, 45.0, 20);

        // Verify count queries
        int countA = inventoryDao.getAvailableCountByProductType(prodA);
        assertTrue(countA >= 2, "Product A should have at least 2 units available");

        java.util.List<Integer> baysA = inventoryDao.getBaysForProductType(prodA);
        assertTrue(baysA.contains(10) && baysA.contains(11), "Bays for Product A must include 10 and 11");

        java.util.List<String> distinctTypes = inventoryDao.getDistinctStoredProductTypes();
        assertTrue(distinctTypes.contains(prodA), "Distinct stored types must include Product A");
        assertTrue(distinctTypes.contains(prodB), "Distinct stored types must include Product B");

        // Test Bulk Unload Validation: Requesting more than available must be rejected
        int excessiveQty = countA + 10;
        java.util.concurrent.atomic.AtomicBoolean rejectedCalled = new java.util.concurrent.atomic.AtomicBoolean(false);
        boolean acceptedExcessive = engine.requestBulkUnload(prodA, excessiveQty, (ok, msg) -> {
            if (!ok) rejectedCalled.set(true);
        });
        assertFalse(acceptedExcessive, "Requesting quantity exceeding available stock must be rejected immediately");
        assertTrue(rejectedCalled.get(), "Callback must report rejection");

        // Test requesting 0 or negative
        boolean acceptedZero = engine.requestBulkUnload(prodA, 0, null);
        assertFalse(acceptedZero, "Requesting 0 units must be rejected");

        // Clean up test bays
        inventoryDao.clearBay(10);
        inventoryDao.clearBay(11);
        inventoryDao.clearBay(20);
    }

    @Test
    void testMultipleBoxBStorageNeverCollides() {
        String prodB = "Product B (Heavy Crate)";
        int bay1 = inventoryDao.findNextAvailableBay(54);
        assertTrue(bay1 > 0, "Initial bay should be available");

        // Store first BOX-B
        boolean stored1 = inventoryDao.storeProductInBay("BOX-B", prodB, "Machinery", 1, 45.0, bay1);
        assertTrue(stored1, "Storing first BOX-B should succeed");

        // Next bay must strictly NOT be bay1
        int bay2 = inventoryDao.findNextAvailableBay(54);
        assertNotEquals(bay1, bay2, "findNextAvailableBay must not return the already occupied bay1");

        // Store second BOX-B
        boolean stored2 = inventoryDao.storeProductInBay("BOX-B", prodB, "Machinery", 1, 45.0, bay2);
        assertTrue(stored2, "Storing second BOX-B should succeed");

        // Next bay must not be bay1 or bay2
        int bay3 = inventoryDao.findNextAvailableBay(54);
        assertNotEquals(bay1, bay3, "Bay 3 must not be bay1");
        assertNotEquals(bay2, bay3, "Bay 3 must not be bay2");

        // Both products must exist in their respective bays simultaneously
        Product p1 = inventoryDao.getProductByBay(bay1);
        Product p2 = inventoryDao.getProductByBay(bay2);
        assertNotNull(p1, "Bay 1 must still contain its product");
        assertNotNull(p2, "Bay 2 must contain its product");
        assertEquals(prodB, p1.getName());
        assertEquals(prodB, p2.getName());
        assertNotEquals(p1.getSku(), p2.getSku(), "SKUs for different bays must be distinct");

        // Clean up
        inventoryDao.clearBay(bay1);
        inventoryDao.clearBay(bay2);
    }

    @Test
    void testBatchPutawayControl() {
        // Test starting batch putaway
        boolean started = engine.startBatchPutaway(3, null, null);
        assertTrue(started, "Starting batch putaway with valid count should succeed");
        assertTrue(engine.isBatchPutawayActive(), "Batch putaway flag must be true");
        assertEquals(3, engine.getTargetPutawayCount(), "Target count should be 3");
        assertEquals(0, engine.getCompletedPutawayCount(), "Initial completed count should be 0");
        assertTrue(engine.isAutoMode(), "Auto mode should be engaged for batch execution");

        // Test cancel
        engine.cancelBatchPutaway();
        assertFalse(engine.isBatchPutawayActive(), "Batch putaway must be inactive after cancel");
        assertEquals(0, engine.getTargetPutawayCount(), "Target count must be reset to 0 after cancel");
        assertFalse(engine.isAutoMode(), "Auto mode should be false after cancel");

        // Test autoMode(false) also resets batch
        engine.startBatchPutaway(5, null, null);
        assertTrue(engine.isBatchPutawayActive());
        assertEquals(5, engine.getTargetPutawayCount());
        engine.setAutoMode(false);
        assertFalse(engine.isBatchPutawayActive());
        assertEquals(0, engine.getTargetPutawayCount());

        // Test invalid batch count (0 or negative)
        assertFalse(engine.startBatchPutaway(0, null, null), "Zero count must be rejected");
        assertFalse(engine.startBatchPutaway(-5, null, null), "Negative count must be rejected");
    }

    @Test
    void testBunchDispatchValidationAndFifoOrdering() {
        // Setup 3 items in specific bays
        inventoryDao.clearBay(31);
        inventoryDao.clearBay(32);
        inventoryDao.clearBay(33);

        inventoryDao.storeProductInBay("ITEM-1", "Product One", "Cat1", 1, 10.0, 31);
        inventoryDao.storeProductInBay("ITEM-2", "Product Two", "Cat2", 1, 20.0, 32);
        inventoryDao.storeProductInBay("ITEM-3", "Product Three", "Cat3", 1, 30.0, 33);

        int totalStored = inventoryDao.getTotalStoredCount();
        assertTrue(totalStored >= 3, "Total stored count must be at least 3");

        // Verify getOccupiedBays returns occupied bays in FIFO limit
        java.util.List<Integer> bays = inventoryDao.getOccupiedBays(2);
        assertEquals(2, bays.size(), "Should return exactly 2 occupied bays when limit is 2");

        // Test rejection when requestedQty > totalStored
        java.util.concurrent.atomic.AtomicBoolean rejected = new java.util.concurrent.atomic.AtomicBoolean(false);
        boolean started = engine.requestBunchDispatch(totalStored + 999, null, (ok, msg) -> {
            if (!ok) rejected.set(true);
        });
        assertFalse(started, "Excessive dispatch quantity must be rejected");
        assertTrue(rejected.get(), "Rejection callback must be invoked");

        // Test rejection with 0 or negative
        assertFalse(engine.requestBunchDispatch(0, null, null), "Zero dispatch quantity must be rejected");
        assertFalse(engine.requestBunchDispatch(-1, null, null), "Negative dispatch quantity must be rejected");

        // Clean up
        inventoryDao.clearBay(31);
        inventoryDao.clearBay(32);
        inventoryDao.clearBay(33);
    }

    @Test
    void testScanLoopDecoupledFromSequenceWorker() throws InterruptedException {
        // Start engine scan loop
        engine.start();
        assertFalse(engine.isUnloading(), "Engine should not be unloading initially");

        // Submit retrieval request - should not deadlock or queue indefinitely behind scan loop
        engine.requestRetrieval(99); // invalid bay (> 54), handled gracefully
        assertFalse(engine.isUnloading());

        // Stop engine cleanly
        engine.stop();
        assertEquals(AsrsAutomationEngine.AsrsState.IDLE, engine.getCurrentState());
    }
}
