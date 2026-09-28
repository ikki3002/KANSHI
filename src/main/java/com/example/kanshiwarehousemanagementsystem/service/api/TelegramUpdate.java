package com.example.kanshiwarehousemanagementsystem.service.api;

import java.util.List;

/**
 * Data Transfer Object for deserializing Telegram Bot API getUpdates JSON responses.
 * Demonstrates JSON-to-Java mapping using Gson for inbound bot command polling.
 */
public class TelegramUpdate {

    private boolean ok;
    private List<Update> result;

    public boolean isOk() { return ok; }
    public List<Update> getResult() { return result; }

    public static class Update {
        private long update_id;
        private Message message;

        public long getUpdateId() { return update_id; }
        public Message getMessage() { return message; }
    }

    public static class Message {
        private Chat chat;
        private String text;

        public Chat getChat() { return chat; }
        public String getText() { return text; }
    }

    public static class Chat {
        private long id;

        public long getId() { return id; }
    }
}
