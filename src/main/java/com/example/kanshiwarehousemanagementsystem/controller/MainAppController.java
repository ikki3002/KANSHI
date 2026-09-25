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
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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

    // Live Hardware Interactive Pipeline (Option B)
    @FXML private HBox pipelineTrack;
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

    private final Map<String, ActuatorBlockRef> actuatorRefs = new ConcurrentHashMap<>();
    private final Map<String, SensorBlockRef> sensorRefs = new ConcurrentHashMap<>();
    private final Map<String, Integer> sensorCounters = new ConcurrentHashMap<>();
    private final AtomicInteger totalDetectedPackages = new AtomicInteger(0);
    private final List<String> pipelineOrder = new ArrayList<>();

    private Timeline clockTimeline;

    // SCADA Status & Animation
    @FXML private Label lblMimicLineStatus;

    private double beltOffset = 0;
    private Timeline mimicAnimationTimeline;
    private volatile boolean isVisionSensorActive = false;

    private static class ActuatorBlockRef {
        VBox block;
        Label statusPill;
        Button toggleBtn;
        Canvas beltCanvas;
    }

    private static class SensorBlockRef {
        VBox block;
        Circle led;
        Label statusLabel;
        Label counterLabel;
        Canvas sensorCanvas;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initClock();
        setupTagTable();
        setupTagForm();
        refreshDynamicHardwareUI();
        refreshKpiMetrics();
        initMimicAnimation();

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
    // Option B: Real-Time Conveyor Line Pipeline Animation & Visual Rendering
    // =========================================================================

    private void initMimicAnimation() {
        mimicAnimationTimeline = new Timeline(new KeyFrame(Duration.millis(50), e -> {
            boolean anyRunning = tagManager.getActuatorTags().stream().anyMatch(ModbusTag::isActive);
            if (anyRunning) {
                beltOffset = (beltOffset + 2.5) % 18;
                for (ModbusTag tag : tagManager.getActuatorTags()) {
                    if (tag.isActive()) {
                        ActuatorBlockRef ref = actuatorRefs.get(tag.getId());
                        if (ref != null && ref.beltCanvas != null) {
                            if (isCurvedConveyor(tag)) {
                                drawCurvedConveyorBelt(ref.beltCanvas, true, beltOffset);
                            } else {
                                drawConveyorBelt(ref.beltCanvas, true, beltOffset);
                            }
                        }
                    }
                }
            }
        }));
        mimicAnimationTimeline.setCycleCount(Animation.INDEFINITE);
        mimicAnimationTimeline.play();
    }

    private boolean isCurvedConveyor(ModbusTag tag) {
        if (tag == null || tag.getName() == null) return false;
        String name = tag.getName().toLowerCase();
        return name.contains("curved") || name.contains("corner") || name.contains("turn");
    }

    /**
     * Draws animated 90-degree curved roller belt markings for corner conveyor blocks.
     */
    private void drawCurvedConveyorBelt(Canvas canvas, boolean running, double offset) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 1. Background
        gc.setFill(running ? Color.web("#fff1f2") : Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        // 2. Chassis box
        gc.setStroke(running ? Color.web("#7a0c1e") : Color.web("#cbd5e1"));
        gc.setLineWidth(running ? 2.0 : 1.5);
        gc.strokeRoundRect(2, 2, w - 4, h - 4, 6, 6);

        // 3. Curved rails (90-degree outer and inner guides)
        gc.setStroke(running ? Color.web("#991b1b") : Color.web("#64748b"));
        gc.setLineWidth(3);
        gc.strokeArc(6, -26, 110, 95, 270, 90, ArcType.OPEN);
        gc.strokeArc(6, 6, 56, 52, 270, 90, ArcType.OPEN);

        // 4. Moving radial roller lines
        if (running) {
            gc.setStroke(Color.web("#e11d48"));
            gc.setLineWidth(2.0);
            for (double angle = 275 + (offset % 18); angle < 355; angle += 18) {
                double rad = Math.toRadians(angle);
                double x1 = 34 + 28 * Math.cos(rad);
                double y1 = 26 - 26 * Math.sin(rad);
                double x2 = 34 + 55 * Math.cos(rad);
                double y2 = 26 - 47 * Math.sin(rad);
                gc.strokeLine(x1, y1, x2, y2);
            }
            // Active rotation label
            gc.setFill(Color.web("#7a0c1e"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            gc.fillText("↷ 90° CW", w - 68, h / 2 + 4);
        } else {
            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1.5);
            for (double angle = 275; angle < 355; angle += 18) {
                double rad = Math.toRadians(angle);
                double x1 = 34 + 28 * Math.cos(rad);
                double y1 = 26 - 26 * Math.sin(rad);
                double x2 = 34 + 55 * Math.cos(rad);
                double y2 = 26 - 47 * Math.sin(rad);
                gc.strokeLine(x1, y1, x2, y2);
            }
            gc.setFill(Color.web("#64748b"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            gc.fillText("↷ Corner", w - 65, h / 2 + 4);
        }
    }

    /**
     * Draws animated roller belt markings and chassis on the in-block canvas.
     */
    private void drawConveyorBelt(Canvas canvas, boolean running, double offset) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 1. Background
        gc.setFill(running ? Color.web("#fff1f2") : Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        // 2. Top and bottom rails
        gc.setFill(running ? Color.web("#991b1b") : Color.web("#64748b"));
        gc.fillRect(2, 2, w - 4, 3);
        gc.fillRect(2, h - 5, w - 4, 3);

        // 3. Rollers
        if (running) {
            gc.setStroke(Color.web("#e11d48"));
            gc.setLineWidth(2.0);
            for (double x = 6 + (offset % 16); x < w - 6; x += 16) {
                gc.strokeLine(x, 5, x + 5, h - 5);
            }
            // Chevron arrow
            gc.setFill(Color.web("#7a0c1e"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 10));
            gc.fillText("▶▶", w - 24, h / 2 + 4);
        } else {
            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1.5);
            for (double x = 6; x < w - 6; x += 16) {
                gc.strokeLine(x, 6, x, h - 6);
            }
        }
    }

    /**
     * Draws optical sensor head, laser cone, and detected package on the in-block canvas.
     */
    private void drawSensorVisual(Canvas canvas, boolean detected) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 1. Clear background
        gc.setFill(detected ? Color.web("#f0fdf4") : Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        double centerX = w / 2.0;

        // 2. Optical Sensor Head at top
        gc.setFill(detected ? Color.web("#22c55e") : Color.web("#0284c7"));
        gc.fillRoundRect(centerX - 16, 2, 32, 12, 4, 4);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
        gc.fillText("OPTICAL", centerX - 14, 11);

        if (detected) {
            // Neon green laser beam
            gc.setStroke(Color.web("#22c55e"));
            gc.setLineWidth(2.5);
            gc.strokeLine(centerX, 14, centerX, h - 4);

            // Light translucent laser cone
            gc.setFill(Color.rgb(34, 197, 94, 0.22));
            gc.fillPolygon(
                new double[]{centerX, centerX - 18, centerX + 18},
                new double[]{14, h - 4, h - 4},
                3
            );

            // Detected Package Box
            gc.setFill(Color.web("#d97706"));
            gc.setStroke(Color.web("#92400e"));
            gc.setLineWidth(1.5);
            double boxW = 28;
            double boxH = 20;
            double boxX = centerX - boxW / 2;
            double boxY = h - boxH - 3;
            gc.fillRect(boxX, boxY, boxW, boxH);
            gc.strokeRect(boxX, boxY, boxW, boxH);

            // Box sealing tape
            gc.setStroke(Color.web("#fef3c7"));
            gc.setLineWidth(1.5);
            gc.strokeLine(boxX, boxY + boxH / 2, boxX + boxW, boxY + boxH / 2);

            // Box text
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 7));
            gc.fillText("BOX", boxX + 6, boxY + 12);
        } else {
            // Idle guide line
            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1.0);
            gc.setLineDashes(3);
            gc.strokeLine(centerX, 14, centerX, h - 4);
            gc.setLineDashes(null);
        }
    }

    /**
     * Updates the line status badge in the pipeline header.
     */
    public synchronized void updateMimicLineStatus() {
        if (lblMimicLineStatus == null) return;
        boolean anyRunning = tagManager.getActuatorTags().stream().anyMatch(ModbusTag::isActive);
        if (anyRunning) {
            lblMimicLineStatus.setText("LINE STATUS: ACTIVE / RUNNING");
            lblMimicLineStatus.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #15803d; -fx-background-color: #dcfce7; -fx-padding: 5 12; -fx-background-radius: 4px;");
        } else if (ioService.isConnected()) {
            lblMimicLineStatus.setText("LINE STATUS: STANDBY / IDLE");
            lblMimicLineStatus.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b; -fx-background-color: #f1f5f9; -fx-padding: 5 12; -fx-background-radius: 4px;");
        } else {
            lblMimicLineStatus.setText("LINE STATUS: OFFLINE");
            lblMimicLineStatus.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #991b1b; -fx-background-color: #fee2e2; -fx-padding: 5 12; -fx-background-radius: 4px;");
        }
    }

    public synchronized void redrawMimic() {
        updateMimicLineStatus();
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
        if (mimicAnimationTimeline != null) {
            mimicAnimationTimeline.stop();
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
        List<ModbusTag> allTags = tagManager.getAllTags();
        Set<String> validIds = allTags.stream().map(ModbusTag::getId).collect(Collectors.toSet());
        pipelineOrder.removeIf(id -> !validIds.contains(id));

        if (pipelineOrder.isEmpty()) {
            resetPipelineOrderToDefault();
        } else {
            for (ModbusTag tag : allTags) {
                if (!pipelineOrder.contains(tag.getId())) {
                    pipelineOrder.add(tag.getId());
                }
            }
        }

        renderPipeline();
    }

    private synchronized void renderPipeline() {
        if (pipelineTrack == null) return;
        pipelineTrack.getChildren().clear();
        actuatorRefs.clear();
        sensorRefs.clear();

        // 1. Check if there is a curved conveyor corner in the sequence
        int curvedIndex = -1;
        for (int i = 0; i < pipelineOrder.size(); i++) {
            ModbusTag tag = tagManager.findTagById(pipelineOrder.get(i));
            if (tag != null && isCurvedConveyor(tag)) {
                curvedIndex = i;
                break;
            }
        }

        // 2. Infeed Terminal always starts the line
        pipelineTrack.getChildren().add(createInfeedTerminal());

        if (curvedIndex == -1) {
            // No curve: render linear straight run
            for (String tagId : pipelineOrder) {
                ModbusTag tag = tagManager.findTagById(tagId);
                if (tag == null) continue;
                pipelineTrack.getChildren().add(createConnectorArrow());
                if (tag.getType() == TagType.COIL) {
                    pipelineTrack.getChildren().add(createConveyorBlock(tag));
                } else if (tag.getType() == TagType.DISCRETE_INPUT) {
                    pipelineTrack.getChildren().add(createSensorBlock(tag));
                }
            }
            pipelineTrack.getChildren().add(createConnectorArrow());
            pipelineTrack.getChildren().add(createDepotTerminal());
        } else {
            // L-Shape Layout: stations up to curvedIndex go in horizontal top arm
            for (int i = 0; i < curvedIndex; i++) {
                ModbusTag tag = tagManager.findTagById(pipelineOrder.get(i));
                if (tag == null) continue;
                pipelineTrack.getChildren().add(createConnectorArrow());
                if (tag.getType() == TagType.COIL) {
                    pipelineTrack.getChildren().add(createConveyorBlock(tag));
                } else if (tag.getType() == TagType.DISCRETE_INPUT) {
                    pipelineTrack.getChildren().add(createSensorBlock(tag));
                }
            }

            // Connector arrow into the corner
            pipelineTrack.getChildren().add(createConnectorArrow());

            // 3. Corner column (Curved Conveyor + vertical downward descent into Depot)
            VBox cornerColumn = new VBox(8);
            cornerColumn.setAlignment(Pos.TOP_CENTER);

            // Curved Conveyor Block
            ModbusTag curvedTag = tagManager.findTagById(pipelineOrder.get(curvedIndex));
            cornerColumn.getChildren().add(createConveyorBlock(curvedTag));

            // Any stations after the curve flow vertically downward
            for (int i = curvedIndex + 1; i < pipelineOrder.size(); i++) {
                ModbusTag tag = tagManager.findTagById(pipelineOrder.get(i));
                if (tag == null) continue;
                cornerColumn.getChildren().add(createDownwardConnectorArrow());
                if (tag.getType() == TagType.COIL) {
                    cornerColumn.getChildren().add(createConveyorBlock(tag));
                } else if (tag.getType() == TagType.DISCRETE_INPUT) {
                    cornerColumn.getChildren().add(createSensorBlock(tag));
                }
            }

            // Downward arrow into Depot
            cornerColumn.getChildren().add(createDownwardConnectorArrow());
            cornerColumn.getChildren().add(createDepotTerminal());

            // Add corner column to horizontal track
            pipelineTrack.getChildren().add(cornerColumn);
        }

        updateMimicLineStatus();
    }

    private Node createDownwardConnectorArrow() {
        VBox box = new VBox(1);
        box.setAlignment(Pos.CENTER);
        box.setPrefHeight(26);
        Label line = new Label("│");
        line.getStyleClass().add("pipeline-downward-arrow");
        Label arrow = new Label("▼");
        arrow.getStyleClass().add("pipeline-downward-arrow");
        box.getChildren().addAll(line, arrow);
        return box;
    }

    private Node createInfeedTerminal() {
        VBox box = new VBox(4);
        box.getStyleClass().add("pipeline-terminal");
        Label icon = new Label("📥");
        icon.setStyle("-fx-font-size: 20px;");
        Label lbl = new Label("INFEED");
        lbl.getStyleClass().add("pipeline-terminal-label");
        Label sub = new Label("Entry Chute");
        sub.getStyleClass().add("pipeline-terminal-sub");
        box.getChildren().addAll(icon, lbl, sub);
        return box;
    }

    private Node createDepotTerminal() {
        VBox box = new VBox(4);
        box.getStyleClass().add("pipeline-terminal");
        box.setMinWidth(110);
        box.setMaxWidth(130);
        box.setAlignment(Pos.CENTER);
        Label icon = new Label("📦");
        icon.setStyle("-fx-font-size: 20px;");
        Label lbl = new Label("DEPOT");
        lbl.getStyleClass().add("pipeline-terminal-label");
        Label sub = new Label("Outfeed Chute");
        sub.getStyleClass().add("pipeline-terminal-sub");
        box.getChildren().addAll(icon, lbl, sub);
        return box;
    }

    private Node createConnectorArrow() {
        Label arrow = new Label("──►");
        arrow.getStyleClass().add("pipeline-connector-label");
        return arrow;
    }

    private Node createConveyorBlock(ModbusTag tag) {
        boolean isCurved = isCurvedConveyor(tag);
        VBox block = new VBox(8);
        block.getStyleClass().add("pipeline-block");
        if (tag.isActive()) {
            block.getStyleClass().add("pipeline-block-running");
        }

        attachDragAndDropHandlers(block, tag.getId());

        // Header row
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label grip = new Label("⠿");
        grip.setStyle("-fx-font-size: 15px; -fx-text-fill: #94a3b8; -fx-cursor: move;");
        Label nameLbl = new Label(tag.getName());
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1e293b;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label(isCurved ? "Coil " + tag.getAddress() + " (90° CW)" : "Coil " + tag.getAddress());
        badge.getStyleClass().add(isCurved ? "pipeline-corner-tag" : "tag-badge");
        topRow.getChildren().addAll(grip, nameLbl, spacer, badge);

        // Animated Belt Canvas
        Canvas beltCanvas = new Canvas(195, isCurved ? 50 : 32);
        if (isCurved) {
            drawCurvedConveyorBelt(beltCanvas, tag.isActive(), beltOffset);
        } else {
            drawConveyorBelt(beltCanvas, tag.isActive(), beltOffset);
        }

        // Status row
        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        Label statusPill = new Label(tag.isActive() ? "● RUNNING" : "● IDLE");
        statusPill.getStyleClass().add(tag.isActive() ? "status-pill-running" : "status-pill-stopped");
        statusRow.getChildren().addAll(new Label("Status:"), statusPill);

        // Controls row
        HBox controlsRow = new HBox(8);
        controlsRow.setAlignment(Pos.CENTER_LEFT);

        Button toggleBtn = new Button(tag.isActive() ? "⏹ STOP" : "▶ START");
        toggleBtn.getStyleClass().add(tag.isActive() ? "btn-estop" : "btn-primary");
        toggleBtn.setStyle("-fx-font-size: 11px; -fx-padding: 6 12;");
        HBox.setHgrow(toggleBtn, Priority.ALWAYS);
        toggleBtn.setMaxWidth(Double.MAX_VALUE);
        toggleBtn.setOnAction(e -> handleToggleActuator(tag));

        Button testBtn = new Button("⚡ 2s");
        testBtn.getStyleClass().add("btn-secondary");
        testBtn.setStyle("-fx-font-size: 11px; -fx-padding: 6 10;");
        testBtn.setOnAction(e -> handleQuickTestActuator(tag));

        controlsRow.getChildren().addAll(toggleBtn, testBtn);

        block.getChildren().addAll(topRow, beltCanvas, statusRow, controlsRow);

        ActuatorBlockRef ref = new ActuatorBlockRef();
        ref.block = block;
        ref.statusPill = statusPill;
        ref.toggleBtn = toggleBtn;
        ref.beltCanvas = beltCanvas;
        actuatorRefs.put(tag.getId(), ref);

        return block;
    }

    private Node createSensorBlock(ModbusTag tag) {
        VBox block = new VBox(8);
        block.getStyleClass().add("pipeline-block");
        if (tag.isActive()) {
            block.getStyleClass().add("pipeline-block-detected");
        }

        attachDragAndDropHandlers(block, tag.getId());

        // Header row
        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label grip = new Label("⠿");
        grip.setStyle("-fx-font-size: 15px; -fx-text-fill: #94a3b8; -fx-cursor: move;");
        Label nameLbl = new Label(tag.getName());
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #1e293b;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label("Input " + tag.getAddress());
        badge.getStyleClass().add("tag-badge");
        topRow.getChildren().addAll(grip, nameLbl, spacer, badge);

        // Sensor Canvas
        Canvas sensorCanvas = new Canvas(195, 42);
        drawSensorVisual(sensorCanvas, tag.isActive());

        // LED and Status row
        HBox ledRow = new HBox(8);
        ledRow.setAlignment(Pos.CENTER_LEFT);
        Circle led = new Circle(7);
        led.getStyleClass().add(tag.isActive() ? "sensor-led-on" : "sensor-led-off");
        Label statusLbl = new Label(tag.isActive() ? "OBJECT DETECTED" : "BEAM CLEAR");
        statusLbl.setStyle(tag.isActive() ? "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #15803d;" : "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
        ledRow.getChildren().addAll(led, statusLbl);

        // Detection Counter row
        sensorCounters.putIfAbsent(tag.getId(), 0);
        Label counterBadge = new Label("Detections: " + sensorCounters.get(tag.getId()));
        counterBadge.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-background-color: #f1f5f9; -fx-padding: 3 8; -fx-background-radius: 4px;");

        block.getChildren().addAll(topRow, sensorCanvas, ledRow, counterBadge);

        SensorBlockRef ref = new SensorBlockRef();
        ref.block = block;
        ref.led = led;
        ref.statusLabel = statusLbl;
        ref.counterLabel = counterBadge;
        ref.sensorCanvas = sensorCanvas;
        sensorRefs.put(tag.getId(), ref);

        return block;
    }

    private void attachDragAndDropHandlers(VBox block, String tagId) {
        block.setOnDragDetected(event -> {
            Dragboard db = block.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(tagId);
            db.setContent(content);
            block.setOpacity(0.6);
            event.consume();
        });

        block.setOnDragDone(event -> {
            block.setOpacity(1.0);
            event.consume();
        });

        block.setOnDragOver(event -> {
            if (event.getGestureSource() != block && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        block.setOnDragEntered(event -> {
            if (event.getGestureSource() != block && event.getDragboard().hasString()) {
                block.getStyleClass().add("drag-hover-target");
            }
            event.consume();
        });

        block.setOnDragExited(event -> {
            block.getStyleClass().remove("drag-hover-target");
            event.consume();
        });

        block.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                String sourceId = db.getString();
                reorderPipeline(sourceId, tagId);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private synchronized void reorderPipeline(String sourceId, String targetId) {
        if (sourceId == null || targetId == null || sourceId.equals(targetId)) return;
        int srcIndex = pipelineOrder.indexOf(sourceId);
        int tgtIndex = pipelineOrder.indexOf(targetId);
        if (srcIndex != -1 && tgtIndex != -1) {
            pipelineOrder.remove(srcIndex);
            pipelineOrder.add(tgtIndex, sourceId);
            renderPipeline();
            ModbusTag src = tagManager.findTagById(sourceId);
            ModbusTag tgt = tagManager.findTagById(targetId);
            String srcName = src != null ? src.getName() : sourceId;
            String tgtName = tgt != null ? tgt.getName() : targetId;
            log("[PIPELINE] Machine reordered: " + srcName + " moved to position of " + tgtName);
            logAudit("OT-SCADA", "Pipeline station reordered: " + srcName + " moved to slot " + (tgtIndex + 1));
        }
    }

    @FXML
    private void handleResetLineOrder(ActionEvent event) {
        resetPipelineOrderToDefault();
        renderPipeline();
        log("[PIPELINE] Equipment line sequence reset to default layout.");
        logAudit("OT-SCADA", "Pipeline sequence reset to factory default layout.");
    }

    private void resetPipelineOrderToDefault() {
        pipelineOrder.clear();
        List<ModbusTag> all = tagManager.getAllTags();
        List<String> preferredNames = List.of(
            "Belt Conveyor 0",
            "Vision Sensor 0",
            "Belt Conveyor 1",
            "Curved Belt Conveyor"
        );
        for (String name : preferredNames) {
            ModbusTag tag = tagManager.findTagByName(name);
            if (tag != null && !pipelineOrder.contains(tag.getId())) {
                pipelineOrder.add(tag.getId());
            }
        }
        for (ModbusTag tag : all) {
            if (!pipelineOrder.contains(tag.getId())) {
                pipelineOrder.add(tag.getId());
            }
        }
    }

    private void handleQuickTestActuator(ModbusTag tag) {
        if (!ioService.isConnected()) {
            showAlert("Not Connected", "Please connect to Factory I/O Modbus TCP server first.");
            return;
        }
        log("[TEST] Triggering 2s quick pulse test on " + tag.getName() + "...");
        new Thread(() -> {
            try {
                ioService.writeTag(tag, true);
                Platform.runLater(() -> {
                    updateActuatorTileUI(tag, true);
                    log("[ACTUATOR] " + tag.getName() + " (Coil " + tag.getAddress() + ") -> PULSE START");
                });
                Thread.sleep(2000);
                ioService.writeTag(tag, false);
                Platform.runLater(() -> {
                    updateActuatorTileUI(tag, false);
                    log("[ACTUATOR] " + tag.getName() + " (Coil " + tag.getAddress() + ") -> PULSE FINISHED");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> log("[ERROR] Quick pulse test failed for " + tag.getName() + ": " + ex.getMessage()));
            }
        }).start();
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
        ActuatorBlockRef ref = actuatorRefs.get(tag.getId());
        if (ref != null) {
            ref.statusPill.setText(running ? "● RUNNING" : "● IDLE");
            ref.statusPill.getStyleClass().removeAll("status-pill-running", "status-pill-stopped");
            ref.statusPill.getStyleClass().add(running ? "status-pill-running" : "status-pill-stopped");

            ref.toggleBtn.setText(running ? "⏹ STOP" : "▶ START");
            ref.toggleBtn.getStyleClass().removeAll("btn-primary", "btn-estop");
            ref.toggleBtn.getStyleClass().add(running ? "btn-estop" : "btn-primary");

            if (running) {
                if (!ref.block.getStyleClass().contains("pipeline-block-running")) {
                    ref.block.getStyleClass().add("pipeline-block-running");
                }
            } else {
                ref.block.getStyleClass().remove("pipeline-block-running");
            }

            if (isCurvedConveyor(tag)) {
                drawCurvedConveyorBelt(ref.beltCanvas, running, beltOffset);
            } else {
                drawConveyorBelt(ref.beltCanvas, running, beltOffset);
            }
        }

        boolean anyRunning = tagManager.getActuatorTags().stream().anyMatch(ModbusTag::isActive);
        if (anyRunning) {
            lblKpiLineState.setText("RUNNING");
            lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #15803d;");
        } else if (ioService.isConnected()) {
            lblKpiLineState.setText("IDLE");
            lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #64748b;");
        }

        updateMimicLineStatus();
    }

    private void updateSensorTileUI(ModbusTag tag, boolean active) {
        isVisionSensorActive = active;
        SensorBlockRef ref = sensorRefs.get(tag.getId());
        if (ref != null) {
            ref.led.getStyleClass().removeAll("sensor-led-on", "sensor-led-off");
            ref.led.getStyleClass().add(active ? "sensor-led-on" : "sensor-led-off");
            ref.statusLabel.setText(active ? "OBJECT DETECTED" : "BEAM CLEAR");
            ref.statusLabel.setStyle(active ? "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #15803d;" : "-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #64748b;");

            if (active) {
                if (!ref.block.getStyleClass().contains("pipeline-block-detected")) {
                    ref.block.getStyleClass().add("pipeline-block-detected");
                }
                int count = sensorCounters.compute(tag.getId(), (k, v) -> v == null ? 1 : v + 1);
                ref.counterLabel.setText("Detections: " + count);
                int total = totalDetectedPackages.incrementAndGet();
                lblKpiPackageCount.setText(total + " Pcs");
            } else {
                ref.block.getStyleClass().remove("pipeline-block-detected");
            }

            drawSensorVisual(ref.sensorCanvas, active);
        }
        updateMimicLineStatus();
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
            Platform.runLater(this::redrawMimic);
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
                        redrawMimic();

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
                        redrawMimic();
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
                redrawMimic();
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
