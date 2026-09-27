package com.example.kanshiwarehousemanagementsystem.service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to evaluate password strength with real-time feedback and hints.
 * Simple, robust, and clean logic suitable for beginner understanding.
 */
public class PasswordValidator {

    public enum StrengthLevel {
        EMPTY("Enter a password", 0.0),
        WEAK("Weak Password", 0.33),
        MEDIUM("Moderate Password", 0.66),
        STRONG("Strong Password", 1.0);

        private final String label;
        private final double progress;

        StrengthLevel(String label, double progress) {
            this.label = label;
            this.progress = progress;
        }

        public String getLabel() {
            return label;
        }

        public double getProgress() {
            return progress;
        }
    }

    public static class ValidationResult {
        private final StrengthLevel level;
        private final double progress;
        private final String hint;
        private final boolean isAcceptable;

        public ValidationResult(StrengthLevel level, double progress, String hint, boolean isAcceptable) {
            this.level = level;
            this.progress = progress;
            this.hint = hint;
            this.isAcceptable = isAcceptable;
        }

        public StrengthLevel getLevel() {
            return level;
        }

        public double getProgress() {
            return progress;
        }

        public String getHint() {
            return hint;
        }

        public boolean isAcceptable() {
            return isAcceptable;
        }
    }

    /**
     * Evaluates password complexity and returns a structured validation result.
     */
    public static ValidationResult evaluate(String password) {
        if (password == null || password.trim().isEmpty()) {
            return new ValidationResult(StrengthLevel.EMPTY, 0.0, "Password cannot be blank", false);
        }

        boolean hasLength = password.length() >= 8;
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasDigit = password.matches(".*[0-9].*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");

        int score = 0;
        List<String> missingCriteria = new ArrayList<>();

        if (hasLength) score++;
        else missingCriteria.add("at least 8 characters");

        if (hasUpper) score++;
        else missingCriteria.add("1 uppercase letter");

        if (hasLower) score++;
        else missingCriteria.add("1 lowercase letter");

        if (hasDigit) score++;
        else missingCriteria.add("1 number");

        if (hasSpecial) score++;
        else missingCriteria.add("1 special symbol");

        if (score <= 2) {
            String hint = "Needs " + String.join(", ", missingCriteria);
            return new ValidationResult(StrengthLevel.WEAK, 0.33, hint, false);
        } else if (score <= 4) {
            String hint = missingCriteria.isEmpty() ? "Good password" : "Consider adding " + String.join(", ", missingCriteria);
            return new ValidationResult(StrengthLevel.MEDIUM, 0.66, hint, true);
        } else {
            return new ValidationResult(StrengthLevel.STRONG, 1.0, "Excellent password strength", true);
        }
    }
}
