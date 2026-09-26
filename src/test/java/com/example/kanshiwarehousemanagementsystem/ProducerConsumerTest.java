package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.concurrency.WarehouseBuffer;
import com.example.kanshiwarehousemanagementsystem.model.PackagePayload;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class ProducerConsumerTest {

    @Test
    public void testBasicPutAndTake() throws InterruptedException {
        WarehouseBuffer<PackagePayload> buffer = new WarehouseBuffer<>(3);
        assertTrue(buffer.isEmpty());
        assertEquals(0, buffer.size());
        assertEquals(3, buffer.getCapacity());

        PackagePayload p1 = new PackagePayload("PKG-1", "BOX-SML-101", "Small Box", 1);
        PackagePayload p2 = new PackagePayload("PKG-2", "BOX-MED-102", "Medium Box", 2);

        buffer.put(p1);
        buffer.put(p2);

        assertEquals(2, buffer.size());
        assertFalse(buffer.isEmpty());
        assertFalse(buffer.isFull());

        PackagePayload taken1 = buffer.take();
        assertEquals("PKG-1", taken1.getTrackingId());
        assertEquals(1, buffer.size());

        PackagePayload taken2 = buffer.take();
        assertEquals("PKG-2", taken2.getTrackingId());
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void testProducerBlocksWhenBufferIsFull() throws Exception {
        WarehouseBuffer<PackagePayload> buffer = new WarehouseBuffer<>(2);
        buffer.put(new PackagePayload("PKG-1", "SKU-1", "Item 1", 1));
        buffer.put(new PackagePayload("PKG-2", "SKU-2", "Item 2", 1));
        assertTrue(buffer.isFull());

        CountDownLatch producerStarted = new CountDownLatch(1);
        AtomicBoolean producerCompleted = new AtomicBoolean(false);

        Thread producerThread = new Thread(() -> {
            try {
                producerStarted.countDown();
                // This put MUST block because buffer capacity is 2 and full
                buffer.put(new PackagePayload("PKG-3", "SKU-3", "Item 3", 1));
                producerCompleted.set(true);
            } catch (InterruptedException ignored) {
            }
        });

        producerThread.start();
        assertTrue(producerStarted.await(1, TimeUnit.SECONDS));

        // Allow thread to attempt put
        Thread.sleep(200);
        assertFalse(producerCompleted.get(), "Producer should be BLOCKED while buffer is full");

        // Now consume one item to free up capacity
        PackagePayload taken = buffer.take();
        assertEquals("PKG-1", taken.getTrackingId());

        // Wait for producer to unblock
        producerThread.join(1000);
        assertTrue(producerCompleted.get(), "Producer should complete put after consumer took an item");
        assertEquals(2, buffer.size());
    }

    @Test
    public void testConsumerBlocksWhenBufferIsEmpty() throws Exception {
        WarehouseBuffer<PackagePayload> buffer = new WarehouseBuffer<>(2);
        assertTrue(buffer.isEmpty());

        CountDownLatch consumerStarted = new CountDownLatch(1);
        AtomicBoolean consumerCompleted = new AtomicBoolean(false);

        Thread consumerThread = new Thread(() -> {
            try {
                consumerStarted.countDown();
                // This take MUST block because buffer is empty
                PackagePayload pkg = buffer.take();
                assertNotNull(pkg);
                consumerCompleted.set(true);
            } catch (InterruptedException ignored) {
            }
        });

        consumerThread.start();
        assertTrue(consumerStarted.await(1, TimeUnit.SECONDS));

        // Allow thread to attempt take
        Thread.sleep(200);
        assertFalse(consumerCompleted.get(), "Consumer should be BLOCKED while buffer is empty");

        // Now produce an item
        buffer.put(new PackagePayload("PKG-UNBLOCK", "SKU-TEST", "Unblock Item", 1));

        consumerThread.join(1000);
        assertTrue(consumerCompleted.get(), "Consumer should unblock and consume newly produced item");
        assertTrue(buffer.isEmpty());
    }

    @Test
    public void testConcurrentMultiThreadedProducerConsumer() throws Exception {
        int capacity = 5;
        int totalItems = 50;
        WarehouseBuffer<PackagePayload> buffer = new WarehouseBuffer<>(capacity);

        List<PackagePayload> consumedList = new CopyOnWriteArrayList<>();
        CountDownLatch finishLatch = new CountDownLatch(totalItems);

        // 2 Producer threads
        ExecutorService producers = Executors.newFixedThreadPool(2);
        AtomicInteger producedCounter = new AtomicInteger(0);

        for (int i = 0; i < totalItems; i++) {
            producers.submit(() -> {
                int id = producedCounter.incrementAndGet();
                try {
                    buffer.put(new PackagePayload("PKG-" + id, "SKU-" + id, "Product " + id, 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // 2 Consumer threads
        ExecutorService consumers = Executors.newFixedThreadPool(2);
        for (int i = 0; i < 2; i++) {
            consumers.submit(() -> {
                while (finishLatch.getCount() > 0) {
                    try {
                        PackagePayload payload = buffer.take();
                        consumedList.add(payload);
                        finishLatch.countDown();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        boolean finishedInTime = finishLatch.await(5, TimeUnit.SECONDS);
        assertTrue(finishedInTime, "All 50 items should be produced and consumed through bounded buffer without deadlock");
        assertEquals(totalItems, consumedList.size());

        producers.shutdownNow();
        consumers.shutdownNow();
    }
}
