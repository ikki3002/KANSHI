package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.database.DatabaseManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * Main JavaFX Application bootstrap for Kanshi WMS & SCADA.
 * Configured for non-modular execution with maximized screen sizing.
 */
public class HelloApplication extends Application {

    @Override
    public void init() {
        // Initialize SQLite database and seed initial accounts
        DatabaseManager.initializeDatabase();
    }
    @Override
    public void start(Stage stage) throws IOException {
        URL fxmlLocation = HelloApplication.class.getResource("login-view.fxml");
        if (fxmlLocation == null) {
            throw new IllegalStateException("CRITICAL ERROR: 'login-view.fxml' was not found on classpath. Please run 'Build -> Rebuild Project' in IntelliJ or 'mvn compile'.");
        }
        FXMLLoader fxmlLoader = new FXMLLoader(fxmlLocation);
        Scene scene = new Scene(fxmlLoader.load());

        // Apply White + Dark Red Cherry industrial stylesheet with Segoe UI typography
        URL cssResource = HelloApplication.class.getResource("css/industrial-dark.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        stage.setTitle("KANSHI WMS");

        // Set generous minimum bounds and open in maximized window state
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setMaximized(true);

        // Clean exit handler on window close
        stage.setOnCloseRequest(event -> {
            javafx.application.Platform.exit();
            System.exit(0);
        });

        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
