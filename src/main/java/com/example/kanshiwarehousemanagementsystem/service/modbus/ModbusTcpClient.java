package com.example.kanshiwarehousemanagementsystem.service.modbus;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight, pure-Java Modbus TCP Client.
 * Demonstrates socket networking, binary protocol encoding/decoding, and thread safety.
 * Zero external dependencies required.
 */
public class ModbusTcpClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final int slaveId;
    private final int timeoutMillis;

    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private final AtomicInteger transactionCounter = new AtomicInteger(1);

    public ModbusTcpClient(String host, int port, int slaveId, int timeoutMillis) {
        this.host = host;
        this.port = port;
        this.slaveId = slaveId;
        this.timeoutMillis = timeoutMillis;
    }

    public ModbusTcpClient(String host, int port, int slaveId) {
        this(host, port, slaveId, 3000);
    }

    /**
     * Connects to the Modbus TCP Server (Factory I/O).
     */
    public synchronized void connect() throws IOException {
        if (isConnected()) {
            return;
        }

        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMillis);
        socket.setSoTimeout(timeoutMillis);
        socket.setTcpNoDelay(true);

        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
    }

    /**
     * Checks if the client socket is active and connected.
     */
    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Disconnects from the Modbus server.
     */
    public synchronized void disconnect() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        } finally {
            socket = null;
            out = null;
            in = null;
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    /**
     * Function Code 05 (0x05): Write Single Coil (Turn conveyor ON/OFF).
     *
     * @param address Coil address (0 = Belt Conveyor 0, 1 = Belt Conveyor 1, 2 = Curved Conveyor)
     * @param state   true for ON, false for OFF
     * @return true if successfully acknowledged by server
     */
    public synchronized boolean writeSingleCoil(int address, boolean state) throws IOException {
        ensureConnected();

        int transactionId = getNextTransactionId();
        byte[] request = new byte[12];

        // MBAP Header (7 bytes)
        request[0] = (byte) ((transactionId >> 8) & 0xFF);
        request[1] = (byte) (transactionId & 0xFF);
        request[2] = 0x00; // Protocol ID (0 = Modbus)
        request[3] = 0x00;
        request[4] = 0x00; // Length (6 bytes following)
        request[5] = 0x06;
        request[6] = (byte) (slaveId & 0xFF);

        // PDU: Function Code 0x05
        request[7] = 0x05;
        request[8] = (byte) ((address >> 8) & 0xFF);
        request[9] = (byte) (address & 0xFF);
        request[10] = (byte) (state ? 0xFF : 0x00);
        request[11] = 0x00;

        out.write(request);
        out.flush();

        byte[] response = readResponse(12);

        // Check for Modbus exception (FC | 0x80)
        if (response[7] == (byte) 0x85) {
            int exceptionCode = response[8] & 0xFF;
            throw new IOException("Modbus Exception 0x85 (Code: " + exceptionCode + ")");
        }

        return response[7] == 0x05;
    }

    /**
     * Function Code 02 (0x02): Read Discrete Inputs (Read Vision Sensor state).
     *
     * @param address Input address (0 = Vision Sensor 0)
     * @return true if sensor is active, false if clear
     */
    public synchronized boolean readDiscreteInput(int address) throws IOException {
        ensureConnected();

        int transactionId = getNextTransactionId();
        byte[] request = new byte[12];

        // MBAP Header
        request[0] = (byte) ((transactionId >> 8) & 0xFF);
        request[1] = (byte) (transactionId & 0xFF);
        request[2] = 0x00;
        request[3] = 0x00;
        request[4] = 0x00;
        request[5] = 0x06; // 6 bytes following
        request[6] = (byte) (slaveId & 0xFF);

        // PDU: Function Code 0x02
        request[7] = 0x02;
        request[8] = (byte) ((address >> 8) & 0xFF);
        request[9] = (byte) (address & 0xFF);
        request[10] = 0x00; // Quantity = 1 input
        request[11] = 0x01;

        out.write(request);
        out.flush();

        // Response format: MBAP (7 bytes) + FC (1) + ByteCount (1) + Data (1 byte) = 10 bytes
        byte[] response = readResponse(10);

        if (response[7] == (byte) 0x82) {
            int exceptionCode = response[8] & 0xFF;
            throw new IOException("Modbus Exception 0x82 (Code: " + exceptionCode + ")");
        }

        // Data byte is at index 9: bit 0 corresponds to the input state
        int dataByte = response[9] & 0xFF;
        return (dataByte & 0x01) != 0;
    }

    /**
     * Function Code 01 (0x01): Read Coils (Read current conveyor actuator states).
     *
     * @param startAddress Starting coil address (e.g. 0)
     * @param count        Number of coils to read (e.g. 3)
     * @return boolean array of coil states
     */
    public synchronized boolean[] readCoils(int startAddress, int count) throws IOException {
        ensureConnected();

        int transactionId = getNextTransactionId();
        byte[] request = new byte[12];

        // MBAP Header
        request[0] = (byte) ((transactionId >> 8) & 0xFF);
        request[1] = (byte) (transactionId & 0xFF);
        request[2] = 0x00;
        request[3] = 0x00;
        request[4] = 0x00;
        request[5] = 0x06;
        request[6] = (byte) (slaveId & 0xFF);

        // PDU: Function Code 0x01
        request[7] = 0x01;
        request[8] = (byte) ((startAddress >> 8) & 0xFF);
        request[9] = (byte) (startAddress & 0xFF);
        request[10] = (byte) ((count >> 8) & 0xFF);
        request[11] = (byte) (count & 0xFF);

        out.write(request);
        out.flush();

        int expectedBytes = (count + 7) / 8;
        int expectedResponseLength = 9 + expectedBytes;
        byte[] response = readResponse(expectedResponseLength);

        if (response[7] == (byte) 0x81) {
            int exceptionCode = response[8] & 0xFF;
            throw new IOException("Modbus Exception 0x81 (Code: " + exceptionCode + ")");
        }

        boolean[] states = new boolean[count];
        for (int i = 0; i < count; i++) {
            int byteIndex = 9 + (i / 8);
            int bitIndex = i % 8;
            states[i] = ((response[byteIndex] >> bitIndex) & 0x01) != 0;
        }

        return states;
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            connect();
        }
    }

    private int getNextTransactionId() {
        int id = transactionCounter.incrementAndGet();
        if (id > 0x7FFF) {
            transactionCounter.set(1);
        }
        return id;
    }

    private byte[] readResponse(int expectedLength) throws IOException {
        byte[] buffer = new byte[expectedLength];
        in.readFully(buffer);
        return buffer;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getSlaveId() {
        return slaveId;
    }
}
