package com.example.kanshiwarehousemanagementsystem.model.device;

/**
 * Common interface contract for all warehouse automation devices (actuators, sensors, diverters).
 * Demonstrates Object-Oriented Programming (Interfaces & Abstraction).
 */
public interface IndustrialDevice {

    /**
     * Unique identifier for the hardware tag or device.
     */
    String getDeviceId();

    /**
     * Human-readable equipment name.
     */
    String getName();

    /**
     * Diagnostic health status of the device.
     */
    boolean isOperational();

    /**
     * Resets the device back to its default baseline state.
     */
    void reset();
}
