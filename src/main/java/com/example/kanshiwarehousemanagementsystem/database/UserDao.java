package com.example.kanshiwarehousemanagementsystem.database;

import com.example.kanshiwarehousemanagementsystem.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for User authentication and registration.
 * Implements industry-standard identifier lookup (Username or Email)
 * and Advanced OOP (Inheriting from BaseDao<User> and implementing CrudDao<User>).
 */
public class UserDao extends BaseDao<User> {

    @Override
    protected User mapResultSet(ResultSet rs) throws SQLException {
        return new User(
                rs.getInt("id"),
                rs.getString("email"),
                rs.getString("username"),
                rs.getString("created_at")
        );
    }

    @Override
    public List<User> getAll() {
        List<User> users = new ArrayList<>();
        String sql = "SELECT id, email, username, created_at FROM users ORDER BY username ASC;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                users.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch all users: " + e.getMessage());
        }
        return users;
    }

    @Override
    public User getById(int id) {
        String sql = "SELECT id, email, username, created_at FROM users WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch user by ID: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean add(User entity) {
        return register(entity.getEmail(), entity.getUsername(), "Default@123");
    }

    @Override
    public boolean update(User entity) {
        String sql = "UPDATE users SET email = ?, username = ? WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, entity.getEmail());
            pstmt.setString(2, entity.getUsername());
            pstmt.setInt(3, entity.getId());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed to update user: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        String sql = "DELETE FROM users WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed to delete user: " + e.getMessage());
            return false;
        }
    }

    /**
     * Authenticates a user by either Username or Email along with their password.
     */
    public User authenticate(String identifier, String password) {
        String sql = "SELECT id, email, username, created_at FROM users " +
                "WHERE (LOWER(username) = LOWER(?) OR LOWER(email) = LOWER(?)) AND password = ?";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, identifier);
            pstmt.setString(2, identifier);
            pstmt.setString(3, password);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSet(rs);
                }
            }
        } catch (SQLException e) {
            System.err.println("Authentication error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Checks whether an email is already registered.
     */
    public boolean emailExists(String email) {
        String sql = "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Email check error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Checks whether a username is already registered.
     */
    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM users WHERE LOWER(username) = LOWER(?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Username check error: " + e.getMessage());
        }
        return false;
    }

    /**
     * Registers a new user with email, username, and password.
     */
    public boolean register(String email, String username, String password) {
        String sql = "INSERT INTO users (email, username, password) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, email.trim().toLowerCase());
            pstmt.setString(2, username.trim());
            pstmt.setString(3, password);

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Registration error: " + e.getMessage());
        }
        return false;
    }
}
