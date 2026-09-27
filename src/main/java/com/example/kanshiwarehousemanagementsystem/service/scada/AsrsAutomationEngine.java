package com.example.kanshiwarehousemanagementsystem.service.scada;

import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag;
import com.example.kanshiwarehousemanagementsystem.model.Product;
import com.example.kanshiwarehousemanagementsystem.service.modbus.FactoryIOService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Embedded Soft-PLC Automation Controller for the Factory I/O Automated Warehouse (ASRS).
 * Implements a robust, deterministic state machine strictly coordinating:
 * 1. Sequential Infeed Roller handling (Entry roller runs ONLY when crane is verified ready at Station 55).
 * 2. Optical / Vision Inspection product identification.
 * 3. 2-Axis Stacker Crane positioning (Target Position 1..55).
 * 4. Telescopic Fork actuation (Forks Left, Lift UP/DOWN, Center Retraction).
 * 5. Coordinated Outbound Unload / Dispatch retrieval (with strict requested vs available validation).
 * 6. Real-time synchronization with the SQLite Inventory Database.
 */
public class AsrsAutomationEngine {

    // Modbus Discrete Input Addresses (Factory I/O Automated Warehouse driver)
    public static final int IN_AT_ENTRY = 0;
    public static final int IN_AT_LOAD = 1;
    public static final int IN_AT_LEFT = 2;
    public static final int IN_AT_MIDDLE = 3;
    public static final int IN_AT_RIGHT = 4;
    public static final int IN_AT_UNLOAD = 5;
    public static final int IN_AT_EXIT = 6;
    public static final int IN_MOVING_X = 7;
    public static final int IN_MOVING_Z = 8;
    public static final int IN_START = 9;
    public static final int IN_RESET = 10;
    public static final int IN_STOP = 11;
    public static final int IN_ESTOP = 12;
    public static final int IN_AUTO = 13;
    public static final int IN_RUNNING = 14;

    // Modbus Coil Addresses
    public static final int COIL_ENTRY_CONVEYOR = 0;
    public static final int COIL_LOAD_CONVEYOR = 1;
    public static final int COIL_FORKS_LEFT = 2;
    public static final int COIL_FORKS_RIGHT = 3;
    public static final int COIL_LIFT = 4;
    public static final int COIL_UNLOAD_CONVEYOR = 5;
    public static final int COIL_EXIT_CONVEYOR = 6;
    public static final int COIL_LIGHT_START = 7;
    public static final int COIL_LIGHT_RESET = 8;
    public static final int COIL_LIGHT_STOP = 9;

    // Modbus Holding Register
    public static final int REG_TARGET_POSITION = 0;
    public static final int STATION_INFEED_LOAD = 55;

    public enum AsrsState {
        IDLE("IDLE - System Ready"),
        CHECK_CRANE_READY("Checking Crane at Ready/Pickup Position"),
        PRODUCT_DETECTED("Product Detected at Entry / Vision Inspection"),
        MOVE_PRODUCT_TO_PICKUP("Moving Product to Crane Pickup Station"),
        CRANE_PICKUP("Crane Picking Up Product"),
        FIND_EMPTY_POSITION("Allocating Available Storage Position"),
        MOVE_TO_STORAGE("Crane Moving to Storage Bay"),
        PLACE_PRODUCT("Crane Placing Product into Storage Shelf"),
        UPDATE_DATABASE("Updating Inventory Ledger"),
        RETURN_TO_READY("Crane Returning to Ready Position (Bay 55)"),

        UNLOAD_REQUEST("Unload / Dispatch Request Received"),
        CHECK_INVENTORY("Verifying Inventory & Availability"),
        FIND_PRODUCT("Locating Product Storage Position"),
        MOVE_TO_DISPATCH("Crane Transporting Product to Dispatch Area"),
        RELEASE_PRODUCT("Releasing Product onto Outfeed / Exit Rollers"),

        FAULT("FAULT / EMERGENCY STOP");

        private final String description;
        AsrsState(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    public static class ProductProfile {
        public final String skuPrefix;
        public final String name;
        public final String category;
        public final double unitPrice;

        public ProductProfile(String skuPrefix, String name, String category, double unitPrice) {
            this.skuPrefix = skuPrefix;
            this.name = name;
            this.category = category;
            this.unitPrice = unitPrice;
        }
    }

    private static final ProductProfile[] CATALOG_PROFILES = new ProductProfile[]{
            new ProductProfile("BOX-A", "Product A (Standard Box)", "Packaging", 15.00),
            new ProductProfile("BOX-B", "Product B (Heavy Crate)", "Machinery", 45.00),
            new ProductProfile("PAL-C", "Product C (Palletized Cargo)", "Logistics", 80.00)
    };

    private final FactoryIOService ioService;
    private final InventoryDao inventoryDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Asrs-SoftPLC-Thread");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean autoMode = new AtomicBoolean(false);
    private final AtomicBoolean isUnloading = new AtomicBoolean(false);
    private volatile AsrsState currentState = AsrsState.IDLE;
    private final AtomicInteger currentTargetPosition = new AtomicInteger(0);
    private final AtomicInteger activeBay = new AtomicInteger(0);
    private final AtomicInteger profileIndex = new AtomicInteger(0);

    // Callbacks for UI updates
    private BiConsumer<AsrsState, String> stateListener;
    private Runnable inventoryRefreshCallback;

    public AsrsAutomationEngine(FactoryIOService ioService, InventoryDao inventoryDao) {
        this.ioService = ioService;
        this.inventoryDao = inventoryDao;
    }

    public void setStateListener(BiConsumer<AsrsState, String> listener) {
        this.stateListener = listener;
    }

    public void setInventoryRefreshCallback(Runnable callback) {
        this.inventoryRefreshCallback = callback;
    }

    public synchronized void start() {
        if (running.get()) return;
        running.set(true);
        executor.submit(this::runExecutionLoop);
    }

    public synchronized void stop() {
        running.set(false);
        autoMode.set(false);
        isUnloading.set(false);
        stopAllConveyorsSafe();
        setState(AsrsState.IDLE, "Automation loop stopped.");
    }

    public void setAutoMode(boolean enabled) {
        this.autoMode.set(enabled);
        if (!enabled) {
            stopAllConveyorsSafe();
        }
        notifyStateChange("Auto-Cycle Mode " + (enabled ? "ENABLED" : "PAUSED"));
    }

    public boolean isAutoMode() {
        return autoMode.get();
    }

    public boolean isUnloading() {
        return isUnloading.get();
    }

    public AsrsState getCurrentState() {
        return currentState;
    }

    public int getCurrentTargetPosition() {
        return currentTargetPosition.get();
    }

    public int getActiveBay() {
        return activeBay.get();
    }

    /**
     * Checks if the crane is confirmed at its required/ready pickup position (Station 55),
     * stationary (X/Z not moving), and forks centered.
     */
    public boolean isCraneReadyAtPickup() {
        if (!ioService.isConnected()) return false;
        try {
            boolean movingX = ioService.readTag(createInputTag(IN_MOVING_X));
            boolean movingZ = ioService.readTag(createInputTag(IN_MOVING_Z));
            boolean forksMiddle = ioService.readTag(createInputTag(IN_AT_MIDDLE));
            return !movingX && !movingZ && forksMiddle && (currentTargetPosition.get() == STATION_INFEED_LOAD);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Triggers an automated pallet retrieval sequence for a specific bay (1..54).
     */
    public void requestRetrieval(int bayNumber) {
        if (bayNumber < 1 || bayNumber > 54) return;
        if (isUnloading.get() || currentState != AsrsState.IDLE) {
            notifyStateChange("System is currently busy (" + currentState.getDescription() + "). Retrieval deferred.");
            return;
        }

        Product p = inventoryDao.getProductByBay(bayNumber);
        String prodName = (p != null && p.getName() != null) ? p.getName() : "Pallet #" + bayNumber;

        isUnloading.set(true);
        executor.submit(() -> {
            try {
                executeSingleRetrieval(bayNumber, prodName);
            } finally {
                isUnloading.set(false);
            }
        });
    }

    /**
     * Initiates bulk unloading / dispatch of products by product type and requested quantity.
     * Aborts immediately if requested quantity > available quantity.
     */
    public synchronized boolean requestBulkUnload(String productType, int requestedQuantity, BiConsumer<Boolean, String> resultCallback) {
        if (requestedQuantity <= 0) {
            String msg = "Requested quantity must be at least 1 unit.";
            if (resultCallback != null) resultCallback.accept(false, msg);
            return false;
        }

        int available = inventoryDao.getAvailableCountByProductType(productType);
        if (requestedQuantity > available) {
            String msg = String.format("Unload Rejected: Requested %d unit(s) of '%s', but only %d available in warehouse.",
                    requestedQuantity, productType, available);
            setState(AsrsState.UNLOAD_REQUEST, msg);
            if (resultCallback != null) resultCallback.accept(false, msg);
            return false;
        }

        if (isUnloading.get() || currentState != AsrsState.IDLE) {
            String msg = "System is currently busy (" + currentState.getDescription() + "). Please wait until IDLE.";
            if (resultCallback != null) resultCallback.accept(false, msg);
            return false;
        }

        isUnloading.set(true);
        if (resultCallback != null) resultCallback.accept(true, "Unload operation approved and starting.");

        executor.submit(() -> {
            try {
                executeBulkUnloadSequence(productType, requestedQuantity);
            } finally {
                isUnloading.set(false);
            }
        });
        return true;
    }

    /**
     * Immediate emergency stop, halting all conveyor motors and crane drives.
     */
    public void emergencyStop() {
        autoMode.set(false);
        isUnloading.set(false);
        setState(AsrsState.FAULT, "EMERGENCY STOP TRIPPED");
        stopAllConveyorsSafe();
    }

    private void stopAllConveyorsSafe() {
        try {
            if (ioService.isConnected()) {
                setCoil(COIL_ENTRY_CONVEYOR, false);
                setCoil(COIL_LOAD_CONVEYOR, false);
                setCoil(COIL_UNLOAD_CONVEYOR, false);
                setCoil(COIL_EXIT_CONVEYOR, false);
                setCoil(COIL_FORKS_LEFT, false);
                setCoil(COIL_FORKS_RIGHT, false);
                setCoil(COIL_LIFT, false);
            }
        } catch (Exception ignored) {}
    }

    /**
     * Main Soft-PLC deterministic scan cycle.
     */
    private void runExecutionLoop() {
        while (running.get()) {
            try {
                if (ioService.isConnected() && autoMode.get() && currentState == AsrsState.IDLE && !isUnloading.get()) {
                    executePutawaySequence();
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Executes the strict sequential Putaway Sequence:
     * CHECK_CRANE_READY -> PRODUCT_DETECTED -> MOVE_PRODUCT_TO_PICKUP ->
     * CRANE_PICKUP -> FIND_EMPTY_POSITION -> MOVE_TO_STORAGE ->
     * PLACE_PRODUCT -> UPDATE_DATABASE -> RETURN_TO_READY -> IDLE
     */
    private void executePutawaySequence() {
        try {
            // Step 1: CHECK_CRANE_READY
            setState(AsrsState.CHECK_CRANE_READY, "Verifying crane is at Ready/Pickup Position (Station 55)...");

            if (currentTargetPosition.get() != STATION_INFEED_LOAD) {
                writeTargetPosition(STATION_INFEED_LOAD);
            }
            waitForCraneArrival(14000);

            // Ensure forks are centered and lift is lowered
            setCoil(COIL_FORKS_LEFT, false);
            setCoil(COIL_FORKS_RIGHT, false);
            setCoil(COIL_LIFT, false);
            waitForInput(IN_AT_MIDDLE, true, 3000);

            if (!isCraneReadyAtPickup()) {
                Thread.sleep(300);
                return;
            }

            // Verify rack capacity
            int targetBay = inventoryDao.findNextAvailableBay(54);
            if (targetBay <= 0) {
                notifyStateChange("Storage Matrix is 100% FULL (54/54 Bays Occupied). Infeed roller paused.");
                setState(AsrsState.IDLE, "Warehouse Full (54/54 Bays Occupied)");
                return;
            }

            // Step 2: Crane is ready -> Start the first roller ONLY
            setState(AsrsState.CHECK_CRANE_READY, "Crane Ready at Station 55. Starting Entry Roller...");
            setCoil(COIL_ENTRY_CONVEYOR, true);

            // Step 3: Wait for product to enter loading area / vision sensor
            boolean arrivedAtEntry = waitForInput(IN_AT_ENTRY, true, 8000);
            if (!arrivedAtEntry) {
                // No product reached entry within window; stop roller and remain IDLE
                setCoil(COIL_ENTRY_CONVEYOR, false);
                setState(AsrsState.IDLE, "System Ready - Waiting for Infeed Pallet");
                return;
            }

            // PRODUCT_DETECTED: Stop first roller immediately to prevent jamming next item
            setCoil(COIL_ENTRY_CONVEYOR, false);
            setState(AsrsState.PRODUCT_DETECTED, "Product detected at Vision Sensor. Inspecting product type...");
            ProductProfile profile = inspectProductType();

            // Step 4: MOVE_PRODUCT_TO_PICKUP: Run load roller until product is at crane pickup bed
            setState(AsrsState.MOVE_PRODUCT_TO_PICKUP, "Transferring " + profile.name + " to Crane Pickup Bed...");
            setCoil(COIL_LOAD_CONVEYOR, true);
            waitForInput(IN_AT_LOAD, true, 8000);
            setCoil(COIL_LOAD_CONVEYOR, false); // Stop load roller immediately

            // Step 5: CRANE_PICKUP
            setState(AsrsState.CRANE_PICKUP, "Crane picking up " + profile.name + " from Infeed Bed");
            setCoil(COIL_FORKS_LEFT, true);
            waitForInput(IN_AT_LEFT, true, 4000);
            setCoil(COIL_LIFT, true);
            Thread.sleep(600);
            setCoil(COIL_FORKS_LEFT, false);
            waitForInput(IN_AT_MIDDLE, true, 4000);

            // Step 6: FIND_EMPTY_POSITION
            setState(AsrsState.FIND_EMPTY_POSITION, "Allocating next available bay in 54-bay matrix...");
            targetBay = inventoryDao.findNextAvailableBay(54);
            if (targetBay <= 0) {
                targetBay = 1; // Fallback
            }
            activeBay.set(targetBay);

            // Step 7: MOVE_TO_STORAGE
            setState(AsrsState.MOVE_TO_STORAGE, "Transporting " + profile.name + " to Storage Bay " + targetBay);
            writeTargetPosition(targetBay);
            waitForCraneArrival(14000);

            // Step 8: PLACE_PRODUCT
            setState(AsrsState.PLACE_PRODUCT, "Placing " + profile.name + " into Bay " + targetBay);
            setCoil(COIL_FORKS_LEFT, true);
            waitForInput(IN_AT_LEFT, true, 4000);
            setCoil(COIL_LIFT, false);
            Thread.sleep(600);
            setCoil(COIL_FORKS_LEFT, false);
            waitForInput(IN_AT_MIDDLE, true, 4000);

            // Step 9: UPDATE_DATABASE
            setState(AsrsState.UPDATE_DATABASE, "Updating Inventory Ledger: Bay " + targetBay + " = " + profile.name);
            inventoryDao.storeProductInBay(profile.skuPrefix, profile.name, profile.category, 1, profile.unitPrice, targetBay);
            if (inventoryRefreshCallback != null) {
                inventoryRefreshCallback.run();
            }

            // Step 10: RETURN_TO_READY
            setState(AsrsState.RETURN_TO_READY, "Crane returning to Ready Position (Bay 55)...");
            writeTargetPosition(STATION_INFEED_LOAD);
            waitForCraneArrival(14000);
            activeBay.set(0);

            // Step 11: IDLE
            setState(AsrsState.IDLE, "Putaway Complete: Bay " + targetBay + " stored. Crane ready at Station 55 for next cycle.");

        } catch (Exception e) {
            stopAllConveyorsSafe();
            setState(AsrsState.FAULT, "Putaway cycle error: " + e.getMessage());
        }
    }

    /**
     * Executes the strict sequential Unload / Retrieval sequence:
     * UNLOAD_REQUEST -> CHECK_INVENTORY -> FIND_PRODUCT -> CRANE_PICKUP ->
     * MOVE_TO_DISPATCH -> RELEASE_PRODUCT -> UPDATE_DATABASE -> RETURN_TO_READY -> IDLE
     */
    private void executeBulkUnloadSequence(String productType, int requestedQuantity) {
        try {
            setState(AsrsState.UNLOAD_REQUEST, "Unload Request: " + requestedQuantity + " unit(s) of " + productType);

            // Step 1: CHECK_INVENTORY
            setState(AsrsState.CHECK_INVENTORY, "Verifying inventory for " + productType + "...");
            List<Integer> bays = inventoryDao.getBaysForProductType(productType);
            if (bays.size() < requestedQuantity) {
                setState(AsrsState.IDLE, "Unload Aborted: Insufficient stock found during verification.");
                return;
            }

            // Unload items one by one sequentially
            for (int i = 0; i < requestedQuantity; i++) {
                if (!running.get()) break;

                int bayNumber = bays.get(i);
                activeBay.set(bayNumber);

                // Step 2: FIND_PRODUCT
                setState(AsrsState.FIND_PRODUCT, String.format("Locating %s in Bay %d (%d of %d)", productType, bayNumber, i + 1, requestedQuantity));
                writeTargetPosition(bayNumber);
                waitForCraneArrival(14000);

                // Step 3: CRANE_PICKUP
                setState(AsrsState.CRANE_PICKUP, "Picking pallet from Bay " + bayNumber);
                setCoil(COIL_FORKS_LEFT, true);
                waitForInput(IN_AT_LEFT, true, 4000);
                setCoil(COIL_LIFT, true);
                Thread.sleep(600);
                setCoil(COIL_FORKS_LEFT, false);
                waitForInput(IN_AT_MIDDLE, true, 4000);

                // Step 4: MOVE_TO_DISPATCH
                setState(AsrsState.MOVE_TO_DISPATCH, "Transporting " + productType + " to Dispatch Area (Position 55)");
                writeTargetPosition(STATION_INFEED_LOAD);
                waitForCraneArrival(14000);

                // Step 5: RELEASE_PRODUCT
                setState(AsrsState.RELEASE_PRODUCT, "Releasing " + productType + " onto Outfeed Roller Bed");
                setCoil(COIL_FORKS_LEFT, true);
                waitForInput(IN_AT_LEFT, true, 4000);
                setCoil(COIL_LIFT, false);
                Thread.sleep(600);
                setCoil(COIL_FORKS_LEFT, false);
                waitForInput(IN_AT_MIDDLE, true, 4000);

                // Run only the outfeed rollers during release
                setCoil(COIL_UNLOAD_CONVEYOR, true);
                setCoil(COIL_EXIT_CONVEYOR, true);
                waitForInput(IN_AT_EXIT, true, 8000);
                Thread.sleep(1200);
                setCoil(COIL_UNLOAD_CONVEYOR, false);
                setCoil(COIL_EXIT_CONVEYOR, false);

                // Step 6: UPDATE_DATABASE
                setState(AsrsState.UPDATE_DATABASE, "Updating Inventory: Bay " + bayNumber + " discharged and freed");
                inventoryDao.clearBay(bayNumber);
                if (inventoryRefreshCallback != null) {
                    inventoryRefreshCallback.run();
                }

                // Step 7: RETURN_TO_READY
                setState(AsrsState.RETURN_TO_READY, "Crane returned to Ready Position (Bay 55)");
                writeTargetPosition(STATION_INFEED_LOAD);
                waitForCraneArrival(14000);
                activeBay.set(0);
            }

            setState(AsrsState.IDLE, String.format("Unload Completed: Dispatched %d unit(s) of %s successfully.", requestedQuantity, productType));

        } catch (Exception e) {
            stopAllConveyorsSafe();
            setState(AsrsState.FAULT, "Unload sequence error: " + e.getMessage());
        }
    }

    private void executeSingleRetrieval(int bayNumber, String prodName) {
        try {
            activeBay.set(bayNumber);

            // Step 1: FIND_PRODUCT
            setState(AsrsState.FIND_PRODUCT, "Locating " + prodName + " in Bay " + bayNumber);
            writeTargetPosition(bayNumber);
            waitForCraneArrival(14000);

            // Step 2: CRANE_PICKUP
            setState(AsrsState.CRANE_PICKUP, "Picking pallet from Bay " + bayNumber);
            setCoil(COIL_FORKS_LEFT, true);
            waitForInput(IN_AT_LEFT, true, 4000);
            setCoil(COIL_LIFT, true);
            Thread.sleep(600);
            setCoil(COIL_FORKS_LEFT, false);
            waitForInput(IN_AT_MIDDLE, true, 4000);

            // Step 3: MOVE_TO_DISPATCH
            setState(AsrsState.MOVE_TO_DISPATCH, "Transporting " + prodName + " to Dispatch Area (Position 55)");
            writeTargetPosition(STATION_INFEED_LOAD);
            waitForCraneArrival(14000);

            // Step 4: RELEASE_PRODUCT
            setState(AsrsState.RELEASE_PRODUCT, "Releasing " + prodName + " onto Outfeed Roller Bed");
            setCoil(COIL_FORKS_LEFT, true);
            waitForInput(IN_AT_LEFT, true, 4000);
            setCoil(COIL_LIFT, false);
            Thread.sleep(600);
            setCoil(COIL_FORKS_LEFT, false);
            waitForInput(IN_AT_MIDDLE, true, 4000);

            // Outfeed rollers
            setCoil(COIL_UNLOAD_CONVEYOR, true);
            setCoil(COIL_EXIT_CONVEYOR, true);
            waitForInput(IN_AT_EXIT, true, 8000);
            Thread.sleep(1200);
            setCoil(COIL_UNLOAD_CONVEYOR, false);
            setCoil(COIL_EXIT_CONVEYOR, false);

            // Step 5: UPDATE_DATABASE
            setState(AsrsState.UPDATE_DATABASE, "Updating Inventory: Bay " + bayNumber + " cleared");
            inventoryDao.clearBay(bayNumber);
            if (inventoryRefreshCallback != null) {
                inventoryRefreshCallback.run();
            }

            // Step 6: RETURN_TO_READY
            setState(AsrsState.RETURN_TO_READY, "Crane returned to Ready Position (Bay 55)");
            writeTargetPosition(STATION_INFEED_LOAD);
            waitForCraneArrival(14000);
            activeBay.set(0);

            setState(AsrsState.IDLE, "Retrieval Complete: Bay " + bayNumber + " discharged and freed.");

        } catch (Exception e) {
            stopAllConveyorsSafe();
            setState(AsrsState.FAULT, "Retrieval error: " + e.getMessage());
        }
    }

    private ProductProfile inspectProductType() {
        try {
            int visionCode = ioService.readRegister(1);
            if (visionCode >= 1 && visionCode <= CATALOG_PROFILES.length) {
                return CATALOG_PROFILES[visionCode - 1];
            }
        } catch (Exception ignored) {}
        int idx = Math.abs(profileIndex.getAndIncrement() % CATALOG_PROFILES.length);
        return CATALOG_PROFILES[idx];
    }

    private void writeTargetPosition(int pos) throws IOException {
        currentTargetPosition.set(pos);
        ioService.writeRegister(REG_TARGET_POSITION, pos);
    }

    private void setCoil(int address, boolean value) throws IOException {
        ModbusTag tag = new ModbusTag("coil_" + address, "Coil " + address, address, ModbusTag.TagType.COIL);
        ioService.writeTag(tag, value);
    }

    private boolean waitForInput(int address, boolean expected, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        ModbusTag tag = createInputTag(address);
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!running.get()) return false;
            boolean val = ioService.readTag(tag);
            if (val == expected) {
                return true;
            }
            Thread.sleep(60);
        }
        return false;
    }

    private void waitForCraneArrival(long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        Thread.sleep(400); // Startup window for motors to assert Moving bits
        ModbusTag tagX = createInputTag(IN_MOVING_X);
        ModbusTag tagZ = createInputTag(IN_MOVING_Z);

        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!running.get()) return;
            boolean movingX = ioService.readTag(tagX);
            boolean movingZ = ioService.readTag(tagZ);
            if (!movingX && !movingZ) {
                Thread.sleep(250); // Settle time
                return;
            }
            Thread.sleep(100);
        }
    }

    private ModbusTag createInputTag(int address) {
        return new ModbusTag("input_" + address, "Input " + address, address, ModbusTag.TagType.DISCRETE_INPUT);
    }

    private void setState(AsrsState state, String message) {
        this.currentState = state;
        notifyStateChange(message);
    }

    private void notifyStateChange(String message) {
        if (stateListener != null) {
            stateListener.accept(currentState, message);
        }
    }
}
