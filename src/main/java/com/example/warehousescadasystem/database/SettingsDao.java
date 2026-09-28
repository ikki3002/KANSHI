package com.example.warehousescadasystem.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Data Access Object for persistent system settings and module configurations.
 */
public class SettingsDao {

    public String getSetting(String key, String defaultValue) {
        String sql = "SELECT value FROM app_settings WHERE key = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String val = rs.getString("value");
                    return val != null ? val : defaultValue;
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch setting '" + key + "': " + e.getMessage());
        }
        return defaultValue;
    }

    public void setSetting(String key, String value) {
        String sql = "INSERT INTO app_settings (key, value) VALUES (?, ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = excluded.value;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Failed to set setting '" + key + "': " + e.getMessage());
        }
    }

    public boolean hasSetting(String key) {
        String sql = "SELECT 1 FROM app_settings WHERE key = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            return false;
        }
    }
}
