package com.example.kanshiwarehousemanagementsystem;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/**
 * JavaFX Application class for the Hardware Test Station.
 * Launched via HardwareTestLauncher.
 */
public class HardwareTestApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HardwareTestApp.class.getResource("hardware-test-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1150, 760);

        URL cssResource = HardwareTestApp.class.getResource("css/industrial-dark.css");
        if (cssResource != null) {
            scene.getStylesheets().add(cssResource.toExternalForm());
        }

        stage.setTitle("KANSHI WMS // Factory I/O Hardware Test Station & Tag Profiler");
        stage.setMinWidth(1000);
        stage.setMinHeight(650);
        stage.setScene(scene);
        stage.show();
    }
}
