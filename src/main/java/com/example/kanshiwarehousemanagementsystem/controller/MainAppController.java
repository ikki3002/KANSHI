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
import com.example.kanshiwarehousemanagementsystem.model.FloorCellPlacement;
import com.example.kanshiwarehousemanagementsystem.model.FloorCellPlacement.AssetType;
import com.example.kanshiwarehousemanagementsystem.model.FloorCellPlacement.Direction;
import com.example.kanshiwarehousemanagementsystem.service.scada.FloorLayoutService;
import javafx.scene.layout.GridPane;
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

    // Navigation Rail & Sidebar Toggle
    @FXML private VBox navRail;
    @FXML private Button btnToggleSidebar;
    @FXML private Label lblNavCatCore;
    @FXML private Label lblNavCatWorkspaces;
    @FXML private Label lblNavCatSystem;
    @FXML private VBox navRailFooter;
    private boolean isSidebarCollapsed = false;

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

    // 2D Factory Floor SCADA Studio
    @FXML private GridPane floorGridPane;
    @FXML private Button btnToggleDesignMode;
    @FXML private Label lblFloorStudioSubtitle;
    @FXML private HBox boxDesignModeBanner;
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
    private final FloorLayoutService floorLayoutService = new FloorLayoutService();
    private final ObservableList<ModbusTag> tableData = FXCollections.observableArrayList();

    private boolean isDesignMode = false;
    private final Map<String, ActuatorBlockRef> actuatorRefs = new ConcurrentHashMap<>();
    private final Map<String, SensorBlockRef> sensorRefs = new ConcurrentHashMap<>();
    private final Map<String, Integer> sensorCounters = new ConcurrentHashMap<>();
    private final AtomicInteger totalDetectedPackages = new AtomicInteger(0);

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
        Direction direction;
    }

    private static class SensorBlockRef {
        VBox block;
        Circle led;
        Label statusLabel;
        Label counterLabel;
        Canvas sensorCanvas;
        Direction direction;
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
    // 2D SCADA Studio Canvas Animation & Directional Graphics Rendering
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
                                drawCurvedConveyorBelt(ref.beltCanvas, true, beltOffset, ref.direction);
                            } else {
                                drawConveyorBelt(ref.beltCanvas, true, beltOffset, ref.direction);
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
    private void drawCurvedConveyorBelt(Canvas canvas, boolean running, double offset, Direction dir) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // 1. Background
        gc.setFill(running ? Color.web("#fff1f2") : Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        // 2. Chassis box
        gc.setStroke(running ? Color.web("#7a0c1e") : Color.web("#cbd5e1"));
        gc.setLineWidth(running ? 1.8 : 1.2);
        gc.strokeRoundRect(2, 2, w - 4, h - 4, 5, 5);

        // 3. Curved rails
        gc.setStroke(running ? Color.web("#991b1b") : Color.web("#64748b"));
        gc.setLineWidth(2.5);
        gc.strokeArc(4, -18, 76, 68, 270, 90, ArcType.OPEN);
        gc.strokeArc(4, 4, 40, 36, 270, 90, ArcType.OPEN);

        // 4. Moving radial roller lines
        if (running) {
            gc.setStroke(Color.web("#e11d48"));
            gc.setLineWidth(1.6);
            for (double angle = 275 + (offset % 18); angle < 355; angle += 18) {
                double rad = Math.toRadians(angle);
                double x1 = 24 + 20 * Math.cos(rad);
                double y1 = 20 - 18 * Math.sin(rad);
                double x2 = 24 + 38 * Math.cos(rad);
                double y2 = 20 - 34 * Math.sin(rad);
                gc.strokeLine(x1, y1, x2, y2);
            }
            gc.setFill(Color.web("#7a0c1e"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
            gc.fillText("↷ 90° CW", w - 54, h / 2 + 3);
        } else {
            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1.2);
            for (double angle = 275; angle < 355; angle += 18) {
                double rad = Math.toRadians(angle);
                double x1 = 24 + 20 * Math.cos(rad);
                double y1 = 20 - 18 * Math.sin(rad);
                double x2 = 24 + 38 * Math.cos(rad);
                double y2 = 20 - 34 * Math.sin(rad);
                gc.strokeLine(x1, y1, x2, y2);
            }
            gc.setFill(Color.web("#64748b"));
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
            gc.fillText("↷ Corner", w - 50, h / 2 + 3);
        }
    }

    /**
     * Draws animated roller belt markings and chassis on the in-block canvas according to flow direction.
     */
    private void drawConveyorBelt(Canvas canvas, boolean running, double offset, Direction dir) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(running ? Color.web("#fff1f2") : Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        Direction d = dir != null ? dir : Direction.EAST;

        if (d == Direction.EAST || d == Direction.WEST) {
            // Horizontal rails
            gc.setFill(running ? Color.web("#991b1b") : Color.web("#64748b"));
            gc.fillRect(2, 2, w - 4, 2.5);
            gc.fillRect(2, h - 4, w - 4, 2.5);

            if (running) {
                gc.setStroke(Color.web("#e11d48"));
                gc.setLineWidth(1.6);
                double off = (d == Direction.EAST) ? (offset % 14) : (14 - (offset % 14));
                for (double x = 4 + off; x < w - 4; x += 14) {
                    gc.strokeLine(x, 4, x + (d == Direction.EAST ? 4 : -4), h - 4);
                }
                gc.setFill(Color.web("#7a0c1e"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
                if (d == Direction.EAST) {
                    gc.fillText("▶▶", w - 18, h / 2 + 3);
                } else {
                    gc.fillText("◀◀", 6, h / 2 + 3);
                }
            } else {
                gc.setStroke(Color.web("#cbd5e1"));
                gc.setLineWidth(1.2);
                for (double x = 4; x < w - 4; x += 14) {
                    gc.strokeLine(x, 4, x, h - 4);
                }
                gc.setFill(Color.web("#94a3b8"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
                gc.fillText(d == Direction.EAST ? "→" : "←", w / 2 - 4, h / 2 + 3);
            }
        } else {
            // Vertical rails (NORTH or SOUTH)
            gc.setFill(running ? Color.web("#991b1b") : Color.web("#64748b"));
            gc.fillRect(2, 2, 2.5, h - 4);
            gc.fillRect(w - 4, 2, 2.5, h - 4);

            if (running) {
                gc.setStroke(Color.web("#e11d48"));
                gc.setLineWidth(1.6);
                double off = (d == Direction.SOUTH) ? (offset % 10) : (10 - (offset % 10));
                for (double y = 4 + off; y < h - 4; y += 10) {
                    gc.strokeLine(4, y, w - 4, y + (d == Direction.SOUTH ? 2 : -2));
                }
                gc.setFill(Color.web("#7a0c1e"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
                if (d == Direction.SOUTH) {
                    gc.fillText("▼▼", w / 2 - 6, h - 3);
                } else {
                    gc.fillText("▲▲", w / 2 - 6, 11);
                }
            } else {
                gc.setStroke(Color.web("#cbd5e1"));
                gc.setLineWidth(1.2);
                for (double y = 4; y < h - 4; y += 10) {
                    gc.strokeLine(4, y, w - 4, y);
                }
                gc.setFill(Color.web("#94a3b8"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
                gc.fillText(d == Direction.SOUTH ? "↓" : "↑", w / 2 - 4, h / 2 + 3);
            }
        }
    }

    /**
     * Draws optical sensor head, laser cone, and detected package on the in-block canvas.
     */
    private void drawSensorVisual(Canvas canvas, boolean detected, Direction dir) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(detected ? Color.web("#f0fdf4") : Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        double centerX = w / 2.0;

        // Optical Sensor Head at top
        gc.setFill(detected ? Color.web("#22c55e") : Color.web("#0284c7"));
        gc.fillRoundRect(centerX - 14, 1, 28, 10, 3, 3);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 7));
        gc.fillText("OPTICAL", centerX - 12, 8);

        if (detected) {
            gc.setStroke(Color.web("#22c55e"));
            gc.setLineWidth(2.0);
            gc.strokeLine(centerX, 11, centerX, h - 2);

            gc.setFill(Color.rgb(34, 197, 94, 0.22));
            gc.fillPolygon(
                new double[]{centerX, centerX - 14, centerX + 14},
                new double[]{11, h - 2, h - 2},
                3
            );

            double boxW = 22;
            double boxH = 14;
            double boxX = centerX - boxW / 2;
            double boxY = h - boxH - 2;
            gc.setFill(Color.web("#d97706"));
            gc.fillRect(boxX, boxY, boxW, boxH);
            gc.setStroke(Color.web("#92400e"));
            gc.setLineWidth(1.2);
            gc.strokeRect(boxX, boxY, boxW, boxH);

            gc.setStroke(Color.web("#fef3c7"));
            gc.setLineWidth(1.2);
            gc.strokeLine(boxX, boxY + boxH / 2, boxX + boxW, boxY + boxH / 2);

            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 6));
            gc.fillText("BOX", boxX + 4, boxY + 9);
        } else {
            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1.0);
            gc.setLineDashes(3);
            gc.strokeLine(centerX, 11, centerX, h - 2);
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
    // Navigation Rail View Switcher (Level 1 <-> Level 2 Workspaces) & Sidebar Toggle
    // =========================================================================

    @FXML
    private void handleToggleSidebar(ActionEvent event) {
        if (navRail == null) return;
        isSidebarCollapsed = !isSidebarCollapsed;

        if (isSidebarCollapsed) {
            navRail.setPrefWidth(64);
            navRail.setMinWidth(64);
            navRail.setMaxWidth(64);

            if (lblNavCatCore != null) { lblNavCatCore.setVisible(false); lblNavCatCore.setManaged(false); }
            if (lblNavCatWorkspaces != null) { lblNavCatWorkspaces.setVisible(false); lblNavCatWorkspaces.setManaged(false); }
            if (lblNavCatSystem != null) { lblNavCatSystem.setVisible(false); lblNavCatSystem.setManaged(false); }
            if (navRailFooter != null) { navRailFooter.setVisible(false); navRailFooter.setManaged(false); }

            btnNavDashboard.setText("📊");
            btnNavDashboard.setTooltip(new Tooltip("Overview Dashboard"));
            btnNavScada.setText("🏭");
            btnNavScada.setTooltip(new Tooltip("OT SCADA Station"));
            btnNavInventory.setText("📦");
            btnNavInventory.setTooltip(new Tooltip("Inventory Ledger"));
            btnNavFinance.setText("💰");
            btnNavFinance.setTooltip(new Tooltip("Finance & Invoicing"));
            btnNavTags.setText("⚙️");
            btnNavTags.setTooltip(new Tooltip("Hardware Tag Profiler"));

            btnNavDashboard.setAlignment(Pos.CENTER);
            btnNavScada.setAlignment(Pos.CENTER);
            btnNavInventory.setAlignment(Pos.CENTER);
            btnNavFinance.setAlignment(Pos.CENTER);
            btnNavTags.setAlignment(Pos.CENTER);

            if (btnToggleSidebar != null) {
                btnToggleSidebar.setText("▶");
            }
        } else {
            navRail.setPrefWidth(230);
            navRail.setMinWidth(230);
            navRail.setMaxWidth(230);

            if (lblNavCatCore != null) { lblNavCatCore.setVisible(true); lblNavCatCore.setManaged(true); }
            if (lblNavCatWorkspaces != null) { lblNavCatWorkspaces.setVisible(true); lblNavCatWorkspaces.setManaged(true); }
            if (lblNavCatSystem != null) { lblNavCatSystem.setVisible(true); lblNavCatSystem.setManaged(true); }
            if (navRailFooter != null) { navRailFooter.setVisible(true); navRailFooter.setManaged(true); }

            btnNavDashboard.setText("📊 Overview Dashboard");
            btnNavDashboard.setTooltip(null);
            btnNavScada.setText("🏭 OT SCADA Station");
            btnNavScada.setTooltip(null);
            btnNavInventory.setText("📦 Inventory Ledger");
            btnNavInventory.setTooltip(null);
            btnNavFinance.setText("💰 Finance & Invoicing");
            btnNavFinance.setTooltip(null);
            btnNavTags.setText("⚙️ Hardware Tag Profiler");
            btnNavTags.setTooltip(null);

            btnNavDashboard.setAlignment(Pos.CENTER_LEFT);
            btnNavScada.setAlignment(Pos.CENTER_LEFT);
            btnNavInventory.setAlignment(Pos.CENTER_LEFT);
            btnNavFinance.setAlignment(Pos.CENTER_LEFT);
            btnNavTags.setAlignment(Pos.CENTER_LEFT);

            if (btnToggleSidebar != null) {
                btnToggleSidebar.setText("☰");
            }
        }
    }

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
                    floorLayoutService.removePlacementByTagId(tag.getId());
                    floorLayoutService.saveToFile();
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
        renderFloorGrid();
    }

    @FXML
    private void handleToggleDesignMode(ActionEvent event) {
        isDesignMode = !isDesignMode;
        if (isDesignMode) {
            btnToggleDesignMode.setText("💾 Done & Lock Layout");
            btnToggleDesignMode.getStyleClass().removeAll("btn-secondary");
            btnToggleDesignMode.getStyleClass().add("btn-primary");
            boxDesignModeBanner.setVisible(true);
            boxDesignModeBanner.setManaged(true);
            lblFloorStudioSubtitle.setText("LAYOUT STUDIO ACTIVE: Click [+] on an empty cell to add equipment, [↻] to rotate flow direction, or [🗑] to remove.");
            log("[SCADA STUDIO] Entered layout design mode.");
        } else {
            floorLayoutService.saveToFile();
            btnToggleDesignMode.setText("✏️ Edit Floor Layout");
            btnToggleDesignMode.getStyleClass().removeAll("btn-primary");
            btnToggleDesignMode.getStyleClass().add("btn-secondary");
            boxDesignModeBanner.setVisible(false);
            boxDesignModeBanner.setManaged(false);
            lblFloorStudioSubtitle.setText("Live operational SCADA mimic. Toggle Edit Mode to place and orient machines on the factory floor grid.");
            log("[SCADA STUDIO] Factory floor layout updated and locked into operational mode.");
            logAudit("OT-SCADA", "Factory floor layout updated and locked into operational mode.");
        }
        renderFloorGrid();
    }

    @FXML
    private void handleResetFloorLayout(ActionEvent event) {
        floorLayoutService.resetToDefaults();
        floorLayoutService.saveToFile();
        renderFloorGrid();
        log("[SCADA STUDIO] Factory floor layout reset to standard factory scene.");
        logAudit("OT-SCADA", "Factory floor layout reset to default configuration.");
    }

    private synchronized void renderFloorGrid() {
        if (floorGridPane == null) return;
        floorGridPane.getChildren().clear();
        actuatorRefs.clear();
        sensorRefs.clear();

        int totalRows = floorLayoutService.getGridRows();
        int totalCols = floorLayoutService.getGridCols();

        for (int r = 0; r < totalRows; r++) {
            for (int c = 0; c < totalCols; c++) {
                FloorCellPlacement placement = floorLayoutService.findPlacement(r, c);
                Node cellNode;
                if (placement != null) {
                    cellNode = isDesignMode ? createDesignCell(placement) : createOperationalCell(placement);
                } else {
                    cellNode = isDesignMode ? createEmptyDesignCell(r, c) : createEmptyOperationalCell();
                }
                floorGridPane.add(cellNode, c, r);
            }
        }

        if (isDesignMode) {
            // Right edge: Column expansion strip
            VBox colControls = new VBox(6);
            colControls.setAlignment(Pos.CENTER);
            colControls.setStyle("-fx-padding: 4px;");

            Button addColBtn = new Button("➕\nC\nO\nL");
            addColBtn.getStyleClass().add("grid-edge-col-btn");
            addColBtn.setTooltip(new Tooltip("Add Column (Expand Factory Floor)"));
            addColBtn.setOnAction(e -> handleExpandColumn());
            colControls.getChildren().add(addColBtn);

            if (floorLayoutService.isColEmpty(totalCols - 1) && totalCols > 1) {
                Button removeColBtn = new Button("➖\nC\nO\nL");
                removeColBtn.getStyleClass().addAll("grid-edge-col-btn", "grid-edge-sub-btn");
                removeColBtn.setTooltip(new Tooltip("Remove Empty Column " + totalCols));
                removeColBtn.setOnAction(e -> handleShrinkColumn());
                colControls.getChildren().add(removeColBtn);
            }

            floorGridPane.add(colControls, totalCols, 0, 1, Math.max(1, totalRows));

            // Bottom edge: Row expansion strip
            HBox rowControls = new HBox(8);
            rowControls.setAlignment(Pos.CENTER);
            rowControls.setStyle("-fx-padding: 4px;");

            Button addRowBtn = new Button("➕ Add Row");
            addRowBtn.getStyleClass().add("grid-edge-row-btn");
            addRowBtn.setTooltip(new Tooltip("Add Row (Expand Factory Floor)"));
            addRowBtn.setOnAction(e -> handleExpandRow());
            rowControls.getChildren().add(addRowBtn);

            if (floorLayoutService.isRowEmpty(totalRows - 1) && totalRows > 1) {
                Button removeRowBtn = new Button("➖ Remove Row " + totalRows);
                removeRowBtn.getStyleClass().addAll("grid-edge-row-btn", "grid-edge-sub-btn");
                removeRowBtn.setTooltip(new Tooltip("Remove Empty Row " + totalRows));
                removeRowBtn.setOnAction(e -> handleShrinkRow());
                rowControls.getChildren().add(removeRowBtn);
            }

            floorGridPane.add(rowControls, 0, totalRows, Math.max(1, totalCols), 1);
        }

        updateMimicLineStatus();
    }

    @FXML
    private void handleExpandColumn() {
        floorLayoutService.addCol();
        floorLayoutService.saveToFile();
        renderFloorGrid();
        log("[SCADA STUDIO] Expanded factory floor columns to " + floorLayoutService.getGridCols() + ".");
    }

    @FXML
    private void handleShrinkColumn() {
        int curCols = floorLayoutService.getGridCols();
        if (curCols <= 1) {
            showAlert("Grid Limit", "Factory floor grid must have at least 1 column.");
            return;
        }
        boolean ok = floorLayoutService.removeCol();
        if (ok) {
            floorLayoutService.saveToFile();
            renderFloorGrid();
            log("[SCADA STUDIO] Reduced factory floor columns to " + floorLayoutService.getGridCols() + ".");
        } else {
            showAlert("Cannot Remove Column", "Column " + curCols + " contains active equipment. Remove or relocate machines first.");
        }
    }

    @FXML
    private void handleExpandRow() {
        floorLayoutService.addRow();
        floorLayoutService.saveToFile();
        renderFloorGrid();
        log("[SCADA STUDIO] Expanded factory floor rows to " + floorLayoutService.getGridRows() + ".");
    }

    @FXML
    private void handleShrinkRow() {
        int curRows = floorLayoutService.getGridRows();
        if (curRows <= 1) {
            showAlert("Grid Limit", "Factory floor grid must have at least 1 row.");
            return;
        }
        boolean ok = floorLayoutService.removeRow();
        if (ok) {
            floorLayoutService.saveToFile();
            renderFloorGrid();
            log("[SCADA STUDIO] Reduced factory floor rows to " + floorLayoutService.getGridRows() + ".");
        } else {
            showAlert("Cannot Remove Row", "Row " + curRows + " contains active equipment. Remove or relocate machines first.");
        }
    }

    private Node createEmptyDesignCell(int row, int col) {
        VBox cell = new VBox(2);
        cell.getStyleClass().add("floor-cell-empty-design");

        Label icon = new Label("➕");
        icon.setStyle("-fx-font-size: 14px; -fx-text-fill: #94a3b8;");

        Label label = new Label("Add Machine");
        label.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #64748b;");

        Label coord = new Label("[" + (row + 1) + "," + (col + 1) + "]");
        coord.setStyle("-fx-font-size: 8px; -fx-text-fill: #94a3b8;");

        cell.getChildren().addAll(icon, label, coord);
        cell.setOnMouseClicked(e -> openEquipmentPickerDialog(row, col));
        return cell;
    }

    private Node createEmptyOperationalCell() {
        Region empty = new Region();
        empty.getStyleClass().add("floor-cell-empty-operational");
        return empty;
    }

    private Node createDesignCell(FloorCellPlacement placement) {
        VBox block = new VBox(3);
        block.getStyleClass().add("pipeline-block");
        block.setMinWidth(118);
        block.setMaxWidth(122);
        block.setMinHeight(84);
        block.setMaxHeight(84);

        // Header Row: Icon, Name, Rotate [↻], Delete [🗑]
        HBox topRow = new HBox(3);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label iconLbl = new Label(placement.getAssetType().getIcon());
        iconLbl.setStyle("-fx-font-size: 10px;");

        Label nameLbl = new Label(getPlacementTitle(placement));
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 9px; -fx-text-fill: #1e293b;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button rotateBtn = new Button("↻");
        rotateBtn.getStyleClass().add("cell-tool-btn");
        rotateBtn.setTooltip(new Tooltip("Rotate 90° (" + placement.getDirection().getLabel() + ")"));
        rotateBtn.setOnAction(e -> {
            placement.rotate();
            floorLayoutService.saveToFile();
            renderFloorGrid();
        });

        Button deleteBtn = new Button("🗑");
        deleteBtn.getStyleClass().add("cell-tool-btn-danger");
        deleteBtn.setTooltip(new Tooltip("Remove from Floor"));
        deleteBtn.setOnAction(e -> {
            floorLayoutService.removePlacement(placement.getRow(), placement.getCol());
            floorLayoutService.saveToFile();
            renderFloorGrid();
        });

        topRow.getChildren().addAll(iconLbl, nameLbl, spacer, rotateBtn, deleteBtn);

        // Static Preview Canvas (108x22)
        Canvas canvas = new Canvas(108, 22);
        drawPreviewForPlacement(canvas, placement);

        // Info / Direction Row
        HBox bottomRow = new HBox(4);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        Label dirBadge = new Label(placement.getDirection().getLabel());
        dirBadge.getStyleClass().add("cell-direction-badge");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        Label tagBadge = new Label(getPlacementTagBadge(placement));
        tagBadge.getStyleClass().add("tag-badge");
        tagBadge.setStyle("-fx-font-size: 8px; -fx-padding: 1 3;");

        bottomRow.getChildren().addAll(dirBadge, spacer2, tagBadge);

        block.getChildren().addAll(topRow, canvas, bottomRow);
        return block;
    }

    private Node createOperationalCell(FloorCellPlacement placement) {
        switch (placement.getAssetType()) {
            case INFEED -> {
                VBox box = new VBox(2);
                box.getStyleClass().add("pipeline-terminal");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);
                box.setAlignment(Pos.CENTER);
                Label icon = new Label("📥");
                icon.setStyle("-fx-font-size: 18px;");
                Label lbl = new Label("INFEED");
                lbl.getStyleClass().add("pipeline-terminal-label");
                Label sub = new Label("Entry (" + placement.getDirection().getLabel() + ")");
                sub.getStyleClass().add("pipeline-terminal-sub");
                box.getChildren().addAll(icon, lbl, sub);
                box.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));
                return box;
            }
            case DEPOT -> {
                VBox box = new VBox(2);
                box.getStyleClass().add("pipeline-terminal");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);
                box.setAlignment(Pos.CENTER);
                Label icon = new Label("📦");
                icon.setStyle("-fx-font-size: 18px;");
                Label lbl = new Label("DEPOT");
                lbl.getStyleClass().add("pipeline-terminal-label");
                Label sub = new Label("Outfeed (" + placement.getDirection().getLabel() + ")");
                sub.getStyleClass().add("pipeline-terminal-sub");
                box.getChildren().addAll(icon, lbl, sub);
                box.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));
                return box;
            }
            case BRIDGE -> {
                VBox box = new VBox(2);
                box.getStyleClass().add("pipeline-block");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);
                box.setAlignment(Pos.CENTER);
                Label arrow = new Label(getBridgeArrowForDirection(placement.getDirection()));
                arrow.getStyleClass().add("pipeline-connector-label");
                arrow.setStyle("-fx-font-size: 18px; -fx-text-fill: #7a0c1e;");
                Label lbl = new Label("Conveyor Bridge");
                lbl.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
                box.getChildren().addAll(arrow, lbl);
                box.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));
                return box;
            }
            case CONVEYOR, CURVED_CONVEYOR -> {
                ModbusTag tag = tagManager.findTagById(placement.getTagId());
                if (tag == null) {
                    return createMissingTagCell(placement);
                }
                return createOperationalConveyorBlock(tag, placement);
            }
            case SENSOR -> {
                ModbusTag tag = tagManager.findTagById(placement.getTagId());
                if (tag == null) {
                    return createMissingTagCell(placement);
                }
                return createOperationalSensorBlock(tag, placement);
            }
        }
        return createEmptyOperationalCell();
    }

    private Node createMissingTagCell(FloorCellPlacement placement) {
        VBox box = new VBox(2);
        box.getStyleClass().add("pipeline-block");
        box.setMinWidth(118);
        box.setMaxWidth(122);
        box.setMinHeight(84);
        box.setMaxHeight(84);
        box.setAlignment(Pos.CENTER);
        Label icon = new Label("⚠️");
        icon.setStyle("-fx-font-size: 16px;");
        Label lbl = new Label(placement.getAssetType().getDisplayName());
        lbl.setStyle("-fx-font-weight: bold; -fx-font-size: 9px; -fx-text-fill: #991b1b;");
        Label sub = new Label("Unassigned Tag");
        sub.setStyle("-fx-font-size: 8px; -fx-text-fill: #64748b;");
        box.getChildren().addAll(icon, lbl, sub);
        box.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));
        return box;
    }

    private Node createOperationalConveyorBlock(ModbusTag tag, FloorCellPlacement placement) {
        boolean isCurved = (placement.getAssetType() == AssetType.CURVED_CONVEYOR) || isCurvedConveyor(tag);
        VBox block = new VBox(3);
        block.getStyleClass().add("pipeline-block");
        if (tag.isActive()) {
            block.getStyleClass().add("pipeline-block-running");
        }
        block.setMinWidth(118);
        block.setMaxWidth(122);
        block.setMinHeight(84);
        block.setMaxHeight(84);

        // Header row: icon, name, badge
        HBox topRow = new HBox(4);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label(isCurved ? "↷" : "⚙️");
        icon.setStyle("-fx-font-size: 10px;");
        Label nameLbl = new Label(tag.getName());
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 9px; -fx-text-fill: #1e293b;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label(isCurved ? "C" + tag.getAddress() + " (" + placement.getDirection().getLabel() + ")" : "C" + tag.getAddress());
        badge.getStyleClass().add(isCurved ? "pipeline-corner-tag" : "tag-badge");
        badge.setStyle("-fx-font-size: 8px; -fx-padding: 1 3;");
        topRow.getChildren().addAll(icon, nameLbl, spacer, badge);

        // Animated Belt Canvas
        Canvas beltCanvas = new Canvas(108, isCurved ? 28 : 18);
        if (isCurved) {
            drawCurvedConveyorBelt(beltCanvas, tag.isActive(), beltOffset, placement.getDirection());
        } else {
            drawConveyorBelt(beltCanvas, tag.isActive(), beltOffset, placement.getDirection());
        }

        // Status & Symbol Control row
        HBox bottomRow = new HBox(4);
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        Label statusPill = new Label(tag.isActive() ? "● RUN" : "● IDLE");
        statusPill.getStyleClass().add(tag.isActive() ? "status-pill-running" : "status-pill-stopped");
        statusPill.setStyle("-fx-font-size: 8px; -fx-padding: 1 4;");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        // Symbol-only Run/Stop button: ▶ when stopped/idle, ⏹ when running
        Button toggleBtn = new Button(tag.isActive() ? "⏹" : "▶");
        toggleBtn.getStyleClass().add(tag.isActive() ? "symbol-toggle-btn-stop" : "symbol-toggle-btn");
        toggleBtn.setTooltip(new Tooltip(tag.isActive() ? "Stop Conveyor" : "Start Conveyor"));
        toggleBtn.setOnAction(e -> {
            e.consume(); // Prevent bubbling up to open details dialog
            handleToggleActuator(tag);
        });

        bottomRow.getChildren().addAll(statusPill, spacer2, toggleBtn);

        block.getChildren().addAll(topRow, beltCanvas, bottomRow);

        // Clicking operational card opens full diagnostics & parameters dialog
        block.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));

        ActuatorBlockRef ref = new ActuatorBlockRef();
        ref.block = block;
        ref.statusPill = statusPill;
        ref.toggleBtn = toggleBtn;
        ref.beltCanvas = beltCanvas;
        ref.direction = placement.getDirection();
        actuatorRefs.put(tag.getId(), ref);

        return block;
    }

    private Node createOperationalSensorBlock(ModbusTag tag, FloorCellPlacement placement) {
        VBox block = new VBox(3);
        block.getStyleClass().add("pipeline-block");
        if (tag.isActive()) {
            block.getStyleClass().add("pipeline-block-detected");
        }
        block.setMinWidth(118);
        block.setMaxWidth(122);
        block.setMinHeight(84);
        block.setMaxHeight(84);

        // Header row
        HBox topRow = new HBox(4);
        topRow.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label("👁️");
        icon.setStyle("-fx-font-size: 10px;");
        Label nameLbl = new Label(tag.getName());
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 9px; -fx-text-fill: #1e293b;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label badge = new Label("DI " + tag.getAddress());
        badge.getStyleClass().add("tag-badge");
        badge.setStyle("-fx-font-size: 8px; -fx-padding: 1 3;");
        topRow.getChildren().addAll(icon, nameLbl, spacer, badge);

        // Sensor Canvas
        Canvas sensorCanvas = new Canvas(108, 22);
        drawSensorVisual(sensorCanvas, tag.isActive(), placement.getDirection());

        // LED, Status and Detection Counter inline
        HBox ledRow = new HBox(4);
        ledRow.setAlignment(Pos.CENTER_LEFT);
        Circle led = new Circle(4);
        led.getStyleClass().add(tag.isActive() ? "sensor-led-on" : "sensor-led-off");
        Label statusLbl = new Label(tag.isActive() ? "DETECTED" : "CLEAR");
        statusLbl.setStyle(tag.isActive() ? "-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #15803d;" : "-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");

        sensorCounters.putIfAbsent(tag.getId(), 0);
        Label counterBadge = new Label("Ct: " + sensorCounters.get(tag.getId()));
        counterBadge.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-background-color: #f1f5f9; -fx-padding: 1 4; -fx-background-radius: 3px;");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        ledRow.getChildren().addAll(led, statusLbl, spacer2, counterBadge);

        block.getChildren().addAll(topRow, sensorCanvas, ledRow);

        // Clicking sensor card opens full diagnostics & parameters dialog
        block.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));

        SensorBlockRef ref = new SensorBlockRef();
        ref.block = block;
        ref.led = led;
        ref.statusLabel = statusLbl;
        ref.counterLabel = counterBadge;
        ref.sensorCanvas = sensorCanvas;
        ref.direction = placement.getDirection();
        sensorRefs.put(tag.getId(), ref);

        return block;
    }

    /**
     * Opens rich telemetry, hardware parameters, and engineering diagnostics dialog for a machine.
     */
    private void openEquipmentDetailsDialog(FloorCellPlacement placement) {
        if (placement == null) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Equipment Telemetry & Diagnostics");

        if (floorGridPane != null && floorGridPane.getScene() != null && floorGridPane.getScene().getWindow() != null) {
            dialog.initOwner(floorGridPane.getScene().getWindow());
        }

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().add(ButtonType.CLOSE);
        try {
            dialogPane.getStylesheets().add(getClass().getResource("/com/example/kanshiwarehousemanagementsystem/css/industrial-dark.css").toExternalForm());
        } catch (Exception ignored) {}
        dialogPane.getStyleClass().add("equipment-details-dialog");

        VBox content = new VBox(12);
        content.setPrefWidth(420);

        // Header: Icon, Name, Coordinates
        HBox headerBox = new HBox(12);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        Label icon = new Label(placement.getAssetType().getIcon());
        icon.setStyle("-fx-font-size: 26px; -fx-padding: 6 10; -fx-background-color: #fee2e2; -fx-background-radius: 8px;");

        VBox titleBox = new VBox(3);
        Label titleLbl = new Label(getPlacementTitle(placement));
        titleLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");
        Label subLbl = new Label("Factory Floor Grid Position: Row " + (placement.getRow() + 1) + ", Column " + (placement.getCol() + 1));
        subLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");
        titleBox.getChildren().addAll(titleLbl, subLbl);
        headerBox.getChildren().addAll(icon, titleBox);

        // Telemetry Grid
        VBox propBox = new VBox(6);
        propBox.getStyleClass().add("telemetry-prop-box");

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);

        ModbusTag tag = placement.getTagId() != null ? tagManager.findTagById(placement.getTagId()) : null;

        grid.add(new Label("Asset Type:"), 0, 0);
        Label assetLbl = new Label(placement.getAssetType().getDisplayName());
        assetLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
        grid.add(assetLbl, 1, 0);

        grid.add(new Label("Flow Direction:"), 0, 1);
        Label dirLbl = new Label(placement.getDirection().getLabel() + " (" + placement.getDirection().name() + ")");
        dirLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
        grid.add(dirLbl, 1, 1);

        grid.add(new Label("Tag ID & Binding:"), 0, 2);
        Label tagIdLbl = new Label(tag != null ? tag.getId() + " (" + tag.getName() + ")" : "Unassigned / Passive");
        tagIdLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #7a0c1e;");
        grid.add(tagIdLbl, 1, 2);

        grid.add(new Label("Modbus Address:"), 0, 3);
        Label addrLbl = new Label(tag != null ? (tag.getType() == TagType.COIL ? "Coil %M" : "Discrete Input %I") + tag.getAddress() : "N/A");
        addrLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
        grid.add(addrLbl, 1, 3);

        grid.add(new Label("Live Status:"), 0, 4);
        Label stateLbl = new Label();
        if (tag != null) {
            if (placement.getAssetType() == AssetType.SENSOR) {
                stateLbl.setText(tag.isActive() ? "DETECTED (HIGH)" : "CLEAR (LOW)");
                stateLbl.setStyle(tag.isActive() ? "-fx-font-weight: bold; -fx-text-fill: #15803d;" : "-fx-font-weight: bold; -fx-text-fill: #64748b;");
            } else {
                stateLbl.setText(tag.isActive() ? "RUNNING / ACTIVE" : "STOPPED / IDLE");
                stateLbl.setStyle(tag.isActive() ? "-fx-font-weight: bold; -fx-text-fill: #15803d;" : "-fx-font-weight: bold; -fx-text-fill: #64748b;");
            }
        } else {
            stateLbl.setText("PASSIVE FIXTURE");
            stateLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #64748b;");
        }
        grid.add(stateLbl, 1, 4);

        if (placement.getAssetType() == AssetType.SENSOR && tag != null) {
            grid.add(new Label("Detections:"), 0, 5);
            Label ctLbl = new Label(sensorCounters.getOrDefault(tag.getId(), 0) + " Triggers");
            ctLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b;");
            grid.add(ctLbl, 1, 5);
        }

        propBox.getChildren().add(grid);

        // Engineering Actions Section
        VBox diagSection = new VBox(8);
        Label diagTitle = new Label("ENGINEERING DIAGNOSTICS & CONTROLS");
        diagTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #7a0c1e;");

        HBox actionBtns = new HBox(8);
        actionBtns.setAlignment(Pos.CENTER_LEFT);

        if (tag != null && (placement.getAssetType() == AssetType.CONVEYOR || placement.getAssetType() == AssetType.CURVED_CONVEYOR)) {
            Button diagToggleBtn = new Button(tag.isActive() ? "⏹ Stop Machine" : "▶ Start Machine");
            diagToggleBtn.getStyleClass().add(tag.isActive() ? "btn-estop" : "btn-primary");
            diagToggleBtn.setOnAction(e -> {
                handleToggleActuator(tag);
                dialog.close();
            });

            Button pulseBtn = new Button("⚡ 2s Pulse Test");
            pulseBtn.getStyleClass().add("btn-secondary");
            pulseBtn.setOnAction(e -> {
                handleQuickTestActuator(tag);
                dialog.close();
            });

            actionBtns.getChildren().addAll(diagToggleBtn, pulseBtn);
        } else if (tag != null && placement.getAssetType() == AssetType.SENSOR) {
            Button resetCtBtn = new Button("↺ Reset Counter");
            resetCtBtn.getStyleClass().add("btn-secondary");
            resetCtBtn.setOnAction(e -> {
                sensorCounters.put(tag.getId(), 0);
                SensorBlockRef ref = sensorRefs.get(tag.getId());
                if (ref != null && ref.counterLabel != null) {
                    ref.counterLabel.setText("Ct: 0");
                }
                log("[SENSOR] Counter reset to 0 for " + tag.getName());
                dialog.close();
            });
            actionBtns.getChildren().add(resetCtBtn);
        } else {
            Label passiveNotice = new Label("Passive fixture — no electrical actuation or direct I/O binding.");
            passiveNotice.setStyle("-fx-font-size: 11px; -fx-text-fill: #94a3b8;");
            actionBtns.getChildren().add(passiveNotice);
        }

        diagSection.getChildren().addAll(diagTitle, actionBtns);

        content.getChildren().addAll(headerBox, new Separator(), propBox, new Separator(), diagSection);
        dialogPane.setContent(content);

        dialog.showAndWait();
    }

    private String getPlacementTitle(FloorCellPlacement placement) {
        if (placement.getTagId() != null) {
            ModbusTag tag = tagManager.findTagById(placement.getTagId());
            if (tag != null) return tag.getName();
        }
        return placement.getAssetType().getDisplayName();
    }

    private String getPlacementTagBadge(FloorCellPlacement placement) {
        if (placement.getTagId() != null) {
            ModbusTag tag = tagManager.findTagById(placement.getTagId());
            if (tag != null) {
                return (tag.getType() == TagType.COIL ? "Coil " : "DI ") + tag.getAddress();
            }
        }
        return placement.getAssetType().getSubTitle();
    }

    private String getBridgeArrowForDirection(Direction dir) {
        if (dir == null) return "──►";
        return switch (dir) {
            case EAST -> "──►";
            case WEST -> "◄──";
            case SOUTH -> "│\n▼";
            case NORTH -> "▲\n│";
        };
    }

    private void drawPreviewForPlacement(Canvas canvas, FloorCellPlacement placement) {
        if (canvas == null) return;
        switch (placement.getAssetType()) {
            case CONVEYOR -> drawConveyorBelt(canvas, false, 0, placement.getDirection());
            case CURVED_CONVEYOR -> drawCurvedConveyorBelt(canvas, false, 0, placement.getDirection());
            case SENSOR -> drawSensorVisual(canvas, false, placement.getDirection());
            case INFEED -> {
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.setFill(Color.web("#0f172a"));
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
                gc.fillText("📥 ENTRY CHUTE", 10, 15);
            }
            case DEPOT -> {
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.setFill(Color.web("#0f172a"));
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                gc.setFill(Color.WHITE);
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
                gc.fillText("📦 OUTFEED DEPOT", 8, 15);
            }
            case BRIDGE -> {
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.setFill(Color.web("#f8fafc"));
                gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
                gc.setFill(Color.web("#7a0c1e"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                gc.fillText(getBridgeArrowForDirection(placement.getDirection()), canvas.getWidth() / 2 - 8, 15);
            }
        }
    }

    private void openEquipmentPickerDialog(int row, int col) {
        Dialog<FloorCellPlacement> dialog = new Dialog<>();
        dialog.setTitle("Factory Hardware Placement");
        dialog.setHeaderText("Place Equipment at Grid Slot [" + (row + 1) + ", " + (col + 1) + "]");

        ButtonType placeButtonType = new ButtonType("Place Machine", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(placeButtonType, ButtonType.CANCEL);

        VBox content = new VBox(10);
        content.setPrefWidth(380);

        Label prompt = new Label("Select hardware tag or facility terminal to install:");
        prompt.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #1e293b;");

        ComboBox<PlacementOption> comboOptions = new ComboBox<>();
        comboOptions.setMaxWidth(Double.MAX_VALUE);

        ObservableList<PlacementOption> options = FXCollections.observableArrayList();

        // 1. Actuators from Tag Profiler
        for (ModbusTag tag : tagManager.getActuatorTags()) {
            boolean alreadyPlaced = floorLayoutService.isTagPlaced(tag.getId());
            AssetType type = isCurvedConveyor(tag) ? AssetType.CURVED_CONVEYOR : AssetType.CONVEYOR;
            String label = type.getIcon() + " " + tag.getName() + " (Coil " + tag.getAddress() + ")" + (alreadyPlaced ? " [In Use]" : "");
            options.add(new PlacementOption(type, tag.getId(), label, alreadyPlaced));
        }

        // 2. Sensors from Tag Profiler
        for (ModbusTag tag : tagManager.getSensorTags()) {
            boolean alreadyPlaced = floorLayoutService.isTagPlaced(tag.getId());
            String label = "👁️ " + tag.getName() + " (Input " + tag.getAddress() + ")" + (alreadyPlaced ? " [In Use]" : "");
            options.add(new PlacementOption(AssetType.SENSOR, tag.getId(), label, alreadyPlaced));
        }

        // 3. Terminals & Bridge
        options.add(new PlacementOption(AssetType.INFEED, null, "📥 Infeed Chute (Entry Point)", false));
        options.add(new PlacementOption(AssetType.DEPOT, null, "📦 Warehouse Depot (Outfeed Chute)", false));
        options.add(new PlacementOption(AssetType.BRIDGE, null, "──► Conveyor Bridge (Flow Link)", false));

        comboOptions.setItems(options);
        comboOptions.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(PlacementOption item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setDisable(false);
                } else {
                    setText(item.display);
                    setDisable(item.isPlaced);
                    if (item.isPlaced) {
                        setStyle("-fx-text-fill: #94a3b8;");
                    } else {
                        setStyle("-fx-text-fill: #0f172a; -fx-font-weight: 500;");
                    }
                }
            }
        });
        comboOptions.setButtonCell(comboOptions.getCellFactory().call(null));

        for (PlacementOption opt : options) {
            if (!opt.isPlaced) {
                comboOptions.getSelectionModel().select(opt);
                break;
            }
        }

        Label dirLabel = new Label("Initial Flow Orientation:");
        dirLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #475569;");
        ComboBox<Direction> comboDir = new ComboBox<>(FXCollections.observableArrayList(Direction.values()));
        comboDir.getSelectionModel().select(Direction.EAST);
        comboDir.setMaxWidth(Double.MAX_VALUE);

        content.getChildren().addAll(prompt, comboOptions, dirLabel, comboDir);
        dialog.getDialogPane().setContent(content);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == placeButtonType) {
                PlacementOption selected = comboOptions.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    return new FloorCellPlacement(row, col, selected.type, selected.tagId, comboDir.getValue());
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(placement -> {
            floorLayoutService.setPlacement(placement);
            floorLayoutService.saveToFile();
            renderFloorGrid();
            log("[SCADA STUDIO] Installed " + placement.getAssetType().getDisplayName() + " at [" + (row + 1) + "," + (col + 1) + "]");
        });
    }

    private static class PlacementOption {
        AssetType type;
        String tagId;
        String display;
        boolean isPlaced;

        PlacementOption(AssetType type, String tagId, String display, boolean isPlaced) {
            this.type = type;
            this.tagId = tagId;
            this.display = display;
            this.isPlaced = isPlaced;
        }

        @Override
        public String toString() {
            return display;
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
            ref.statusPill.setText(running ? "● RUN" : "● IDLE");
            ref.statusPill.getStyleClass().removeAll("status-pill-running", "status-pill-stopped");
            ref.statusPill.getStyleClass().add(running ? "status-pill-running" : "status-pill-stopped");

            ref.toggleBtn.setText(running ? "⏹" : "▶");
            ref.toggleBtn.getStyleClass().removeAll("symbol-toggle-btn", "symbol-toggle-btn-stop", "btn-primary", "btn-estop");
            ref.toggleBtn.getStyleClass().add(running ? "symbol-toggle-btn-stop" : "symbol-toggle-btn");
            ref.toggleBtn.setTooltip(new Tooltip(running ? "Stop Conveyor" : "Start Conveyor"));

            if (running) {
                if (!ref.block.getStyleClass().contains("pipeline-block-running")) {
                    ref.block.getStyleClass().add("pipeline-block-running");
                }
            } else {
                ref.block.getStyleClass().remove("pipeline-block-running");
            }

            if (isCurvedConveyor(tag)) {
                drawCurvedConveyorBelt(ref.beltCanvas, running, beltOffset, ref.direction);
            } else {
                drawConveyorBelt(ref.beltCanvas, running, beltOffset, ref.direction);
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
            ref.statusLabel.setText(active ? "DETECTED" : "CLEAR");
            ref.statusLabel.setStyle(active ? "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #15803d;" : "-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #64748b;");

            if (active) {
                if (!ref.block.getStyleClass().contains("pipeline-block-detected")) {
                    ref.block.getStyleClass().add("pipeline-block-detected");
                }
                int count = sensorCounters.compute(tag.getId(), (k, v) -> v == null ? 1 : v + 1);
                ref.counterLabel.setText("Ct: " + count);
                int total = totalDetectedPackages.incrementAndGet();
                lblKpiPackageCount.setText(total + " Pcs");
            } else {
                ref.block.getStyleClass().remove("pipeline-block-detected");
            }

            drawSensorVisual(ref.sensorCanvas, active, ref.direction);
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
