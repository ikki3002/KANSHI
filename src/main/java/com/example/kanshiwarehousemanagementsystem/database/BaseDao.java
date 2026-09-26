package com.example.kanshiwarehousemanagementsystem.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Abstract Base Data Access Object (DAO) implementing the Template Method pattern.
 * Demonstrates advanced Object-Oriented Programming (Abstract Classes, Inheritance, Code Reusability).
 *
 * @param <T> The domain entity type managed by this DAO.
 */
public abstract class BaseDao<T> implements CrudDao<T> {

    /**
     * Obtains a live database connection from the DatabaseManager.
     *
     * @return An active SQLite Connection.
     * @throws SQLException If connection fails.
     */
    protected Connection getConnection() throws SQLException {
        return DatabaseManager.getConnection();
    }

    /**
     * Abstract Template Method: Subclasses must define how a SQL ResultSet row
     * maps to the concrete domain object T.
     *
     * @param rs The active SQL ResultSet cursor.
     * @return The mapped domain entity.
     * @throws SQLException If reading column data fails.
     */
    protected abstract T mapResultSet(ResultSet rs) throws SQLException;

    /**
     * Safely rolls back an uncommitted transaction without throwing exceptions.
     *
     * @param conn The connection to rollback.
     */
    protected void rollbackQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.rollback();
            } catch (SQLException e) {
                System.err.println("Failed to rollback transaction: " + e.getMessage());
            }
        }
    }

    /**
     * Safely closes a connection quietly.
     *
     * @param conn The connection to close.
     */
    protected void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException ignored) {
            }
        }
    }
}
