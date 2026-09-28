package com.example.kanshiwarehousemanagementsystem.service.api;

import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Telegram Bot API integration service.
 * Demonstrates:
 * 1. Outbound HTTP POST for real-time warehouse operation notifications.
 * 2. Inbound long-polling via getUpdates for remote command shortcuts.
 * 3. Asynchronous non-blocking I/O with Java 21 HttpClient and ScheduledExecutorService.
 * 4. JSON serialization/deserialization with Gson.
 */
public class TelegramBotService {

    private static final String BASE_URL = "https://api.telegram.org/bot";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    private static final Gson gson = new Gson();

    private String botToken;
    private String chatId;
    private boolean connected = false;
    private long lastUpdateId = 0;

    private ScheduledExecutorService pollingExecutor;
    private InventoryDao inventoryDao;
    private Supplier<String> systemStatusSupplier;

    /**
     * Configures the bot credentials.
     */
    public void setCredentials(String botToken, String chatId) {
        this.botToken = botToken != null ? botToken.trim() : "";
        this.chatId = chatId != null ? chatId.trim() : "";
    }

    /**
     * Sets the InventoryDao for responding to /inventory commands.
     */
    public void setInventoryDao(InventoryDao dao) {
        this.inventoryDao = dao;
    }

    /**
     * Sets a supplier that returns the current system status string for /status commands.
     */
    public void setSystemStatusSupplier(Supplier<String> supplier) {
        this.systemStatusSupplier = supplier;
    }

    /**
     * Tests the connection by sending a test message and returns true if successful.
     */
    public CompletableFuture<Boolean> testConnection() {
        return sendMessage("✅ *Kanshi WMS Connected*\nTelegram Bot integration is active and receiving warehouse telemetry.")
                .thenApply(success -> {
                    this.connected = success;
                    return success;
                });
    }

    /**
     * Sends a text message to the configured chat via Telegram Bot API.
     *
     * @param text The message text (supports Markdown).
     * @return CompletableFuture resolving to true if the message was sent successfully.
     */
    public CompletableFuture<Boolean> sendMessage(String text) {
        if (botToken == null || botToken.isEmpty() || chatId == null || chatId.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        String url = BASE_URL + botToken + "/sendMessage";
        String jsonBody = gson.toJson(new SendMessagePayload(chatId, text, "Markdown"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    boolean ok = response.statusCode() == 200;
                    if (ok) {
                        this.connected = true;
                    }
                    return ok;
                })
                .exceptionally(ex -> {
                    System.err.println("[TelegramBot] Send failed: " + ex.getMessage());
                    return false;
                });
    }

    /**
     * Starts the background polling thread that checks for inbound Telegram commands.
     */
    public void startPolling() {
        if (pollingExecutor != null && !pollingExecutor.isShutdown()) {
            return;
        }
        pollingExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TelegramPollThread");
            t.setDaemon(true);
            return t;
        });
        pollingExecutor.scheduleWithFixedDelay(this::pollUpdates, 2, 10, TimeUnit.SECONDS);
    }

    /**
     * Stops the background polling thread.
     */
    public void stopPolling() {
        if (pollingExecutor != null) {
            pollingExecutor.shutdownNow();
            pollingExecutor = null;
        }
    }

    /**
     * Polls the Telegram API for new messages and processes any recognized commands.
     */
    private void pollUpdates() {
        if (botToken == null || botToken.isEmpty()) return;

        try {
            String url = BASE_URL + botToken + "/getUpdates?offset=" + (lastUpdateId + 1) + "&timeout=5";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                TelegramUpdate update = gson.fromJson(response.body(), TelegramUpdate.class);
                if (update != null && update.isOk() && update.getResult() != null) {
                    for (TelegramUpdate.Update u : update.getResult()) {
                        lastUpdateId = u.getUpdateId();
                        if (u.getMessage() != null && u.getMessage().getText() != null) {
                            String command = u.getMessage().getText().trim().toLowerCase();
                            String replyChatId = String.valueOf(u.getMessage().getChat().getId());
                            String reply = handleCommand(command);
                            if (reply != null && !reply.isEmpty()) {
                                sendReply(replyChatId, reply);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Silently ignore polling errors (network hiccups, timeouts)
        }
    }

    /**
     * Routes inbound Telegram commands to the appropriate handler.
     */
    String handleCommand(String command) {
        // Strip the @botname suffix if present (e.g., "/inventory@MyBot")
        if (command.contains("@")) {
            command = command.substring(0, command.indexOf("@"));
        }

        return switch (command) {
            case "/inventory" -> buildInventoryReport();
            case "/status" -> buildStatusReport();
            case "/help", "/start" -> buildHelpMessage();
            default -> null; // Ignore unrecognized messages
        };
    }

    private String buildInventoryReport() {
        if (inventoryDao == null) {
            return "⚠️ Inventory data not available.";
        }
        int units = inventoryDao.getTotalStockCount();
        double val = inventoryDao.getTotalValuation();
        int skus = inventoryDao.getDistinctProductCount();
        int low = inventoryDao.getLowStockCount(15);

        return String.format(
                "📦 *Kanshi Inventory Summary*\n" +
                "━━━━━━━━━━━━━━━━━━\n" +
                "Total Units: *%,d*\n" +
                "Active SKUs: *%d*\n" +
                "Total Valuation: *$%,.2f*\n" +
                "Low Stock Alerts: *%d*",
                units, skus, val, low);
    }

    private String buildStatusReport() {
        if (systemStatusSupplier != null) {
            return systemStatusSupplier.get();
        }
        return "🏭 *Kanshi System Status*\n━━━━━━━━━━━━━━━━━━\nStatus data unavailable.";
    }

    private String buildHelpMessage() {
        return "🤖 *Kanshi WMS Bot Commands*\n" +
                "━━━━━━━━━━━━━━━━━━\n" +
                "/inventory — Current stock summary\n" +
                "/status — System health & PLC status\n" +
                "/help — Show this message";
    }

    /**
     * Sends a reply to a specific chat (may differ from the configured chatId for group chats).
     */
    private void sendReply(String replyToChatId, String text) {
        if (botToken == null || botToken.isEmpty()) return;

        String url = BASE_URL + botToken + "/sendMessage";
        String jsonBody = gson.toJson(new SendMessagePayload(replyToChatId, text, "Markdown"));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
    }

    public boolean isConnected() { return connected; }

    /**
     * Internal payload class for Telegram sendMessage API JSON body.
     */
    @SuppressWarnings("unused")
    private static class SendMessagePayload {
        final String chat_id;
        final String text;
        final String parse_mode;

        SendMessagePayload(String chatId, String text, String parseMode) {
            this.chat_id = chatId;
            this.text = text;
            this.parse_mode = parseMode;
        }
    }
}
