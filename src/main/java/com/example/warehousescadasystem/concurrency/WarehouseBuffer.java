package com.example.warehousescadasystem.concurrency;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;



public class WarehouseBuffer<T> {

    @FunctionalInterface
    public interface BufferChangeListener {
        void onChange(int currentSize, int capacity);
    }

    private final int capacity;
    private final Queue<T> queue;
    private final ReentrantLock lock;
    private final Condition notFull;
    private final Condition notEmpty;
    private BufferChangeListener changeListener;

    public WarehouseBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Buffer capacity must be strictly greater than 0");
        }
        this.capacity = capacity;
        this.queue = new LinkedList<>();
        this.lock = new ReentrantLock(true); // Fair lock policy
        this.notFull = lock.newCondition();
        this.notEmpty = lock.newCondition();
    }


    public void put(T item) throws InterruptedException {
        lock.lock();
        try {
            while (queue.size() == capacity) {
                // Buffer is full: producer thread blocks without busy-waiting
                notFull.await();
            }
            queue.add(item);
            // Notify blocked consumers that a new item is available
            notEmpty.signal();
            notifyListener();
        } finally {
            lock.unlock();
        }
    }


    public T take() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                // Buffer is empty: consumer thread blocks efficiently
                notEmpty.await();
            }
            T item = queue.poll();
            // Notify blocked producers that space has freed up
            notFull.signal();
            notifyListener();
            return item;
        } finally {
            lock.unlock();
        }
    }


    public int size() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isFull() {
        lock.lock();
        try {
            return queue.size() == capacity;
        } finally {
            lock.unlock();
        }
    }

    public boolean isEmpty() {
        lock.lock();
        try {
            return queue.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    public void setChangeListener(BufferChangeListener listener) {
        lock.lock();
        try {
            this.changeListener = listener;
            notifyListener();
        } finally {
            lock.unlock();
        }
    }

    private void notifyListener() {
        if (changeListener != null) {
            int currentSize = queue.size();
            changeListener.onChange(currentSize, capacity);
        }
    }
}
