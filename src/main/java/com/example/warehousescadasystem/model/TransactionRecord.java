package com.example.warehousescadasystem.model;


public class TransactionRecord {
    private int id;
    private String timestamp;
    private String type;
    private String category;
    private String description;
    private String details;

    public TransactionRecord() {
    }

    public TransactionRecord(int id, String timestamp, String type, String category, String description, String details) {
        this.id = id;
        this.timestamp = timestamp;
        this.type = type;
        this.category = category;
        this.description = description;
        this.details = details;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getFormattedLogLine() {
        // Extract time portion if full timestamp
        String timePart = timestamp;
        if (timestamp != null && timestamp.length() >= 19 && timestamp.contains(" ")) {
            timePart = timestamp.substring(11, 19);
        }
        return "[" + timePart + "] [" + category + "] " + description + "\n";
    }
}
