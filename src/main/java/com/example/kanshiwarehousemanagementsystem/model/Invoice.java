package com.example.kanshiwarehousemanagementsystem.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing a commercial warehouse invoice.
 * Establishes a 1-to-Many relationship with InvoiceItem and a Foreign Key reference to User.
 * Demonstrates Object-Oriented encapsulation and composition.
 */
public class Invoice {
    private int id;
    private String invoiceNumber;
    private int userId;
    private String customerName;
    private double totalAmount;
    private String status;
    private String createdAt;
    private List<InvoiceItem> items;

    public Invoice(int id, String invoiceNumber, int userId, String customerName, double totalAmount, String status, String createdAt) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.userId = userId;
        this.customerName = customerName;
        this.totalAmount = totalAmount;
        this.status = status;
        this.createdAt = createdAt;
        this.items = new ArrayList<>();
    }

    public Invoice(String invoiceNumber, int userId, String customerName, double totalAmount, String status) {
        this(0, invoiceNumber, userId, customerName, totalAmount, status, "");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public int getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public List<InvoiceItem> getItems() {
        return items;
    }

    public void setItems(List<InvoiceItem> items) {
        this.items = items != null ? items : new ArrayList<>();
        recalculateTotal();
    }

    public void addItem(InvoiceItem item) {
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.add(item);
        recalculateTotal();
    }

    public void recalculateTotal() {
        if (items != null) {
            this.totalAmount = items.stream().mapToDouble(InvoiceItem::getSubtotal).sum();
        }
    }
}
