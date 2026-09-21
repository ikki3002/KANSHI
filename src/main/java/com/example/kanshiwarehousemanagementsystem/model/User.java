package com.example.kanshiwarehousemanagementsystem.model;

/**
 * User model representing a warehouse system user.
 * Demonstrates Week 1 Core OOP concepts (encapsulation, constructors, getters/setters).
 */
public class User {
    private int id;
    private String email;
    private String username;
    private String createdAt;

    public User() {
    }

    public User(int id, String email, String username, String createdAt) {
        this.id = id;
        this.email = email;
        this.username = username;
        this.createdAt = createdAt;
    }

    public User(String email, String username) {
        this.email = email;
        this.username = username;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", email='" + email + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
}
