package com.example.kanshiwarehousemanagementsystem.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Access Object for daily warehouse transaction summaries.
 * Provides upsert operations for incrementing daily counters and
 * monthly summary queries for the calendar widget.
 */
public class TransactionLogDao {

    private Connection getConnection() throws SQLException {
        return DatabaseManager.getConnection();
    }

    /**
     * Increments the putaway counter for today's date.
     */
    public void incrementPutaway(int count) {
        upsertColumn("putaway_count", count);
    }

    /**
     * Increments the dispatch counter for today's date.
     */
    public void incrementDispatch(int count) {
        upsertColumn("dispatch_count", count);
    }

    /**
     * Increments the invoice counter for today's date.
     */
    public void incrementInvoice(int count) {
        upsertColumn("invoice_count", count);
    }

    /**
     * Increments the stock adjustment counter for today's date.
     */
    public void incrementAdjustment(int count) {
        upsertColumn("adjustment_count", count);
    }

    /**
     * Upserts a specific column counter for today's date using SQLite's INSERT OR IGNORE + UPDATE pattern.
     */
    private void upsertColumn(String column, int delta) {
        String today = LocalDate.now().toString();
        String insertSql = "INSERT OR IGNORE INTO daily_transactions (date) VALUES (?);";
        // Column name is hardcoded from internal callers, not user input, so concatenation is safe here.
        String updateSql = "UPDATE daily_transactions SET " + column + " = " + column + " + ? WHERE date = ?;";

        try (Connection conn = getConnection()) {
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                insertStmt.setString(1, today);
                insertStmt.executeUpdate();
            }
            try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                updateStmt.setInt(1, delta);
                updateStmt.setString(2, today);
                updateStmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Failed to upsert daily transaction log: " + e.getMessage());
        }
    }

    /**
     * Retrieves all daily transaction records for a given month.
     *
     * @param yearMonth The year-month to query (e.g., September 2026).
     * @return Map of day-of-month (1–31) to an int[4] array: [putaway, dispatch, invoice, adjustment].
     */
    public Map<Integer, int[]> getMonthSummary(YearMonth yearMonth) {
        Map<Integer, int[]> result = new LinkedHashMap<>();
        String startDate = yearMonth.atDay(1).toString();
        String endDate = yearMonth.atEndOfMonth().toString();

        String sql = "SELECT date, putaway_count, dispatch_count, invoice_count, adjustment_count " +
                "FROM daily_transactions WHERE date >= ? AND date <= ? ORDER BY date;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, startDate);
            pstmt.setString(2, endDate);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = LocalDate.parse(rs.getString("date"));
                    int dayOfMonth = date.getDayOfMonth();
                    int[] counts = new int[]{
                            rs.getInt("putaway_count"),
                            rs.getInt("dispatch_count"),
                            rs.getInt("invoice_count"),
                            rs.getInt("adjustment_count")
                    };
                    result.put(dayOfMonth, counts);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to query monthly transaction summary: " + e.getMessage());
        }
        return result;
    }

    /**
     * Returns the total operation count for a given day's data array.
     */
    public static int totalOps(int[] counts) {
        if (counts == null) return 0;
        return counts[0] + counts[1] + counts[2] + counts[3];
    }
}
