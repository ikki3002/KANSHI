package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.database.DatabaseManager;
import com.example.kanshiwarehousemanagementsystem.database.InvoiceDao;
import com.example.kanshiwarehousemanagementsystem.model.Invoice;
import com.example.kanshiwarehousemanagementsystem.model.InvoiceItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseRelationshipTest {

    private static InvoiceDao invoiceDao;

    @BeforeAll
    public static void setUp() {
        DatabaseManager.initializeDatabase();
        invoiceDao = new InvoiceDao();
    }

    @Test
    public void testInvoicesAndLineItemsRelationalJoin() {
        List<Invoice> invoices = invoiceDao.getAll();
        assertNotNull(invoices);
        assertFalse(invoices.isEmpty(), "Database should contain at least one seeded relational invoice");

        Invoice firstInvoice = invoiceDao.getById(invoices.get(0).getId());
        assertNotNull(firstInvoice);
        assertNotNull(firstInvoice.getItems());
        assertFalse(firstInvoice.getItems().isEmpty(), "Invoice should contain related line items via Foreign Key JOIN");

        // Verify that JOIN brought back product SKU and product name from inventory table
        InvoiceItem item = firstInvoice.getItems().get(0);
        assertTrue(item.getProductId() > 0);
        assertNotNull(item.getProductSku(), "Relational JOIN should populate productSku");
        assertNotNull(item.getProductName(), "Relational JOIN should populate productName");
    }

    @Test
    public void testInvoiceCreationAndCascadeDeletion() {
        // 1. Create a new Invoice for user ID 1 (user@gmail.com)
        String invoiceNum = "TEST-INV-" + System.currentTimeMillis();
        Invoice newInvoice = new Invoice(invoiceNum, 1, "Acme Logistics Test", 150.0, "PENDING");
        newInvoice.addItem(new InvoiceItem(1, "BOX-SML-101", "Standard Cardboard Box (Small)", 4, 12.50));
        newInvoice.addItem(new InvoiceItem(2, "BOX-MED-102", "Heavy Duty Corrugated Box (Med)", 5, 20.00));

        boolean added = invoiceDao.add(newInvoice);
        assertTrue(added, "Adding relational invoice with line items in transaction should succeed");
        assertTrue(newInvoice.getId() > 0, "Invoice should have generated primary key ID");

        // 2. Fetch invoice and verify items exist in child table
        Invoice fetched = invoiceDao.getById(newInvoice.getId());
        assertNotNull(fetched);
        assertEquals(2, fetched.getItems().size(), "Invoice should have exactly 2 line items in child table");

        // 3. Test Foreign Key Cascade Delete
        boolean deleted = invoiceDao.delete(newInvoice.getId());
        assertTrue(deleted, "Deleting parent invoice should succeed");

        // Verify parent is gone
        assertNull(invoiceDao.getById(newInvoice.getId()));

        // Verify child invoice_items are automatically deleted by SQLite ON DELETE CASCADE
        List<InvoiceItem> orphanedItems = invoiceDao.getItemsForInvoice(newInvoice.getId());
        assertTrue(orphanedItems.isEmpty(), "Child invoice_items MUST be automatically deleted via SQLite ON DELETE CASCADE");
    }

    @Test
    public void testForeignKeyViolationRejection() {
        // Attempt to insert an invoice referencing non-existent user_id = 999999
        String sql = "INSERT INTO invoices (invoice_number, user_id, customer_name, total_amount, status) " +
                "VALUES ('FAIL-INV-999', 999999, 'Non-existent User Customer', 100.0, 'PAID');";

        assertThrows(SQLException.class, () -> {
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.executeUpdate();
            }
        }, "Inserting invoice with invalid user_id must throw Foreign Key constraint SQLException");
    }
}
