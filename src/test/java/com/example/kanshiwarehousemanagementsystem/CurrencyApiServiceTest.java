package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.service.api.CurrencyApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CurrencyApiServiceTest {

    private CurrencyApiService currencyService;

    @BeforeEach
    void setUp() {
        currencyService = new CurrencyApiService();
    }

    @Test
    void testSupportedCurrenciesIncludeMajorGlobalCurrencies() {
        List<String> currencies = currencyService.getSupportedCurrencies();
        assertTrue(currencies.contains("USD"));
        assertTrue(currencies.contains("EUR"));
        assertTrue(currencies.contains("GBP"));
        assertTrue(currencies.contains("JPY"));
        assertTrue(currencies.contains("CAD"));
    }

    @Test
    void testDefaultUsdConversionIsIdentity() {
        double amount = 1500.0;
        assertEquals(1500.0, currencyService.convert(amount, "USD"), 0.001);
        assertEquals(1500.0, currencyService.convert(amount, null), 0.001);
    }

    @Test
    void testConversionWithCustomRates() {
        currencyService.setCustomRate("EUR", 0.90);
        currencyService.setCustomRate("JPY", 150.0);

        assertEquals(90.0, currencyService.convert(100.0, "EUR"), 0.001);
        assertEquals(15000.0, currencyService.convert(100.0, "JPY"), 0.001);
    }

    @Test
    void testCurrencyFormatting() {
        currencyService.setCustomRate("EUR", 0.90);
        currencyService.setCustomRate("JPY", 150.0);
        currencyService.setCustomRate("GBP", 0.80);

        assertEquals("$100.00", currencyService.formatCurrency(100.0, "USD"));
        assertEquals("€90.00", currencyService.formatCurrency(100.0, "EUR"));
        assertEquals("£80.00", currencyService.formatCurrency(100.0, "GBP"));
        assertEquals("¥15,000", currencyService.formatCurrency(100.0, "JPY"));
    }

    @Test
    void testSymbolsMapping() {
        assertEquals("$", currencyService.getCurrencySymbol("USD"));
        assertEquals("€", currencyService.getCurrencySymbol("EUR"));
        assertEquals("£", currencyService.getCurrencySymbol("GBP"));
        assertEquals("¥", currencyService.getCurrencySymbol("JPY"));
        assertEquals("CA$", currencyService.getCurrencySymbol("CAD"));
        assertEquals("₹", currencyService.getCurrencySymbol("INR"));
    }
}
