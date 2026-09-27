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
 *
 * Physical layout assumptions (Factory I/O "Automated Warehouse" scene):
 *   - Infeed roller bed (Load Conveyor) is on the LEFT side of the crane at Station 55    → FORKS_LEFT / IN_AT_LEFT
 *   - Outfeed roller bed (Unload Conveyor) is on the RIGHT side of the crane at Station 55 → FORKS_RIGHT / IN_AT_RIGHT
 *   - Storage rack bays (1-54) are on the RIGHT side of the crane aisle                 → FORKS_RIGHT / IN_AT_RIGHT
 *
 * Conveyor rules:
 *   - ENTRY_CONVEYOR and LOAD_CONVEYOR start together once crane is confirmed ready at Station 55.
 *   - IN_AT_ENTRY (vision sensor) fires while the box is still moving — used ONLY to read product type.
 *   - Both conveyors stop only when IN_AT_LOAD fires (box fully on crane pickup bed).
 *   - During unload, UNLOAD_CONVEYOR and EXIT_CONVEYOR run until IN_AT_EXIT clears into the Remover.
 */
public class AsrsAutomationEngine {

    // ── Modbus Discrete Input Addresses (Factory I/O Automated Warehouse) ──────
    public static final int IN_AT_ENTRY  = 0;   // Vision / optical sensor at conveyor entry
    public static final int IN_AT_LOAD   = 1;   // Sensor at crane's pickup bed (load position)
    public static final int IN_AT_LEFT   = 2;   // Forks fully extended LEFT
    public static final int IN_AT_MIDDLE = 3;   // Forks centered (home)
    public static final int IN_AT_RIGHT  = 4;   // Forks fully extended RIGHT
    public static final int IN_AT_UNLOAD = 5;   // Sensor at outfeed/unload roller position
    public static final int IN_AT_EXIT   = 6;   // Sensor at system exit gate
    public static final int IN_MOVING_X  = 7;   // Crane horizontal axis is moving
    public static final int IN_MOVING_Z  = 8;   // Crane vertical axis is moving
    public static final int IN_START     = 9;
    public static final int IN_RESET     = 10;
    public static final int IN_STOP      = 11;
    public static final int IN_ESTOP     = 12;
    public static final int IN_AUTO      = 13;
    public static final int IN_RUNNING   = 14;

    // ── Modbus Coil Addresses ────────────────────────────────────────────────────
    public static final int COIL_ENTRY_CONVEYOR = 0;   // First infeed roller
    public static final int COIL_LOAD_CONVEYOR  = 1;   // Second roller (entry → crane bed)
    public static final int COIL_FORKS_LEFT     = 2;   // Extend forks LEFT (infeed load bed)
    public static final int COIL_FORKS_RIGHT    = 3;   // Extend forks RIGHT (rack bays & outfeed unload bed)
    public static final int COIL_LIFT           = 4;   // Raise/lower fork carriage
    public static final int COIL_UNLOAD_CONVEYOR= 5;   // Outfeed roller
    public static final int COIL_EXIT_CONVEYOR  = 6;   // Exit belt
    public static final int COIL_LIGHT_START    = 7;
    public static final int COIL_LIGHT_RESET    = 8;
    public static final int COIL_LIGHT_STOP     = 9;

    // ── Modbus Holding Registers ─────────────────────────────────────────────────
    public static final int REG_TARGET_POSITION  = 0;  // Write: crane target (1-54=bay, 55=infeed)
    public static final int REG_VISION_CODE      = 1;  // Read:  vision sensor product code (1,2,3)

    public static final int STATION_INFEED_LOAD  = 55; // Home / infeed / outfeed position

    // ── State Machine ─────────────────────────────────────────────────────────────
    public enum AsrsState {
        IDLE              ("IDLE - System Ready"),
        CHECK_CRANE_READY ("Checking Crane at Ready/Pickup Position (Station 55)"),
        PRODUCT_DETECTED  ("Product Detected at Vision Sensor"),
        MOVE_PRODUCT_TO_PICKUP("Moving Product to Crane Pickup Bed"),
        CRANE_PICKUP      ("Crane Picking Up Product"),
        FIND_EMPTY_POSITION("Allocating Available Storage Bay"),
        MOVE_TO_STORAGE   ("Crane Moving to Storage Bay"),
        PLACE_PRODUCT     ("Crane Placing Product into Rack"),
        UPDATE_DATABASE   ("Updating Inventory Ledger"),
        RETURN_TO_READY   ("Crane Returning to Station 55"),
        UNLOAD_REQUEST    ("Unload / Dispatch Request Received"),
        CHECK_INVENTORY   ("Verifying Inventory & Availability"),
        FIND_PRODUCT      ("Locating Product in Rack"),
        MOVE_TO_DISPATCH  ("Crane Transporting Product to Dispatch Station"),
        RELEASE_PRODUCT   ("Releasing Product onto Outfeed Rollers"),
        FAULT             ("FAULT / EMERGENCY STOP");

        private final String description;
        AsrsState(String d) { this.description = d; }
        public String getDescription() { return description; }
    }

    // ── Product Catalog ───────────────────────────────────────────────────────────
    public static class ProductProfile {
        public final String skuPrefix, name, category;
        public final double unitPrice;
        public ProductProfile(String skuPrefix, String name, String category, double unitPrice) {
            this.skuPrefix = skuPrefix; this.name = name;
            this.category = category;   this.unitPrice = unitPrice;
        }
    }

    private static final ProductProfile[] CATALOG_PROFILES = {
        new ProductProfile("BOX-A", "Product A (Standard Box)",    "Packaging", 15.00),
        new ProductProfile("BOX-B", "Product B (Heavy Crate)",     "Machinery",  45.00),
        new ProductProfile("PAL-C", "Product C (Palletized Cargo)","Logistics",  80.00)
    };

    // ── Fields ────────────────────────────────────────────────────────────────────
    private final FactoryIOService ioService;
    private final InventoryDao     inventoryDao;

    // Dedicated single-thread worker for crane sequences (dispatch, bulk unload, single retrieval)
    private final ExecutorService sequenceExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Asrs-Sequence-Worker");
        t.setDaemon(true);
        return t;
    });

    // Dedicated thread for the continuous Soft-PLC scan loop (100ms cycle)
    private Thread scanThread;

    private final AtomicBoolean  running    = new AtomicBoolean(false);
    private final AtomicBoolean  autoMode   = new AtomicBoolean(false);
    private final AtomicBoolean  isUnloading= new AtomicBoolean(false);
    private volatile AsrsState   currentState = AsrsState.IDLE;
    private final AtomicInteger  currentTargetPosition = new AtomicInteger(0);
    private final AtomicInteger  activeBay  = new AtomicInteger(0);
    private final AtomicInteger  profileIndex = new AtomicInteger(0);

    // Batch putaway control
    private final AtomicInteger targetPutawayCount = new AtomicInteger(0);
    private final AtomicInteger completedPutawayCount = new AtomicInteger(0);
    private final AtomicBoolean batchPutawayActive = new AtomicBoolean(false);
    private BiConsumer<Integer, Integer> batchPutawayProgressCallback;
    private Runnable batchPutawayCompleteCallback;

    private BiConsumer<AsrsState, String> stateListener;
    private Runnable inventoryRefreshCallback;

    // ── Constructor ───────────────────────────────────────────────────────────────
    public AsrsAutomationEngine(FactoryIOService ioService, InventoryDao inventoryDao) {
        this.ioService    = ioService;
        this.inventoryDao = inventoryDao;
    }

    // ── Public API ────────────────────────────────────────────────────────────────
    public void setStateListener(BiConsumer<AsrsState, String> listener) { this.stateListener = listener; }
    public void setInventoryRefreshCallback(Runnable cb) { this.inventoryRefreshCallback = cb; }

    public synchronized void start() {
        if (running.get() && scanThread != null && scanThread.isAlive()) return;
        running.set(true);
        scanThread = new Thread(this::runExecutionLoop, "Asrs-SoftPLC-Scan-Thread");
        scanThread.setDaemon(true);
        scanThread.start();
    }

    public synchronized void stop() {
        running.set(false);
        autoMode.set(false);
        batchPutawayActive.set(false);
        targetPutawayCount.set(0);
        isUnloading.set(false);
        if (scanThread != null) {
            scanThread.interrupt();
            scanThread = null;
        }
        stopAllActuators();
        setState(AsrsState.IDLE, "System stopped.");
    }

    public void setAutoMode(boolean enabled) {
        autoMode.set(enabled);
        if (!enabled) {
            batchPutawayActive.set(false);
            targetPutawayCount.set(0);
            stopAllActuators();
        }
        notifyStateChange("Auto-Cycle Mode " + (enabled ? "ENABLED" : "PAUSED"));
    }

    public boolean isAutoMode()    { return autoMode.get(); }
    public boolean isUnloading()   { return isUnloading.get(); }
    public boolean isBatchPutawayActive() { return batchPutawayActive.get(); }
    public int getCompletedPutawayCount() { return completedPutawayCount.get(); }
    public int getTargetPutawayCount()    { return targetPutawayCount.get(); }

    public void cancelBatchPutaway() {
        batchPutawayActive.set(false);
        targetPutawayCount.set(0);
        autoMode.set(false);
        stopAllActuators();
        setState(AsrsState.IDLE, "Batch putaway stopped by operator.");
    }

    public AsrsState getCurrentState() { return currentState; }
    public int getCurrentTargetPosition() { return currentTargetPosition.get(); }
    public int getActiveBay()      { return activeBay.get(); }

    public boolean isCraneReadyAtPickup() {
        if (!ioService.isConnected()) return false;
        try {
            boolean movingX    = ioService.readTag(inputTag(IN_MOVING_X));
            boolean movingZ    = ioService.readTag(inputTag(IN_MOVING_Z));
            boolean forkMiddle = readSensor(IN_AT_MIDDLE);  // inverted read for NC sensor
            return !movingX && !movingZ && forkMiddle && (currentTargetPosition.get() == STATION_INFEED_LOAD);
        } catch (Exception e) { return false; }
    }

    public void emergencyStop() {
        autoMode.set(false);
        batchPutawayActive.set(false);
        isUnloading.set(false);
        setState(AsrsState.FAULT, "EMERGENCY STOP TRIPPED");
        stopAllActuators();
    }

    /** Retrieve a single bay by bay number. */
    public void requestRetrieval(int bayNumber) {
        if (bayNumber < 1 || bayNumber > 54) return;
        if (isUnloading.get() || currentState != AsrsState.IDLE || batchPutawayActive.get()) {
            notifyStateChange("System busy (" + currentState.getDescription() + "). Retrieval deferred.");
            return;
        }
        Product p = inventoryDao.getProductByBay(bayNumber);
        String prodName = (p != null && p.getName() != null) ? p.getName() : "Pallet #" + bayNumber;
        isUnloading.set(true);
        autoMode.set(false);
        sequenceExecutor.submit(() -> {
            try { executeSingleRetrieval(bayNumber, prodName); }
            finally { isUnloading.set(false); }
        });
    }

    /** Bulk dispatch by product type and quantity. */
    public synchronized boolean requestBulkUnload(String productType, int requestedQty,
                                                   BiConsumer<Boolean, String> resultCallback) {
        if (requestedQty <= 0) {
            if (resultCallback != null) resultCallback.accept(false, "Requested quantity must be at least 1.");
            return false;
        }
        int available = inventoryDao.getAvailableCountByProductType(productType);
        if (requestedQty > available) {
            String msg = String.format("Unload Rejected: Requested %d unit(s) of '%s', only %d available.",
                    requestedQty, productType, available);
            setState(AsrsState.UNLOAD_REQUEST, msg);
            if (resultCallback != null) resultCallback.accept(false, msg);
            return false;
        }
        if (isUnloading.get() || currentState != AsrsState.IDLE || batchPutawayActive.get()) {
            String msg = "System busy (" + currentState.getDescription() + "). Please wait.";
            if (resultCallback != null) resultCallback.accept(false, msg);
            return false;
        }
        isUnloading.set(true);
        autoMode.set(false);
        sequenceExecutor.submit(() -> {
            try {
                executeBulkUnloadSequence(productType, requestedQty);
                if (resultCallback != null) {
                    resultCallback.accept(true, String.format("Unload complete: %d x '%s' dispatched.", requestedQty, productType));
                }
            } catch (Exception e) {
                if (resultCallback != null) {
                    resultCallback.accept(false, "Unload error: " + e.getMessage());
                }
            } finally {
                isUnloading.set(false);
            }
        });
        return true;
    }

    /**
     * Initiates automated batch putaway of exactly targetCount products into rack bays.
     * When targetCount is reached, all conveyors stop, crane returns to rest at Station 55,
     * and system transitions to IDLE.
     */
    public synchronized boolean startBatchPutaway(int targetCount,
                                                  BiConsumer<Integer, Integer> progressCallback,
                                                  Runnable onComplete) {
        if (targetCount <= 0) return false;
        if (isUnloading.get()) {
            String msg = "System busy with dispatch (" + currentState.getDescription() + "). Batch putaway deferred.";
            notifyStateChange(msg);
            return false;
        }
        int vacant = inventoryDao.getVacantCount(54);
        if (targetCount > vacant) {
            String msg = String.format("Cannot store %d units: only %d bay(s) vacant.", targetCount, vacant);
            notifyStateChange(msg);
            return false;
        }
        targetPutawayCount.set(targetCount);
        completedPutawayCount.set(0);
        batchPutawayActive.set(true);
        batchPutawayProgressCallback = progressCallback;
        batchPutawayCompleteCallback = onComplete;
        autoMode.set(true);
        notifyStateChange(String.format("Batch Putaway Target Set: %d pallet(s). Soft-PLC active.", targetCount));
        return true;
    }

    /**
     * Dispatches M items as a bunch in FIFO order (oldest stored pallets first).
     * Once all M items are unloaded through the exit conveyor, the crane and conveyors go to rest at Station 55.
     */
    public synchronized boolean requestBunchDispatch(int requestedQty,
                                                      BiConsumer<Integer, Integer> progressCallback,
                                                      BiConsumer<Boolean, String> resultCallback) {
        if (requestedQty <= 0) {
            if (resultCallback != null) resultCallback.accept(false, "Requested dispatch quantity must be at least 1.");
            return false;
        }
        int totalStored = inventoryDao.getTotalStoredCount();
        if (requestedQty > totalStored) {
            String msg = String.format("Dispatch Rejected: Requested %d unit(s), only %d stored in warehouse.",
                    requestedQty, totalStored);
            setState(AsrsState.UNLOAD_REQUEST, msg);
            if (resultCallback != null) resultCallback.accept(false, msg);
            return false;
        }
        if (isUnloading.get() || currentState != AsrsState.IDLE || batchPutawayActive.get()) {
            String msg = "System busy (" + currentState.getDescription() + "). Please wait.";
            if (resultCallback != null) resultCallback.accept(false, msg);
            return false;
        }
        isUnloading.set(true);
        autoMode.set(false);
        sequenceExecutor.submit(() -> {
            try {
                executeBunchDispatchSequence(requestedQty, progressCallback, resultCallback);
            } catch (Exception e) {
                if (resultCallback != null) {
                    resultCallback.accept(false, "Dispatch error: " + e.getMessage());
                }
            } finally {
                isUnloading.set(false);
            }
        });
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // SCAN LOOP
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Main Soft-PLC scan loop (100 ms cycle).
     * When auto mode is ON and the system is IDLE, immediately launches the putaway sequence.
     * The putaway sequence itself controls when rollers start and controls the 8-second
     * product wait — the scan loop does NOT do any sensor pre-checks.
     */
    private void runExecutionLoop() {
        while (running.get()) {
            try {
                if (ioService.isConnected() && autoMode.get()
                        && currentState == AsrsState.IDLE && !isUnloading.get()) {
                    executePutawaySequence();
                }
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                try { Thread.sleep(200); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // PUTAWAY SEQUENCE  (Infeed → Crane → Rack)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Full putaway (store) sequence:
     *
     *  1. Confirm crane is at Station 55, forks centered, lift down.
     *  2. Start ENTRY_CONVEYOR + LOAD_CONVEYOR together.
     *  3. When IN_AT_ENTRY fires → read product type from vision register (do NOT stop rollers).
     *  4. When IN_AT_LOAD fires  → box is fully on crane bed → STOP both rollers.
     *  5. Extend FORKS_LEFT → LIFT UP → retract FORKS_LEFT (carry pallet to crane center).
     *  6. Find next empty bay. Travel to bay (LIFT stays ON).
     *  7. Extend FORKS_RIGHT → LIFT DOWN → retract FORKS_RIGHT (place pallet on rack).
     *  8. Update database. Return to Station 55. → IDLE.
     */
    private void executePutawaySequence() {
        try {

            // ── Step 1: Confirm crane at Station 55 ──────────────────────────────
            setState(AsrsState.CHECK_CRANE_READY, "Confirming crane at Station 55...");
            writeTarget(STATION_INFEED_LOAD);
            awaitCraneStop(14_000);

            // Ensure forks are centered and lift is lowered before any roller moves.
            coil(COIL_FORKS_LEFT,  false);
            coil(COIL_FORKS_RIGHT, false);
            coil(COIL_LIFT,        false); // Ensure carriage is down at pickup height
            awaitInput(IN_AT_MIDDLE, true, 4_000);
            Thread.sleep(300); // Allow lift cylinder to fully lower

            // Check rack has capacity.
            int targetBay = inventoryDao.findNextAvailableBay(54);
            if (targetBay <= 0) {
                setState(AsrsState.IDLE, "Warehouse FULL (54/54 bays occupied). Rollers will not start.");
                return;
            }

            // ── Step 2: Start BOTH infeed rollers simultaneously ─────────────────
            //    They run together until the box reaches the crane bed (IN_AT_LOAD).
            setState(AsrsState.CHECK_CRANE_READY, "Crane at Station 55. Starting Entry + Load rollers...");
            coil(COIL_ENTRY_CONVEYOR, true);
            coil(COIL_LOAD_CONVEYOR,  true);

            // ── Step 3: Vision sensor read-point ─────────────────────────────────
            //    IN_AT_ENTRY fires as the box passes the vision sensor.
            //    READ THE PRODUCT TYPE HERE — do NOT stop the rollers.
            //    The box is still moving toward the crane bed.
            boolean seenAtEntry = awaitInput(IN_AT_ENTRY, true, 8_000);
            if (!seenAtEntry) {
                // Nothing arrived within 8 s — stop rollers, return to IDLE for next cycle.
                coil(COIL_ENTRY_CONVEYOR, false);
                coil(COIL_LOAD_CONVEYOR,  false);
                setState(AsrsState.IDLE, "No pallet at entry within 8 s. Rollers stopped. Will retry.");
                return;
            }
            // Box is passing the vision sensor — identify it while it keeps rolling.
            setState(AsrsState.PRODUCT_DETECTED, "Box at vision sensor — reading product type...");
            ProductProfile profile = readProductType();

            // ── Step 4: Wait for box to reach crane pickup bed ───────────────────
            //    IN_AT_LOAD fires when the box is FULLY on the crane bed.
            //    ONLY then stop both rollers.
            setState(AsrsState.MOVE_PRODUCT_TO_PICKUP, "Carrying " + profile.name + " to crane bed...");
            boolean reachedLoad = awaitInput(IN_AT_LOAD, true, 8_000);
            coil(COIL_ENTRY_CONVEYOR, false);   // ← stop here, NOT at IN_AT_ENTRY
            coil(COIL_LOAD_CONVEYOR,  false);
            if (!reachedLoad) {
                setState(AsrsState.FAULT, "Pallet did not reach crane bed within timeout — FAULT.");
                return;
            }

            // ── Step 5: Pick up from infeed bed (LEFT side at Station 55) ────────
            setState(AsrsState.CRANE_PICKUP, "Picking up " + profile.name + " from infeed bed (LEFT forks)");
            // CRITICAL: Crane lift MUST be DOWN (false) so forks slide UNDER the pallet runners
            coil(COIL_LIFT, false);
            Thread.sleep(300); // Ensure carriage is at its lowest position

            // 1. Extend forks LEFT under pallet
            coil(COIL_FORKS_LEFT, true);
            boolean extendedLeft = awaitInput(IN_AT_LEFT, true, 6_000);    // wait for forks to fully reach left
            if (!extendedLeft) {
                throw new IOException("Forks failed to reach LEFT infeed bed position (timeout).");
            }
            Thread.sleep(300); // Settle under pallet

            // 2. ONLY AFTER forks are under pallet: RAISE LIFT to lift pallet off rollers
            coil(COIL_LIFT, true);
            Thread.sleep(800); // Allow lift cylinder to fully raise pallet

            // 3. Retract forks to middle with pallet lifted
            coil(COIL_FORKS_LEFT, false);
            boolean centered = awaitInput(IN_AT_MIDDLE, true, 6_000);  // wait for forks back to center
            if (!centered) {
                throw new IOException("Forks failed to return to CENTER after infeed pickup (timeout).");
            }
            Thread.sleep(300); // Settle

            // ── Step 6: Allocate storage bay ─────────────────────────────────────
            setState(AsrsState.FIND_EMPTY_POSITION, "Finding next empty bay...");
            targetBay = inventoryDao.findNextAvailableBay(54);
            if (targetBay <= 0) {
                setState(AsrsState.FAULT, "Storage rack FULL (54/54 bays occupied). Cannot allocate storage position.");
                return;
            }
            activeBay.set(targetBay);

            // Crucial: Reserve bay in database IMMEDIATELY upon allocation so subsequent
            // checks or concurrent operations never pick this same bay while crane is in transit.
            setState(AsrsState.UPDATE_DATABASE, "Allocating Bay " + targetBay + " = " + profile.name);
            boolean recorded = inventoryDao.storeProductInBay(profile.skuPrefix, profile.name,
                    profile.category, 1, profile.unitPrice, targetBay);
            if (!recorded) {
                setState(AsrsState.FAULT, "Database error: Failed to record allocation for Bay " + targetBay);
                return;
            }
            if (inventoryRefreshCallback != null) inventoryRefreshCallback.run();

            // ── Step 7: Travel to bay (LIFT stays ON throughout) ─────────────────
            setState(AsrsState.MOVE_TO_STORAGE, "Moving " + profile.name + " to Bay " + targetBay);
            writeTarget(targetBay);
            awaitCraneStop(20_000);

            // ── Step 8: Place into rack (RIGHT side — storage rack) ───────────────
            setState(AsrsState.PLACE_PRODUCT, "Placing " + profile.name + " into Rack Bay " + targetBay + " (RIGHT forks)");
            // 1. Extend forks RIGHT into rack shelf (lift remains UP carrying pallet)
            coil(COIL_FORKS_RIGHT, true);
            boolean extendedRight = awaitInput(IN_AT_RIGHT, true, 6_000);   // wait for forks to fully reach right
            if (!extendedRight) {
                throw new IOException("Forks failed to reach RIGHT rack position at Bay " + targetBay + " (timeout).");
            }
            Thread.sleep(300); // Settle over rack shelf supports

            // 2. LOWER LIFT to deposit pallet onto rack shelf
            coil(COIL_LIFT, false);
            Thread.sleep(800); // Wait for lift cylinder to fully lower pallet onto shelf

            // 3. Retract empty forks back to center
            coil(COIL_FORKS_RIGHT, false);
            centered = awaitInput(IN_AT_MIDDLE, true, 6_000);
            if (!centered) {
                throw new IOException("Forks failed to return to CENTER after placing product (timeout).");
            }
            Thread.sleep(300); // Settle

            // ── Step 9: Confirm placement in database ─────────────────────────────
            setState(AsrsState.UPDATE_DATABASE, "Confirmed Bay " + targetBay + " stored.");
            if (inventoryRefreshCallback != null) inventoryRefreshCallback.run();

            // ── Step 10: Return to Station 55 ────────────────────────────────────
            setState(AsrsState.RETURN_TO_READY, "Returning to Station 55...");
            coil(COIL_LIFT, false); // Ensure lift remains lowered
            writeTarget(STATION_INFEED_LOAD);
            awaitCraneStop(20_000);
            activeBay.set(0);

            if (batchPutawayActive.get() || targetPutawayCount.get() > 0) {
                int done = completedPutawayCount.incrementAndGet();
                int target = targetPutawayCount.get();
                if (batchPutawayProgressCallback != null) {
                    batchPutawayProgressCallback.accept(done, target);
                }
                if (target > 0 && done >= target) {
                    batchPutawayActive.set(false);
                    targetPutawayCount.set(0);
                    autoMode.set(false);
                    stopAllActuators();
                    try {
                        coil(COIL_LIGHT_START, false);
                        coil(COIL_LIGHT_STOP, true);
                    } catch (Exception ignored) {}
                    setState(AsrsState.IDLE, String.format("Batch putaway complete: %d/%d items stored in rack. System at rest.", done, target));
                    if (batchPutawayCompleteCallback != null) {
                        try {
                            batchPutawayCompleteCallback.run();
                        } catch (Exception ignored) {}
                    }
                    return;
                }
            }

            setState(AsrsState.IDLE, "Putaway done: Bay " + targetBay + " stored. Ready at Station 55.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopAllActuators();
            setState(AsrsState.IDLE, "Putaway interrupted.");
        } catch (Exception e) {
            stopAllActuators();
            setState(AsrsState.FAULT, "Putaway error: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BUNCH DISPATCH SEQUENCE  (FIFO Rack Bays → Crane → Outfeed)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Executes bunch dispatch sequence for requestedQty pallets in FIFO order.
     */
    private void executeBunchDispatchSequence(int requestedQty,
                                              BiConsumer<Integer, Integer> progressCallback,
                                              BiConsumer<Boolean, String> resultCallback) {
        try {
            setState(AsrsState.UNLOAD_REQUEST, "Bunch dispatch: retrieving " + requestedQty + " pallet(s)...");

            setState(AsrsState.CHECK_INVENTORY, "Querying oldest stored pallets (FIFO)...");
            List<Integer> bays = inventoryDao.getOccupiedBays(requestedQty);
            if (bays.size() < requestedQty) {
                setState(AsrsState.IDLE, "Dispatch aborted: insufficient occupied bays found.");
                if (resultCallback != null) resultCallback.accept(false, "Insufficient stock in warehouse.");
                return;
            }

            for (int i = 0; i < requestedQty; i++) {
                if (!running.get()) { stopAllActuators(); break; }

                int bay = bays.get(i);
                activeBay.set(bay);
                Product p = inventoryDao.getProductByBay(bay);
                String prodName = (p != null && p.getName() != null) ? p.getName() : "Pallet #" + bay;

                // ── 1. Travel to bay ──────────────────────────────────────────
                setState(AsrsState.FIND_PRODUCT,
                        String.format("Retrieving %s from Bay %d (%d of %d)", prodName, bay, i + 1, requestedQty));
                writeTarget(bay);
                awaitCraneStop(20_000);

                // ── 2. Pick from rack (RIGHT side) ────────────────────────────
                setState(AsrsState.CRANE_PICKUP, "Picking from Rack Bay " + bay + " (RIGHT forks)");
                coil(COIL_LIFT, false);
                Thread.sleep(300);
                coil(COIL_FORKS_RIGHT, true);
                awaitInput(IN_AT_RIGHT, true, 6_000);
                Thread.sleep(300);
                coil(COIL_LIFT, true);
                Thread.sleep(800);
                coil(COIL_FORKS_RIGHT, false);
                awaitInput(IN_AT_MIDDLE, true, 6_000);
                Thread.sleep(300);

                // ── 3. Travel to Station 55 ───────────────────────────────────
                setState(AsrsState.MOVE_TO_DISPATCH, "Transporting " + prodName + " to Station 55");
                writeTarget(STATION_INFEED_LOAD);
                awaitCraneStop(20_000);

                // ── 4 & 5. Deposit onto Unload Conveyor (RIGHT forks) & convey to Remover ─────
                dischargeProductToRemover(prodName);

                // ── 6. Update database ────────────────────────────────────────
                setState(AsrsState.UPDATE_DATABASE, "Bay " + bay + " cleared");
                inventoryDao.clearBay(bay);
                if (inventoryRefreshCallback != null) inventoryRefreshCallback.run();

                activeBay.set(0);
                if (progressCallback != null) {
                    progressCallback.accept(i + 1, requestedQty);
                }
            }

            // ── 7. Return to rest at Station 55 ───────────────────────────────
            setState(AsrsState.RETURN_TO_READY, "All bunch items dispatched. Confirming crane at Station 55.");
            coil(COIL_LIFT, false);
            writeTarget(STATION_INFEED_LOAD);
            awaitCraneStop(6_000);
            stopAllActuators();
            coil(COIL_LIGHT_START, false);
            coil(COIL_LIGHT_STOP, true);

            setState(AsrsState.IDLE,
                    String.format("Bunch dispatch complete: %d pallet(s) dispatched. System at rest.", requestedQty));
            if (resultCallback != null) {
                resultCallback.accept(true, String.format("Dispatched %d pallet(s) successfully.", requestedQty));
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopAllActuators();
            setState(AsrsState.IDLE, "Bunch dispatch interrupted.");
            if (resultCallback != null) {
                resultCallback.accept(false, "Bunch dispatch interrupted.");
            }
        } catch (Exception e) {
            stopAllActuators();
            setState(AsrsState.FAULT, "Bunch dispatch error: " + e.getMessage());
            if (resultCallback != null) {
                resultCallback.accept(false, "Bunch dispatch error: " + e.getMessage());
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // BULK UNLOAD SEQUENCE  (Rack → Crane → Outfeed)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Dispatches multiple items of the same product type one by one.
     *
     * For each item:
     *   Travel to bay → pick (RIGHT forks) → travel to Station 55
     *   → deposit (LEFT forks onto outfeed bed) → run outfeed rollers → update DB.
     *
     * RETURN_TO_READY is executed ONCE after the loop, not inside it.
     * (After each MOVE_TO_DISPATCH the crane is already at Station 55.)
     */
    private void executeBulkUnloadSequence(String productType, int requestedQty) {
        try {
            setState(AsrsState.UNLOAD_REQUEST, "Dispatching " + requestedQty + " x " + productType);

            setState(AsrsState.CHECK_INVENTORY, "Verifying inventory for " + productType + "...");
            List<Integer> bays = inventoryDao.getBaysForProductType(productType);
            if (bays.size() < requestedQty) {
                setState(AsrsState.IDLE, "Unload aborted: insufficient stock at verification time.");
                return;
            }

            for (int i = 0; i < requestedQty; i++) {
                if (!running.get()) { stopAllActuators(); break; }

                int bay = bays.get(i);
                activeBay.set(bay);

                // ── Travel to bay ──────────────────────────────────────────────
                setState(AsrsState.FIND_PRODUCT,
                        String.format("Retrieving %s from Bay %d (%d of %d)", productType, bay, i+1, requestedQty));
                writeTarget(bay);
                awaitCraneStop(20_000);

                // ── Pick from rack (RIGHT side) ────────────────────────────────
                setState(AsrsState.CRANE_PICKUP, "Picking from Rack Bay " + bay + " (RIGHT forks)");
                coil(COIL_LIFT, false);              // Ensure carriage is lowered before extending into rack
                Thread.sleep(300);
                coil(COIL_FORKS_RIGHT, true);
                awaitInput(IN_AT_RIGHT, true, 6_000);
                Thread.sleep(300);
                coil(COIL_LIFT, true);               // lift pallet off rack
                Thread.sleep(800);
                coil(COIL_FORKS_RIGHT, false);       // retract with pallet; LIFT stays ON
                awaitInput(IN_AT_MIDDLE, true, 6_000);
                Thread.sleep(300);

                // ── Travel to Station 55 ───────────────────────────────────────
                setState(AsrsState.MOVE_TO_DISPATCH, "Transporting " + productType + " to Station 55");
                writeTarget(STATION_INFEED_LOAD);
                awaitCraneStop(20_000);

                // ── Deposit onto Unload Conveyor (RIGHT forks) & convey to Remover ─────
                dischargeProductToRemover(productType);

                // ── Update database ────────────────────────────────────────────
                setState(AsrsState.UPDATE_DATABASE, "Bay " + bay + " cleared");
                inventoryDao.clearBay(bay);
                if (inventoryRefreshCallback != null) inventoryRefreshCallback.run();

                activeBay.set(0);
                // Crane is already at Station 55 (MOVE_TO_DISPATCH brought it here).
                // Next iteration goes directly to the next bay — no wasted return trip.
            }

            // Single RETURN_TO_READY after all items — crane is already at 55.
            setState(AsrsState.RETURN_TO_READY, "All items dispatched. Confirming crane at Station 55.");
            coil(COIL_LIFT, false);
            writeTarget(STATION_INFEED_LOAD);
            awaitCraneStop(6_000);

            setState(AsrsState.IDLE,
                    String.format("Unload complete: %d x '%s' dispatched.", requestedQty, productType));

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopAllActuators();
            setState(AsrsState.IDLE, "Unload interrupted.");
        } catch (Exception e) {
            stopAllActuators();
            setState(AsrsState.FAULT, "Unload error: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // SINGLE RETRIEVAL SEQUENCE  (used by requestRetrieval)
    // ══════════════════════════════════════════════════════════════════════════════

    private void executeSingleRetrieval(int bay, String prodName) {
        try {
            activeBay.set(bay);

            // Travel to bay
            setState(AsrsState.FIND_PRODUCT, "Retrieving " + prodName + " from Bay " + bay);
            writeTarget(bay);
            awaitCraneStop(20_000);

            // Pick from rack (RIGHT side)
            setState(AsrsState.CRANE_PICKUP, "Picking from Rack Bay " + bay + " (RIGHT forks)");
            coil(COIL_LIFT, false);
            Thread.sleep(300);
            coil(COIL_FORKS_RIGHT, true);
            awaitInput(IN_AT_RIGHT, true, 6_000);
            Thread.sleep(300);
            coil(COIL_LIFT, true);
            Thread.sleep(800);
            coil(COIL_FORKS_RIGHT, false);
            awaitInput(IN_AT_MIDDLE, true, 6_000);
            Thread.sleep(300);

            // Travel to Station 55
            setState(AsrsState.MOVE_TO_DISPATCH, "Transporting " + prodName + " to Station 55");
            writeTarget(STATION_INFEED_LOAD);
            awaitCraneStop(20_000);

            // Deposit onto Unload Conveyor (RIGHT forks) & convey to Remover
            dischargeProductToRemover(prodName);

            // Update database
            setState(AsrsState.UPDATE_DATABASE, "Bay " + bay + " cleared");
            inventoryDao.clearBay(bay);
            if (inventoryRefreshCallback != null) inventoryRefreshCallback.run();

            // Return to Station 55
            setState(AsrsState.RETURN_TO_READY, "Returning to Station 55...");
            coil(COIL_LIFT, false);
            writeTarget(STATION_INFEED_LOAD);
            awaitCraneStop(20_000);
            activeBay.set(0);

            setState(AsrsState.IDLE, "Retrieval complete: Bay " + bay + " dispatched.");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopAllActuators();
            setState(AsrsState.IDLE, "Retrieval interrupted.");
        } catch (Exception e) {
            stopAllActuators();
            setState(AsrsState.FAULT, "Retrieval error: " + e.getMessage());
        }
    }

    /**
     * Deposits the retrieved pallet from the crane carriage onto the Unload Conveyor (RIGHT forks),
     * retracts the forks to center, and drives both the Unload and Exit conveyors until
     * the pallet passes the exit sensor and falls cleanly into the Remover.
     */
    private void dischargeProductToRemover(String prodName) throws Exception {
        // ── 1. Deposit onto Unload Conveyor (RIGHT side at Station 55) ─────────────
        setState(AsrsState.RELEASE_PRODUCT, "Depositing " + prodName + " onto Unload Conveyor (RIGHT forks)");
        coil(COIL_FORKS_RIGHT, true);
        boolean extendedRight = awaitInput(IN_AT_RIGHT, true, 6_000);
        if (!extendedRight) {
            throw new IOException("Forks failed to reach RIGHT outfeed/unload position (timeout).");
        }
        Thread.sleep(300);

        // Lower fork carriage so pallet rests on the Unload Conveyor rollers
        coil(COIL_LIFT, false);
        Thread.sleep(800);

        // Retract forks back to center
        coil(COIL_FORKS_RIGHT, false);
        boolean centered = awaitInput(IN_AT_MIDDLE, true, 6_000);
        if (!centered) {
            throw new IOException("Forks failed to retract to center from unload conveyor (timeout).");
        }
        Thread.sleep(300);

        // ── 2. Run Unload & Exit conveyors to transport pallet to Remover ──────────
        setState(AsrsState.RELEASE_PRODUCT, "Outfeed active: conveying " + prodName + " to exit remover...");
        coil(COIL_UNLOAD_CONVEYOR, true);
        coil(COIL_EXIT_CONVEYOR,   true);

        // Wait for pallet leading edge to reach Exit sensor
        boolean reachedExit = awaitInput(IN_AT_EXIT, true, 16_000);
        if (reachedExit) {
            // Wait for pallet trailing edge to clear Exit sensor beam
            awaitInput(IN_AT_EXIT, false, 8_000);
            // Run extra margin so the pallet rolls completely off into the Remover
            Thread.sleep(2_500);
        } else {
            // Fallback timeout run if sensor was not tripped
            Thread.sleep(8_000);
        }

        coil(COIL_UNLOAD_CONVEYOR, false);
        coil(COIL_EXIT_CONVEYOR,   false);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ══════════════════════════════════════════════════════════════════════════════

    /** Read vision register and return matching product profile (fallback = round-robin). */
    private ProductProfile readProductType() {
        try {
            int code = ioService.readRegister(REG_VISION_CODE);
            if (code >= 1 && code <= CATALOG_PROFILES.length) return CATALOG_PROFILES[code - 1];
        } catch (Exception ignored) {}
        return CATALOG_PROFILES[Math.abs(profileIndex.getAndIncrement() % CATALOG_PROFILES.length)];
    }

    /** Write crane target position register. */
    private void writeTarget(int pos) throws IOException {
        currentTargetPosition.set(pos);
        ioService.writeRegister(REG_TARGET_POSITION, pos);
    }

    /** Write a single coil (actuator). */
    private void coil(int address, boolean value) throws IOException {
        ioService.writeTag(new ModbusTag("coil_" + address, "Coil " + address,
                address, ModbusTag.TagType.COIL), value);
    }

    /**
     * Block until the sensor at {@code address} equals {@code expected},
     * or until {@code timeoutMs} elapses.
     * Uses {@link #readSensor(int)} which normalizes active-LOW vs active-HIGH sensors.
     * @return true if the expected value was seen before timeout.
     */
    private boolean awaitInput(int address, boolean expected, long timeoutMs) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!running.get()) return false;
            if (readSensor(address) == expected) return true;
            Thread.sleep(50);
        }
        return false;
    }

    /**
     * Checks if the sensor address corresponds to a conveyor optical retroreflective beam sensor
     * (Normally-Closed / active-LOW in Factory I/O).
     */
    private boolean isOpticalConveyorSensor(int address) {
        return address == IN_AT_ENTRY || address == IN_AT_LOAD ||
               address == IN_AT_UNLOAD || address == IN_AT_EXIT;
    }

    /**
     * Reads a sensor normalized to active-HIGH semantics:
     * returns true when the condition is met (product present, or fork at position).
     *
     * - Optical conveyor sensors (0, 1, 5, 6: AT_ENTRY, AT_LOAD, AT_UNLOAD, AT_EXIT) are active-LOW:
     *   raw 0 = product present (beam broken) -> returns true
     *   raw 1 = beam clear (nothing)          -> returns false
     *
     * - Fork position limit switches (2, 3, 4: AT_LEFT, AT_MIDDLE, AT_RIGHT) are active-HIGH:
     *   raw 1 = fork at position              -> returns true
     *   raw 0 = fork not at position          -> returns false
     */
    private boolean readSensor(int address) throws Exception {
        boolean raw = ioService.readTag(inputTag(address));
        if (isOpticalConveyorSensor(address)) {
            return !raw; // Invert NC optical sensor: 0 means product is present
        }
        return raw;     // NO limit switches: 1 means fork reached limit/position
    }

    /**
     * Block until both X and Z crane movement bits are LOW (crane stopped).
     * Waits for initial movement startup if applicable, then confirms
     * stationary status with multi-cycle debounce.
     */
    private void awaitCraneStop(long timeoutMs) throws Exception {
        Thread.sleep(300); // startup window
        ModbusTag tagX = inputTag(IN_MOVING_X);
        ModbusTag tagZ = inputTag(IN_MOVING_Z);

        // Wait up to 1.2s for movement to begin (if target differs from current position)
        long startDeadline = System.currentTimeMillis() + 1200;
        while (System.currentTimeMillis() < startDeadline) {
            if (!running.get()) return;
            if (ioService.readTag(tagX) || ioService.readTag(tagZ)) {
                break;
            }
            Thread.sleep(50);
        }

        // Wait for both axes to stop moving (require 3 consecutive stopped readings)
        long deadline = System.currentTimeMillis() + timeoutMs;
        int stoppedCount = 0;
        while (System.currentTimeMillis() < deadline) {
            if (!running.get()) return;
            boolean movingX = ioService.readTag(tagX);
            boolean movingZ = ioService.readTag(tagZ);
            if (!movingX && !movingZ) {
                stoppedCount++;
                if (stoppedCount >= 3) { // 3 consecutive checks (150ms stable)
                    Thread.sleep(250);   // Mechanical settle
                    return;
                }
            } else {
                stoppedCount = 0;
            }
            Thread.sleep(50);
        }
    }

    /** Turn off every actuator safely (used on stop / fault / interrupt). */
    public void stopAllActuators() {
        try {
            if (!ioService.isConnected()) return;
            coil(COIL_ENTRY_CONVEYOR,  false);
            coil(COIL_LOAD_CONVEYOR,   false);
            coil(COIL_FORKS_LEFT,      false);
            coil(COIL_FORKS_RIGHT,     false);
            coil(COIL_LIFT,            false);
            coil(COIL_UNLOAD_CONVEYOR, false);
            coil(COIL_EXIT_CONVEYOR,   false);
        } catch (Exception ignored) {}
    }

    private ModbusTag inputTag(int address) {
        return new ModbusTag("input_" + address, "Input " + address,
                address, ModbusTag.TagType.DISCRETE_INPUT);
    }

    private void setState(AsrsState state, String message) {
        this.currentState = state;
        notifyStateChange(message);
    }

    private void notifyStateChange(String message) {
        if (stateListener != null) stateListener.accept(currentState, message);
    }
}
