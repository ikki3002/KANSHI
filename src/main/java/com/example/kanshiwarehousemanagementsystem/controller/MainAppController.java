package com.example.kanshiwarehousemanagementsystem.controller;

import com.example.kanshiwarehousemanagementsystem.HelloApplication;
import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag.TagType;
import com.example.kanshiwarehousemanagementsystem.model.Product;
import com.example.kanshiwarehousemanagementsystem.model.User;
import com.example.kanshiwarehousemanagementsystem.service.modbus.FactoryIOService;
import com.example.kanshiwarehousemanagementsystem.service.modbus.TagManager;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
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
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
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
import java.util.concurrent.CopyOnWriteArrayList;
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

    // Level 1 KPI Overview Labels & Badges
    @FXML private Label lblKpiValuation;
    @FXML private Label lblKpiInventory;
    @FXML private Label lblKpiLineState;
    @FXML private Label lblKpiPackageCount;
    @FXML private Label lblKpiSkuCount;
    @FXML private Label lblKpiActiveEqCount;
    @FXML private Circle circleKpiLineDot;
    @FXML private Label lblKpiLowStockBadge;

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

    // Overview Dashboard Stream & Filter Buttons
    @FXML private TextArea txtAuditStream;
    @FXML private Button btnFilterAll;
    @FXML private Button btnFilterOt;
    @FXML private Button btnFilterIt;
    @FXML private Button btnFilterSys;

    // Tag Settings
    @FXML private TableView<ModbusTag> tableTags;
    @FXML private TableColumn<ModbusTag, String> colTagName;
    @FXML private TableColumn<ModbusTag, String> colTagType;
    @FXML private TableColumn<ModbusTag, Number> colTagAddress;
    @FXML private TableColumn<ModbusTag, Void> colTagAction;

    @FXML private TextField txtNewTagName;
    @FXML private TextField txtNewTagAddress;
    @FXML private ComboBox<TagType> cmbNewTagType;

    // Level 2B Inventory Ledger FXML Controls
    @FXML private Label lblInvLedgerValuation;
    @FXML private Label lblInvLedgerTotalUnits;
    @FXML private TextField txtInventorySearch;
    @FXML private ComboBox<String> cmbCategoryFilter;
    @FXML private TableView<Product> tableInventory;
    @FXML private TableColumn<Product, String> colInvSku;
    @FXML private TableColumn<Product, String> colInvName;
    @FXML private TableColumn<Product, String> colInvCategory;
    @FXML private TableColumn<Product, Number> colInvQuantity;
    @FXML private TableColumn<Product, Number> colInvUnitPrice;
    @FXML private TableColumn<Product, Number> colInvTotalValue;
    @FXML private TableColumn<Product, String> colInvLocation;
    @FXML private TableColumn<Product, Void> colInvActions;
    @FXML private Label lblInventoryRowCount;

    private final ObservableList<Product> inventoryData = FXCollections.observableArrayList();
    private FilteredList<Product> filteredInventoryData;

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
        Circle ledDot;
        Button toggleBtn;
        Canvas beltCanvas;
        Direction direction;
        Tooltip tooltip;
    }

    private static class SensorBlockRef {
        VBox block;
        Circle led;
        Label counterLabel;
        Canvas sensorCanvas;
        Direction direction;
        Tooltip tooltip;
    }

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        initClock();
        setupTagTable();
        setupTagForm();
        setupInventoryLedger();
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
     * Canvas is 108 x 54px. Pivot is placed at a canvas corner so the arc sweeps
     * across the full visible area without clipping.
     *
     * Direction semantics (which corner the pivot sits at):
     *   EAST  (→ then ↓) : pivot bottom-left  (0, h)   arc sweeps 0→90°
     *   WEST  (← then ↑) : pivot top-right    (w, 0)   arc sweeps 180→270°
     *   SOUTH (↓ then →) : pivot top-left     (0, 0)   arc sweeps 270→360° (same as EAST visually flipped)
     *   NORTH (↑ then ←) : pivot bottom-right (w, h)   arc sweeps 90→180°
     */
    private void drawCurvedConveyorBelt(Canvas canvas, boolean running, double offset, Direction dir) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();   // 108
        double h = canvas.getHeight();  // 54

        // --- 1. Background ---
        gc.setFill(running ? Color.web("#f0fdf4") : Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        // --- 2. Chassis border ---
        gc.setStroke(running ? Color.web("#22c55e") : Color.web("#cbd5e1"));
        gc.setLineWidth(running ? 1.8 : 1.0);
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, 5, 5);

        // --- 3. Choose pivot corner and arc sweep based on direction ---
        Direction d = (dir != null) ? dir : Direction.EAST;
        double pivotX, pivotY;
        double arcStartDeg;

        switch (d) {
            case WEST -> {
                pivotX = w; pivotY = 0;     // top-right corner
                arcStartDeg = 180;
            }
            case SOUTH -> {
                pivotX = 0;  pivotY = 0;    // top-left corner
                arcStartDeg = 270;
            }
            case NORTH -> {
                pivotX = w; pivotY = h;     // bottom-right corner
                arcStartDeg = 90;
            }
            default -> {                    // EAST: bottom-left
                pivotX = 0;  pivotY = h;
                arcStartDeg = 0;
            }
        }

        // Radii chosen so arcs stay within the 108×54 canvas:
        //   inner rail ≈ h/2 - 4 = ~23px from pivot
        //   outer rail ≈ h - 4   = ~50px from pivot
        double rInner = h / 2.0 - 3;   // ~24
        double rOuter = h - 4;         // ~50

        // --- 4. Draw the two belt rails (arcs) ---
        Color railColor = running ? Color.web("#15803d") : Color.web("#64748b");
        gc.setStroke(railColor);
        gc.setLineWidth(running ? 2.8 : 2.0);

        // strokeArc(x, y, w, h, startAngle, arcExtent, arcType)
        // JavaFX arc: x,y is top-left of bounding box; angle 0 = 3 o'clock, CCW positive.
        // We sweep +90° for all orientations; the pivot corner placement provides the rotation.
        gc.strokeArc(pivotX - rOuter, pivotY - rOuter, rOuter * 2, rOuter * 2, arcStartDeg, 90, ArcType.OPEN);
        gc.strokeArc(pivotX - rInner, pivotY - rInner, rInner * 2, rInner * 2, arcStartDeg, 90, ArcType.OPEN);

        if (running) {
            // Bright glow highlight just outside the outer rail
            gc.setStroke(Color.web("#4ade80"));
            gc.setLineWidth(1.0);
            double rGlow = rOuter + 2;
            gc.strokeArc(pivotX - rGlow, pivotY - rGlow, rGlow * 2, rGlow * 2, arcStartDeg, 90, ArcType.OPEN);
        }

        // --- 5. Radial rollers between the two rails ---
        double rollerStep = 12.0;  // degrees between each roller
        // Animate by advancing the start angle with belt offset
        double animShift = running ? (offset / 18.0 * rollerStep) % rollerStep : 0;
        double endAngle = arcStartDeg + 90;

        gc.setStroke(running ? Color.web("#94a3b8") : Color.web("#cbd5e1"));
        gc.setLineWidth(running ? 2.0 : 1.5);

        for (double angleDeg = arcStartDeg + animShift; angleDeg < endAngle; angleDeg += rollerStep) {
            double rad = Math.toRadians(angleDeg);
            double cosA = Math.cos(rad);
            double sinA = Math.sin(rad);
            // JavaFX y-axis is inverted vs math: subtract sin
            double x1 = pivotX + rInner * cosA;
            double y1 = pivotY - rInner * sinA;
            double x2 = pivotX + rOuter * cosA;
            double y2 = pivotY - rOuter * sinA;
            gc.strokeLine(x1, y1, x2, y2);
        }

        // --- 6. Center-line flow indicator arc ---
        double rMid = (rInner + rOuter) / 2.0;
        if (running) {
            gc.setStroke(Color.web("#22c55e"));
            gc.setLineWidth(2.0);
            // Draw a shorter highlighted segment that advances with offset
            double flowStart = arcStartDeg + (offset % 30);
            if (flowStart > endAngle) flowStart = arcStartDeg;
            double flowSweep = Math.min(28, endAngle - flowStart);
            gc.strokeArc(pivotX - rMid, pivotY - rMid, rMid * 2, rMid * 2, flowStart, flowSweep, ArcType.OPEN);
        } else {
            gc.setStroke(Color.web("#94a3b8"));
            gc.setLineWidth(1.2);
            gc.strokeArc(pivotX - rMid, pivotY - rMid, rMid * 2, rMid * 2, arcStartDeg, 90, ArcType.OPEN);
        }

        // --- 7. Corner label ---
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 9));
        if (running) {
            gc.setFill(Color.web("#15803d"));
            gc.fillText("↷ 90°", 4, 12);
        } else {
            gc.setFill(Color.web("#94a3b8"));
            gc.fillText("CORNER", 3, 12);
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

        // 1. Background
        gc.setFill(running ? Color.web("#f0fdf4") : Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        // 2. Chassis border
        gc.setStroke(running ? Color.web("#22c55e") : Color.web("#cbd5e1"));
        gc.setLineWidth(running ? 1.8 : 1.0);
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, 5, 5);

        Direction d = dir != null ? dir : Direction.EAST;

        if (d == Direction.EAST || d == Direction.WEST) {
            // Horizontal rails (Top and Bottom)
            gc.setFill(running ? Color.web("#15803d") : Color.web("#64748b"));
            gc.fillRect(2, 3, w - 4, 3.5);
            gc.fillRect(2, h - 6.5, w - 4, 3.5);

            if (running) {
                gc.setFill(Color.web("#4ade80"));
                gc.fillRect(2, 2, w - 4, 1.2);
                gc.fillRect(2, h - 3.2, w - 4, 1.2);
            }

            // Rollers spanning vertically across the belt bed
            for (double x = 4; x < w - 8; x += 14) {
                gc.setFill(running ? Color.web("#ffffff") : Color.web("#f8fafc"));
                gc.fillRoundRect(x, 7, 9, h - 14, 3, 3);
                gc.setStroke(running ? Color.web("#94a3b8") : Color.web("#cbd5e1"));
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(x, 7, 9, h - 14, 3, 3);

                if (running) {
                    double rotY = 8 + ((offset + x * 2) % (h - 18));
                    gc.setStroke(Color.web("#22c55e"));
                    gc.setLineWidth(1.5);
                    gc.strokeLine(x + 1, rotY, x + 8, rotY);
                }
            }

            // Center Flow Chevron Stream
            if (running) {
                double flowOff = (d == Direction.EAST) ? (offset % 24) : (24 - (offset % 24));
                gc.setFill(Color.web("#15803d"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 13));
                for (double fx = 6 + flowOff; fx < w - 6; fx += 24) {
                    gc.fillText(d == Direction.EAST ? "»" : "«", fx, h / 2 + 5);
                }
            } else {
                gc.setFill(Color.web("#94a3b8"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                gc.fillText(d == Direction.EAST ? "→" : "←", w / 2 - 4, h / 2 + 4);
            }
        } else {
            // Vertical rails (Left and Right)
            gc.setFill(running ? Color.web("#15803d") : Color.web("#64748b"));
            gc.fillRect(3, 2, 3.5, h - 4);
            gc.fillRect(w - 6.5, 2, 3.5, h - 4);

            if (running) {
                gc.setFill(Color.web("#4ade80"));
                gc.fillRect(2, 2, 1.2, h - 4);
                gc.fillRect(w - 3.2, 2, 1.2, h - 4);
            }

            // Rollers spanning horizontally across the belt bed
            for (double y = 4; y < h - 8; y += 12) {
                gc.setFill(running ? Color.web("#ffffff") : Color.web("#f8fafc"));
                gc.fillRoundRect(7, y, w - 14, 8, 3, 3);
                gc.setStroke(running ? Color.web("#94a3b8") : Color.web("#cbd5e1"));
                gc.setLineWidth(1.0);
                gc.strokeRoundRect(7, y, w - 14, 8, 3, 3);

                if (running) {
                    double rotX = 8 + ((offset + y * 2) % (w - 18));
                    gc.setStroke(Color.web("#22c55e"));
                    gc.setLineWidth(1.5);
                    gc.strokeLine(rotX, y + 1, rotX, y + 7);
                }
            }

            // Center Flow Chevron Stream
            if (running) {
                double flowOff = (d == Direction.SOUTH) ? (offset % 20) : (20 - (offset % 20));
                gc.setFill(Color.web("#15803d"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                for (double fy = 6 + flowOff; fy < h - 6; fy += 20) {
                    gc.fillText(d == Direction.SOUTH ? "▼" : "▲", w / 2 - 5, fy + 8);
                }
            } else {
                gc.setFill(Color.web("#94a3b8"));
                gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
                gc.fillText(d == Direction.SOUTH ? "↓" : "↑", w / 2 - 4, h / 2 + 4);
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

        gc.setStroke(detected ? Color.web("#22c55e") : Color.web("#cbd5e1"));
        gc.setLineWidth(detected ? 1.8 : 1.0);
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, 5, 5);

        double centerX = w / 2.0;

        // Optical Sensor Head at top
        gc.setFill(Color.web("#0f172a"));
        gc.fillRoundRect(centerX - 16, 2, 32, 12, 3, 3);

        gc.setFill(detected ? Color.web("#22c55e") : Color.web("#0284c7"));
        gc.fillOval(centerX - 4, 10, 8, 4);

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 7));
        gc.fillText("OPTICAL", centerX - 14, 10);

        // Conveyor bed rails at bottom
        gc.setFill(Color.web("#64748b"));
        gc.fillRect(2, h - 6, w - 4, 3);

        if (detected) {
            // Crate centered on conveyor bed
            double bw = 32, bh = 22;
            double bx = centerX - bw / 2;
            double by = h - bh - 6;

            // Translucent laser halo cone
            gc.setFill(Color.rgb(34, 197, 94, 0.20));
            gc.fillPolygon(
                new double[]{centerX, bx, bx + bw},
                new double[]{14, by, by},
                3
            );

            // Emerald laser line
            gc.setStroke(Color.web("#22c55e"));
            gc.setLineWidth(2.4);
            gc.strokeLine(centerX, 14, centerX, by);

            // 3D Shipping Crate
            gc.setFill(Color.web("#d97706"));
            gc.fillRoundRect(bx, by, bw, bh, 3, 3);
            gc.setStroke(Color.web("#92400e"));
            gc.setLineWidth(1.2);
            gc.strokeRoundRect(bx, by, bw, bh, 3, 3);

            // Packing tape
            gc.setStroke(Color.web("#fef3c7"));
            gc.setLineWidth(1.6);
            gc.strokeLine(bx, by + bh / 2, bx + bw, by + bh / 2);

            // Kanshi label
            gc.setFill(Color.WHITE);
            gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 7));
            gc.fillText("KANSHI", bx + 3, by + 10);

            // Laser impact dot & ripple
            gc.setFill(Color.web("#4ade80"));
            gc.fillOval(centerX - 3, by - 2, 6, 4);
            gc.setStroke(Color.web("#22c55e"));
            gc.setLineWidth(1.0);
            gc.strokeOval(centerX - 6, by - 4, 12, 8);
        } else {
            // Clear Standby Beam
            gc.setStroke(Color.web("#94a3b8"));
            gc.setLineWidth(1.2);
            gc.setLineDashes(4);
            gc.strokeLine(centerX, 14, centerX, h - 6);
            gc.setLineDashes(null);

            // Reflector plate at bottom
            gc.setFill(Color.web("#64748b"));
            gc.fillRoundRect(centerX - 10, h - 6, 20, 3, 1.5, 1.5);
        }
    }

    private void drawInfeedVisual(Canvas canvas, double offset, Direction dir) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#cbd5e1"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, 5, 5);

        // Hopper chute geometry (wedge funnel on left)
        gc.setFill(Color.web("#1e293b"));
        gc.fillPolygon(
            new double[]{4, 4, 32, 32},
            new double[]{4, h - 4, h - 14, 14},
            4
        );
        gc.setStroke(Color.web("#0f172a"));
        gc.setLineWidth(1.5);
        gc.strokePolygon(
            new double[]{4, 4, 32, 32},
            new double[]{4, h - 4, h - 14, 14},
            4
        );

        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
        gc.fillText("FEED", 8, h / 2 + 3);

        // Rollers continuing out of hopper
        for (double x = 38; x < w - 8; x += 14) {
            gc.setFill(Color.web("#f1f5f9"));
            gc.fillRoundRect(x, 10, 9, h - 20, 3, 3);
            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1.0);
            gc.strokeRoundRect(x, 10, 9, h - 20, 3, 3);
        }

        // Animated entry arrow
        gc.setFill(Color.web("#7a0c1e"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        double arrowX = 40 + (offset % 20);
        gc.fillText("»»", Math.min(arrowX, w - 24), h / 2 + 4);
    }

    private void drawDepotVisual(Canvas canvas, double offset, Direction dir) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#cbd5e1"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, 5, 5);

        // Rollers on left entering depot bay
        for (double x = 4; x < 54; x += 14) {
            gc.setFill(Color.web("#f1f5f9"));
            gc.fillRoundRect(x, 10, 9, h - 20, 3, 3);
            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1.0);
            gc.strokeRoundRect(x, 10, 9, h - 20, 3, 3);
        }

        // Pallet bay on right
        gc.setFill(Color.web("#0f172a"));
        gc.fillRoundRect(58, 4, w - 62, h - 8, 4, 4);

        // Wooden pallet base
        gc.setFill(Color.web("#b45309"));
        gc.fillRoundRect(62, h - 14, w - 70, 7, 2, 2);

        // Stacked boxes
        gc.setFill(Color.web("#d97706"));
        gc.fillRoundRect(64, h - 34, 18, 18, 2, 2);
        gc.setStroke(Color.web("#92400e"));
        gc.strokeRect(64, h - 34, 18, 18);

        gc.setFill(Color.web("#d97706"));
        gc.fillRoundRect(80, h - 28, 16, 12, 2, 2);
        gc.setStroke(Color.web("#92400e"));
        gc.strokeRect(80, h - 28, 16, 12);

        gc.setFill(Color.web("#15803d"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 12));
        double arrowX = 8 + (offset % 20);
        gc.fillText("»", Math.min(arrowX, 44), h / 2 + 4);
    }

    private void drawBridgeVisual(Canvas canvas, Direction dir) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#cbd5e1"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, 5, 5);

        // Bridge chassis rails
        gc.setFill(Color.web("#64748b"));
        gc.fillRect(2, 4, w - 4, 3);
        gc.fillRect(2, h - 7, w - 4, 3);

        // Steel rollers across bridge
        for (double x = 4; x < w - 6; x += 14) {
            gc.setFill(Color.web("#f1f5f9"));
            gc.fillRoundRect(x, 9, 9, h - 18, 3, 3);
            gc.setStroke(Color.web("#cbd5e1"));
            gc.setLineWidth(1.0);
            gc.strokeRoundRect(x, 9, 9, h - 18, 3, 3);
        }

        // Bridge direction arrow
        gc.setFill(Color.web("#7a0c1e"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 18));
        String arrow = getBridgeArrowForDirection(dir);
        gc.fillText(arrow, w / 2 - 12, h / 2 + 6);
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

    // =========================================================================
    // Level 2B: IT Warehouse Inventory Stock Ledger Implementation (Module 4)
    // =========================================================================

    private void setupInventoryLedger() {
        if (tableInventory == null) return;

        // 1. Column Value Factories
        colInvSku.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getSku()));
        colInvName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colInvCategory.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getCategory()));
        colInvQuantity.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getQuantity()));
        colInvUnitPrice.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().getUnitPrice()));
        colInvTotalValue.setCellValueFactory(cellData -> new SimpleDoubleProperty(cellData.getValue().getTotalValue()));
        colInvLocation.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getLocation()));

        // 2. Custom Column Cell Renderers
        colInvSku.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    badge.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-text-fill: #7a0c1e; -fx-background-color: #fee2e2; -fx-padding: 3 8; -fx-background-radius: 4px;");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colInvCategory.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label pill = new Label(item);
                    pill.getStyleClass().add("tag-badge");
                    pill.setStyle("-fx-font-size: 11px; -fx-padding: 3 8; -fx-background-radius: 12px;");
                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        colInvQuantity.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    int qty = item.intValue();
                    Label pill = new Label(qty + (qty <= 15 ? " (LOW)" : ""));
                    if (qty <= 15) {
                        pill.setStyle("-fx-font-weight: bold; -fx-text-fill: #991b1b; -fx-background-color: #fee2e2; -fx-padding: 3 8; -fx-background-radius: 4px;");
                    } else {
                        pill.setStyle("-fx-font-weight: bold; -fx-text-fill: #15803d; -fx-background-color: #dcfce7; -fx-padding: 3 8; -fx-background-radius: 4px;");
                    }
                    setGraphic(pill);
                    setText(null);
                }
            }
        });

        colInvUnitPrice.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("$%.2f", item.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-padding: 0 10; -fx-font-size: 12px;");
            }
        });

        colInvTotalValue.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("$%.2f", item.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 0 10; -fx-font-size: 12px;");
            }
        });

        colInvActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("✏️");
            private final Button btnDelete = new Button("🗑");
            private final HBox pane = new HBox(6, btnEdit, btnDelete);

            {
                pane.setAlignment(Pos.CENTER);
                btnEdit.getStyleClass().add("cell-tool-btn");
                btnEdit.setTooltip(new Tooltip("Edit Product Details"));
                btnEdit.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    openEditProductDialog(p);
                });

                btnDelete.getStyleClass().add("cell-tool-btn-danger");
                btnDelete.setTooltip(new Tooltip("Delete Product from Ledger"));
                btnDelete.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    handleDeleteProduct(p);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        // 3. Category Filter Dropdown
        cmbCategoryFilter.setItems(FXCollections.observableArrayList(
                "All Categories", "Packaging", "Material Handling", "Automation Parts", "Spares"
        ));
        cmbCategoryFilter.getSelectionModel().selectFirst();

        // 4. Live Search and Filter Chain
        filteredInventoryData = new FilteredList<>(inventoryData, p -> true);

        txtInventorySearch.textProperty().addListener((obs, oldVal, newVal) -> applyInventoryFilter());
        cmbCategoryFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyInventoryFilter());

        SortedList<Product> sortedList = new SortedList<>(filteredInventoryData);
        sortedList.comparatorProperty().bind(tableInventory.comparatorProperty());
        tableInventory.setItems(sortedList);

        loadInventoryData();
    }

    private void applyInventoryFilter() {
        String query = txtInventorySearch.getText() != null ? txtInventorySearch.getText().trim().toLowerCase() : "";
        String cat = cmbCategoryFilter.getValue();
        boolean filterCategory = cat != null && !"All Categories".equalsIgnoreCase(cat);

        filteredInventoryData.setPredicate(p -> {
            if (p == null) return false;
            boolean matchesCat = !filterCategory || (p.getCategory() != null && p.getCategory().equalsIgnoreCase(cat));
            if (!matchesCat) return false;

            if (query.isEmpty()) return true;
            boolean matchesSku = p.getSku() != null && p.getSku().toLowerCase().contains(query);
            boolean matchesName = p.getName() != null && p.getName().toLowerCase().contains(query);
            boolean matchesLoc = p.getLocation() != null && p.getLocation().toLowerCase().contains(query);
            return matchesSku || matchesName || matchesLoc;
        });

        if (lblInventoryRowCount != null) {
            lblInventoryRowCount.setText("Showing " + filteredInventoryData.size() + " of " + inventoryData.size() + " products");
        }
    }

    public void loadInventoryData() {
        List<Product> products = inventoryDao.getAllProducts();
        inventoryData.setAll(products);

        int totalUnits = inventoryDao.getTotalStockCount();
        double totalValuation = inventoryDao.getTotalValuation();

        if (lblInvLedgerTotalUnits != null) {
            lblInvLedgerTotalUnits.setText(String.format("%,d Units", totalUnits));
        }
        if (lblInvLedgerValuation != null) {
            lblInvLedgerValuation.setText(String.format("$%,.2f", totalValuation));
        }
        if (lblInventoryRowCount != null) {
            lblInventoryRowCount.setText("Showing " + (filteredInventoryData != null ? filteredInventoryData.size() : products.size()) + " of " + products.size() + " products");
        }

        refreshKpiMetrics();
    }

    @FXML
    private void handleRefreshInventory(ActionEvent event) {
        loadInventoryData();
        log("[INVENTORY] Refreshed stock ledger from SQLite.");
        logAudit("IT-STOCK", "Stock ledger reloaded from SQLite database.");
    }

    @FXML
    private void handleOpenAddProduct(ActionEvent event) {
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Add New Warehouse Product");
        dialog.setHeaderText("Register an incoming product SKU into SQLite inventory.");
        if (mainContentPane != null && mainContentPane.getScene() != null && mainContentPane.getScene().getWindow() != null) {
            dialog.initOwner(mainContentPane.getScene().getWindow());
        }

        ButtonType saveBtnType = new ButtonType("Save Product", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 20px;");

        TextField txtSku = new TextField();
        txtSku.setPromptText("e.g. BOX-LRG-103");
        TextField txtName = new TextField();
        txtName.setPromptText("e.g. Extra Large Shipping Crate");
        ComboBox<String> cmbCat = new ComboBox<>(FXCollections.observableArrayList("Packaging", "Material Handling", "Automation Parts", "Spares"));
        cmbCat.getSelectionModel().selectFirst();
        TextField txtQty = new TextField("50");
        TextField txtPrice = new TextField("25.00");
        TextField txtLocation = new TextField();
        txtLocation.setPromptText("e.g. Aisle B-03");

        grid.add(new Label("SKU Code:"), 0, 0);
        grid.add(txtSku, 1, 0);
        grid.add(new Label("Product Name:"), 0, 1);
        grid.add(txtName, 1, 1);
        grid.add(new Label("Category:"), 0, 2);
        grid.add(cmbCat, 1, 2);
        grid.add(new Label("Initial Quantity:"), 0, 3);
        grid.add(txtQty, 1, 3);
        grid.add(new Label("Unit Price ($):"), 0, 4);
        grid.add(txtPrice, 1, 4);
        grid.add(new Label("Bin Location:"), 0, 5);
        grid.add(txtLocation, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == saveBtnType) {
                String sku = txtSku.getText().trim();
                String name = txtName.getText().trim();
                String cat = cmbCat.getValue();
                String loc = txtLocation.getText().trim();
                int qty;
                double price;

                if (sku.isEmpty() || name.isEmpty() || loc.isEmpty()) {
                    showAlert("Validation Error", "All fields are required.");
                    return null;
                }
                if (!inventoryDao.isSkuUnique(sku, 0)) {
                    showAlert("SKU Exists", "A product with SKU '" + sku + "' already exists in SQLite.");
                    return null;
                }
                try {
                    qty = Integer.parseInt(txtQty.getText().trim());
                    price = Double.parseDouble(txtPrice.getText().trim());
                    if (qty < 0 || price < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    showAlert("Number Error", "Quantity must be integer >= 0 and price must be a valid positive number.");
                    return null;
                }
                return new Product(sku, name, cat, qty, price, loc);
            }
            return null;
        });

        dialog.showAndWait().ifPresent(p -> {
            boolean ok = inventoryDao.addProduct(p);
            if (ok) {
                loadInventoryData();
                log("[INVENTORY] Added new product: " + p.getSku() + " - " + p.getName());
                logAudit("IT-STOCK", "Product registered: " + p.getSku() + " (" + p.getName() + "), Qty: " + p.getQuantity() + ", Price: $" + p.getUnitPrice());
            } else {
                showAlert("Database Error", "Failed to add product into SQLite.");
            }
        });
    }

    private void openEditProductDialog(Product product) {
        if (product == null) return;
        Dialog<Product> dialog = new Dialog<>();
        dialog.setTitle("Edit Product - " + product.getSku());
        dialog.setHeaderText("Update details for product SKU: " + product.getSku());
        if (mainContentPane != null && mainContentPane.getScene() != null && mainContentPane.getScene().getWindow() != null) {
            dialog.initOwner(mainContentPane.getScene().getWindow());
        }

        ButtonType updateBtnType = new ButtonType("Update Product", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(updateBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 20px;");

        TextField txtSku = new TextField(product.getSku());
        txtSku.setEditable(false);
        txtSku.setStyle("-fx-background-color: #f1f5f9;");

        TextField txtName = new TextField(product.getName());
        ComboBox<String> cmbCat = new ComboBox<>(FXCollections.observableArrayList("Packaging", "Material Handling", "Automation Parts", "Spares"));
        cmbCat.setValue(product.getCategory());
        TextField txtQty = new TextField(String.valueOf(product.getQuantity()));
        TextField txtPrice = new TextField(String.valueOf(product.getUnitPrice()));
        TextField txtLocation = new TextField(product.getLocation());

        grid.add(new Label("SKU Code (Fixed):"), 0, 0);
        grid.add(txtSku, 1, 0);
        grid.add(new Label("Product Name:"), 0, 1);
        grid.add(txtName, 1, 1);
        grid.add(new Label("Category:"), 0, 2);
        grid.add(cmbCat, 1, 2);
        grid.add(new Label("Quantity on Hand:"), 0, 3);
        grid.add(txtQty, 1, 3);
        grid.add(new Label("Unit Price ($):"), 0, 4);
        grid.add(txtPrice, 1, 4);
        grid.add(new Label("Bin Location:"), 0, 5);
        grid.add(txtLocation, 1, 5);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == updateBtnType) {
                String name = txtName.getText().trim();
                String cat = cmbCat.getValue();
                String loc = txtLocation.getText().trim();
                int qty;
                double price;

                if (name.isEmpty() || loc.isEmpty()) {
                    showAlert("Validation Error", "Name and Location are required.");
                    return null;
                }
                try {
                    qty = Integer.parseInt(txtQty.getText().trim());
                    price = Double.parseDouble(txtPrice.getText().trim());
                    if (qty < 0 || price < 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    showAlert("Number Error", "Quantity must be integer >= 0 and price must be a valid positive number.");
                    return null;
                }
                product.setName(name);
                product.setCategory(cat);
                product.setQuantity(qty);
                product.setUnitPrice(price);
                product.setLocation(loc);
                return product;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(p -> {
            boolean ok = inventoryDao.updateProduct(p);
            if (ok) {
                loadInventoryData();
                log("[INVENTORY] Updated product: " + p.getSku() + " (" + p.getName() + ")");
                logAudit("IT-STOCK", "Product updated: " + p.getSku() + " -> Qty: " + p.getQuantity() + ", Price: $" + p.getUnitPrice() + ", Loc: " + p.getLocation());
            } else {
                showAlert("Database Error", "Failed to update product in SQLite.");
            }
        });
    }

    @FXML
    private void handleOpenStockAdjustment(ActionEvent event) {
        List<Product> products = inventoryDao.getAllProducts();
        if (products.isEmpty()) {
            showAlert("No Products", "No inventory products found to adjust.");
            return;
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Stock Adjustment & Reconciliation");
        dialog.setHeaderText("Manually adjust physical stock counts (audit reconciliation, delivery, or damage).");
        if (mainContentPane != null && mainContentPane.getScene() != null && mainContentPane.getScene().getWindow() != null) {
            dialog.initOwner(mainContentPane.getScene().getWindow());
        }

        ButtonType applyBtnType = new ButtonType("Apply Adjustment", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setStyle("-fx-padding: 20px;");

        ComboBox<Product> cmbProduct = new ComboBox<>(FXCollections.observableArrayList(products));
        cmbProduct.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getSku() + " - " + item.getName() + " (Current: " + item.getQuantity() + ")");
            }
        });
        cmbProduct.setButtonCell(cmbProduct.getCellFactory().call(null));
        cmbProduct.getSelectionModel().selectFirst();

        ComboBox<String> cmbType = new ComboBox<>(FXCollections.observableArrayList(
                "[+] Inbound Delivery / Add Units",
                "[-] Outbound Dispatch / Deduct Units",
                "[-] Damage Write-Off / Deduct Units"
        ));
        cmbType.getSelectionModel().selectFirst();

        TextField txtDelta = new TextField("10");
        TextField txtReason = new TextField("Physical Inventory Reconciliation");

        grid.add(new Label("Select Product SKU:"), 0, 0);
        grid.add(cmbProduct, 1, 0);
        grid.add(new Label("Adjustment Type:"), 0, 1);
        grid.add(cmbType, 1, 1);
        grid.add(new Label("Units to Adjust:"), 0, 2);
        grid.add(txtDelta, 1, 2);
        grid.add(new Label("Reason / Audit Note:"), 0, 3);
        grid.add(txtReason, 1, 3);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == applyBtnType) {
                Product selected = cmbProduct.getValue();
                if (selected == null) return null;
                int amount;
                try {
                    amount = Integer.parseInt(txtDelta.getText().trim());
                    if (amount <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    showAlert("Input Error", "Adjustment quantity must be a positive integer.");
                    return null;
                }

                int signedDelta = cmbType.getValue().startsWith("[+]") ? amount : -amount;
                boolean ok = inventoryDao.updateStockDelta(selected.getSku(), signedDelta);
                if (ok) {
                    loadInventoryData();
                    int newStock = inventoryDao.getStockQuantity(selected.getSku());
                    log("[INVENTORY] Adjusted stock for " + selected.getSku() + ": " + (signedDelta >= 0 ? "+" : "") + signedDelta + " units (New total: " + newStock + ")");
                    logAudit("IT-STOCK", "Stock adjustment on " + selected.getSku() + ": " + (signedDelta >= 0 ? "+" : "") + signedDelta + " (" + txtReason.getText().trim() + ", Total: " + newStock + ")");
                } else {
                    showAlert("Database Error", "Failed to update stock in SQLite.");
                }
            }
            return null;
        });

        dialog.showAndWait();
    }

    private void handleDeleteProduct(Product product) {
        if (product == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Product Confirmation");
        alert.setHeaderText("Remove product from inventory ledger?");
        alert.setContentText("Are you sure you want to permanently delete:\n\n" +
                product.getSku() + " - " + product.getName() + "\nCurrent Stock: " + product.getQuantity() + " Units\n\nThis cannot be undone.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                boolean ok = inventoryDao.deleteProduct(product.getId());
                if (ok) {
                    loadInventoryData();
                    log("[INVENTORY] Deleted product: " + product.getSku() + " - " + product.getName());
                    logAudit("IT-STOCK", "Product deleted from SQLite: " + product.getSku() + " (" + product.getName() + ")");
                } else {
                    showAlert("Delete Error", "Failed to delete product from database.");
                }
            }
        });
    }

    @FXML
    private void handleExportInventoryJson(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Warehouse Inventory to JSON");
        fileChooser.setInitialFileName("kanshi_inventory_export.json");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));

        Stage stage = (Stage) tableInventory.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            boolean ok = inventoryDao.exportInventoryToJson(file);
            if (ok) {
                showAlert("Export Successful", "Successfully exported inventory catalog to:\n" + file.getAbsolutePath());
                log("[INVENTORY] Exported " + inventoryData.size() + " inventory records to JSON: " + file.getName());
                logAudit("SYS", "Warehouse inventory catalog exported to JSON: " + file.getName());
            } else {
                showAlert("Export Failed", "Could not export inventory data to the specified file.");
            }
        }
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
                box.getStyleClass().add("pipeline-block");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);

                HBox microTopBar = new HBox(4);
                microTopBar.getStyleClass().add("card-micro-bar");
                Label icon = new Label("📥");
                icon.setStyle("-fx-font-size: 10px;");
                Label lbl = new Label("ENTRY");
                lbl.getStyleClass().add("micro-dir-arrow");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label dir = new Label(getBridgeArrowForDirection(placement.getDirection()));
                dir.getStyleClass().add("micro-dir-arrow");
                microTopBar.getChildren().addAll(icon, lbl, spacer, dir);

                Canvas canvas = new Canvas(108, 54);
                drawInfeedVisual(canvas, beltOffset, placement.getDirection());

                box.getChildren().addAll(microTopBar, canvas);
                Tooltip.install(box, new Tooltip("Infeed Chute (Entry Point)\nClick for Diagnostics"));
                box.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));
                return box;
            }
            case DEPOT -> {
                VBox box = new VBox(2);
                box.getStyleClass().add("pipeline-block");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);

                HBox microTopBar = new HBox(4);
                microTopBar.getStyleClass().add("card-micro-bar");
                Label icon = new Label("📦");
                icon.setStyle("-fx-font-size: 10px;");
                Label lbl = new Label("DEPOT");
                lbl.getStyleClass().add("micro-dir-arrow");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label dir = new Label(getBridgeArrowForDirection(placement.getDirection()));
                dir.getStyleClass().add("micro-dir-arrow");
                microTopBar.getChildren().addAll(icon, lbl, spacer, dir);

                Canvas canvas = new Canvas(108, 54);
                drawDepotVisual(canvas, beltOffset, placement.getDirection());

                box.getChildren().addAll(microTopBar, canvas);
                Tooltip.install(box, new Tooltip("Warehouse Depot (Outfeed Chute)\nClick for Diagnostics"));
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

                HBox microTopBar = new HBox(4);
                microTopBar.getStyleClass().add("card-micro-bar");
                Label arrow = new Label(getBridgeArrowForDirection(placement.getDirection()));
                arrow.getStyleClass().add("micro-dir-arrow");
                Label lbl = new Label("BRIDGE");
                lbl.getStyleClass().add("micro-dir-arrow");
                microTopBar.getChildren().addAll(arrow, lbl);

                Canvas canvas = new Canvas(108, 54);
                drawBridgeVisual(canvas, placement.getDirection());

                box.getChildren().addAll(microTopBar, canvas);
                Tooltip.install(box, new Tooltip("Conveyor Bridge (Flow Link)\nClick for Diagnostics"));
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
        Tooltip.install(box, new Tooltip("Unassigned Equipment\nClick for Diagnostics & Assignment"));
        box.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));
        return box;
    }

    private Node createOperationalConveyorBlock(ModbusTag tag, FloorCellPlacement placement) {
        boolean isCurved = (placement.getAssetType() == AssetType.CURVED_CONVEYOR) || isCurvedConveyor(tag);
        VBox block = new VBox(2);
        block.getStyleClass().add("pipeline-block");
        if (tag.isActive()) {
            block.getStyleClass().add("pipeline-block-running");
        }
        block.setMinWidth(118);
        block.setMaxWidth(122);
        block.setMinHeight(84);
        block.setMaxHeight(84);

        // Micro Top Bar (no name, no dot-run text pill)
        HBox microTopBar = new HBox(4);
        microTopBar.getStyleClass().add("card-micro-bar");

        // State LED dot: green when on, ash when off
        Circle ledDot = new Circle(4);
        ledDot.getStyleClass().add(tag.isActive() ? "state-led-dot-active" : "state-led-dot-idle");

        Label dirLbl = new Label(isCurved ? "↷" : getBridgeArrowForDirection(placement.getDirection()));
        dirLbl.getStyleClass().add("micro-dir-arrow");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Symbol-only Run/Stop button: ▶ when stopped/idle, ⏹ when running
        Button toggleBtn = new Button(tag.isActive() ? "⏹" : "▶");
        toggleBtn.getStyleClass().add(tag.isActive() ? "symbol-toggle-btn-stop" : "symbol-toggle-btn");
        toggleBtn.setTooltip(new Tooltip(tag.isActive() ? "Stop Conveyor" : "Start Conveyor"));
        toggleBtn.setOnAction(e -> {
            e.consume(); // Prevent bubbling up to open details dialog
            handleToggleActuator(tag);
        });

        microTopBar.getChildren().addAll(ledDot, dirLbl, spacer, toggleBtn);

        // High-Definition Animated Belt Canvas (108 x 54)
        Canvas beltCanvas = new Canvas(108, 54);
        if (isCurved) {
            drawCurvedConveyorBelt(beltCanvas, tag.isActive(), beltOffset, placement.getDirection());
        } else {
            drawConveyorBelt(beltCanvas, tag.isActive(), beltOffset, placement.getDirection());
        }

        block.getChildren().addAll(microTopBar, beltCanvas);

        // Rich Smart Tooltip on mouse hover
        Tooltip tooltip = new Tooltip(tag.getName() + " (Coil " + tag.getAddress() + ") • " + (tag.isActive() ? "RUNNING" : "STANDBY") + "\nClick for Diagnostics & Controls");
        Tooltip.install(block, tooltip);

        // Clicking operational card opens full diagnostics & parameters dialog
        block.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));

        ActuatorBlockRef ref = new ActuatorBlockRef();
        ref.block = block;
        ref.ledDot = ledDot;
        ref.toggleBtn = toggleBtn;
        ref.beltCanvas = beltCanvas;
        ref.direction = placement.getDirection();
        ref.tooltip = tooltip;
        actuatorRefs.put(tag.getId(), ref);

        return block;
    }

    private Node createOperationalSensorBlock(ModbusTag tag, FloorCellPlacement placement) {
        VBox block = new VBox(2);
        block.getStyleClass().add("pipeline-block");
        if (tag.isActive()) {
            block.getStyleClass().add("pipeline-block-detected");
        }
        block.setMinWidth(118);
        block.setMaxWidth(122);
        block.setMinHeight(84);
        block.setMaxHeight(84);

        // Micro Top Bar
        HBox microTopBar = new HBox(4);
        microTopBar.getStyleClass().add("card-micro-bar");

        // State LED dot: green when detected, ash when clear
        Circle led = new Circle(4);
        led.getStyleClass().add(tag.isActive() ? "state-led-dot-active" : "state-led-dot-idle");

        Label sensLbl = new Label("OPTICAL " + getBridgeArrowForDirection(placement.getDirection()));
        sensLbl.getStyleClass().add("micro-dir-arrow");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        sensorCounters.putIfAbsent(tag.getId(), 0);
        Label counterBadge = new Label("Ct: " + sensorCounters.get(tag.getId()));
        counterBadge.getStyleClass().add("card-counter-chip");

        microTopBar.getChildren().addAll(led, sensLbl, spacer, counterBadge);

        // High-Definition Sensor Canvas (108 x 54)
        Canvas sensorCanvas = new Canvas(108, 54);
        drawSensorVisual(sensorCanvas, tag.isActive(), placement.getDirection());

        block.getChildren().addAll(microTopBar, sensorCanvas);

        // Rich Smart Tooltip on mouse hover
        Tooltip tooltip = new Tooltip(tag.getName() + " (DI " + tag.getAddress() + ") • " + (tag.isActive() ? "DETECTED" : "CLEAR") + "\nClick for Diagnostics & Controls");
        Tooltip.install(block, tooltip);

        // Clicking sensor card opens full diagnostics & parameters dialog
        block.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));

        SensorBlockRef ref = new SensorBlockRef();
        ref.block = block;
        ref.led = led;
        ref.counterLabel = counterBadge;
        ref.sensorCanvas = sensorCanvas;
        ref.direction = placement.getDirection();
        ref.tooltip = tooltip;
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
            // State LED dot: green when running, ash when idle
            if (ref.ledDot != null) {
                ref.ledDot.getStyleClass().removeAll("state-led-dot-active", "state-led-dot-idle");
                ref.ledDot.getStyleClass().add(running ? "state-led-dot-active" : "state-led-dot-idle");
            }

            // Symbol-only Run/Stop button: ⏹ when running, ▶ when stopped
            if (ref.toggleBtn != null) {
                ref.toggleBtn.setText(running ? "⏹" : "▶");
                ref.toggleBtn.getStyleClass().removeAll("symbol-toggle-btn", "symbol-toggle-btn-stop", "btn-primary", "btn-estop");
                ref.toggleBtn.getStyleClass().add(running ? "symbol-toggle-btn-stop" : "symbol-toggle-btn");
                ref.toggleBtn.setTooltip(new Tooltip(running ? "Stop Conveyor" : "Start Conveyor"));
            }

            if (running) {
                if (!ref.block.getStyleClass().contains("pipeline-block-running")) {
                    ref.block.getStyleClass().add("pipeline-block-running");
                }
            } else {
                ref.block.getStyleClass().remove("pipeline-block-running");
            }

            // Update tooltip text with live telemetry
            if (ref.tooltip != null) {
                ref.tooltip.setText(tag.getName() + " (Coil " + tag.getAddress() + ") • " + (running ? "RUNNING" : "STANDBY") + "\nClick for Diagnostics & Controls");
            }

            if (isCurvedConveyor(tag)) {
                drawCurvedConveyorBelt(ref.beltCanvas, running, beltOffset, ref.direction);
            } else {
                drawConveyorBelt(ref.beltCanvas, running, beltOffset, ref.direction);
            }
        }

        long runningCount = tagManager.getActuatorTags().stream().filter(ModbusTag::isActive).count();
        int totalActuators = tagManager.getActuatorTags().size();
        if (lblKpiActiveEqCount != null) {
            lblKpiActiveEqCount.setText(runningCount + "/" + totalActuators + " Active");
        }

        boolean anyRunning = runningCount > 0;
        if (anyRunning) {
            if (lblKpiLineState != null) {
                lblKpiLineState.setText("RUNNING");
                lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #15803d;");
            }
            if (circleKpiLineDot != null) {
                circleKpiLineDot.setFill(Color.web("#22c55e"));
            }
        } else if (ioService.isConnected()) {
            if (lblKpiLineState != null) {
                lblKpiLineState.setText("IDLE");
                lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #64748b;");
            }
            if (circleKpiLineDot != null) {
                circleKpiLineDot.setFill(Color.web("#94a3b8"));
            }
        }

        updateMimicLineStatus();
    }

    private void updateSensorTileUI(ModbusTag tag, boolean active) {
        isVisionSensorActive = active;
        SensorBlockRef ref = sensorRefs.get(tag.getId());
        if (ref != null) {
            // State LED dot: green when detected, ash when clear
            if (ref.led != null) {
                ref.led.getStyleClass().removeAll("state-led-dot-active", "state-led-dot-idle", "sensor-led-on", "sensor-led-off");
                ref.led.getStyleClass().add(active ? "state-led-dot-active" : "state-led-dot-idle");
            }

            if (active) {
                if (!ref.block.getStyleClass().contains("pipeline-block-detected")) {
                    ref.block.getStyleClass().add("pipeline-block-detected");
                }
                int count = sensorCounters.compute(tag.getId(), (k, v) -> v == null ? 1 : v + 1);
                if (ref.counterLabel != null) {
                    ref.counterLabel.setText("Ct: " + count);
                }
                int total = totalDetectedPackages.incrementAndGet();
                if (lblKpiPackageCount != null) {
                    lblKpiPackageCount.setText(total + " Pcs");
                }

                // IT-OT Convergence Bridge: Optical sensor detection auto-increments SQLite inventory
                new Thread(() -> {
                    String targetSku = "BOX-SML-101";
                    boolean updated = inventoryDao.updateStockDelta(targetSku, 1);
                    int currentStock = inventoryDao.getStockQuantity(targetSku);
                    Platform.runLater(() -> {
                        logAudit("OT", "Optical sensor " + tag.getName() + " triggered: package transferred to infeed chute.");
                        if (updated) {
                            logAudit("IT", "SQLite inventory auto-incremented: SKU " + targetSku + " (+1 unit, Total: " + currentStock + ").");
                        }
                        refreshKpiMetrics();
                    });
                }).start();
            } else {
                ref.block.getStyleClass().remove("pipeline-block-detected");
            }

            // Update tooltip text with live telemetry
            if (ref.tooltip != null) {
                ref.tooltip.setText(tag.getName() + " (DI " + tag.getAddress() + ") • " + (active ? "DETECTED" : "CLEAR") + "\nClick for Diagnostics & Controls");
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

    private static class AuditRecord {
        final String timestamp;
        final String category;
        final String message;

        AuditRecord(String timestamp, String category, String message) {
            this.timestamp = timestamp;
            this.category = category;
            this.message = message;
        }

        String getFormattedLine() {
            return "[" + timestamp + "] [" + category + "] " + message + "\n";
        }
    }

    private final List<AuditRecord> auditRecords = new CopyOnWriteArrayList<>();
    private String currentAuditFilter = "ALL";

    @FXML
    private void handleClearAuditStream(ActionEvent event) {
        auditRecords.clear();
        if (txtAuditStream != null) {
            txtAuditStream.clear();
        }
    }

    @FXML
    private void handleFilterAuditAll(ActionEvent event) {
        currentAuditFilter = "ALL";
        setAuditFilterStyle(btnFilterAll);
        renderFilteredAuditLog();
    }

    @FXML
    private void handleFilterAuditOt(ActionEvent event) {
        currentAuditFilter = "OT";
        setAuditFilterStyle(btnFilterOt);
        renderFilteredAuditLog();
    }

    @FXML
    private void handleFilterAuditIt(ActionEvent event) {
        currentAuditFilter = "IT";
        setAuditFilterStyle(btnFilterIt);
        renderFilteredAuditLog();
    }

    @FXML
    private void handleFilterAuditSys(ActionEvent event) {
        currentAuditFilter = "SYS";
        setAuditFilterStyle(btnFilterSys);
        renderFilteredAuditLog();
    }

    private void setAuditFilterStyle(Button activeBtn) {
        Button[] btns = {btnFilterAll, btnFilterOt, btnFilterIt, btnFilterSys};
        for (Button b : btns) {
            if (b != null) {
                b.getStyleClass().removeAll("filter-chip-active", "filter-chip");
                b.getStyleClass().add(b == activeBtn ? "filter-chip-active" : "filter-chip");
            }
        }
    }

    private boolean shouldDisplayInFilter(String category) {
        if ("ALL".equalsIgnoreCase(currentAuditFilter)) return true;
        if ("OT".equalsIgnoreCase(currentAuditFilter) && category.toUpperCase().contains("OT")) return true;
        if ("IT".equalsIgnoreCase(currentAuditFilter) && (category.toUpperCase().contains("IT") || category.toUpperCase().contains("AUTH"))) return true;
        if ("SYS".equalsIgnoreCase(currentAuditFilter) && (category.toUpperCase().contains("SYS") || category.toUpperCase().contains("TAG"))) return true;
        return false;
    }

    private void renderFilteredAuditLog() {
        if (txtAuditStream == null) return;
        StringBuilder sb = new StringBuilder();
        for (AuditRecord r : auditRecords) {
            if (shouldDisplayInFilter(r.category)) {
                sb.append(r.getFormattedLine());
            }
        }
        txtAuditStream.setText(sb.toString());
        txtAuditStream.positionCaret(txtAuditStream.getText().length());
    }

    /**
     * Refreshes executive KPI metrics directly from the SQLite database.
     */
    public void refreshKpiMetrics() {
        int totalUnits = inventoryDao.getTotalStockCount();
        double totalValuation = inventoryDao.getTotalValuation();
        int distinctSkus = inventoryDao.getDistinctProductCount();
        int lowStockCount = inventoryDao.getLowStockCount(15);

        if (lblKpiInventory != null) {
            lblKpiInventory.setText(String.format("%,d Units", totalUnits));
        }
        if (lblKpiValuation != null) {
            lblKpiValuation.setText(String.format("$%,.2f", totalValuation));
        }
        if (lblKpiSkuCount != null) {
            lblKpiSkuCount.setText(distinctSkus + " Active SKUs");
        }
        if (lblKpiLowStockBadge != null) {
            if (lowStockCount > 0) {
                lblKpiLowStockBadge.setText("⚠️ " + lowStockCount + " Low Stock");
                lblKpiLowStockBadge.getStyleClass().removeAll("kpi-mini-badge-green", "kpi-mini-badge-red");
                lblKpiLowStockBadge.getStyleClass().add("kpi-mini-badge-red");
            } else {
                lblKpiLowStockBadge.setText("Stock Normal");
                lblKpiLowStockBadge.getStyleClass().removeAll("kpi-mini-badge-green", "kpi-mini-badge-red");
                lblKpiLowStockBadge.getStyleClass().add("kpi-mini-badge-green");
            }
        }

        long runningCount = tagManager.getActuatorTags().stream().filter(ModbusTag::isActive).count();
        int totalActuators = tagManager.getActuatorTags().size();
        if (lblKpiActiveEqCount != null) {
            lblKpiActiveEqCount.setText(runningCount + "/" + totalActuators + " Active");
        }
    }

    /**
     * Writes timestamped entries to the converged activity audit log.
     */
    public void logAudit(String category, String message) {
        String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        AuditRecord record = new AuditRecord(timestamp, category, message);
        auditRecords.add(record);

        Platform.runLater(() -> {
            if (txtAuditStream != null && shouldDisplayInFilter(category)) {
                txtAuditStream.appendText(record.getFormattedLine());
                txtAuditStream.positionCaret(txtAuditStream.getText().length());
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
