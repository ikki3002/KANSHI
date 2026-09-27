package com.example.kanshiwarehousemanagementsystem.service.scada;

import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.model.Product;
import com.example.kanshiwarehousemanagementsystem.service.modbus.FactoryIOService;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Embedded Soft-PLC Automation Controller for the Factory I/O Automated Warehouse (ASRS).
 * Implements a deterministic, thread-safe state machine managing:
 * 1. Infeed Entry & Load Conveyors.
 * 2. 2-Axis Stacker Crane positioning (Target Position 1..55).
 * 3. Telescopic Fork actuation (Forks Left, Lift UP/DOWN, Center Retraction).
 * 4. Outfeed Unload & Exit Conveyors.
 * 5. Automatic Synchronization with SQLite Inventory Database.
 */
public class AsrsAutomationEngine {

    // Modbus Discrete Input Addresses (from Factory I/O Automated Warehouse driver)
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
        INFEED_ACTIVE("Infeed Active - Pallet Moving to Load Station"),
        CRANE_MOVING_TO_LOAD("Crane Moving to Load Pick-up Station (Bay 55)"),
        FORKS_EXTENDING_PICK("Forks Extending to Infeed Pallet"),
        LIFTING_PALLET("Lifting Pallet off Rollers"),
        FORKS_RETRACTING_PICK("Forks Retracting with Pallet"),
        CRANE_TRAVELING_TO_BAY("Crane Traveling to Storage Bay"),
        FORKS_EXTENDING_PLACE("Forks Extending into Storage Shelf"),
        LOWERING_PALLET("Lowering Pallet onto Rack Supports"),
        FORKS_RETRACTING_PLACE("Forks Retracting - Slot Stored"),
        RETRIEVAL_CRANE_TRAVELING("Retrieval: Crane Traveling to Target Bay"),
        RETRIEVAL_FORKS_PICK("Retrieval: Picking Pallet from Rack"),
        RETRIEVAL_CRANE_TO_UNLOAD("Retrieval: Crane Traveling to Outfeed Station"),
        RETRIEVAL_FORKS_PLACE("Retrieval: Depositing Pallet onto Unload Conveyor"),
        OUTFEED_DISCHARGING("Outfeed Discharging Pallet to Exit"),
        FAULT("FAULT / EMERGENCY STOP");

        private final String description;
        AsrsState(String description) { this.description = description; }
        public String getDescription() { return description; }
    }

    private final FactoryIOService ioService;
    private final InventoryDao inventoryDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Asrs-SoftPLC-Thread");
        t.setDaemon(true);
        return t;
    });

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean autoMode = new AtomicBoolean(false);
    private volatile AsrsState currentState = AsrsState.IDLE;
    private final AtomicInteger currentTargetPosition = new AtomicInteger(0);
    private final AtomicInteger activeBay = new AtomicInteger(0);

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
        setState(AsrsState.IDLE, "Automation loop stopped.");
    }

    public void setAutoMode(boolean enabled) {
        this.autoMode.set(enabled);
        try {
            if (ioService.isConnected()) {
                ioService.writeRegister(REG_TARGET_POSITION, 0);
            }
        } catch (IOException ignored) {}
        notifyStateChange("Auto-Cycle Mode " + (enabled ? "ENABLED" : "PAUSED"));
    }

    public boolean isAutoMode() {
        return autoMode.get();
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
     * Triggers an automated pallet retrieval sequence for a given rack bay (1..54).
     */
    public void requestRetrieval(int bayNumber) {
        if (bayNumber < 1 || bayNumber > 54) return;
        executor.submit(() -> executeRetrievalSequence(bayNumber));
    }

    /**
     * Triggers an immediate emergency stop, halting all conveyor motors and crane drives.
     */
    public void emergencyStop() {
        autoMode.set(false);
        setState(AsrsState.FAULT, "EMERGENCY STOP TRIPPED");
        try {
            if (ioService.isConnected()) {
                ioService.writeRegister(REG_TARGET_POSITION, 0);
                for (int coil = 0; coil <= 6; coil++) {
                    ioService.writeTag(new com.example.kanshiwarehousemanagementsystem.model.ModbusTag("coil_" + coil, "Coil " + coil, coil, com.example.kanshiwarehousemanagementsystem.model.ModbusTag.TagType.COIL), false);
                }
            }
        } catch (IOException ignored) {}
    }

    /**
     * Main Soft-PLC deterministic scan cycle (50ms).
     */
    private void runExecutionLoop() {
        while (running.get()) {
            try {
                if (ioService.isConnected() && autoMode.get() && currentState == AsrsState.IDLE) {
                    boolean atEntry = ioService.readTag(createInputTag(IN_AT_ENTRY));
                    boolean atLoad = ioService.readTag(createInputTag(IN_AT_LOAD));

                    if (atEntry || atLoad) {
                        executePutawaySequence();
                    }
                }
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Executes the complete Inbound Putaway Sequence:
     * Infeed -> Crane Load Station 55 -> Pick -> Travel to Bay -> Place -> Update DB.
     */
    private void executePutawaySequence() {
        try {
            int targetBay = inventoryDao.findNextAvailableBay(54);
            if (targetBay <= 0) {
                notifyStateChange("Storage Matrix is 100% FULL (54/54 Bays Occupied). Infeed paused.");
                return;
            }
            activeBay.set(targetBay);

            // Step 1: Run Infeed Conveyors until pallet reaches Load position
            setState(AsrsState.INFEED_ACTIVE, "Infeed Conveyors Running -> Moving to Load Station");
            setCoil(COIL_ENTRY_CONVEYOR, true);
            setCoil(COIL_LOAD_CONVEYOR, true);

            waitForInput(IN_AT_LOAD, true, 8000);
            setCoil(COIL_LOAD_CONVEYOR, false);
            setCoil(COIL_ENTRY_CONVEYOR, false);

            // Step 2: Send Crane to Infeed Load Station (Position 55)
            setState(AsrsState.CRANE_MOVING_TO_LOAD, "Stacker Crane traveling to Infeed Station (Position 55)");
            writeTargetPosition(STATION_INFEED_LOAD);
            waitForCraneArrival(12000);

            // Step 3: Extend telescopic forks to pick pallet
            setState(AsrsState.FORKS_EXTENDING_PICK, "Extending Telescopic Forks Left into Infeed Pallet");
            setCoil(COIL_FORKS_LEFT, true);
            waitForInput(IN_AT_LEFT, true, 4000);

            // Step 4: Micro-lift forks to lift pallet off rollers
            setState(AsrsState.LIFTING_PALLET, "Micro-elevation: Lifting Pallet off Infeed Bed");
            setCoil(COIL_LIFT, true);
            Thread.sleep(600);

            // Step 5: Retract forks back to crane platform
            setState(AsrsState.FORKS_RETRACTING_PICK, "Retracting Forks with Pallet to Crane Center");
            setCoil(COIL_FORKS_LEFT, false);
            waitForInput(IN_AT_MIDDLE, true, 4000);

            // Step 6: Crane travels to target rack bay
            setState(AsrsState.CRANE_TRAVELING_TO_BAY, "Crane traveling to High-Bay Rack: Bay " + targetBay);
            writeTargetPosition(targetBay);
            waitForCraneArrival(14000);

            // Step 7: Extend forks into rack shelf
            setState(AsrsState.FORKS_EXTENDING_PLACE, "Extending Forks into Rack Shelf: Bay " + targetBay);
            setCoil(COIL_FORKS_LEFT, true);
            waitForInput(IN_AT_LEFT, true, 4000);

            // Step 8: Lower forks to rest pallet on rack supports
            setState(AsrsState.LOWERING_PALLET, "Lowering Pallet onto Rack Supports");
            setCoil(COIL_LIFT, false);
            Thread.sleep(600);

            // Step 9: Retract forks back to middle
            setState(AsrsState.FORKS_RETRACTING_PLACE, "Retracting Forks to Center");
            setCoil(COIL_FORKS_LEFT, false);
            waitForInput(IN_AT_MIDDLE, true, 4000);

            // Step 10: Update SQLite Inventory & notify UI
            String assignedSku = "BOX-SML-101";
            inventoryDao.storeProductInBay(assignedSku, "Standard Cardboard Box (Small)", "Packaging", 1, 12.50, targetBay);
            if (inventoryRefreshCallback != null) {
                inventoryRefreshCallback.run();
            }

            setState(AsrsState.IDLE, "Putaway Complete: Pallet successfully stored in Bay " + targetBay);
            activeBay.set(0);

        } catch (Exception e) {
            setState(AsrsState.FAULT, "Putaway cycle error: " + e.getMessage());
        }
    }

    /**
     * Executes the complete Outbound Retrieval Sequence:
     * Bay X -> Crane Pick -> Outfeed Transfer -> Drop on Unload -> Exit Conveyor.
     */
    private void executeRetrievalSequence(int bayNumber) {
        try {
            Product product = inventoryDao.getProductByBay(bayNumber);
            String sku = (product != null) ? product.getSku() : "Pallet";
            activeBay.set(bayNumber);

            // Step 1: Send Crane to Target Bay
            setState(AsrsState.RETRIEVAL_CRANE_TRAVELING, "Crane traveling to Bay " + bayNumber + " to retrieve " + sku);
            writeTargetPosition(bayNumber);
            waitForCraneArrival(14000);

            // Step 2: Extend forks under pallet
            setState(AsrsState.RETRIEVAL_FORKS_PICK, "Extending Forks into Bay " + bayNumber);
            setCoil(COIL_FORKS_LEFT, true);
            waitForInput(IN_AT_LEFT, true, 4000);

            // Step 3: Lift pallet off rack supports
            setCoil(COIL_LIFT, true);
            Thread.sleep(600);

            // Step 4: Retract forks to middle
            setCoil(COIL_FORKS_LEFT, false);
            waitForInput(IN_AT_MIDDLE, true, 4000);

            // Step 5: Crane travels to Outfeed Transfer Station (Position 55)
            setState(AsrsState.RETRIEVAL_CRANE_TO_UNLOAD, "Crane transporting pallet to Outfeed Station");
            writeTargetPosition(STATION_INFEED_LOAD);
            waitForCraneArrival(14000);

            // Step 6: Extend forks to deposit onto unload conveyor
            setState(AsrsState.RETRIEVAL_FORKS_PLACE, "Extending Forks to deposit pallet onto Outfeed Conveyor");
            setCoil(COIL_FORKS_LEFT, true);
            waitForInput(IN_AT_LEFT, true, 4000);

            // Step 7: Lower pallet onto conveyor bed
            setCoil(COIL_LIFT, false);
            Thread.sleep(600);

            // Step 8: Retract forks to middle
            setCoil(COIL_FORKS_LEFT, false);
            waitForInput(IN_AT_MIDDLE, true, 4000);

            // Step 9: Discharge pallet through Unload & Exit Conveyors
            setState(AsrsState.OUTFEED_DISCHARGING, "Running Outfeed Conveyors -> Discharging to Exit");
            setCoil(COIL_UNLOAD_CONVEYOR, true);
            setCoil(COIL_EXIT_CONVEYOR, true);

            waitForInput(IN_AT_EXIT, true, 8000);
            Thread.sleep(1200); // Allow clearance through exit

            setCoil(COIL_UNLOAD_CONVEYOR, false);
            setCoil(COIL_EXIT_CONVEYOR, false);

            // Step 10: Clear Bay in SQLite Inventory
            inventoryDao.clearBay(bayNumber);
            if (inventoryRefreshCallback != null) {
                inventoryRefreshCallback.run();
            }

            setState(AsrsState.IDLE, "Retrieval Complete: Bay " + bayNumber + " (" + sku + ") cleared and discharged.");
            activeBay.set(0);

        } catch (Exception e) {
            setState(AsrsState.FAULT, "Retrieval error: " + e.getMessage());
        }
    }

    private void writeTargetPosition(int pos) throws IOException {
        currentTargetPosition.set(pos);
        ioService.writeRegister(REG_TARGET_POSITION, pos);
    }

    private void setCoil(int address, boolean value) throws IOException {
        com.example.kanshiwarehousemanagementsystem.model.ModbusTag tag =
                new com.example.kanshiwarehousemanagementsystem.model.ModbusTag("coil_" + address, "Coil " + address, address, com.example.kanshiwarehousemanagementsystem.model.ModbusTag.TagType.COIL);
        ioService.writeTag(tag, value);
    }

    private void waitForInput(int address, boolean expected, long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        com.example.kanshiwarehousemanagementsystem.model.ModbusTag tag = createInputTag(address);
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!running.get()) return;
            boolean val = ioService.readTag(tag);
            if (val == expected) {
                return;
            }
            Thread.sleep(80);
        }
    }

    private void waitForCraneArrival(long timeoutMs) throws Exception {
        long start = System.currentTimeMillis();
        Thread.sleep(400); // Give motors brief startup window to assert Moving bits
        com.example.kanshiwarehousemanagementsystem.model.ModbusTag tagX = createInputTag(IN_MOVING_X);
        com.example.kanshiwarehousemanagementsystem.model.ModbusTag tagZ = createInputTag(IN_MOVING_Z);

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

    private com.example.kanshiwarehousemanagementsystem.model.ModbusTag createInputTag(int address) {
        return new com.example.kanshiwarehousemanagementsystem.model.ModbusTag("input_" + address, "Input " + address, address, com.example.kanshiwarehousemanagementsystem.model.ModbusTag.TagType.DISCRETE_INPUT);
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
