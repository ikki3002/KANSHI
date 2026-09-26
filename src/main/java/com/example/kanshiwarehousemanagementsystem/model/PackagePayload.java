package com.example.kanshiwarehousemanagementsystem.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Immutable package item payload transferred across the Producer-Consumer buffer.
 * Demonstrates Object-Oriented Encapsulation and Data Transfer Objects (DTO).
 */
public class PackagePayload {

    private final String trackingId;
    private final String sku;
    private final String productName;
    private final int quantity;
    private final String timestamp;

    public PackagePayload(String trackingId, String sku, String productName, int quantity) {
        this.trackingId = trackingId;
        this.sku = sku;
        this.productName = productName;
        this.quantity = quantity;
        this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    public String getTrackingId() {
        return trackingId;
    }

    public String getSku() {
        return sku;
    }

    public String getProductName() {
        return productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s) x%d", trackingId, productName, sku, quantity);
    }
}
