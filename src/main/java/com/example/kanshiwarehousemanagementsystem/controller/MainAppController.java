package com.example.kanshiwarehousemanagementsystem.controller;

import com.example.kanshiwarehousemanagementsystem.HelloApplication;
import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag.TagType;
import com.example.kanshiwarehousemanagementsystem.model.User;
import com.example.kanshiwarehousemanagementsystem.service.modbus.FactoryIOService;
import com.example.kanshiwarehousemanagementsystem.service.modbus.TagManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Controller for the integrated Kanshi WMS Main Application.
 * Implements 3-Level Architecture: Level 1 Command Hub, Level 2 Workspaces (SCADA, Inventory, Finance, Tags).
 */
public class MainAppController implements Initializable {

    // Top Header & Real-Time Telemetry
    @FXML private Label lblOperatorEmail;
    @FXML private Label lblClock;
    @FXML private Circle circleHeartbeat;
    @FXML private Label lblHeartbeatStatus;

    // Navigation Rail Buttons
    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavScada;
    @FXML private Button btnNavInventory;
    @FXML private Button btnNavFinance;
    @FXML private Button btnNavTags;

    // Workspace View Containers
    @FXML private StackPane mainContentPane;
    @FXML private ScrollPane paneDashboard;
    @FXML private VBox paneScada;
    @FXML private VBox paneInventory;
    @FXML private VBox paneFinance;
    @FXML private VBox paneTags;

    // Level 1 KPI Overview Labels
    @FXML private Label lblKpiValuation;
    @FXML private Label lblKpiInventory;
    @FXML private Label lblKpiLineState;
    @FXML private Label lblKpiPackageCount;

    // Connection Bar (SCADA View)
    @FXML private TextField txtHost;
    @FXML private TextField txtPort;
    @FXML private Button btnConnect;
    @FXML private Circle circleStatus;
    @FXML private Label lblConnectionStatus;
    @FXML private Button btnAutoTest;
    @FXML private Button btnEstop;

    // Live Hardware Controls
    @FXML private FlowPane actuatorsContainer;
    @FXML private FlowPane sensorsContainer;
    @FXML private TextArea txtLog;

    // Overview Dashboard Stream
    @FXML private TextArea txtAuditStream;

    // Tag Settings
    @FXML private TableView<ModbusTag> tableTags;
    @FXML private TableColumn<ModbusTag, String> colTagName;
    @FXML private TableColumn<ModbusTag, String> colTagType;
    @FXML private TableColumn<ModbusTag, Number> colTagAddress;
    @FXML private TableColumn<ModbusTag, Void> colTagAction;

    @FXML private TextField txtNewTagName;
    @FXML private TextField txtNewTagAddress;
    @FXML private ComboBox<TagType> cmbNewTagType;

    private User sessionUser;
    private final TagManager tagManager = new TagManager();
    private final FactoryIOService ioService = new FactoryIOService();
    private final InventoryDao inventoryDao = new InventoryDao();
    private final ObservableList<ModbusTag> tableData = FXCollections.observableArrayList();

    private final Map<String, ActuatorCardRef> actuatorRefs = new ConcurrentHashMap<>();
    private final Map<String, SensorCardRef> sensorRefs = new ConcurrentHashMap<>();
    private final Map<String, Integer> sensorCounters = new ConcurrentHashMap<>();
    private final AtomicInteger totalDetectedPackages = new AtomicInteger(0);

    private Timeline clockTimeline;

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
        initClock();
        setupTagTable();
        setupTagForm();
        refreshDynamicHardwareUI();
        refreshKpiMetrics();

        log("[SYSTEM] Kanshi WMS 3-Level Executive Shell initialized.");
        logAudit("SYS", "Executive Command Center initialized. Modbus TCP & SQLite ready.");
    }

    /**
     * Initializes the live ticking header clock.
     */
    private void initClock() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss  |  dd MMM yyyy");
        lblClock.setText(LocalDateTime.now().format(formatter));
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            lblClock.setText(LocalDateTime.now().format(formatter));
        }));
        clockTimeline.setCycleCount(Animation.INDEFINITE);
        clockTimeline.play();
    }

    // =========================================================================
    // Navigation Rail View Switcher (Level 1 <-> Level 2 Workspaces)
    // =========================================================================

    @FXML
    private void handleNavDashboard(ActionEvent event) {
        activateView(paneDashboard, btnNavDashboard);
    }

    @FXML
    private void handleNavScada(ActionEvent event) {
        activateView(paneScada, btnNavScada);
    }

    @FXML
    private void handleNavInventory(ActionEvent event) {
        activateView(paneInventory, btnNavInventory);
    }

    @FXML
    private void handleNavFinance(ActionEvent event) {
        activateView(paneFinance, btnNavFinance);
    }

    @FXML
    private void handleNavTags(ActionEvent event) {
        activateView(paneTags, btnNavTags);
    }

    private void activateView(Node activePane, Button activeBtn) {
        paneDashboard.setVisible(false);
        paneDashboard.setManaged(false);
        paneScada.setVisible(false);
        paneScada.setManaged(false);
        paneInventory.setVisible(false);
        paneInventory.setManaged(false);
        paneFinance.setVisible(false);
        paneFinance.setManaged(false);
        paneTags.setVisible(false);
        paneTags.setManaged(false);

        activePane.setVisible(true);
        activePane.setManaged(true);

        btnNavDashboard.getStyleClass().remove("active");
        btnNavScada.getStyleClass().remove("active");
        btnNavInventory.getStyleClass().remove("active");
        btnNavFinance.getStyleClass().remove("active");
        btnNavTags.getStyleClass().remove("active");

        if (!activeBtn.getStyleClass().contains("active")) {
            activeBtn.getStyleClass().add("active");
        }
    }

    /**
     * Injects the authenticated user session into the main controller.
     */
    public void setUserSession(User user) {
        this.sessionUser = user;
        if (user != null) {
            lblOperatorEmail.setText("👤 Operator: " + (user.getEmail() != null ? user.getEmail() : user.getUsername()));
            log("[AUTH] Session established for user: " + user.getUsername() + " (" + user.getEmail() + ")");
            logAudit("AUTH", "Operator authenticated: " + user.getEmail());
        }
        refreshKpiMetrics();
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        shutdown();

        try {
            Stage stage = (Stage) lblOperatorEmail.getScene().getWindow();
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("login-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load());

            URL cssResource = HelloApplication.class.getResource("css/industrial-dark.css");
            if (cssResource != null) {
                scene.getStylesheets().add(cssResource.toExternalForm());
            }

            stage.setScene(scene);
            stage.setMaximized(true);
        } catch (IOException e) {
            System.err.println("Logout navigation failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clean shutdown of Modbus connections and background threads.
     */
    public void shutdown() {
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
        if (ioService != null) {
            ioService.disconnect();
        }
    }

    private void setupTagTable() {
        colTagName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colTagType.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType().getDisplayName()));
        colTagAddress.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getAddress()));

        colTagAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnDelete = new Button("Delete");

            {
                btnDelete.getStyleClass().add("btn-danger");
                btnDelete.setOnAction(e -> {
                    ModbusTag tag = getTableView().getItems().get(getIndex());
                    tagManager.removeTag(tag.getId());
                    loadTableData();
                    refreshDynamicHardwareUI();
                    log("[TAG MANAGER] Removed tag: " + tag.getName());
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

    public void refreshDynamicHardwareUI() {
        actuatorsContainer.getChildren().clear();
        sensorsContainer.getChildren().clear();
        actuatorRefs.clear();
        sensorRefs.clear();

        // 1. Build Actuators (Coils)
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

        // 2. Build Sensors (Discrete Inputs)
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

        boolean anyRunning = tagManager.getActuatorTags().stream().anyMatch(ModbusTag::isActive);
        if (anyRunning) {
            lblKpiLineState.setText("RUNNING");
            lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #15803d;");
        } else if (ioService.isConnected()) {
            lblKpiLineState.setText("IDLE");
            lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #64748b;");
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
                int total = totalDetectedPackages.incrementAndGet();
                lblKpiPackageCount.setText(total + " Pcs");
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

            circleHeartbeat.setFill(Color.web("#ef4444"));
            circleHeartbeat.setStroke(Color.web("#b91c1c"));
            lblHeartbeatStatus.setText("● OFFLINE: 502");
            lblKpiLineState.setText("DISCONNECTED");
            lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #64748b;");

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

                        circleHeartbeat.setFill(Color.web("#22c55e"));
                        circleHeartbeat.setStroke(Color.web("#16a34a"));
                        lblHeartbeatStatus.setText("● ONLINE: " + port);
                        lblKpiLineState.setText("ONLINE");
                        lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #15803d;");

                        log("[SUCCESS] Connected to Factory I/O Modbus TCP/IP Server at " + host + ":" + port);

                        // Start real-time background sensor polling
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

                        circleHeartbeat.setFill(Color.web("#ef4444"));
                        circleHeartbeat.setStroke(Color.web("#b91c1c"));
                        lblHeartbeatStatus.setText("● FAILED: " + port);
                        lblKpiLineState.setText("ERROR");
                        lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #b91c1c;");

                        log("[ERROR] Connection failed: " + e.getMessage());
                        showAlert("Connection Error", "Could not connect to " + host + ":" + port + "\n\nEnsure Factory I/O driver is set to Modbus TCP/IP Server and scene is running.");
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
                lblKpiLineState.setText("EMERGENCY STOP");
                lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #b91c1c;");
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

    @FXML
    private void handleRefreshDashboard(ActionEvent event) {
        refreshKpiMetrics();
        logAudit("IT-STOCK", "Dashboard KPIs refreshed from SQLite inventory database.");
    }

    @FXML
    private void handleClearAuditStream(ActionEvent event) {
        if (txtAuditStream != null) {
            txtAuditStream.clear();
        }
    }

    /**
     * Refreshes executive KPI metrics directly from the SQLite database.
     */
    public void refreshKpiMetrics() {
        int totalUnits = inventoryDao.getTotalStockCount();
        double totalValuation = inventoryDao.getTotalValuation();
        if (lblKpiInventory != null) {
            lblKpiInventory.setText(String.format("%,d Units", totalUnits));
        }
        if (lblKpiValuation != null) {
            lblKpiValuation.setText(String.format("$%,.2f", totalValuation));
        }
    }

    /**
     * Writes timestamped entries to the converged activity audit log.
     */
    public void logAudit(String category, String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + timestamp + "] [" + category + "] " + message + "\n";
        Platform.runLater(() -> {
            if (txtAuditStream != null) {
                txtAuditStream.appendText(entry);
            }
        });
    }

    private void log(String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + timestamp + "] " + message + "\n";
        Platform.runLater(() -> {
            if (txtLog != null) {
                txtLog.appendText(entry);
            }
            if (txtAuditStream != null) {
                txtAuditStream.appendText(entry);
            }
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
