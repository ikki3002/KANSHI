package com.example.kanshiwarehousemanagementsystem.service.modbus;

import com.example.kanshiwarehousemanagementsystem.model.ModbusTag;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * High-level Operational Technology (OT) Service for Factory I/O.
 * Supports both predefined tags and dynamic tags loaded via TagManager.
 * Demonstrates Week 4 Java Concurrency and background polling.
 */
public class FactoryIOService {

    // Default PLC Tag Register Addresses (From Factory I/O Driver Configuration)
    public static final int COIL_BELT_CONVEYOR_0 = 0; // Belt Conveyor (6m) 0
    public static final int COIL_BELT_CONVEYOR_1 = 1; // Belt Conveyor (6m) 1
    public static final int COIL_CURVED_CONVEYOR = 2; // Curved Belt Conveyor 0 CW
    public static final int INPUT_VISION_SENSOR = 0;  // Vision Sensor 0

    public static final int DEFAULT_SLAVE_ID = 1;
    public static final int DEFAULT_PORT = 502;
    public static final String DEFAULT_HOST = "127.0.0.1";

    private ModbusTcpClient client;
    private ScheduledExecutorService pollingExecutor;
    private volatile boolean lastVisionSensorState = false;
    private final Map<String, Boolean> sensorStates = new ConcurrentHashMap<>();

    public FactoryIOService(String host, int port, int slaveId) {
        this.client = new ModbusTcpClient(host, port, slaveId);
    }

    public FactoryIOService() {
        this(DEFAULT_HOST, DEFAULT_PORT, DEFAULT_SLAVE_ID);
    }

    /**
     * Connects to Factory I/O Modbus TCP/IP Server.
     */
    public synchronized void connect() throws IOException {
        if (client != null && !client.isConnected()) {
            client.connect();
        }
    }

    /**
     * Connects to a custom host and port.
     */
    public synchronized void connect(String host, int port) throws IOException {
        if (client != null && client.isConnected()) {
            client.disconnect();
        }
        this.client = new ModbusTcpClient(host, port, DEFAULT_SLAVE_ID);
        this.client.connect();
    }

    public synchronized void disconnect() {
        stopSensorPolling();
        if (client != null) {
            client.disconnect();
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    /**
     * Writes state to a dynamic ModbusTag actuator (Coil).
     */
    public boolean writeTag(ModbusTag tag, boolean on) throws IOException {
        if (tag.isActuator()) {
            boolean success = client.writeSingleCoil(tag.getAddress(), on);
            if (success) {
                tag.setActive(on);
            }
            return success;
        }
        return false;
    }

    /**
     * Reads state from a dynamic ModbusTag (Sensor or Actuator).
     */
    public boolean readTag(ModbusTag tag) throws IOException {
        if (tag.isSensor()) {
            boolean state = client.readDiscreteInput(tag.getAddress());
            tag.setActive(state);
            return state;
        } else if (tag.isActuator()) {
            boolean[] states = client.readCoils(tag.getAddress(), 1);
            boolean state = states.length > 0 && states[0];
            tag.setActive(state);
            return state;
        }
        return false;
    }

    /**
     * Turns Belt Conveyor (6m) 0 ON or OFF.
     */
    public boolean setBeltConveyor0(boolean on) throws IOException {
        return client.writeSingleCoil(COIL_BELT_CONVEYOR_0, on);
    }

    /**
     * Turns Belt Conveyor (6m) 1 ON or OFF.
     */
    public boolean setBeltConveyor1(boolean on) throws IOException {
        return client.writeSingleCoil(COIL_BELT_CONVEYOR_1, on);
    }

    /**
     * Turns Curved Belt Conveyor 0 CW ON or OFF.
     */
    public boolean setCurvedConveyor(boolean on) throws IOException {
        return client.writeSingleCoil(COIL_CURVED_CONVEYOR, on);
    }

    /**
     * Emergency Stop: Shuts off all configured conveyor actuators immediately.
     */
    public void emergencyStop(List<ModbusTag> actuatorTags) {
        if (actuatorTags != null) {
            for (ModbusTag tag : actuatorTags) {
                try {
                    writeTag(tag, false);
                } catch (Exception ignored) {}
            }
        }
    }

    public void emergencyStop() throws IOException {
        client.writeSingleCoil(COIL_BELT_CONVEYOR_0, false);
        client.writeSingleCoil(COIL_BELT_CONVEYOR_1, false);
        client.writeSingleCoil(COIL_CURVED_CONVEYOR, false);
    }

    /**
     * Reads current state of Vision Sensor 0 (Discrete Input 0).
     */
    public boolean isVisionSensorDetected() throws IOException {
        return client.readDiscreteInput(INPUT_VISION_SENSOR);
    }

    /**
     * Reads all 3 conveyor actuator coil states [Conveyor0, Conveyor1, CurvedConveyor].
     */
    public boolean[] getConveyorStates() throws IOException {
        return client.readCoils(COIL_BELT_CONVEYOR_0, 3);
    }

    /**
     * Dynamic polling for a list of sensor tags.
     * Fires callback whenever any sensor changes state.
     */
    public synchronized void startDynamicSensorPolling(List<ModbusTag> sensorTags,
                                                       BiConsumer<ModbusTag, Boolean> onSensorChanged,
                                                       int intervalMillis) {
        stopSensorPolling();

        pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FactoryIO-DynamicSensorPoller");
            t.setDaemon(true);
            return t;
        });

        pollingExecutor.scheduleAtFixedRate(() -> {
            if (!isConnected() || sensorTags == null) return;

            for (ModbusTag tag : sensorTags) {
                try {
                    boolean current = client.readDiscreteInput(tag.getAddress());
                    Boolean previous = sensorStates.get(tag.getId());

                    if (previous == null || previous != current) {
                        sensorStates.put(tag.getId(), current);
                        tag.setActive(current);
                        if (onSensorChanged != null) {
                            onSensorChanged.accept(tag, current);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Simple polling for single vision sensor.
     */
    public synchronized void startSensorPolling(Consumer<Boolean> onStateChanged, int intervalMillis) {
        stopSensorPolling();

        pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "FactoryIO-SensorPoller");
            t.setDaemon(true);
            return t;
        });

        pollingExecutor.scheduleAtFixedRate(() -> {
            try {
                if (isConnected()) {
                    boolean currentState = isVisionSensorDetected();
                    if (currentState != lastVisionSensorState) {
                        lastVisionSensorState = currentState;
                        if (onStateChanged != null) {
                            onStateChanged.accept(currentState);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void stopSensorPolling() {
        if (pollingExecutor != null && !pollingExecutor.isShutdown()) {
            pollingExecutor.shutdownNow();
            pollingExecutor = null;
        }
    }

    public boolean getLastVisionSensorState() {
        return lastVisionSensorState;
    }

    public ModbusTcpClient getClient() {
        return client;
    }
}
