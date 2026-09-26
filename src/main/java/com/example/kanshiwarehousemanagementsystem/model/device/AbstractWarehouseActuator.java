package com.example.kanshiwarehousemanagementsystem.model.device;

/**
 * Base abstract class for active warehouse actuators (conveyors, pushers, diverters).
 * Demonstrates Object-Oriented Programming (Abstract Classes, Encapsulation, Polymorphism).
 */
public abstract class AbstractWarehouseActuator implements IndustrialDevice {

    protected final String deviceId;
    protected final String name;
    protected volatile boolean running;

    public AbstractWarehouseActuator(String deviceId, String name) {
        this.deviceId = deviceId;
        this.name = name;
        this.running = false;
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

    public boolean isRunning() {
        return running;
    }

    /**
     * Activates the actuator mechanism.
     */
    public abstract void start();

    /**
     * Deactivates the actuator mechanism.
     */
    public abstract void stop();

    @Override
    public void reset() {
        stop();
    }
}
