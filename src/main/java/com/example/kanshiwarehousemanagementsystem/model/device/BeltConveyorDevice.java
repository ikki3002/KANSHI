package com.example.kanshiwarehousemanagementsystem.model.device;

/**
 * Concrete implementation of an industrial belt conveyor actuator.
 * Demonstrates Object-Oriented Inheritance and Method Overriding.
 */
public class BeltConveyorDevice extends AbstractWarehouseActuator {

    private final double speedMps;

    public BeltConveyorDevice(String deviceId, String name, double speedMps) {
        super(deviceId, name);
        this.speedMps = speedMps;
    }

    @Override
    public void start() {
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    public double getSpeedMps() {
        return speedMps;
    }
}
