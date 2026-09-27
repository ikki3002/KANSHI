package com.example.kanshiwarehousemanagementsystem.service.api;

import java.util.Map;

/**
 * Data transfer object mapping the JSON response from the public Exchange Rates API.
 */
public class ExchangeRateResponse {
    private double amount;
    private String base;
    private String date;
    private Map<String, Double> rates;

    public ExchangeRateResponse() {
    }

    public ExchangeRateResponse(double amount, String base, String date, Map<String, Double> rates) {
        this.amount = amount;
        this.base = base;
        this.date = date;
        this.rates = rates;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Map<String, Double> getRates() {
        return rates;
    }

    public void setRates(Map<String, Double> rates) {
        this.rates = rates;
    }
}
