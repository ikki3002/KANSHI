package com.example.kanshiwarehousemanagementsystem.database;

import com.example.kanshiwarehousemanagementsystem.model.Invoice;
import com.example.kanshiwarehousemanagementsystem.model.InvoiceItem;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object (DAO) for warehouse Invoicing operations.
 * Demonstrates Week 6 Relational DB Foreign Key constraints, Multi-Table JOINs,
 * SQL Transactions (commit/rollback), and BaseDao inheritance.
 */
public class InvoiceDao extends BaseDao<Invoice> {

    @Override
    protected Invoice mapResultSet(ResultSet rs) throws SQLException {
        return new Invoice(
                rs.getInt("id"),
                rs.getString("invoice_number"),
                rs.getInt("user_id"),
                rs.getString("customer_name"),
                rs.getDouble("total_amount"),
                rs.getString("status"),
                rs.getString("created_at")
        );
    }

    @Override
    public List<Invoice> getAll() {
        List<Invoice> invoices = new ArrayList<>();
        String sql = "SELECT id, invoice_number, user_id, customer_name, total_amount, status, created_at " +
                "FROM invoices ORDER BY id DESC;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                invoices.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch all invoices: " + e.getMessage());
        }
        return invoices;
    }

    @Override
    public Invoice getById(int id) {
        String sql = "SELECT id, invoice_number, user_id, customer_name, total_amount, status, created_at " +
                "FROM invoices WHERE id = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    Invoice invoice = mapResultSet(rs);
                    invoice.setItems(getItemsForInvoice(invoice.getId()));
                    return invoice;
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch invoice by ID: " + e.getMessage());
        }
        return null;
    }

    @Override
    public boolean add(Invoice entity) {
        String insertInvoiceSql = "INSERT INTO invoices (invoice_number, user_id, customer_name, total_amount, status) " +
                "VALUES (?, ?, ?, ?, ?);";
        String insertItemSql = "INSERT INTO invoice_items (invoice_id, product_id, quantity, unit_price, subtotal) " +
                "VALUES (?, ?, ?, ?, ?);";

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Begin ACID Transaction

            int generatedInvoiceId;
            try (PreparedStatement pstmt = conn.prepareStatement(insertInvoiceSql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, entity.getInvoiceNumber());
                pstmt.setInt(2, entity.getUserId());
                pstmt.setString(3, entity.getCustomerName());
                pstmt.setDouble(4, entity.getTotalAmount());
                pstmt.setString(5, entity.getStatus() != null ? entity.getStatus() : "PAID");

                int affected = pstmt.executeUpdate();
                if (affected == 0) {
                    conn.rollback();
                    return false;
                }

                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        generatedInvoiceId = generatedKeys.getInt(1);
                        entity.setId(generatedInvoiceId);
                    } else {
                        conn.rollback();
                        return false;
                    }
                }
            }

            // Insert related line items linking to inventory products
            if (entity.getItems() != null && !entity.getItems().isEmpty()) {
                try (PreparedStatement itemStmt = conn.prepareStatement(insertItemSql)) {
                    for (InvoiceItem item : entity.getItems()) {
                        itemStmt.setInt(1, generatedInvoiceId);
                        itemStmt.setInt(2, item.getProductId());
                        itemStmt.setInt(3, item.getQuantity());
                        itemStmt.setDouble(4, item.getUnitPrice());
                        itemStmt.setDouble(5, item.getSubtotal());
                        itemStmt.addBatch();
                    }
                    itemStmt.executeBatch();
                }
            }

            conn.commit(); // Commit Transaction
            return true;

        } catch (SQLException e) {
            System.err.println("Transaction failed while adding invoice: " + e.getMessage());
            rollbackQuietly(conn);
            return false;
        } finally {
            closeQuietly(conn);
        }
    }

    @Override
    public boolean update(Invoice entity) {
        String sql = "UPDATE invoices SET customer_name = ?, status = ?, total_amount = ? WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, entity.getCustomerName());
            pstmt.setString(2, entity.getStatus());
            pstmt.setDouble(3, entity.getTotalAmount());
            pstmt.setInt(4, entity.getId());

            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed to update invoice: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean delete(int id) {
        // SQLite foreign keys ON DELETE CASCADE will automatically delete corresponding invoice_items
        String sql = "DELETE FROM invoices WHERE id = ?;";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("Failed to delete invoice: " + e.getMessage());
            return false;
        }
    }

    /**
     * Multi-Table Relational JOIN: Fetches line items for an invoice with product SKU and name.
     */
    public List<InvoiceItem> getItemsForInvoice(int invoiceId) {
        List<InvoiceItem> items = new ArrayList<>();
        String sql = "SELECT ii.id, ii.invoice_id, ii.product_id, ii.quantity, ii.unit_price, ii.subtotal, " +
                "p.sku AS product_sku, p.name AS product_name " +
                "FROM invoice_items ii " +
                "JOIN inventory p ON ii.product_id = p.id " +
                "WHERE ii.invoice_id = ?;";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, invoiceId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    InvoiceItem item = new InvoiceItem(
                            rs.getInt("id"),
                            rs.getInt("invoice_id"),
                            rs.getInt("product_id"),
                            rs.getString("product_sku"),
                            rs.getString("product_name"),
                            rs.getInt("quantity"),
                            rs.getDouble("unit_price"),
                            rs.getDouble("subtotal")
                    );
                    items.add(item);
                }
            }
        } catch (SQLException e) {
            System.err.println("Failed to fetch invoice items: " + e.getMessage());
        }
        return items;
    }

    /**
     * Counts all line items across all invoices to verify referential integrity.
     */
    public int countTotalInvoiceItems() {
        String sql = "SELECT COUNT(*) FROM invoice_items;";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("Failed to count invoice items: " + e.getMessage());
        }
        return 0;
    }
}
