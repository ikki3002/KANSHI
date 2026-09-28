package com.example.warehousescadasystem;

import com.example.warehousescadasystem.database.DatabaseManager;
import com.example.warehousescadasystem.database.InvoiceDao;
import com.example.warehousescadasystem.model.Invoice;
import com.example.warehousescadasystem.model.InvoiceItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseRelationshipTest {

    private static InvoiceDao invoiceDao;

    @org.junit.jupiter.api.BeforeAll
    public static void setUp() {
        DatabaseManager.initializeDatabase();
        DatabaseManager.purgeWarehouseData();
        invoiceDao = new InvoiceDao();

        com.example.warehousescadasystem.database.InventoryDao inventoryDao = new com.example.warehousescadasystem.database.InventoryDao();
        inventoryDao.addProduct(new com.example.warehousescadasystem.model.Product(0, "BOX-SML-101", "Standard Cardboard Box (Small)", "Packaging", 150, 12.50, "Bay-01"));
        inventoryDao.addProduct(new com.example.warehousescadasystem.model.Product(0, "PAL-EUR-201", "Euro Pallet EPAL-1 Heavy Duty", "Material Handling", 40, 35.00, "Bay-12"));

        List<com.example.warehousescadasystem.model.Product> products = inventoryDao.getAll();
        int prodId1 = products.get(0).getId();
        int prodId2 = products.get(1).getId();

        Invoice initialInvoice = new Invoice("INV-2026-001", 1, "Global Logistics Corp", 305.00, "PAID");
        initialInvoice.addItem(new InvoiceItem(prodId1, products.get(0).getSku(), products.get(0).getName(), 10, 12.50));
        initialInvoice.addItem(new InvoiceItem(prodId2, products.get(1).getSku(), products.get(1).getName(), 5, 36.00));
        invoiceDao.add(initialInvoice);
    }

    @org.junit.jupiter.api.AfterAll
    public static void tearDown() {
        DatabaseManager.purgeWarehouseData();
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
        List<com.example.warehousescadasystem.model.Product> products = new com.example.warehousescadasystem.database.InventoryDao().getAll();
        int prodId1 = products.get(0).getId();
        int prodId2 = products.get(1).getId();

        // 1. Create a new Invoice for user ID 1 (user@gmail.com)
        String invoiceNum = "TEST-INV-" + System.currentTimeMillis();
        Invoice newInvoice = new Invoice(invoiceNum, 1, "Acme Logistics Test", 150.0, "PENDING");
        newInvoice.addItem(new InvoiceItem(prodId1, products.get(0).getSku(), products.get(0).getName(), 4, 12.50));
        newInvoice.addItem(new InvoiceItem(prodId2, products.get(1).getSku(), products.get(1).getName(), 5, 20.00));

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
