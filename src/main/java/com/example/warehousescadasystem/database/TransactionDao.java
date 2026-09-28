package com.example.warehousescadasystem.database;

import com.example.warehousescadasystem.model.TransactionRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for persisting operational, inventory, and financial transactions
 * to the SQLite database and reloading historical records upon application boot.
 */
public class TransactionDao {

    private final TransactionLogDao transactionLogDao = new TransactionLogDao();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Connection getConnection() throws SQLException {
        return DatabaseManager.getConnection();
    }

    /**
     * Records a unified warehouse transaction into SQLite and increments daily summary counters.
     */
    public void recordTransaction(String type, String category, String description, String details, int quantityDelta) {
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String sql = "INSERT INTO transactions (timestamp, type, category, description, details) VALUES (?, ?, ?, ?, ?);";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, timestamp);
            pstmt.setString(2, type);
            pstmt.setString(3, category);
            pstmt.setString(4, description);
            pstmt.setString(5, details != null ? details : "");
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to insert transaction into database: " + e.getMessage());
        }

        // Synchronize daily_transactions counters for calendar widget
        if (type != null) {
            switch (type.toUpperCase()) {
                case "PUTAWAY":
                    transactionLogDao.incrementPutaway(quantityDelta > 0 ? quantityDelta : 1);
                    break;
                case "DISPATCH":
                    transactionLogDao.incrementDispatch(quantityDelta > 0 ? quantityDelta : 1);
                    break;
                case "INVOICE":
                    transactionLogDao.incrementInvoice(quantityDelta > 0 ? quantityDelta : 1);
                    break;
                case "ADJUSTMENT":
                    transactionLogDao.incrementAdjustment(quantityDelta > 0 ? quantityDelta : 1);
                    break;
                default:
                    // Audit or config events do not increment numeric volume counters
                    break;
            }
        }
    }

    public void recordTransaction(String type, String category, String description) {
        recordTransaction(type, category, description, null, 1);
    }

    /**
     * Retrieves all recorded transactions ordered by ascending id (chronological order).
     */
    public List<TransactionRecord> getAllTransactions() {
        List<TransactionRecord> list = new ArrayList<>();
        String sql = "SELECT id, timestamp, type, category, description, details FROM transactions ORDER BY id ASC;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                list.add(new TransactionRecord(
                        rs.getInt("id"),
                        rs.getString("timestamp"),
                        rs.getString("type"),
                        rs.getString("category"),
                        rs.getString("description"),
                        rs.getString("details")
                ));
            }
        } catch (SQLException e) {
            System.err.println("Failed to load transactions from database: " + e.getMessage());
        }
        return list;
    }

    /**
     * Retrieves recent transactions limited by count.
     */
    public List<TransactionRecord> getRecentTransactions(int limit) {
        List<TransactionRecord> list = new ArrayList<>();
        String sql = "SELECT id, timestamp, type, category, description, details FROM transactions ORDER BY id DESC LIMIT ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(0, new TransactionRecord(
                            rs.getInt("id"),
                            rs.getString("timestamp"),
                            rs.getString("type"),
                            rs.getString("category"),
                            rs.getString("description"),
                            rs.getString("details")
                    ));
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to load recent transactions: " + e.getMessage());
        }
        return list;
    }

    public TransactionLogDao getDailyLogDao() {
        return transactionLogDao;
    }
}
