package com.example.warehousescadasystem.model;

/**
 * Represents a warehouse inventory item / product.
 * Encapsulates SKU, pricing, stock levels, and warehouse location.
 * Implements Week 1 Core OOP & Encapsulation.
 */
public class Product {
    private int id;
    private String sku;
    private String name;
    private String category;
    private int quantity;
    private double unitPrice;
    private String location;
    private String status = "STORED";

    public Product() {
    }

    public Product(int id, String sku, String name, String category, int quantity, double unitPrice, String location) {
        this(id, sku, name, category, quantity, unitPrice, location, "STORED");
    }

    public Product(int id, String sku, String name, String category, int quantity, double unitPrice, String location, String status) {
        this.id = id;
        this.sku = sku;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
        this.location = location;
        this.status = (status != null && !status.isEmpty()) ? status : "STORED";
    }

    public Product(String sku, String name, String category, int quantity, double unitPrice, String location) {
        this(0, sku, name, category, quantity, unitPrice, location, "STORED");
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSku() {
        return sku;
    }

    public void setSku(String sku) {
        this.sku = sku;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public double getTotalValue() {
        return quantity * unitPrice;
    }

    @Override
    public String toString() {
        return "Product{" +
                "sku='" + sku + '\'' +
                ", name='" + name + '\'' +
                ", quantity=" + quantity +
                ", unitPrice=" + unitPrice +
                '}';
    }
}
