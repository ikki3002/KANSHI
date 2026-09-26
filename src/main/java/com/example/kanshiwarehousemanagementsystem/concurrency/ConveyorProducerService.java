package com.example.kanshiwarehousemanagementsystem.concurrency;

import com.example.kanshiwarehousemanagementsystem.model.PackagePayload;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Producer service solving the producer half of the Producer-Consumer problem.
 * Pushes physical conveyor package detections into the WarehouseBuffer.
 * If the buffer is full, the producer thread blocks until consumer frees capacity.
 */
public class ConveyorProducerService {

    private final WarehouseBuffer<PackagePayload> buffer;
    private final AtomicInteger sequenceCounter;
    private final ExecutorService producerExecutor;

    public ConveyorProducerService(WarehouseBuffer<PackagePayload> buffer) {
        this.buffer = buffer;
        this.sequenceCounter = new AtomicInteger(1000);
        this.producerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "Kanshi-ConveyorProducer-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Produces a single package detection into the bounded buffer.
     * Runs asynchronously on a producer thread so it does not block the JavaFX UI.
     */
    public void produceAsync(String sku, String name, int quantity) {
        producerExecutor.submit(() -> {
            try {
                String trackingId = "PKG-" + sequenceCounter.incrementAndGet();
                PackagePayload payload = new PackagePayload(trackingId, sku, name, quantity);
                // Blocks if buffer is full (demonstrating backpressure & Producer-Consumer synchronization)
                buffer.put(payload);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /**
     * Simulates rapid batch production (e.g. 5 boxes arriving on the line in quick succession).
     */
    public void produceBatchAsync(int batchSize, String sku, String name) {
        producerExecutor.submit(() -> {
            try {
                for (int i = 0; i < batchSize; i++) {
                    String trackingId = "BATCH-" + sequenceCounter.incrementAndGet();
                    PackagePayload payload = new PackagePayload(trackingId, sku, name, 1);
                    buffer.put(payload);
                    Thread.sleep(100); // 100ms interval between arriving boxes
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public void shutdown() {
        producerExecutor.shutdownNow();
    }
}
