package com.example.kanshiwarehousemanagementsystem.service.modbus;

import java.io.IOException;
import java.util.Scanner;

/**
 * Standalone Modbus TCP Verification Runner for Factory I/O.
 * Demonstrates basic hardware communication, actuator toggling, and sensor reading.
 * Run directly via main() to verify that Factory I/O actuators and sensors respond.
 */
public class ModbusVerificationRunner {

    public static void main(String[] args) {
        System.out.println("===============================================================");
        System.out.println("   KANSHI WMS & SCADA // FACTORY I/O MODBUS BRIDGE TEST");
        System.out.println("===============================================================");

        String host = "127.0.0.1";
        if (args.length > 0 && args[0] != null && !args[0].trim().isEmpty()) {
            host = args[0].trim();
        }

        int port = FactoryIOService.DEFAULT_PORT;
        int slaveId = FactoryIOService.DEFAULT_SLAVE_ID;

        System.out.println("[INFO] Attempting connection to Modbus TCP Server: " + host + ":" + port + " (Slave ID: " + slaveId + ")");

        FactoryIOService service = new FactoryIOService(host, port, slaveId);

        try {
            service.connect();
        } catch (IOException e) {
            System.err.println("[WARN] Could not connect to " + host + ":" + port + " (" + e.getMessage() + ")");
            // Fallback to IP from driver screenshot if localhost failed
            if ("127.0.0.1".equals(host)) {
                String fallbackHost = "192.170.20.103";
                System.out.println("[INFO] Attempting fallback to driver IP: " + fallbackHost + ":" + port);
                try {
                    service.connect(fallbackHost, port);
                    host = fallbackHost;
                } catch (IOException ex) {
                    System.err.println("[ERROR] Fallback connection also failed: " + ex.getMessage());
                    printTroubleshootingTips();
                    return;
                }
            } else {
                printTroubleshootingTips();
                return;
            }
        }

        System.out.println("[SUCCESS] Connected to Factory I/O Modbus TCP/IP Server at " + host + ":" + port + "!");
        System.out.println();

        // 1. Initial State Check
        try {
            boolean sensorState = service.isVisionSensorDetected();
            System.out.println(">> Vision Sensor 0 (Input 0): " + (sensorState ? "[TRIGGERED / ACTIVE]" : "[CLEAR / IDLE]"));
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to read Vision Sensor 0: " + e.getMessage());
        }

        // 2. Automated Actuator Test Sequence
        System.out.println();
        System.out.println("--- Starting Automated 2-Second Conveyor Test Sequence ---");

        try {
            System.out.print(">> Testing Coil 0: Belt Conveyor (6m) 0 [START] ... ");
            service.setBeltConveyor0(true);
            System.out.println("RUNNING (Look at Factory I/O)");
            Thread.sleep(2000);
            service.setBeltConveyor0(false);
            System.out.println(">> Testing Coil 0: Belt Conveyor (6m) 0 [STOP]");

            Thread.sleep(500);

            System.out.print(">> Testing Coil 1: Belt Conveyor (6m) 1 [START] ... ");
            service.setBeltConveyor1(true);
            System.out.println("RUNNING (Look at Factory I/O)");
            Thread.sleep(2000);
            service.setBeltConveyor1(false);
            System.out.println(">> Testing Coil 1: Belt Conveyor (6m) 1 [STOP]");

            Thread.sleep(500);

            System.out.print(">> Testing Coil 2: Curved Belt Conveyor 0 CW [START] ... ");
            service.setCurvedConveyor(true);
            System.out.println("RUNNING (Look at Factory I/O)");
            Thread.sleep(2000);
            service.setCurvedConveyor(false);
            System.out.println(">> Testing Coil 2: Curved Belt Conveyor 0 CW [STOP]");

            System.out.println("--- Automated Actuator Sequence Completed Successfully! ---");
            System.out.println();

        } catch (Exception e) {
            System.err.println("[ERROR] Actuator test error: " + e.getMessage());
        }

        // 3. Interactive Manual Control Mode
        System.out.println("===============================================================");
        System.out.println("               INTERACTIVE HARDWARE CONTROL MODE");
        System.out.println("===============================================================");
        System.out.println("Commands:");
        System.out.println("  [1] Toggle Belt Conveyor (6m) 0");
        System.out.println("  [2] Toggle Belt Conveyor (6m) 1");
        System.out.println("  [3] Toggle Curved Belt Conveyor 0 CW");
        System.out.println("  [v] Read Vision Sensor 0");
        System.out.println("  [e] EMERGENCY STOP (Stop all conveyors)");
        System.out.println("  [q] Quit and disconnect");
        System.out.println("---------------------------------------------------------------");

        boolean c0 = false;
        boolean c1 = false;
        boolean c2 = false;

        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("Enter command [1/2/3/v/e/q]: ");
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.equals("q")) {
                break;
            }

            try {
                switch (input) {
                    case "1" -> {
                        c0 = !c0;
                        service.setBeltConveyor0(c0);
                        System.out.println(">> Belt Conveyor 0 is now: " + (c0 ? "[RUNNING]" : "[STOPPED]"));
                    }
                    case "2" -> {
                        c1 = !c1;
                        service.setBeltConveyor1(c1);
                        System.out.println(">> Belt Conveyor 1 is now: " + (c1 ? "[RUNNING]" : "[STOPPED]"));
                    }
                    case "3" -> {
                        c2 = !c2;
                        service.setCurvedConveyor(c2);
                        System.out.println(">> Curved Conveyor is now: " + (c2 ? "[RUNNING]" : "[STOPPED]"));
                    }
                    case "v" -> {
                        boolean v = service.isVisionSensorDetected();
                        System.out.println(">> Vision Sensor 0 status: " + (v ? "[OBJECT DETECTED]" : "[CLEAR]"));
                    }
                    case "e" -> {
                        service.emergencyStop();
                        c0 = false;
                        c1 = false;
                        c2 = false;
                        System.out.println(">> [EMERGENCY STOP EXECUTED]: All conveyors stopped.");
                    }
                    default -> System.out.println("Unknown command. Use 1, 2, 3, v, e, or q.");
                }
            } catch (IOException ex) {
                System.err.println("[COMMUNICATION ERROR] " + ex.getMessage());
            }
        }

        // Clean disconnect
        try {
            service.emergencyStop();
        } catch (Exception ignored) {}
        service.disconnect();
        System.out.println("[INFO] Disconnected safely. Modbus test completed.");
    }

    private static void printTroubleshootingTips() {
        System.out.println();
        System.out.println("------------------------- TROUBLESHOOTING -------------------------");
        System.out.println("1. In Factory I/O, ensure the Driver is set to 'Modbus TCP/IP Server'.");
        System.out.println("2. Check that the green checkmark appears next to the Driver name.");
        System.out.println("3. Ensure the Factory I/O scene is in PLAY / RUN mode (press the Play button in Factory I/O).");
        System.out.println("4. Default port is 502. If Windows firewall prompts, allow Java access.");
        System.out.println("5. You can pass the custom IP as argument, e.g.:");
        System.out.println("   java ModbusVerificationRunner 192.170.20.103");
        System.out.println("-------------------------------------------------------------------");
    }
}
