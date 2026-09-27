package com.example.kanshiwarehousemanagementsystem.service.api;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service that exchanges JSON data with the public Frankfurter Exchange Rates REST API.
 * Demonstrates:
 * 1. Java 21 java.net.http.HttpClient for REST communication over HTTPS.
 * 2. Asynchronous non-blocking network I/O with CompletableFuture.
 * 3. JSON deserialization using Google Gson.
 * 4. Resilient caching and offline fallback for enterprise multi-currency WMS accounting.
 */
public class CurrencyApiService {

    private static final String API_URL = "https://api.frankfurter.dev/v1/latest?base=USD";
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson gson = new Gson();

    private final Map<String, Double> rates = new ConcurrentHashMap<>();
    private String lastUpdatedDate = "Default Baseline";
    private boolean isLiveApiConnected = false;

    public CurrencyApiService() {
        initDefaultFallbackRates();
    }

    /**
     * Initializes standard exchange rate fallbacks to guarantee uninterrupted WMS operations
     * even when completely offline or during disconnected academic demonstrations.
     */
    private void initDefaultFallbackRates() {
        rates.put("USD", 1.0);
        rates.put("EUR", 0.88);
        rates.put("GBP", 0.75);
        rates.put("JPY", 155.0);
        rates.put("CAD", 1.40);
        rates.put("AUD", 1.45);
        rates.put("CHF", 0.85);
        rates.put("CNY", 7.20);
        rates.put("INR", 85.0);
    }

    /**
     * Asynchronously fetches live exchange rates from the REST API without blocking the JavaFX UI thread.
     */
    public CompletableFuture<Boolean> fetchLiveRatesAsync() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(6))
                .header("Accept", "application/json")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        ExchangeRateResponse data = gson.fromJson(response.body(), ExchangeRateResponse.class);
                        if (data != null && data.getRates() != null && !data.getRates().isEmpty()) {
                            rates.put("USD", 1.0);
                            rates.putAll(data.getRates());
                            this.lastUpdatedDate = data.getDate();
                            this.isLiveApiConnected = true;
                            return true;
                        }
                    }
                    return false;
                })
                .exceptionally(ex -> {
                    System.err.println("[CurrencyApiService] Notice: API unreachable (" + ex.getMessage() + "). Continuing with fallback rates.");
                    this.isLiveApiConnected = false;
                    return false;
                });
    }

    /**
     * Converts a USD amount to the specified target currency.
     */
    public double convert(double amountInUsd, String targetCurrency) {
        if (targetCurrency == null || "USD".equalsIgnoreCase(targetCurrency)) {
            return amountInUsd;
        }
        Double rate = rates.get(targetCurrency.toUpperCase());
        if (rate == null) {
            return amountInUsd;
        }
        return amountInUsd * rate;
    }

    /**
     * Gets the current conversion rate for a target currency against 1 USD.
     */
    public double getRate(String targetCurrency) {
        if (targetCurrency == null || "USD".equalsIgnoreCase(targetCurrency)) {
            return 1.0;
        }
        return rates.getOrDefault(targetCurrency.toUpperCase(), 1.0);
    }

    /**
     * Formats a USD amount in the target currency with proper currency symbols and decimal places.
     */
    public String formatCurrency(double amountInUsd, String targetCurrency) {
        String curr = targetCurrency == null ? "USD" : targetCurrency.toUpperCase();
        double converted = convert(amountInUsd, curr);
        String symbol = getCurrencySymbol(curr);
        if ("JPY".equals(curr)) {
            return String.format("%s%,.0f", symbol, converted);
        }
        return String.format("%s%,.2f", symbol, converted);
    }

    /**
     * Returns standard international currency symbols.
     */
    public String getCurrencySymbol(String currency) {
        if (currency == null) return "$";
        return switch (currency.toUpperCase()) {
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "JPY" -> "¥";
            case "CAD" -> "CA$";
            case "AUD" -> "A$";
            case "CHF" -> "CHF ";
            case "CNY" -> "¥";
            case "INR" -> "₹";
            default -> "$";
        };
    }

    public List<String> getSupportedCurrencies() {
        return List.of("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "CHF", "CNY", "INR");
    }

    public boolean isLiveApiConnected() {
        return isLiveApiConnected;
    }

    public String getLastUpdatedDate() {
        return lastUpdatedDate;
    }

    public Map<String, Double> getAllRates() {
        return Collections.unmodifiableMap(rates);
    }

    /**
     * Allows injecting rates (useful for deterministic unit testing).
     */
    public void setCustomRate(String currency, double rate) {
        rates.put(currency.toUpperCase(), rate);
    }
}
