package com.example.kanshiwarehousemanagementsystem.service.api;

import com.example.kanshiwarehousemanagementsystem.database.DatabaseManager;
import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.model.Product;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

public class TelegramBotServiceTest {

    private TelegramBotService botService;
    private InventoryDao inventoryDao;

    @BeforeAll
    public static void initDatabase() {
        DatabaseManager.initializeDatabase();
        DatabaseManager.purgeWarehouseData();
    }

    @BeforeEach
    public void setUp() {
        botService = new TelegramBotService();
        inventoryDao = new InventoryDao();
        botService.setInventoryDao(inventoryDao);
    }

    @Test
    public void testSendWithBlankCredentialsFailsGracefully() {
        botService.setCredentials("", "");
        CompletableFuture<Boolean> future = botService.sendMessage("Test Message");
        assertNotNull(future);
        assertFalse(future.join(), "Sending with blank credentials should complete with false");
    }

    @Test
    public void testHelpCommandResponse() {
        String reply = botService.handleCommand("/help");
        assertNotNull(reply);
        assertTrue(reply.contains("/inventory"));
        assertTrue(reply.contains("/status"));

        String startReply = botService.handleCommand("/start");
        assertEquals(reply, startReply, "/start should produce help message");
    }

    @Test
    public void testInventoryCommandReport() {
        inventoryDao.addProduct(new Product(0, "TEST-SKU-001", "Test Package", "Packaging", 50, 10.0, "Bay-01"));

        String reply = botService.handleCommand("/inventory");
        assertNotNull(reply);
        assertTrue(reply.contains("Kanshi Inventory Summary"));
        assertTrue(reply.contains("Total Units"));
        assertTrue(reply.contains("Active SKUs"));
    }

    @Test
    public void testStatusCommandReport() {
        botService.setSystemStatusSupplier(() -> "FACTORY_IO: CONNECTED | LINE: RUNNING");

        String reply = botService.handleCommand("/status");
        assertNotNull(reply);
        assertEquals("FACTORY_IO: CONNECTED | LINE: RUNNING", reply);
    }

    @Test
    public void testCommandWithBotSuffix() {
        String reply = botService.handleCommand("/help@KanshiWmsBot");
        assertNotNull(reply);
        assertTrue(reply.contains("/inventory"), "Bot suffix @KanshiWmsBot should be stripped cleanly");
    }

    @Test
    public void testUnrecognizedCommandReturnsNull() {
        String reply = botService.handleCommand("/unknowncommand");
        assertNull(reply, "Unrecognized command should be ignored (return null)");
    }

    @Test
    public void testStartAndStopPollingIdempotent() {
        assertDoesNotThrow(() -> {
            botService.startPolling();
            botService.startPolling(); // repeated call should be safe
            botService.stopPolling();
            botService.stopPolling();  // repeated call should be safe
        });
    }
}
