package com.example.warehousescadasystem;

import com.example.warehousescadasystem.database.DatabaseManager;
import com.example.warehousescadasystem.database.TransactionLogDao;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionLogDaoTest {

    private TransactionLogDao transactionLogDao;

    @BeforeAll
    public static void initDatabase() {
        DatabaseManager.initializeDatabase();
    }

    @BeforeEach
    public void setUp() {
        transactionLogDao = new TransactionLogDao();
        // Clear daily_transactions table before each test
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM daily_transactions;");
        } catch (Exception e) {
            fail("Failed to clean daily_transactions table: " + e.getMessage());
        }
    }

    @Test
    public void testIncrementOperationsForToday() {
        transactionLogDao.incrementPutaway(3);
        transactionLogDao.incrementDispatch(2);
        transactionLogDao.incrementInvoice(1);
        transactionLogDao.incrementAdjustment(4);

        YearMonth currentMonth = YearMonth.now();
        Map<Integer, int[]> summary = transactionLogDao.getMonthSummary(currentMonth);

        int todayDay = LocalDate.now().getDayOfMonth();
        assertTrue(summary.containsKey(todayDay), "Today's day should exist in monthly summary");

        int[] counts = summary.get(todayDay);
        assertNotNull(counts);
        assertEquals(3, counts[0], "Putaway count should match");
        assertEquals(2, counts[1], "Dispatch count should match");
        assertEquals(1, counts[2], "Invoice count should match");
        assertEquals(4, counts[3], "Adjustment count should match");

        int total = TransactionLogDao.totalOps(counts);
        assertEquals(10, total, "Total ops should equal sum of all categories");
    }

    @Test
    public void testCumulativeIncrements() {
        transactionLogDao.incrementPutaway(2);
        transactionLogDao.incrementPutaway(5);

        YearMonth currentMonth = YearMonth.now();
        Map<Integer, int[]> summary = transactionLogDao.getMonthSummary(currentMonth);

        int todayDay = LocalDate.now().getDayOfMonth();
        int[] counts = summary.get(todayDay);
        assertNotNull(counts);
        assertEquals(7, counts[0], "Cumulative putaway count should be 7");
    }

    @Test
    public void testTotalOpsNullSafe() {
        assertEquals(0, TransactionLogDao.totalOps(null), "Null counts should return 0");
        assertEquals(0, TransactionLogDao.totalOps(new int[]{0, 0, 0, 0}), "Zero array should return 0");
        assertEquals(15, TransactionLogDao.totalOps(new int[]{5, 3, 2, 5}), "Array sum should be 15");
    }

    @Test
    public void testEmptyMonthSummaryReturnsEmptyMap() {
        YearMonth pastMonth = YearMonth.of(2020, 1);
        Map<Integer, int[]> summary = transactionLogDao.getMonthSummary(pastMonth);
        assertNotNull(summary);
        assertTrue(summary.isEmpty(), "Unrecorded month should return empty map");
    }
}
