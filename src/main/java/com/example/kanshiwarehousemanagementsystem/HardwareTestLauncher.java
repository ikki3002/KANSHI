package com.example.kanshiwarehousemanagementsystem;

import javafx.application.Application;

/**
 * Non-modular launcher for HardwareTestApp.
 * Does NOT extend Application to prevent "JavaFX runtime components are missing" on standard classpath.
 */
public class HardwareTestLauncher {
    public static void main(String[] args) {
        Application.launch(HardwareTestApp.class, args);
    }
}
