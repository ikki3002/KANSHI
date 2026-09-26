package com.example.kanshiwarehousemanagementsystem.concurrency;

import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.model.PackagePayload;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Background Consumer Service solving the consumer half of the Producer-Consumer problem.
 * Continually takes PackagePayloads from the WarehouseBuffer, updates SQLite stock,
 * and notifies listeners without blocking the JavaFX UI thread.
 */
public class IntakeConsumerService {

    @FunctionalInterface
    public interface ConsumerCallback {
        void onPackageProcessed(PackagePayload payload, int currentStock);
    }

    private final WarehouseBuffer<PackagePayload> buffer;
    private final InventoryDao inventoryDao;
    private final ConsumerCallback callback;

    private ExecutorService consumerExecutor;
    private volatile boolean running;

    public IntakeConsumerService(WarehouseBuffer<PackagePayload> buffer,
                                 InventoryDao inventoryDao,
                                 ConsumerCallback callback) {
        this.buffer = buffer;
        this.inventoryDao = inventoryDao;
        this.callback = callback;
        this.running = false;
    }

    /**
     * Spawns the dedicated background consumer thread pool.
     */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        consumerExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Kanshi-IntakeConsumer-Thread");
            t.setDaemon(true);
            return t;
        });

        consumerExecutor.submit(this::consumeLoop);
    }

    private void consumeLoop() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Blocks efficiently until producer pushes a package into the bounded buffer
                PackagePayload payload = buffer.take();

                // Process database ingestion
                inventoryDao.updateStockDelta(payload.getSku(), payload.getQuantity());
                int updatedStock = inventoryDao.getStockQuantity(payload.getSku());

                // Notify UI listener
                if (callback != null) {
                    callback.onPackageProcessed(payload, updatedStock);
                }

                // Brief simulated intake barcoding delay (150ms)
                Thread.sleep(150);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("Error in consumer ingest worker: " + e.getMessage());
            }
        }
    }

    /**
     * Gracefully stops the consumer worker thread.
     */
    public synchronized void stop() {
        running = false;
        if (consumerExecutor != null && !consumerExecutor.isShutdown()) {
            consumerExecutor.shutdownNow();
            try {
                if (!consumerExecutor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    consumerExecutor.shutdownNow();
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public boolean isRunning() {
        return running;
    }
}
