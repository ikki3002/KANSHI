package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.service.modbus.FactoryIOService;
import com.example.kanshiwarehousemanagementsystem.service.modbus.ModbusTcpClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ModbusServiceTest {

    @Test
    void testFactoryIOTagAddressesMatchDriverSpecification() {
        assertEquals(0, FactoryIOService.COIL_BELT_CONVEYOR_0, "Belt Conveyor 0 must map to Coil 0");
        assertEquals(1, FactoryIOService.COIL_BELT_CONVEYOR_1, "Belt Conveyor 1 must map to Coil 1");
        assertEquals(2, FactoryIOService.COIL_CURVED_CONVEYOR, "Curved Belt Conveyor must map to Coil 2");
        assertEquals(0, FactoryIOService.INPUT_VISION_SENSOR, "Vision Sensor must map to Discrete Input 0");
        assertEquals(502, FactoryIOService.DEFAULT_PORT, "Modbus TCP standard port must be 502");
        assertEquals(1, FactoryIOService.DEFAULT_SLAVE_ID, "Slave ID from driver must be 1");
    }

    @Test
    void testClientInitializationAndState() {
        ModbusTcpClient client = new ModbusTcpClient("127.0.0.1", 502, 1);
        assertFalse(client.isConnected(), "Client should be disconnected initially");
        assertEquals("127.0.0.1", client.getHost());
        assertEquals(502, client.getPort());
        assertEquals(1, client.getSlaveId());
    }

    @Test
    void testFactoryIOServiceDefaults() {
        FactoryIOService service = new FactoryIOService();
        assertFalse(service.isConnected());
        assertNotNull(service.getClient());
        assertEquals("127.0.0.1", service.getClient().getHost());
    }
}
