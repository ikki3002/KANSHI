package com.example.kanshiwarehousemanagementsystem.model.device;

/**
 * Concrete implementation of an optical retroreflective sensor device.
 * Demonstrates Interface implementation and encapsulation.
 */
public class OpticalSensorDevice implements IndustrialDevice {

    private final String deviceId;
    private final String name;
    private volatile boolean objectDetected;

    public OpticalSensorDevice(String deviceId, String name) {
        this.deviceId = deviceId;
        this.name = name;
        this.objectDetected = false;
    }

    @Override
    public String getDeviceId() {
        return deviceId;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isOperational() {
        return true;
    }

    @Override
    public void reset() {
        this.objectDetected = false;
    }

    public boolean isObjectDetected() {
        return objectDetected;
    }

    public void setObjectDetected(boolean objectDetected) {
        this.objectDetected = objectDetected;
    }
}
