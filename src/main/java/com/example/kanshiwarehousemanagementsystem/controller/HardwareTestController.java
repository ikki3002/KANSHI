package com.example.kanshiwarehousemanagementsystem.controller;

import com.example.kanshiwarehousemanagementsystem.model.ModbusTag;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag.TagType;
import com.example.kanshiwarehousemanagementsystem.service.modbus.FactoryIOService;
import com.example.kanshiwarehousemanagementsystem.service.modbus.TagManager;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.IOException;
import java.net.URL;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for the Factory I/O Hardware Test Station and Tag Settings.
 * Demonstrates JavaFX dynamic UI generation, multithreading, and JSON persistence.
 */
public class HardwareTestController implements Initializable {

    // Top Connection Bar
    @FXML private TextField txtHost;
    @FXML private TextField txtPort;
    @FXML private Button btnConnect;
    @FXML private Circle circleStatus;
    @FXML private Label lblConnectionStatus;
    @FXML private Label lblActiveProfile;
    @FXML private Button btnAutoTest;
    @FXML private Button btnEstop;

    // Tab 1: Live Hardware
    @FXML private FlowPane actuatorsContainer;
    @FXML private FlowPane sensorsContainer;
    @FXML private TextArea txtLog;

    // Tab 2: Tag Settings
    @FXML private TableView<ModbusTag> tableTags;
    @FXML private TableColumn<ModbusTag, String> colTagName;
    @FXML private TableColumn<ModbusTag, String> colTagType;
    @FXML private TableColumn<ModbusTag, Number> colTagAddress;
    @FXML private TableColumn<ModbusTag, Void> colTagAction;

    @FXML private TextField txtNewTagName;
    @FXML private TextField txtNewTagAddress;
    @FXML private ComboBox<TagType> cmbNewTagType;

    private final TagManager tagManager = new TagManager();
    private final FactoryIOService ioService = new FactoryIOService();
    private final ObservableList<ModbusTag> tableData = FXCollections.observableArrayList();

    // Map to hold references to dynamic actuator tile controls: tagId -> controls
    private final Map<String, ActuatorCardRef> actuatorRefs = new ConcurrentHashMap<>();
    // Map to hold references to dynamic sensor tile controls: tagId -> controls
    private final Map<String, SensorCardRef> sensorRefs = new ConcurrentHashMap<>();
    // Sensor event counters
    private final Map<String, Integer> sensorCounters = new ConcurrentHashMap<>();

    private static class ActuatorCardRef {
        VBox card;
        Label statusPill;
        Button toggleBtn;
    }

    private static class SensorCardRef {
        Circle led;
        Label statusLabel;
        Label counterLabel;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        setupTagTable();
        setupTagForm();
        refreshDynamicHardwareUI();

        log("Hardware Test Station initialized. Tag profile loaded from " + tagManager.getConfigFile().getName());
    }

    private void setupTagTable() {
        colTagName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colTagType.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType().getDisplayName()));
        colTagAddress.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getAddress()));

        // Delete button cell
        colTagAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnDelete = new Button("Delete");

            {
                btnDelete.getStyleClass().add("btn-danger");
                btnDelete.setOnAction(e -> {
                    ModbusTag tag = getTableView().getItems().get(getIndex());
                    tagManager.removeTag(tag.getId());
                    loadTableData();
                    refreshDynamicHardwareUI();
                    log("Removed tag: " + tag.getName());
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnDelete);
            }
        });

        loadTableData();
    }

    private void setupTagForm() {
        cmbNewTagType.setItems(FXCollections.observableArrayList(TagType.COIL, TagType.DISCRETE_INPUT));
        cmbNewTagType.getSelectionModel().selectFirst();
    }

    private void loadTableData() {
        tableData.setAll(tagManager.getAllTags());
        tableTags.setItems(tableData);
    }

    /**
     * Dynamically builds actuator tiles and sensor indicator cards based on tagManager.
     */
    public void refreshDynamicHardwareUI() {
        actuatorsContainer.getChildren().clear();
        sensorsContainer.getChildren().clear();
        actuatorRefs.clear();
        sensorRefs.clear();

        // 1. Build Actuator Cards (Coils)
        List<ModbusTag> actuators = tagManager.getActuatorTags();
        for (ModbusTag tag : actuators) {
            VBox card = new VBox(10);
            card.getStyleClass().add("hardware-card");

            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(tag.getName());
            nameLabel.getStyleClass().add("tag-name-label");
            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            Label addressBadge = new Label("Coil " + tag.getAddress());
            addressBadge.getStyleClass().add("tag-badge");
            topRow.getChildren().addAll(nameLabel, spacer, addressBadge);

            HBox statusRow = new HBox(8);
            statusRow.setAlignment(Pos.CENTER_LEFT);
            Label statusPill = new Label(tag.isActive() ? "RUNNING" : "STOPPED");
            statusPill.getStyleClass().add(tag.isActive() ? "status-pill-running" : "status-pill-stopped");
            statusRow.getChildren().addAll(new Label("Status:"), statusPill);

            Button toggleBtn = new Button(tag.isActive() ? "STOP CONVEYOR" : "START CONVEYOR");
            toggleBtn.setMaxWidth(Double.MAX_VALUE);
            toggleBtn.getStyleClass().add(tag.isActive() ? "btn-estop" : "btn-primary");

            toggleBtn.setOnAction(e -> handleToggleActuator(tag));

            card.getChildren().addAll(topRow, statusRow, toggleBtn);
            actuatorsContainer.getChildren().add(card);

            ActuatorCardRef ref = new ActuatorCardRef();
            ref.card = card;
            ref.statusPill = statusPill;
            ref.toggleBtn = toggleBtn;
            actuatorRefs.put(tag.getId(), ref);
        }

        // 2. Build Sensor Cards (Discrete Inputs)
        List<ModbusTag> sensors = tagManager.getSensorTags();
        for (ModbusTag tag : sensors) {
            VBox card = new VBox(10);
            card.getStyleClass().add("hardware-card");

            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(tag.getName());
            nameLabel.getStyleClass().add("tag-name-label");
            Region spacer = new Region();
            HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
            Label addressBadge = new Label("Input " + tag.getAddress());
            addressBadge.getStyleClass().add("tag-badge");
            topRow.getChildren().addAll(nameLabel, spacer, addressBadge);

            HBox ledRow = new HBox(12);
            ledRow.setAlignment(Pos.CENTER_LEFT);
            Circle led = new Circle(10);
            led.getStyleClass().add(tag.isActive() ? "sensor-led-on" : "sensor-led-off");
            Label statusLabel = new Label(tag.isActive() ? "OBJECT DETECTED" : "CLEAR");
            statusLabel.setStyle("-fx-font-weight: bold;");
            ledRow.getChildren().addAll(led, statusLabel);

            sensorCounters.putIfAbsent(tag.getId(), 0);
            Label counterLabel = new Label("Detections: " + sensorCounters.get(tag.getId()));
            counterLabel.getStyleClass().add("station-subtitle");

            card.getChildren().addAll(topRow, ledRow, counterLabel);
            sensorsContainer.getChildren().add(card);

            SensorCardRef ref = new SensorCardRef();
            ref.led = led;
            ref.statusLabel = statusLabel;
            ref.counterLabel = counterLabel;
            sensorRefs.put(tag.getId(), ref);
        }
    }

    private void handleToggleActuator(ModbusTag tag) {
        if (!ioService.isConnected()) {
            showAlert("Not Connected", "Please connect to Factory I/O Modbus TCP server first.");
            return;
        }

        boolean newState = !tag.isActive();
        new Thread(() -> {
            try {
                boolean success = ioService.writeTag(tag, newState);
                if (success) {
                    Platform.runLater(() -> {
                        updateActuatorTileUI(tag, newState);
                        log("[ACTUATOR] " + tag.getName() + " (Coil " + tag.getAddress() + ") -> " + (newState ? "STARTED" : "STOPPED"));
                    });
                }
            } catch (IOException ex) {
                Platform.runLater(() -> log("[ERROR] Failed to write to " + tag.getName() + ": " + ex.getMessage()));
            }
        }).start();
    }

    private void updateActuatorTileUI(ModbusTag tag, boolean running) {
        ActuatorCardRef ref = actuatorRefs.get(tag.getId());
        if (ref != null) {
            ref.statusPill.setText(running ? "RUNNING" : "STOPPED");
            ref.statusPill.getStyleClass().removeAll("status-pill-running", "status-pill-stopped");
            ref.statusPill.getStyleClass().add(running ? "status-pill-running" : "status-pill-stopped");

            ref.toggleBtn.setText(running ? "STOP CONVEYOR" : "START CONVEYOR");
            ref.toggleBtn.getStyleClass().removeAll("btn-primary", "btn-estop");
            ref.toggleBtn.getStyleClass().add(running ? "btn-estop" : "btn-primary");
        }
    }

    private void updateSensorTileUI(ModbusTag tag, boolean active) {
        SensorCardRef ref = sensorRefs.get(tag.getId());
        if (ref != null) {
            ref.led.getStyleClass().removeAll("sensor-led-on", "sensor-led-off");
            ref.led.getStyleClass().add(active ? "sensor-led-on" : "sensor-led-off");
            ref.statusLabel.setText(active ? "OBJECT DETECTED" : "CLEAR");

            if (active) {
                int count = sensorCounters.compute(tag.getId(), (k, v) -> v == null ? 1 : v + 1);
                ref.counterLabel.setText("Detections: " + count);
            }
        }
    }

    @FXML
    private void handleToggleConnect(ActionEvent event) {
        if (ioService.isConnected()) {
            ioService.disconnect();
            circleStatus.setFill(Color.web("#ef4444"));
            lblConnectionStatus.setText("DISCONNECTED");
            btnConnect.setText("Connect");
            log("[INFO] Disconnected from Modbus TCP server.");
        } else {
            String host = txtHost.getText().trim();
            int port;
            try {
                port = Integer.parseInt(txtPort.getText().trim());
            } catch (NumberFormatException e) {
                showAlert("Invalid Port", "Port must be a valid integer number (default 502).");
                return;
            }

            log("[INFO] Connecting to " + host + ":" + port + "...");
            new Thread(() -> {
                try {
                    ioService.connect(host, port);
                    Platform.runLater(() -> {
                        circleStatus.setFill(Color.web("#10b981"));
                        lblConnectionStatus.setText("CONNECTED (" + port + ")");
                        btnConnect.setText("Disconnect");
                        log("[SUCCESS] Connected to Factory I/O Modbus TCP/IP Server at " + host + ":" + port);

                        // Start dynamic background sensor polling (Week 4 Concurrency)
                        ioService.startDynamicSensorPolling(tagManager.getSensorTags(), (tag, active) -> {
                            Platform.runLater(() -> {
                                updateSensorTileUI(tag, active);
                                log("[SENSOR] " + tag.getName() + " -> " + (active ? "DETECTED" : "CLEAR"));
                            });
                        }, 100);
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> {
                        circleStatus.setFill(Color.web("#ef4444"));
                        lblConnectionStatus.setText("FAILED");
                        log("[ERROR] Connection failed: " + e.getMessage());
                        showAlert("Connection Error", "Could not connect to " + host + ":" + port + "\n\nMake sure Factory I/O driver is set to Modbus TCP/IP Server and scene is running.");
                    });
                }
            }).start();
        }
    }

    @FXML
    private void handleEmergencyStop(ActionEvent event) {
        new Thread(() -> {
            ioService.emergencyStop(tagManager.getActuatorTags());
            Platform.runLater(() -> {
                for (ModbusTag tag : tagManager.getActuatorTags()) {
                    updateActuatorTileUI(tag, false);
                }
                log(">> [EMERGENCY STOP] All conveyor actuators stopped immediately!");
            });
        }).start();
    }

    @FXML
    private void handleRunAutoSequence(ActionEvent event) {
        if (!ioService.isConnected()) {
            showAlert("Not Connected", "Please connect to Factory I/O first.");
            return;
        }

        List<ModbusTag> actuators = tagManager.getActuatorTags();
        if (actuators.isEmpty()) {
            showAlert("No Actuators", "No conveyor actuators configured to test.");
            return;
        }

        btnAutoTest.setDisable(true);
        log("--- Starting Automated 2-Second Sequence Across Configured Conveyors ---");

        new Thread(() -> {
            try {
                for (ModbusTag tag : actuators) {
                    Platform.runLater(() -> {
                        log(">> Testing " + tag.getName() + " [START for 2s]...");
                        updateActuatorTileUI(tag, true);
                    });
                    ioService.writeTag(tag, true);
                    Thread.sleep(2000);

                    ioService.writeTag(tag, false);
                    Platform.runLater(() -> {
                        log(">> Testing " + tag.getName() + " [STOP]");
                        updateActuatorTileUI(tag, false);
                    });
                    Thread.sleep(400);
                }
                Platform.runLater(() -> log("--- Automated Test Sequence Completed Successfully! ---"));
            } catch (Exception ex) {
                Platform.runLater(() -> log("[ERROR] Sequence interrupted: " + ex.getMessage()));
            } finally {
                Platform.runLater(() -> btnAutoTest.setDisable(false));
            }
        }).start();
    }

    @FXML
    private void handleAddTag(ActionEvent event) {
        String name = txtNewTagName.getText() == null ? "" : txtNewTagName.getText().trim();
        String addressText = txtNewTagAddress.getText() == null ? "" : txtNewTagAddress.getText().trim();
        TagType type = cmbNewTagType.getValue();

        if (name.isEmpty() || addressText.isEmpty() || type == null) {
            showAlert("Input Error", "Please fill in all tag fields.");
            return;
        }

        int address;
        try {
            address = Integer.parseInt(addressText);
            if (address < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showAlert("Invalid Address", "Address must be a non-negative integer (e.g. 0, 1, 2).");
            return;
        }

        tagManager.addTag(name, address, type);
        loadTableData();
        refreshDynamicHardwareUI();

        txtNewTagName.clear();
        txtNewTagAddress.clear();

        log("[TAG MANAGER] Added new tag: " + name + " (" + type + " " + address + ")");
    }

    @FXML
    private void handleSaveTags(ActionEvent event) {
        boolean ok = tagManager.saveToFile();
        if (ok) {
            showAlert("Saved", "Warehouse tags successfully saved to " + tagManager.getConfigFile().getName());
            log("[TAG MANAGER] Saved configuration to JSON file.");
        } else {
            showAlert("Error", "Could not save configuration.");
        }
    }

    @FXML
    private void handleResetTags(ActionEvent event) {
        tagManager.resetToDefaults();
        tagManager.saveToFile();
        loadTableData();
        refreshDynamicHardwareUI();
        log("[TAG MANAGER] Reset tags to Factory I/O driver scene defaults.");
    }

    @FXML
    private void handleClearLog(ActionEvent event) {
        txtLog.clear();
    }

    private void log(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        txtLog.appendText("[" + timestamp + "] " + message + "\n");
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
