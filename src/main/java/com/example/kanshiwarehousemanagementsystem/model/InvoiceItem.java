package com.example.kanshiwarehousemanagementsystem.model;

/**
 * Line item model establishing the many-to-many relationship between Invoices and Inventory products.
 * Demonstrates Object-Oriented encapsulation and Relational Foreign Key mapping.
 */
public class InvoiceItem {
    private int id;
    private int invoiceId;
    private int productId;
    private String productSku;
    private String productName;
    private int quantity;
    private double unitPrice;
    private double subtotal;

    public InvoiceItem(int id, int invoiceId, int productId, String productSku, String productName, int quantity, double unitPrice, double subtotal) {
        this.id = id;
        this.invoiceId = invoiceId;
        this.productId = productId;
        this.productSku = productSku;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.subtotal = subtotal;
    }

    public InvoiceItem(int productId, String productSku, String productName, int quantity, double unitPrice) {
        this(0, 0, productId, productSku, productName, quantity, unitPrice, quantity * unitPrice);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(int invoiceId) {
        this.invoiceId = invoiceId;
    }

    public int getProductId() {
        return productId;
    }

    public void setProductId(int productId) {
        this.productId = productId;
    }

    public String getProductSku() {
        return productSku;
    }

    public void setProductSku(String productSku) {
        this.productSku = productSku;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
        this.subtotal = this.quantity * this.unitPrice;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
        this.subtotal = this.quantity * this.unitPrice;
    }

    public double getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(double subtotal) {
        this.subtotal = subtotal;
    }
}
