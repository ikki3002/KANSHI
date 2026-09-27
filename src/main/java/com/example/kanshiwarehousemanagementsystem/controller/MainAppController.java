package com.example.kanshiwarehousemanagementsystem.controller;

import com.example.kanshiwarehousemanagementsystem.HelloApplication;
import com.example.kanshiwarehousemanagementsystem.concurrency.ConveyorProducerService;
import com.example.kanshiwarehousemanagementsystem.concurrency.IntakeConsumerService;
import com.example.kanshiwarehousemanagementsystem.concurrency.WarehouseBuffer;
import com.example.kanshiwarehousemanagementsystem.database.InventoryDao;
import com.example.kanshiwarehousemanagementsystem.database.InvoiceDao;
import com.example.kanshiwarehousemanagementsystem.model.Invoice;
import com.example.kanshiwarehousemanagementsystem.model.InvoiceItem;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag.TagType;
import com.example.kanshiwarehousemanagementsystem.model.PackagePayload;
import com.example.kanshiwarehousemanagementsystem.model.Product;
import com.example.kanshiwarehousemanagementsystem.model.User;
import com.example.kanshiwarehousemanagementsystem.service.modbus.FactoryIOService;
import com.example.kanshiwarehousemanagementsystem.service.modbus.TagManager;
import com.example.kanshiwarehousemanagementsystem.service.scada.AsrsAutomationEngine;
import com.example.kanshiwarehousemanagementsystem.service.scada.AsrsAutomationEngine.AsrsState;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
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
import javafx.event.Event;
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
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    @FXML private Button btnNavMatrix;
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
    @FXML private VBox paneStorageMatrix;

    // Level 2E High-Bay Rack Storage Matrix FXML Controls
    @FXML private Label lblAsrsStatusBadge;
    @FXML private Label lblMatrixTotalBays;
    @FXML private Label lblMatrixOccupiedBays;
    @FXML private Label lblMatrixVacantBays;
    @FXML private Label lblMatrixUtilization;
    @FXML private Label lblMatrixValuation;
    @FXML private TextField txtMatrixSearch;
    @FXML private ComboBox<String> cmbMatrixCategoryFilter;
    @FXML private Button btnToggleAutoPutaway;
    @FXML private Button btnOpenUnloadModal;
    @FXML private Button btnScadaUnload;
    @FXML private Button btnDispatchInfeed;
    @FXML private Label lblCraneTelemetryInfo;
    @FXML private GridPane gridStorageMatrix;

    // SCADA Batch Controls
    @FXML private ComboBox<String> cmbPutawayQty;
    @FXML private Button btnStoreBatch;
    @FXML private ComboBox<String> cmbDispatchQty;
    @FXML private Button btnDispatchBatch;

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
    @FXML private Button btnStartAll;
    @FXML private Button btnDashStartAll;

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

    // Level 2C Finance & Invoicing FXML Controls
    @FXML private Label lblFinanceTotalRevenue;
    @FXML private Label lblFinancePaidCount;
    @FXML private Label lblFinancePendingCount;
    @FXML private Label lblFinanceCatalogValuation;
    @FXML private TextField txtFinanceSearch;
    @FXML private ComboBox<String> cmbFinanceStatusFilter;
    @FXML private TableView<Invoice> tableInvoices;
    @FXML private TableColumn<Invoice, String> colInvoiceNumber;
    @FXML private TableColumn<Invoice, String> colInvoiceCustomer;
    @FXML private TableColumn<Invoice, Number> colInvoiceUserId;
    @FXML private TableColumn<Invoice, String> colInvoiceDate;
    @FXML private TableColumn<Invoice, String> colInvoiceStatus;
    @FXML private TableColumn<Invoice, Number> colInvoiceItemsCount;
    @FXML private TableColumn<Invoice, Number> colInvoiceTotal;
    @FXML private TableColumn<Invoice, Void> colInvoiceActions;
    @FXML private Label lblFinanceRowCount;

    private final InvoiceDao invoiceDao = new InvoiceDao();
    private final ObservableList<Invoice> invoiceData = FXCollections.observableArrayList();
    private FilteredList<Invoice> filteredInvoiceData;

    // Producer-Consumer Concurrency Telemetry
    @FXML private Label lblBufferUsage;
    @FXML private Label lblProducerStatus;
    @FXML private Label lblConsumerStatus;
    @FXML private HBox boxBufferVisualSlots;

    private WarehouseBuffer<PackagePayload> warehouseBuffer;
    private ConveyorProducerService producerService;
    private IntakeConsumerService consumerService;

    private final ObservableList<Product> inventoryData = FXCollections.observableArrayList();
    private FilteredList<Product> filteredInventoryData;

    private User sessionUser;
    private final TagManager tagManager = new TagManager();
    private final FactoryIOService ioService = new FactoryIOService();
    private final InventoryDao inventoryDao = new InventoryDao();
    private final FloorLayoutService floorLayoutService = new FloorLayoutService();
    private final ObservableList<ModbusTag> tableData = FXCollections.observableArrayList();
    private AsrsAutomationEngine asrsEngine;

    private boolean isDesignMode = false;
    private final Map<String, ActuatorBlockRef> actuatorRefs = new ConcurrentHashMap<>();
    private final Map<String, SensorBlockRef> sensorRefs = new ConcurrentHashMap<>();
    private final Map<String, Integer> sensorCounters = new ConcurrentHashMap<>();
    private final AtomicInteger totalDetectedPackages = new AtomicInteger(0);

    private Timeline clockTimeline;

    // SCADA Status & Animation
    @FXML private Label lblMimicLineStatus;

    private boolean isSystemRunningAll = false;
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
        setupFinanceWorkspace();
        setupProducerConsumerEngine();
        setupStorageMatrix();
        initAsrsEngine();
        setupBatchControls();
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
        if (lblClock == null) return;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss  |  dd MMM yyyy");
        lblClock.setText(LocalDateTime.now().format(formatter));
        clockTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> {
            if (lblClock != null) {
                lblClock.setText(LocalDateTime.now().format(formatter));
            }
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

    private void drawStackerCraneVisual(Canvas canvas) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(Color.web("#0f172a"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#334155"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, 5, 5);

        // Yellow hazard stripe top cross-beam
        gc.setFill(Color.web("#eab308"));
        gc.fillRect(4, 4, w - 8, 4);
        gc.setStroke(Color.web("#ca8a04"));
        gc.setLineWidth(0.8);
        gc.strokeRect(4, 4, w - 8, 4);

        // Dual vertical mast columns
        gc.setFill(Color.web("#64748b"));
        gc.fillRect(8, 8, 6, h - 16);
        gc.fillRect(w - 14, 8, 6, h - 16);

        // Mast lattice truss lines
        gc.setStroke(Color.web("#475569"));
        gc.setLineWidth(0.8);
        for (double y = 10; y < h - 14; y += 8) {
            gc.strokeLine(8, y, 14, y + 6);
            gc.strokeLine(w - 14, y, w - 8, y + 6);
        }

        // Ground rail
        gc.setFill(Color.web("#334155"));
        gc.fillRect(4, h - 7, w - 8, 4);

        // Central elevator carriage & telescopic forks
        double cy = h / 2.0 - 5;
        gc.setFill(Color.web("#0284c7"));
        gc.fillRoundRect(22, cy, w - 44, 14, 3, 3);
        gc.setStroke(Color.web("#38bdf8"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(22, cy, w - 44, 14, 3, 3);

        // Telescopic forks
        gc.setFill(Color.web("#e2e8f0"));
        gc.fillRect(16, cy + 4, 10, 3);
        gc.fillRect(w - 26, cy + 4, 10, 3);

        // Center crane label
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 8));
        gc.fillText("ASRS CRANE", w / 2.0 - 24, cy + 10);
    }

    private void drawStorageRackVisual(Canvas canvas) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        gc.setFill(Color.web("#f8fafc"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#cbd5e1"));
        gc.setLineWidth(1.0);
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, 5, 5);

        // Vertical rack uprights (blue steel columns)
        gc.setFill(Color.web("#2563eb"));
        gc.fillRect(6, 4, 4, h - 8);
        gc.fillRect(w / 2.0 - 2, 4, 4, h - 8);
        gc.fillRect(w - 10, 4, 4, h - 8);

        // Horizontal shelf beams
        gc.setFill(Color.web("#f97316"));
        double shelfY1 = h / 3.0 + 2;
        double shelfY2 = 2 * h / 3.0 + 4;
        gc.fillRect(6, shelfY1, w - 12, 3);
        gc.fillRect(6, shelfY2, w - 12, 3);
        gc.fillRect(6, h - 6, w - 12, 3);

        // Crates stored on shelves
        gc.setFill(Color.web("#d97706"));
        gc.fillRoundRect(12, shelfY1 - 10, 18, 10, 2, 2);
        gc.setFill(Color.web("#059669"));
        gc.fillRoundRect(w / 2.0 + 6, shelfY1 - 10, 18, 10, 2, 2);

        gc.setFill(Color.web("#4f46e5"));
        gc.fillRoundRect(12, shelfY2 - 10, 18, 10, 2, 2);
        gc.setFill(Color.web("#d97706"));
        gc.fillRoundRect(w / 2.0 + 6, shelfY2 - 10, 18, 10, 2, 2);

        gc.setFill(Color.web("#0284c7"));
        gc.fillRoundRect(12, h - 16, 18, 10, 2, 2);
        gc.setFill(Color.web("#d97706"));
        gc.fillRoundRect(w / 2.0 + 6, h - 16, 18, 10, 2, 2);
    }

    private void drawControlPanelVisual(Canvas canvas, boolean running) {
        if (canvas == null) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // Control Console Enclosure
        gc.setFill(Color.web("#1e293b"));
        gc.fillRect(0, 0, w, h);

        gc.setStroke(Color.web("#475569"));
        gc.setLineWidth(1.2);
        gc.strokeRoundRect(1.5, 1.5, w - 3, h - 3, 5, 5);

        // Title Header
        gc.setFill(Color.web("#94a3b8"));
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 7));
        gc.fillText("CONTROL CONSOLE", 12, 10);

        // Three Indicator Pilot Lamps
        double lamp1X = 22, lampY = 22;
        gc.setFill(Color.web("#334155"));
        gc.fillOval(lamp1X - 6, lampY - 6, 12, 12);
        gc.setFill(running ? Color.web("#22c55e") : Color.web("#14532d"));
        gc.fillOval(lamp1X - 4, lampY - 4, 8, 8);
        if (running) {
            gc.setStroke(Color.web("#4ade80"));
            gc.setLineWidth(1.0);
            gc.strokeOval(lamp1X - 7, lampY - 7, 14, 14);
        }

        double lamp2X = w / 2.0;
        gc.setFill(Color.web("#334155"));
        gc.fillOval(lamp2X - 6, lampY - 6, 12, 12);
        gc.setFill(Color.web("#78350f"));
        gc.fillOval(lamp2X - 4, lampY - 4, 8, 8);

        double lamp3X = w - 22;
        gc.setFill(Color.web("#334155"));
        gc.fillOval(lamp3X - 6, lampY - 6, 12, 12);
        gc.setFill(!running ? Color.web("#ef4444") : Color.web("#7f1d1d"));
        gc.fillOval(lamp3X - 4, lampY - 4, 8, 8);
        if (!running) {
            gc.setStroke(Color.web("#f87171"));
            gc.setLineWidth(1.0);
            gc.strokeOval(lamp3X - 7, lampY - 7, 14, 14);
        }

        // Push Buttons
        double btnY = 38;
        gc.setFill(Color.web("#16a34a"));
        gc.fillRoundRect(lamp1X - 7, btnY - 5, 14, 10, 3, 3);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 6));
        gc.fillText("START", lamp1X - 7, btnY + 12);

        gc.setFill(Color.web("#ca8a04"));
        gc.fillRoundRect(lamp2X - 7, btnY - 5, 14, 10, 3, 3);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 6));
        gc.fillText("RESET", lamp2X - 7, btnY + 12);

        gc.setFill(Color.web("#eab308"));
        gc.fillOval(lamp3X - 8, btnY - 6, 16, 12);
        gc.setFill(Color.web("#dc2626"));
        gc.fillOval(lamp3X - 6, btnY - 5, 12, 10);
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 6));
        gc.fillText("STOP", lamp3X - 6, btnY + 12);
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

    private static final String NAV_ACTIVE_STYLE = "-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-background-radius: 10px; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 10 14; -fx-cursor: hand;";
    private static final String NAV_INACTIVE_STYLE = "-fx-background-color: transparent; -fx-text-fill: #4b5563; -fx-background-radius: 10px; -fx-font-weight: 500; -fx-font-size: 13px; -fx-padding: 10 14; -fx-cursor: hand;";

    @FXML
    private void handleToggleSidebar(ActionEvent event) {
        if (navRail == null) return;
        isSidebarCollapsed = !isSidebarCollapsed;

        if (isSidebarCollapsed) {
            navRail.setPrefWidth(68);
            navRail.setMinWidth(68);
            navRail.setMaxWidth(68);

            if (lblNavCatCore != null) { lblNavCatCore.setVisible(false); lblNavCatCore.setManaged(false); }
            if (lblNavCatWorkspaces != null) { lblNavCatWorkspaces.setVisible(false); lblNavCatWorkspaces.setManaged(false); }
            if (lblNavCatSystem != null) { lblNavCatSystem.setVisible(false); lblNavCatSystem.setManaged(false); }
            if (navRailFooter != null) { navRailFooter.setVisible(false); navRailFooter.setManaged(false); }

            btnNavDashboard.setText("📊");
            btnNavDashboard.setTooltip(new Tooltip("Dashboard"));
            btnNavScada.setText("🏭");
            btnNavScada.setTooltip(new Tooltip("SCADA Station"));
            btnNavMatrix.setText("🗄️");
            btnNavMatrix.setTooltip(new Tooltip("Storage Matrix"));
            btnNavInventory.setText("📦");
            btnNavInventory.setTooltip(new Tooltip("Inventory Ledger"));
            btnNavFinance.setText("💰");
            btnNavFinance.setTooltip(new Tooltip("Finance & Invoicing"));
            btnNavTags.setText("⚙️");
            btnNavTags.setTooltip(new Tooltip("Settings / Tags"));

            btnNavDashboard.setAlignment(Pos.CENTER);
            btnNavScada.setAlignment(Pos.CENTER);
            btnNavMatrix.setAlignment(Pos.CENTER);
            btnNavInventory.setAlignment(Pos.CENTER);
            btnNavFinance.setAlignment(Pos.CENTER);
            btnNavTags.setAlignment(Pos.CENTER);

            if (btnToggleSidebar != null) {
                btnToggleSidebar.setText("▶");
            }
        } else {
            navRail.setPrefWidth(240);
            navRail.setMinWidth(240);
            navRail.setMaxWidth(240);

            if (lblNavCatCore != null) { lblNavCatCore.setVisible(true); lblNavCatCore.setManaged(true); }
            if (lblNavCatWorkspaces != null) { lblNavCatWorkspaces.setVisible(true); lblNavCatWorkspaces.setManaged(true); }
            if (lblNavCatSystem != null) { lblNavCatSystem.setVisible(true); lblNavCatSystem.setManaged(true); }
            if (navRailFooter != null) { navRailFooter.setVisible(true); navRailFooter.setManaged(true); }

            btnNavDashboard.setText("📊 Dashboard");
            btnNavDashboard.setTooltip(null);
            btnNavScada.setText("🏭 SCADA Station");
            btnNavScada.setTooltip(null);
            btnNavMatrix.setText("🗄️ Storage Matrix");
            btnNavMatrix.setTooltip(null);
            btnNavInventory.setText("📦 Inventory Ledger");
            btnNavInventory.setTooltip(null);
            btnNavFinance.setText("💰 Finance & Invoicing");
            btnNavFinance.setTooltip(null);
            btnNavTags.setText("⚙️ Settings / Tags");
            btnNavTags.setTooltip(null);

            btnNavDashboard.setAlignment(Pos.CENTER_LEFT);
            btnNavScada.setAlignment(Pos.CENTER_LEFT);
            btnNavMatrix.setAlignment(Pos.CENTER_LEFT);
            btnNavInventory.setAlignment(Pos.CENTER_LEFT);
            btnNavFinance.setAlignment(Pos.CENTER_LEFT);
            btnNavTags.setAlignment(Pos.CENTER_LEFT);

            if (btnToggleSidebar != null) {
                btnToggleSidebar.setText("☰");
            }
        }
    }

    @FXML
    private void handleNavDashboard(Event event) {
        activateView(paneDashboard, btnNavDashboard);
    }

    @FXML
    private void handleNavScada(Event event) {
        activateView(paneScada, btnNavScada);
    }

    @FXML
    private void handleNavMatrix(Event event) {
        activateView(paneStorageMatrix, btnNavMatrix);
        renderStorageMatrixGrid();
    }

    @FXML
    private void handleNavInventory(Event event) {
        activateView(paneInventory, btnNavInventory);
    }

    @FXML
    private void handleNavFinance(Event event) {
        activateView(paneFinance, btnNavFinance);
        loadInvoiceData();
    }

    @FXML
    private void handleNavTags(Event event) {
        activateView(paneTags, btnNavTags);
    }

    private void activateView(Node activePane, Button activeBtn) {
        paneDashboard.setVisible(false);
        paneDashboard.setManaged(false);
        paneScada.setVisible(false);
        paneScada.setManaged(false);
        paneStorageMatrix.setVisible(false);
        paneStorageMatrix.setManaged(false);
        paneInventory.setVisible(false);
        paneInventory.setManaged(false);
        paneFinance.setVisible(false);
        paneFinance.setManaged(false);
        paneTags.setVisible(false);
        paneTags.setManaged(false);

        activePane.setVisible(true);
        activePane.setManaged(true);

        Button[] navButtons = {btnNavDashboard, btnNavScada, btnNavMatrix, btnNavInventory, btnNavFinance, btnNavTags};
        for (Button btn : navButtons) {
            if (btn != null) {
                String align = isSidebarCollapsed ? "-fx-alignment: CENTER;" : "-fx-alignment: CENTER_LEFT;";
                if (btn == activeBtn) {
                    btn.setStyle(NAV_ACTIVE_STYLE + " " + align);
                } else {
                    btn.setStyle(NAV_INACTIVE_STYLE + " " + align);
                }
            }
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
        if (consumerService != null) {
            consumerService.stop();
        }
        if (producerService != null) {
            producerService.shutdown();
        }
        if (asrsEngine != null) {
            asrsEngine.stop();
        }
    }

    /**
     * Initializes the thread-safe Producer-Consumer intake buffer and background services.
     */
    private void setupProducerConsumerEngine() {
        this.warehouseBuffer = new WarehouseBuffer<>(10);
        this.warehouseBuffer.setChangeListener((currentSize, capacity) -> {
            Platform.runLater(() -> updateBufferVisuals(currentSize, capacity));
        });

        this.producerService = new ConveyorProducerService(this.warehouseBuffer);

        this.consumerService = new IntakeConsumerService(this.warehouseBuffer, this.inventoryDao, (pkg, newStock) -> {
            Platform.runLater(() -> {
                if (lblConsumerStatus != null) {
                    lblConsumerStatus.setText("CONSUMER: INGESTED " + pkg.getTrackingId());
                    lblConsumerStatus.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-size: 10px;");
                }
                logAudit("IT", "📦 Buffer Consumer: Stored " + pkg.getTrackingId() + " (" + pkg.getSku() + ") -> SQLite Stock: " + newStock);
                refreshKpiMetrics();
                loadInventoryData();

                PauseTransition pt = new PauseTransition(Duration.millis(1200));
                pt.setOnFinished(e -> {
                    if (lblConsumerStatus != null) {
                        lblConsumerStatus.setText("CONSUMER: LISTENING");
                        lblConsumerStatus.setStyle("-fx-background-color: #dbeafe; -fx-text-fill: #1d4ed8; -fx-font-size: 10px;");
                    }
                });
                pt.play();
            });
        });

        this.consumerService.start();
        updateBufferVisuals(0, 10);
    }

    private void updateBufferVisuals(int currentSize, int capacity) {
        if (lblBufferUsage != null) {
            int percent = (int) Math.round((currentSize * 100.0) / capacity);
            lblBufferUsage.setText(String.format("Queue Capacity: %d / %d Packages (%d%% Full)", currentSize, capacity, percent));
        }
        if (boxBufferVisualSlots != null) {
            boxBufferVisualSlots.getChildren().clear();
            for (int i = 0; i < capacity; i++) {
                Region slot = new Region();
                slot.setPrefWidth(24);
                slot.setPrefHeight(14);
                if (i < currentSize) {
                    slot.setStyle("-fx-background-color: #14532d; -fx-background-radius: 4px;");
                } else {
                    slot.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 4px; -fx-border-color: #cbd5e1; -fx-border-radius: 4px;");
                }
                boxBufferVisualSlots.getChildren().add(slot);
            }
        }
    }

    @FXML
    private void handleTriggerProducerBatch(ActionEvent event) {
        if (producerService == null) return;
        if (lblProducerStatus != null) {
            lblProducerStatus.setText("PRODUCER: BATCH +5");
            lblProducerStatus.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #b91c1c; -fx-font-size: 10px;");
        }
        producerService.produceBatchAsync(5, "BOX-SML-101", "Standard Cardboard Box (Small)");
        logAudit("OT", "🏭 Conveyor Producer: Enqueued batch of 5 boxes into WarehouseBuffer (capacity 10)");

        PauseTransition pt = new PauseTransition(Duration.millis(1500));
        pt.setOnFinished(e -> {
            if (lblProducerStatus != null) {
                lblProducerStatus.setText("PRODUCER: IDLE");
                lblProducerStatus.setStyle("-fx-font-size: 10px;");
            }
        });
        pt.play();
    }

    private void setupTagTable() {
        colTagName.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getName()));
        colTagType.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getType().getDisplayName()));
        colTagAddress.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getAddress()));

        colTagAction.setCellFactory(param -> new TableCell<>() {
            private final Button btnEdit = new Button("Edit");
            private final Button btnDelete = new Button("Delete");
            private final HBox boxActions = new HBox(6, btnEdit, btnDelete);

            {
                boxActions.setAlignment(Pos.CENTER);
                btnEdit.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-border-color: #bbf7d0; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-cursor: hand;");
                btnDelete.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-border-color: #fca5a5; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-cursor: hand;");

                btnEdit.setOnAction(e -> {
                    ModbusTag tag = getTableView().getItems().get(getIndex());
                    openEditTagDialog(tag);
                });

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
                setGraphic(empty ? null : boxActions);
            }
        });

        tableTags.setRowFactory(tv -> {
            TableRow<ModbusTag> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && (!row.isEmpty())) {
                    ModbusTag rowData = row.getItem();
                    openEditTagDialog(rowData);
                }
            });
            return row;
        });

        loadTableData();
    }

    private void openEditTagDialog(ModbusTag tag) {
        if (tag == null) return;

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Hardware Tag - " + tag.getName());
        dialog.setHeaderText("Update hardware tag parameters for " + tag.getId());
        if (mainContentPane != null && mainContentPane.getScene() != null && mainContentPane.getScene().getWindow() != null) {
            dialog.initOwner(mainContentPane.getScene().getWindow());
        }

        ButtonType saveBtnType = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setStyle("-fx-padding: 20px;");

        TextField txtId = new TextField(tag.getId());
        txtId.setEditable(false);
        txtId.setStyle("-fx-background-color: #f3f4f6; -fx-font-family: 'Consolas', monospace; -fx-border-color: #e5e7eb; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 6 10;");

        TextField txtName = new TextField(tag.getName());
        txtName.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 6 10;");

        TextField txtAddress = new TextField(String.valueOf(tag.getAddress()));
        txtAddress.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-padding: 6 10;");

        ComboBox<TagType> cmbType = new ComboBox<>(FXCollections.observableArrayList(TagType.COIL, TagType.DISCRETE_INPUT, TagType.HOLDING_REGISTER));
        cmbType.setValue(tag.getType());
        cmbType.setMaxWidth(Double.MAX_VALUE);
        cmbType.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 6px; -fx-background-radius: 6px;");

        grid.add(new Label("Tag ID (Internal):"), 0, 0);
        grid.add(txtId, 1, 0);
        grid.add(new Label("Tag Name:"), 0, 1);
        grid.add(txtName, 1, 1);
        grid.add(new Label("Modbus Register Address:"), 0, 2);
        grid.add(txtAddress, 1, 2);
        grid.add(new Label("Tag Type:"), 0, 3);
        grid.add(cmbType, 1, 3);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == saveBtnType) {
            String newName = txtName.getText() == null ? "" : txtName.getText().trim();
            String addressText = txtAddress.getText() == null ? "" : txtAddress.getText().trim();
            TagType newType = cmbType.getValue();

            if (newName.isEmpty() || addressText.isEmpty() || newType == null) {
                showAlert("Validation Error", "Please fill in all tag fields.");
                return;
            }

            int newAddress;
            try {
                newAddress = Integer.parseInt(addressText);
                if (newAddress < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                showAlert("Validation Error", "Modbus Register Address must be a non-negative integer (e.g. 0, 1, 2).");
                return;
            }

            tagManager.updateTag(tag.getId(), newName, newAddress, newType);
            loadTableData();
            refreshDynamicHardwareUI();
            renderFloorGrid();

            log("[TAG MANAGER] Updated hardware tag: " + tag.getId() + " -> " + newName + " (addr=" + newAddress + ", type=" + newType + ")");
            logAudit("HARDWARE-TAGS", "Updated hardware tag: " + tag.getId() + " (" + newName + ")");
        }
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
                    pill.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 10; -fx-background-radius: 999px;");
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
                btnEdit.setStyle("-fx-background-color: #f3f4f6; -fx-border-color: #e5e7eb; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-padding: 3 8; -fx-cursor: hand;");
                btnEdit.setTooltip(new Tooltip("Edit Product Details"));
                btnEdit.setOnAction(e -> {
                    Product p = getTableView().getItems().get(getIndex());
                    openEditProductDialog(p);
                });

                btnDelete.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #fca5a5; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-text-fill: #991b1b; -fx-font-size: 11px; -fx-padding: 3 8; -fx-cursor: hand;");
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

    // =========================================================================
    // Level 2C: Financial Valuation & Commercial Invoicing (Module 6)
    // =========================================================================

    private void setupFinanceWorkspace() {
        if (tableInvoices == null) return;

        // 1. Column Value Factories
        colInvoiceNumber.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getInvoiceNumber()));
        colInvoiceCustomer.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCustomerName()));
        colInvoiceUserId.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getUserId()));
        colInvoiceDate.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCreatedAt() != null ? c.getValue().getCreatedAt() : "-"));
        colInvoiceStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        colInvoiceItemsCount.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getItems() != null ? c.getValue().getItems().size() : 0));
        colInvoiceTotal.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getTotalAmount()));

        // 2. Custom Column Cell Renderers
        colInvoiceNumber.setCellFactory(col -> new TableCell<>() {
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

        colInvoiceStatus.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    Label badge = new Label(item);
                    if ("PAID".equalsIgnoreCase(item)) {
                        badge.setStyle("-fx-font-weight: bold; -fx-text-fill: #15803d; -fx-background-color: #dcfce7; -fx-padding: 3 8; -fx-background-radius: 12px;");
                    } else if ("PENDING".equalsIgnoreCase(item)) {
                        badge.setStyle("-fx-font-weight: bold; -fx-text-fill: #b45309; -fx-background-color: #fef3c7; -fx-padding: 3 8; -fx-background-radius: 12px;");
                    } else {
                        badge.setStyle("-fx-font-weight: bold; -fx-text-fill: #991b1b; -fx-background-color: #fee2e2; -fx-padding: 3 8; -fx-background-radius: 12px;");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });

        colInvoiceUserId.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText("OP-" + item.intValue());
                    setStyle("-fx-alignment: CENTER; -fx-font-family: 'Consolas', monospace; -fx-text-fill: #64748b;");
                }
            }
        });

        colInvoiceItemsCount.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.intValue() + " items");
                    setStyle("-fx-alignment: CENTER; -fx-font-size: 11px; -fx-text-fill: #475569;");
                }
            }
        });

        colInvoiceTotal.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("$%,.2f", item.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-text-fill: #0f172a; -fx-padding: 0 10; -fx-font-size: 12px;");
            }
        });

        colInvoiceActions.setCellFactory(param -> new TableCell<>() {
            private final Button btnReceipt = new Button("🧾");
            private final Button btnStatus = new Button("🔄");
            private final Button btnDelete = new Button("🗑");
            private final HBox pane = new HBox(6, btnReceipt, btnStatus, btnDelete);

            {
                pane.setAlignment(Pos.CENTER);
                btnReceipt.setStyle("-fx-background-color: #f3f4f6; -fx-border-color: #e5e7eb; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-padding: 3 8; -fx-cursor: hand;");
                btnReceipt.setTooltip(new Tooltip("View Commercial Receipt / Order Breakdown"));
                btnReceipt.setOnAction(e -> {
                    Invoice inv = getTableView().getItems().get(getIndex());
                    openInvoiceReceiptDialog(inv);
                });

                btnStatus.setStyle("-fx-background-color: #f3f4f6; -fx-border-color: #e5e7eb; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-padding: 3 8; -fx-cursor: hand;");
                btnStatus.setTooltip(new Tooltip("Toggle Payment Status (PAID / PENDING)"));
                btnStatus.setOnAction(e -> {
                    Invoice inv = getTableView().getItems().get(getIndex());
                    handleToggleInvoiceStatus(inv);
                });

                btnDelete.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #fca5a5; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-text-fill: #991b1b; -fx-font-size: 11px; -fx-padding: 3 8; -fx-cursor: hand;");
                btnDelete.setTooltip(new Tooltip("Delete Invoice (Cascades to Line Items)"));
                btnDelete.setOnAction(e -> {
                    Invoice inv = getTableView().getItems().get(getIndex());
                    handleDeleteInvoice(inv);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });

        // 3. Status Filter Dropdown
        cmbFinanceStatusFilter.setItems(FXCollections.observableArrayList(
                "All Statuses", "PAID", "PENDING", "CANCELLED"
        ));
        cmbFinanceStatusFilter.getSelectionModel().selectFirst();

        // 4. Live Search and Filter Chain
        filteredInvoiceData = new FilteredList<>(invoiceData, inv -> true);
        txtFinanceSearch.textProperty().addListener((obs, oldVal, newVal) -> applyInvoiceFilter());
        cmbFinanceStatusFilter.valueProperty().addListener((obs, oldVal, newVal) -> applyInvoiceFilter());

        SortedList<Invoice> sortedList = new SortedList<>(filteredInvoiceData);
        sortedList.comparatorProperty().bind(tableInvoices.comparatorProperty());
        tableInvoices.setItems(sortedList);

        loadInvoiceData();
    }

    private void applyInvoiceFilter() {
        String query = txtFinanceSearch.getText() != null ? txtFinanceSearch.getText().trim().toLowerCase() : "";
        String status = cmbFinanceStatusFilter.getValue();
        boolean filterStatus = status != null && !"All Statuses".equalsIgnoreCase(status);

        filteredInvoiceData.setPredicate(inv -> {
            if (inv == null) return false;
            boolean matchesStatus = !filterStatus || (inv.getStatus() != null && inv.getStatus().equalsIgnoreCase(status));
            if (!matchesStatus) return false;

            if (query.isEmpty()) return true;
            boolean matchesNum = inv.getInvoiceNumber() != null && inv.getInvoiceNumber().toLowerCase().contains(query);
            boolean matchesCust = inv.getCustomerName() != null && inv.getCustomerName().toLowerCase().contains(query);
            return matchesNum || matchesCust;
        });

        if (lblFinanceRowCount != null) {
            lblFinanceRowCount.setText("Showing " + filteredInvoiceData.size() + " of " + invoiceData.size() + " invoices");
        }
    }

    public void loadInvoiceData() {
        List<Invoice> invoices = invoiceDao.getAll();
        for (Invoice inv : invoices) {
            inv.setItems(invoiceDao.getItemsForInvoice(inv.getId()));
        }
        invoiceData.setAll(invoices);

        double totalRevenue = 0.0;
        int paidCount = 0;
        int pendingCount = 0;

        for (Invoice inv : invoices) {
            if ("PAID".equalsIgnoreCase(inv.getStatus())) {
                totalRevenue += inv.getTotalAmount();
                paidCount++;
            } else if ("PENDING".equalsIgnoreCase(inv.getStatus())) {
                pendingCount++;
            }
        }

        double catalogValuation = inventoryDao.getTotalValuation();

        if (lblFinanceTotalRevenue != null) {
            lblFinanceTotalRevenue.setText(String.format("$%,.2f", totalRevenue));
        }
        if (lblFinancePaidCount != null) {
            lblFinancePaidCount.setText(paidCount + " Paid");
        }
        if (lblFinancePendingCount != null) {
            lblFinancePendingCount.setText(pendingCount + " Pending");
        }
        if (lblFinanceCatalogValuation != null) {
            lblFinanceCatalogValuation.setText(String.format("$%,.2f", catalogValuation));
        }
        if (lblFinanceRowCount != null) {
            lblFinanceRowCount.setText("Showing " + (filteredInvoiceData != null ? filteredInvoiceData.size() : invoices.size()) + " of " + invoices.size() + " invoices");
        }
    }

    @FXML
    private void handleRefreshFinance(ActionEvent event) {
        loadInvoiceData();
        log("[FINANCE] Refreshed invoice ledger from SQLite.");
        logAudit("FINANCE", "Financial invoices and revenue statistics reloaded from SQLite database.");
    }

    private void handleToggleInvoiceStatus(Invoice invoice) {
        if (invoice == null) return;
        String newStatus = "PAID".equalsIgnoreCase(invoice.getStatus()) ? "PENDING" : "PAID";
        invoice.setStatus(newStatus);
        boolean ok = invoiceDao.update(invoice);
        if (ok) {
            loadInvoiceData();
            log("[FINANCE] Invoice " + invoice.getInvoiceNumber() + " status updated to " + newStatus);
            logAudit("FINANCE", "Invoice " + invoice.getInvoiceNumber() + " payment status marked as " + newStatus);
        } else {
            showAlert("Update Failed", "Could not update invoice status in database.");
        }
    }

    private void handleDeleteInvoice(Invoice invoice) {
        if (invoice == null) return;
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm Invoice Deletion");
        alert.setHeaderText("Delete Invoice: " + invoice.getInvoiceNumber());
        alert.setContentText("Are you sure you want to permanently delete this commercial invoice?\n\n" +
                "Referential Integrity Notice: Associated line items in 'invoice_items' will be automatically deleted via SQLite ON DELETE CASCADE.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                boolean ok = invoiceDao.delete(invoice.getId());
                if (ok) {
                    loadInvoiceData();
                    log("[FINANCE] Deleted invoice: " + invoice.getInvoiceNumber());
                    logAudit("FINANCE", "Invoice deleted from SQLite: " + invoice.getInvoiceNumber() + " (Cascade to invoice_items)");
                } else {
                    showAlert("Delete Error", "Failed to delete invoice from database.");
                }
            }
        });
    }

    @FXML
    private void handleOpenCreateInvoice(ActionEvent event) {
        List<Product> products = inventoryDao.getAllProducts();
        if (products.isEmpty()) {
            showAlert("No Products", "No inventory products are available in SQLite to build an invoice. Please add products first.");
            return;
        }

        Dialog<Invoice> dialog = new Dialog<>();
        dialog.setTitle("Create Commercial Invoice & Order Booking");
        dialog.setHeaderText("Issue a commercial sales invoice with multi-item inventory stock deduction.");
        if (mainContentPane != null && mainContentPane.getScene() != null && mainContentPane.getScene().getWindow() != null) {
            dialog.initOwner(mainContentPane.getScene().getWindow());
        }

        ButtonType bookBtnType = new ButtonType("Book & Process Invoice", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(bookBtnType, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(650);

        VBox contentBox = new VBox(14);
        contentBox.setStyle("-fx-padding: 20px;");

        // 1. Customer & Order Header Grid
        GridPane headerGrid = new GridPane();
        headerGrid.setHgap(12);
        headerGrid.setVgap(10);

        String autoInvNumber = "INV-2026-" + String.format("%04d", (int)(Math.random() * 9000 + 1000));
        TextField txtInvNumber = new TextField(autoInvNumber);
        txtInvNumber.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold;");

        TextField txtCustomer = new TextField();
        txtCustomer.setPromptText("e.g. Apex Industrial Solutions Ltd.");

        ComboBox<String> cmbStatus = new ComboBox<>(FXCollections.observableArrayList("PAID", "PENDING"));
        cmbStatus.getSelectionModel().selectFirst();

        headerGrid.add(new Label("Invoice Number:"), 0, 0);
        headerGrid.add(txtInvNumber, 1, 0);
        headerGrid.add(new Label("Payment Status:"), 2, 0);
        headerGrid.add(cmbStatus, 3, 0);

        headerGrid.add(new Label("Customer Name:"), 0, 1);
        headerGrid.add(txtCustomer, 1, 1, 3, 1);

        // 2. Line Item Builder Strip
        VBox builderBox = new VBox(8);
        builderBox.setStyle("-fx-background-color: #f8fafc; -fx-padding: 12px; -fx-background-radius: 6px; -fx-border-color: #e2e8f0; -fx-border-radius: 6px;");

        Label lblBuilderTitle = new Label("Add Inventory Products to Invoice");
        lblBuilderTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 12px;");

        HBox itemAddBar = new HBox(10);
        itemAddBar.setAlignment(Pos.CENTER_LEFT);

        ComboBox<Product> cmbProductPicker = new ComboBox<>(FXCollections.observableArrayList(products));
        cmbProductPicker.setPrefWidth(300);
        cmbProductPicker.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Product item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getSku() + " — " + item.getName() + " ($" + String.format("%.2f", item.getUnitPrice()) + " | Stock: " + item.getQuantity() + ")");
                }
            }
        });
        cmbProductPicker.setButtonCell(cmbProductPicker.getCellFactory().call(null));
        cmbProductPicker.getSelectionModel().selectFirst();

        Spinner<Integer> spinnerQty = new Spinner<>(1, 1000, 1);
        spinnerQty.setPrefWidth(90);
        spinnerQty.setEditable(true);

        Button btnAddItem = new Button("+ Add Line Item");
        btnAddItem.setStyle("-fx-background-color: #14532d; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");

        itemAddBar.getChildren().addAll(new Label("Product:"), cmbProductPicker, new Label("Qty:"), spinnerQty, btnAddItem);
        builderBox.getChildren().addAll(lblBuilderTitle, itemAddBar);

        // 3. Draft Line Items Table
        ObservableList<InvoiceItem> draftItems = FXCollections.observableArrayList();
        TableView<InvoiceItem> tableDraftItems = new TableView<>(draftItems);
        tableDraftItems.setPrefHeight(160);
        tableDraftItems.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<InvoiceItem, String> colDraftSku = new TableColumn<>("SKU");
        colDraftSku.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductSku()));
        colDraftSku.setPrefWidth(100);

        TableColumn<InvoiceItem, String> colDraftName = new TableColumn<>("Product");
        colDraftName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getProductName()));
        colDraftName.setPrefWidth(180);

        TableColumn<InvoiceItem, Number> colDraftQty = new TableColumn<>("Qty");
        colDraftQty.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().getQuantity()));
        colDraftQty.setPrefWidth(60);

        TableColumn<InvoiceItem, Number> colDraftPrice = new TableColumn<>("Unit Price");
        colDraftPrice.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getUnitPrice()));
        colDraftPrice.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("$%.2f", item.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT;");
            }
        });
        colDraftPrice.setPrefWidth(80);

        TableColumn<InvoiceItem, Number> colDraftSubtotal = new TableColumn<>("Subtotal");
        colDraftSubtotal.setCellValueFactory(c -> new SimpleDoubleProperty(c.getValue().getSubtotal()));
        colDraftSubtotal.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : String.format("$%.2f", item.doubleValue()));
                setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold;");
            }
        });
        colDraftSubtotal.setPrefWidth(90);

        TableColumn<InvoiceItem, Void> colDraftRemove = new TableColumn<>("");
        colDraftRemove.setPrefWidth(45);
        colDraftRemove.setCellFactory(col -> new TableCell<>() {
            private final Button btnRemove = new Button("✕");
            {
                btnRemove.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-font-size: 10px; -fx-padding: 3 6; -fx-cursor: hand; -fx-background-radius: 4px;");
                btnRemove.setOnAction(e -> {
                    InvoiceItem item = getTableView().getItems().get(getIndex());
                    draftItems.remove(item);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btnRemove);
            }
        });

        tableDraftItems.getColumns().addAll(colDraftSku, colDraftName, colDraftQty, colDraftPrice, colDraftSubtotal, colDraftRemove);

        // 4. Financial Calculation Summary Bar
        HBox summaryBox = new HBox(20);
        summaryBox.setAlignment(Pos.CENTER_RIGHT);
        summaryBox.setStyle("-fx-background-color: #f1f5f9; -fx-padding: 10px 14px; -fx-background-radius: 6px;");

        Label lblSubtotalVal = new Label("$0.00");
        lblSubtotalVal.setStyle("-fx-font-weight: bold;");
        Label lblTaxVal = new Label("$0.00");
        lblTaxVal.setStyle("-fx-font-weight: bold;");
        Label lblTotalVal = new Label("$0.00");
        lblTotalVal.setStyle("-fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #7a0c1e;");

        summaryBox.getChildren().addAll(
                new Label("Items Subtotal:"), lblSubtotalVal,
                new Label("Tax (5%):"), lblTaxVal,
                new Label("Grand Total:"), lblTotalVal
        );

        Runnable updateSummary = () -> {
            double sub = draftItems.stream().mapToDouble(InvoiceItem::getSubtotal).sum();
            double tax = sub * 0.05;
            double tot = sub + tax;
            lblSubtotalVal.setText(String.format("$%.2f", sub));
            lblTaxVal.setText(String.format("$%.2f", tax));
            lblTotalVal.setText(String.format("$%.2f", tot));
        };

        draftItems.addListener((javafx.collections.ListChangeListener<InvoiceItem>) c -> updateSummary.run());

        btnAddItem.setOnAction(e -> {
            Product selected = cmbProductPicker.getValue();
            if (selected == null) return;
            int qty = spinnerQty.getValue();
            if (qty <= 0) {
                showAlert("Invalid Quantity", "Quantity must be greater than zero.");
                return;
            }

            int existingDraftQty = draftItems.stream()
                    .filter(it -> it.getProductId() == selected.getId())
                    .mapToInt(InvoiceItem::getQuantity)
                    .sum();

            if (existingDraftQty + qty > selected.getQuantity()) {
                showAlert("Insufficient Stock", "Cannot add " + qty + " units of " + selected.getSku() +
                        ". Total requested (" + (existingDraftQty + qty) + ") exceeds available warehouse stock (" + selected.getQuantity() + ").");
                return;
            }

            boolean aggregated = false;
            for (int i = 0; i < draftItems.size(); i++) {
                InvoiceItem it = draftItems.get(i);
                if (it.getProductId() == selected.getId()) {
                    it.setQuantity(it.getQuantity() + qty);
                    draftItems.set(i, it);
                    aggregated = true;
                    break;
                }
            }

            if (!aggregated) {
                InvoiceItem newItem = new InvoiceItem(selected.getId(), selected.getSku(), selected.getName(), qty, selected.getUnitPrice());
                draftItems.add(newItem);
            }
            updateSummary.run();
        });

        contentBox.getChildren().addAll(headerGrid, builderBox, new Label("Draft Invoice Line Items:"), tableDraftItems, summaryBox);
        dialog.getDialogPane().setContent(contentBox);

        dialog.setResultConverter(btn -> {
            if (btn == bookBtnType) {
                String invNum = txtInvNumber.getText().trim();
                String customer = txtCustomer.getText().trim();
                String status = cmbStatus.getValue();

                if (invNum.isEmpty() || customer.isEmpty()) {
                    showAlert("Validation Error", "Invoice Number and Customer Name are required.");
                    return null;
                }

                if (draftItems.isEmpty()) {
                    showAlert("Validation Error", "Please add at least one line item to the invoice.");
                    return null;
                }

                for (InvoiceItem item : draftItems) {
                    Product currentDbProduct = inventoryDao.getById(item.getProductId());
                    if (currentDbProduct == null || currentDbProduct.getQuantity() < item.getQuantity()) {
                        showAlert("Stock Conflict", "Insufficient stock for " + item.getProductSku() +
                                ". Available in DB: " + (currentDbProduct != null ? currentDbProduct.getQuantity() : 0));
                        return null;
                    }
                }

                double sub = draftItems.stream().mapToDouble(InvoiceItem::getSubtotal).sum();
                double grandTotal = sub * 1.05;

                int opUserId = (sessionUser != null && sessionUser.getId() > 0) ? sessionUser.getId() : 1;
                Invoice newInvoice = new Invoice(invNum, opUserId, customer, grandTotal, status);
                for (InvoiceItem it : draftItems) {
                    newInvoice.addItem(it);
                }
                return newInvoice;
            }
            return null;
        });

        dialog.showAndWait().ifPresent(inv -> {
            boolean ok = invoiceDao.add(inv);
            if (ok) {
                for (InvoiceItem it : inv.getItems()) {
                    inventoryDao.updateStockDelta(it.getProductSku(), -it.getQuantity());
                }

                loadInvoiceData();
                loadInventoryData();

                log("[FINANCE] Booked invoice " + inv.getInvoiceNumber() + " ($" + String.format("%.2f", inv.getTotalAmount()) + ") for " + inv.getCustomerName());
                logAudit("FINANCE", "Invoice booked: " + inv.getInvoiceNumber() + " | Customer: " + inv.getCustomerName() +
                        " | Total: $" + String.format("%.2f", inv.getTotalAmount()) + " | Stock deducted for " + inv.getItems().size() + " items.");

                openInvoiceReceiptDialog(inv);
            } else {
                showAlert("Transaction Failed", "Could not record invoice into SQLite database. Transaction was rolled back.");
            }
        });
    }

    private void openInvoiceReceiptDialog(Invoice invoice) {
        if (invoice == null) return;

        if (invoice.getItems() == null || invoice.getItems().isEmpty()) {
            invoice.setItems(invoiceDao.getItemsForInvoice(invoice.getId()));
        }

        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Commercial Invoice Receipt — " + invoice.getInvoiceNumber());
        if (mainContentPane != null && mainContentPane.getScene() != null && mainContentPane.getScene().getWindow() != null) {
            dialog.initOwner(mainContentPane.getScene().getWindow());
        }

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setPrefWidth(550);

        VBox receipt = new VBox(12);
        receipt.setStyle("-fx-background-color: #ffffff; -fx-padding: 24px; -fx-border-color: #e2e8f0; -fx-border-radius: 8px;");

        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        VBox brandBox = new VBox(2);
        Label lblBrand = new Label("KANSHI WMS");
        lblBrand.setStyle("-fx-font-family: 'Segoe UI', sans-serif; -fx-font-weight: 900; -fx-font-size: 18px; -fx-text-fill: #7a0c1e;");
        Label lblSubtitle = new Label("Commercial Logistics & Warehouse Distribution");
        lblSubtitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
        brandBox.getChildren().addAll(lblBrand, lblSubtitle);

        Region spacer = new Region();

        VBox invMetaBox = new VBox(2);
        invMetaBox.setAlignment(Pos.CENTER_RIGHT);
        Label lblInvNo = new Label(invoice.getInvoiceNumber());
        lblInvNo.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-weight: bold; -fx-font-size: 14px; -fx-text-fill: #1e293b;");
        Label lblDate = new Label(invoice.getCreatedAt() != null && !invoice.getCreatedAt().isEmpty() ? invoice.getCreatedAt() : LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        lblDate.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748b;");
        invMetaBox.getChildren().addAll(lblInvNo, lblDate);

        topRow.getChildren().addAll(brandBox, spacer, invMetaBox);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Separator sep1 = new Separator();

        GridPane infoGrid = new GridPane();
        infoGrid.setHgap(16);
        infoGrid.setVgap(6);

        Label lblCustTitle = new Label("Billed To:");
        lblCustTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #64748b;");
        Label lblCustName = new Label(invoice.getCustomerName());
        lblCustName.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #0f172a;");

        Label lblStatusTitle = new Label("Payment Status:");
        lblStatusTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #64748b;");
        Label lblStatusBadge = new Label(invoice.getStatus());
        if ("PAID".equalsIgnoreCase(invoice.getStatus())) {
            lblStatusBadge.setStyle("-fx-background-color: #dcfce7; -fx-text-fill: #15803d; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 10px; -fx-font-size: 11px;");
        } else {
            lblStatusBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-font-weight: bold; -fx-padding: 3 8; -fx-background-radius: 10px; -fx-font-size: 11px;");
        }

        infoGrid.add(lblCustTitle, 0, 0);
        infoGrid.add(lblCustName, 0, 1);
        infoGrid.add(lblStatusTitle, 1, 0);
        infoGrid.add(lblStatusBadge, 1, 1);

        Separator sep2 = new Separator();

        VBox itemsBox = new VBox(6);
        HBox headerRow = new HBox(8);
        headerRow.setStyle("-fx-background-color: #f8fafc; -fx-padding: 6 10; -fx-border-color: #e2e8f0; -fx-border-width: 0 0 1 0;");
        Label hSku = new Label("SKU"); hSku.setPrefWidth(100); hSku.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #64748b;");
        Label hDesc = new Label("DESCRIPTION"); hDesc.setPrefWidth(200); hDesc.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #64748b;");
        Label hQty = new Label("QTY"); hQty.setPrefWidth(50); hQty.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #64748b; -fx-alignment: CENTER-RIGHT;");
        Label hPrice = new Label("PRICE"); hPrice.setPrefWidth(70); hPrice.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #64748b; -fx-alignment: CENTER-RIGHT;");
        Label hSub = new Label("AMOUNT"); hSub.setPrefWidth(80); hSub.setStyle("-fx-font-weight: bold; -fx-font-size: 10px; -fx-text-fill: #64748b; -fx-alignment: CENTER-RIGHT;");
        headerRow.getChildren().addAll(hSku, hDesc, hQty, hPrice, hSub);
        itemsBox.getChildren().add(headerRow);

        double itemsSubtotal = 0.0;
        if (invoice.getItems() != null) {
            for (InvoiceItem item : invoice.getItems()) {
                itemsSubtotal += item.getSubtotal();
                HBox row = new HBox(8);
                row.setStyle("-fx-padding: 6 10; -fx-border-color: #f1f5f9; -fx-border-width: 0 0 1 0;");
                Label rSku = new Label(item.getProductSku()); rSku.setPrefWidth(100); rSku.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 11px;");
                Label rDesc = new Label(item.getProductName()); rDesc.setPrefWidth(200); rDesc.setStyle("-fx-font-size: 11px;");
                Label rQty = new Label(String.valueOf(item.getQuantity())); rQty.setPrefWidth(50); rQty.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-size: 11px;");
                Label rPrice = new Label(String.format("$%.2f", item.getUnitPrice())); rPrice.setPrefWidth(70); rPrice.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-size: 11px;");
                Label rSub = new Label(String.format("$%.2f", item.getSubtotal())); rSub.setPrefWidth(80); rSub.setStyle("-fx-alignment: CENTER-RIGHT; -fx-font-weight: bold; -fx-font-size: 11px;");
                row.getChildren().addAll(rSku, rDesc, rQty, rPrice, rSub);
                itemsBox.getChildren().add(row);
            }
        }

        Separator sep3 = new Separator();

        VBox totalsBox = new VBox(4);
        totalsBox.setAlignment(Pos.CENTER_RIGHT);

        double tax = itemsSubtotal * 0.05;
        double grandTotal = invoice.getTotalAmount();

        HBox rowSub = new HBox(20, new Label("Subtotal:"), new Label(String.format("$%,.2f", itemsSubtotal)));
        rowSub.setAlignment(Pos.CENTER_RIGHT);
        HBox rowTax = new HBox(20, new Label("Estimated Tax (5%):"), new Label(String.format("$%,.2f", tax)));
        rowTax.setAlignment(Pos.CENTER_RIGHT);
        HBox rowTot = new HBox(20, new Label("Grand Total:"), new Label(String.format("$%,.2f", grandTotal)));
        rowTot.setAlignment(Pos.CENTER_RIGHT);
        rowTot.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #7a0c1e;");

        totalsBox.getChildren().addAll(rowSub, rowTax, rowTot);

        receipt.getChildren().addAll(topRow, sep1, infoGrid, sep2, itemsBox, sep3, totalsBox);
        dialog.getDialogPane().setContent(receipt);
        dialog.showAndWait();
    }

    @FXML
    private void handleExportInvoicesJson(ActionEvent event) {
        if (invoiceData.isEmpty()) {
            showAlert("No Invoices", "There are no invoices to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Commercial Invoices to JSON");
        fileChooser.setInitialFileName("kanshi_invoices_export.json");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));

        Stage stage = (Stage) tableInvoices.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            for (Invoice inv : invoiceData) {
                if (inv.getItems() == null || inv.getItems().isEmpty()) {
                    inv.setItems(invoiceDao.getItemsForInvoice(inv.getId()));
                }
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(file)) {
                gson.toJson(invoiceData, writer);
                showAlert("Export Successful", "Successfully exported " + invoiceData.size() + " invoices to:\n" + file.getAbsolutePath());
                log("[FINANCE] Exported " + invoiceData.size() + " invoices to JSON: " + file.getName());
                logAudit("SYS", "Commercial invoice records exported to JSON: " + file.getName());
            } catch (IOException e) {
                showAlert("Export Failed", "Error writing to JSON file: " + e.getMessage());
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
            btnToggleDesignMode.setStyle("-fx-background-color: #14532d; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
            boxDesignModeBanner.setVisible(true);
            boxDesignModeBanner.setManaged(true);
            if (lblFloorStudioSubtitle != null) {
                lblFloorStudioSubtitle.setText("LAYOUT STUDIO ACTIVE: Click [+] on an empty cell to add equipment, [↻] to rotate flow direction, or [🗑] to remove.");
            }
            log("[SCADA STUDIO] Entered layout design mode.");
        } else {
            floorLayoutService.saveToFile();
            btnToggleDesignMode.setText("✏️ Edit Floor Layout");
            btnToggleDesignMode.setStyle("-fx-background-color: #f3f4f6; -fx-border-color: #e5e7eb; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
            boxDesignModeBanner.setVisible(false);
            boxDesignModeBanner.setManaged(false);
            if (lblFloorStudioSubtitle != null) {
                lblFloorStudioSubtitle.setText("Live operational SCADA mimic. Toggle Edit Mode to place and orient machines on the factory floor grid.");
            }
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
            addColBtn.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #bbf7d0; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-text-fill: #166534; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 8 6;");
            addColBtn.setTooltip(new Tooltip("Add Column (Expand Factory Floor)"));
            addColBtn.setOnAction(e -> handleExpandColumn());
            colControls.getChildren().add(addColBtn);

            if (floorLayoutService.isColEmpty(totalCols - 1) && totalCols > 1) {
                Button removeColBtn = new Button("➖\nC\nO\nL");
                removeColBtn.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #fca5a5; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-text-fill: #991b1b; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 8 6;");
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
            addRowBtn.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #bbf7d0; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-text-fill: #166534; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 6 12;");
            addRowBtn.setTooltip(new Tooltip("Add Row (Expand Factory Floor)"));
            addRowBtn.setOnAction(e -> handleExpandRow());
            rowControls.getChildren().add(addRowBtn);

            if (floorLayoutService.isRowEmpty(totalRows - 1) && totalRows > 1) {
                Button removeRowBtn = new Button("➖ Remove Row " + totalRows);
                removeRowBtn.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #fca5a5; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-text-fill: #991b1b; -fx-font-weight: bold; -fx-font-size: 11px; -fx-cursor: hand; -fx-padding: 6 12;");
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
        cell.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #cbd5e1; -fx-border-style: dashed; -fx-border-width: 1.5; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-alignment: CENTER; -fx-cursor: hand; -fx-min-width: 120px; -fx-min-height: 84px;");

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
        empty.setStyle("-fx-background-color: transparent; -fx-min-width: 120px; -fx-min-height: 84px;");
        return empty;
    }

    private Node createDesignCell(FloorCellPlacement placement) {
        VBox block = new VBox(3);
        block.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e5e7eb; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
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
        rotateBtn.setStyle("-fx-background-color: #f3f4f6; -fx-border-color: #e5e7eb; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 10px; -fx-padding: 2 6; -fx-cursor: hand;");
        rotateBtn.setTooltip(new Tooltip("Rotate 90° (" + placement.getDirection().getLabel() + ")"));
        rotateBtn.setOnAction(e -> {
            placement.rotate();
            floorLayoutService.saveToFile();
            renderFloorGrid();
        });

        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle("-fx-background-color: #fee2e2; -fx-border-color: #fca5a5; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-text-fill: #991b1b; -fx-font-size: 10px; -fx-padding: 2 6; -fx-cursor: hand;");
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
        dirBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-size: 8px; -fx-font-weight: bold; -fx-padding: 1 5; -fx-background-radius: 4px;");

        Region spacer2 = new Region();
        HBox.setHgrow(spacer2, Priority.ALWAYS);

        Label tagBadge = new Label(getPlacementTagBadge(placement));
        tagBadge.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-font-size: 8px; -fx-font-weight: bold; -fx-padding: 1 5; -fx-background-radius: 4px;");

        bottomRow.getChildren().addAll(dirBadge, spacer2, tagBadge);

        block.getChildren().addAll(topRow, canvas, bottomRow);
        return block;
    }

    private Node createOperationalCell(FloorCellPlacement placement) {
        switch (placement.getAssetType()) {
            case INFEED -> {
                VBox box = new VBox(2);
                box.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e5e7eb; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);

                HBox microTopBar = new HBox(4);
                microTopBar.setStyle("-fx-padding: 0 0 2 0;");
                Label icon = new Label("📥");
                icon.setStyle("-fx-font-size: 10px;");
                Label lbl = new Label("ENTRY");
                lbl.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label dir = new Label(getBridgeArrowForDirection(placement.getDirection()));
                dir.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
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
                box.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e5e7eb; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);

                HBox microTopBar = new HBox(4);
                microTopBar.setStyle("-fx-padding: 0 0 2 0;");
                Label icon = new Label("📦");
                icon.setStyle("-fx-font-size: 10px;");
                Label lbl = new Label("DEPOT");
                lbl.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label dir = new Label(getBridgeArrowForDirection(placement.getDirection()));
                dir.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
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
                box.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e5e7eb; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);

                HBox microTopBar = new HBox(4);
                microTopBar.setStyle("-fx-padding: 0 0 2 0;");
                Label arrow = new Label(getBridgeArrowForDirection(placement.getDirection()));
                arrow.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
                Label lbl = new Label("BRIDGE");
                lbl.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
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
            case STACKER_CRANE -> {
                VBox box = new VBox(2);
                box.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e5e7eb; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);

                HBox microTopBar = new HBox(4);
                microTopBar.setStyle("-fx-padding: 0 0 2 0;");
                Label icon = new Label("🏗️");
                icon.setStyle("-fx-font-size: 10px;");
                Label lbl = new Label("CRANE");
                lbl.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                int bay = (asrsEngine != null) ? asrsEngine.getActiveBay() : 0;
                Label bayBadge = new Label(bay > 0 ? "Bay " + bay : "Standby");
                bayBadge.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #15803d; -fx-background-color: #dcfce7; -fx-padding: 1 5; -fx-background-radius: 4px;");
                microTopBar.getChildren().addAll(icon, lbl, spacer, bayBadge);

                Canvas canvas = new Canvas(108, 54);
                drawStackerCraneVisual(canvas);

                box.getChildren().addAll(microTopBar, canvas);
                Tooltip.install(box, new Tooltip("ASRS 2-Axis Stacker Crane\nClick for Diagnostics & Controls"));
                box.setOnMouseClicked(e -> openEquipmentDetailsDialog(placement));
                return box;
            }
            case STORAGE_RACK -> {
                VBox box = new VBox(2);
                box.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e5e7eb; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);

                HBox microTopBar = new HBox(4);
                microTopBar.setStyle("-fx-padding: 0 0 2 0;");
                Label icon = new Label("🗄️");
                icon.setStyle("-fx-font-size: 10px;");
                Label lbl = new Label("RACK");
                lbl.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label bayBadge = new Label("54 Bays");
                bayBadge.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #1e40af; -fx-background-color: #dbeafe; -fx-padding: 1 5; -fx-background-radius: 4px;");
                microTopBar.getChildren().addAll(icon, lbl, spacer, bayBadge);

                Canvas canvas = new Canvas(108, 54);
                drawStorageRackVisual(canvas);

                box.getChildren().addAll(microTopBar, canvas);
                Tooltip.install(box, new Tooltip("High-Bay Storage Rack (54 Bays)\nClick to Open Rack Storage Matrix"));
                box.setOnMouseClicked(e -> handleNavMatrix(null));
                return box;
            }
            case CONTROL_PANEL -> {
                VBox box = new VBox(2);
                box.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e5e7eb; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
                box.setMinWidth(118);
                box.setMaxWidth(122);
                box.setMinHeight(84);
                box.setMaxHeight(84);

                HBox microTopBar = new HBox(4);
                microTopBar.setStyle("-fx-padding: 0 0 2 0;");
                Label icon = new Label("🎛️");
                icon.setStyle("-fx-font-size: 10px;");
                Label lbl = new Label("CONSOLE");
                lbl.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Label stateBadge = new Label(isSystemRunningAll ? "RUNNING" : "STOPPED");
                stateBadge.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: " + (isSystemRunningAll ? "#15803d; -fx-background-color: #dcfce7;" : "#991b1b; -fx-background-color: #fee2e2;") + " -fx-padding: 1 5; -fx-background-radius: 4px;");
                microTopBar.getChildren().addAll(icon, lbl, spacer, stateBadge);

                Canvas canvas = new Canvas(108, 54);
                drawControlPanelVisual(canvas, isSystemRunningAll);

                box.getChildren().addAll(microTopBar, canvas);
                Tooltip.install(box, new Tooltip("Automated Warehouse Control Console\nClick to Toggle Master Run All"));
                box.setOnMouseClicked(e -> handleStartAllSystem(null));
                return box;
            }
        }
        return createEmptyOperationalCell();
    }

    private Node createMissingTagCell(FloorCellPlacement placement) {
        VBox box = new VBox(2);
        box.setStyle("-fx-background-color: #fff1f2; -fx-border-color: #fecdd3; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand;");
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
        block.setStyle("-fx-background-color: #ffffff; -fx-border-color: " + (tag.isActive() ? "#22c55e" : "#e5e7eb") + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
        block.setMinWidth(118);
        block.setMaxWidth(122);
        block.setMinHeight(84);
        block.setMaxHeight(84);

        // Micro Top Bar (no name, no dot-run text pill)
        HBox microTopBar = new HBox(4);
        microTopBar.setStyle("-fx-padding: 0 0 2 0;");

        // State LED dot: green when on, ash when off
        Circle ledDot = new Circle(4);
        ledDot.setFill(Color.web(tag.isActive() ? "#22c55e" : "#94a3b8"));

        Label dirLbl = new Label(isCurved ? "↷" : getBridgeArrowForDirection(placement.getDirection()));
        dirLbl.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Symbol-only Run/Stop button: ▶ when stopped/idle, ⏹ when running
        Button toggleBtn = new Button(tag.isActive() ? "⏹" : "▶");
        toggleBtn.setStyle(tag.isActive() ? "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 999px; -fx-font-size: 9px; -fx-padding: 2 6; -fx-cursor: hand;" : "-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-background-radius: 999px; -fx-font-size: 9px; -fx-padding: 2 6; -fx-cursor: hand;");
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
        block.setStyle("-fx-background-color: #ffffff; -fx-border-color: " + (tag.isActive() ? "#3b82f6" : "#e5e7eb") + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
        block.setMinWidth(118);
        block.setMaxWidth(122);
        block.setMinHeight(84);
        block.setMaxHeight(84);

        // Micro Top Bar
        HBox microTopBar = new HBox(4);
        microTopBar.setStyle("-fx-padding: 0 0 2 0;");

        // State LED dot: green when detected, ash when clear
        Circle led = new Circle(4);
        led.setFill(Color.web(tag.isActive() ? "#3b82f6" : "#94a3b8"));

        Label sensLbl = new Label("OPTICAL " + getBridgeArrowForDirection(placement.getDirection()));
        sensLbl.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #64748b;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        sensorCounters.putIfAbsent(tag.getId(), 0);
        Label counterBadge = new Label("Ct: " + sensorCounters.get(tag.getId()));
        counterBadge.setStyle("-fx-background-color: #f1f5f9; -fx-text-fill: #475569; -fx-font-size: 8px; -fx-font-weight: bold; -fx-padding: 1 5; -fx-background-radius: 4px;");

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
        dialogPane.setStyle("-fx-background-color: #ffffff; -fx-padding: 16px; -fx-font-family: 'Segoe UI', -apple-system, sans-serif;");

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
        propBox.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 8px; -fx-background-radius: 8px; -fx-padding: 12px;");

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
            diagToggleBtn.setStyle(tag.isActive() ? "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 999px; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 6 14; -fx-cursor: hand;" : "-fx-background-color: #14532d; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-weight: bold; -fx-font-size: 11px; -fx-padding: 6 14; -fx-cursor: hand;");
            diagToggleBtn.setOnAction(e -> {
                handleToggleActuator(tag);
                dialog.close();
            });

            Button pulseBtn = new Button("⚡ 2s Pulse Test");
            pulseBtn.setStyle("-fx-background-color: #f3f4f6; -fx-border-color: #e5e7eb; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 6 14; -fx-cursor: hand;");
            pulseBtn.setOnAction(e -> {
                handleQuickTestActuator(tag);
                dialog.close();
            });

            actionBtns.getChildren().addAll(diagToggleBtn, pulseBtn);
        } else if (tag != null && placement.getAssetType() == AssetType.SENSOR) {
            Button resetCtBtn = new Button("↺ Reset Counter");
            resetCtBtn.setStyle("-fx-background-color: #f3f4f6; -fx-border-color: #e5e7eb; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: 600; -fx-padding: 6 14; -fx-cursor: hand;");
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
        } else if (placement.getAssetType() == AssetType.STACKER_CRANE) {
            Button btnMatrix = new Button("🗄️ Open Storage Matrix");
            btnMatrix.setStyle("-fx-background-color: #1e40af; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
            btnMatrix.setOnAction(e -> {
                dialog.close();
                handleNavMatrix(null);
            });
            actionBtns.getChildren().add(btnMatrix);
        } else if (placement.getAssetType() == AssetType.STORAGE_RACK) {
            Button btnMatrix = new Button("🗄️ View 54-Bay Matrix Twin");
            btnMatrix.setStyle("-fx-background-color: #1e40af; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
            btnMatrix.setOnAction(e -> {
                dialog.close();
                handleNavMatrix(null);
            });
            actionBtns.getChildren().add(btnMatrix);
        } else if (placement.getAssetType() == AssetType.CONTROL_PANEL) {
            Button btnStartToggle = new Button(isSystemRunningAll ? "⏹ Stop All Lines" : "▶ Start All Lines");
            btnStartToggle.setStyle(isSystemRunningAll ? "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;" : "-fx-background-color: #14532d; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 6 14; -fx-cursor: hand;");
            btnStartToggle.setOnAction(e -> {
                dialog.close();
                handleStartAllSystem(null);
            });
            actionBtns.getChildren().add(btnStartToggle);
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
            case STACKER_CRANE -> drawStackerCraneVisual(canvas);
            case STORAGE_RACK -> drawStorageRackVisual(canvas);
            case CONTROL_PANEL -> drawControlPanelVisual(canvas, false);
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
        options.add(new PlacementOption(AssetType.STACKER_CRANE, null, "🏗️ Stacker Crane (ASRS 2-Axis)", false));
        options.add(new PlacementOption(AssetType.STORAGE_RACK, null, "🗄️ Storage Rack (High-Bay)", false));
        options.add(new PlacementOption(AssetType.CONTROL_PANEL, null, "🎛️ Control Console (Auto/Manual)", false));

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
                ref.ledDot.setFill(Color.web(running ? "#22c55e" : "#94a3b8"));
            }

            // Symbol-only Run/Stop button: ⏹ when running, ▶ when stopped
            if (ref.toggleBtn != null) {
                ref.toggleBtn.setText(running ? "⏹" : "▶");
                ref.toggleBtn.setStyle(running ? "-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 999px; -fx-font-size: 9px; -fx-padding: 2 6; -fx-cursor: hand;" : "-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-background-radius: 999px; -fx-font-size: 9px; -fx-padding: 2 6; -fx-cursor: hand;");
                ref.toggleBtn.setTooltip(new Tooltip(running ? "Stop Conveyor" : "Start Conveyor"));
            }

            if (ref.block != null) {
                ref.block.setStyle("-fx-background-color: #ffffff; -fx-border-color: " + (running ? "#22c55e" : "#e5e7eb") + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
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
                ref.led.setFill(Color.web(active ? "#3b82f6" : "#94a3b8"));
            }

            if (ref.block != null) {
                ref.block.setStyle("-fx-background-color: #ffffff; -fx-border-color: " + (active ? "#3b82f6" : "#e5e7eb") + "; -fx-border-radius: 12px; -fx-background-radius: 12px; -fx-padding: 6px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.04), 6, 0, 0, 2);");
            }

            if (active) {
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

            isSystemRunningAll = false;
            updateMasterStartButtonState(false);

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
    private void handleStartAllSystem(ActionEvent event) {
        if (!ioService.isConnected()) {
            showAlert("Not Connected", "Please connect to Factory I/O Modbus TCP server first.");
            return;
        }
        if (asrsEngine == null) {
            showAlert("Engine Error", "ASRS Automation Engine is not initialized.");
            return;
        }

        if (isSystemRunningAll || asrsEngine.isAutoMode() || asrsEngine.isBatchPutawayActive()) {
            stopFullSystem();
        } else {
            startPutawayFromSelection();
        }
    }

    @FXML
    private void handleOpenUnloadDialog(ActionEvent event) {
        if (!ioService.isConnected()) {
            showAlert("Not Connected", "Please connect to Factory I/O Modbus TCP server first.");
            return;
        }

        List<String> storedProducts = inventoryDao.getDistinctStoredProductTypes();
        if (storedProducts.isEmpty()) {
            showAlert("No Products Stored", "The warehouse currently has no stored products in rack bays to unload.");
            return;
        }

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Unload / Dispatch Stored Products");
        dialog.setHeaderText("Request automated retrieval of stored warehouse inventory.");
        if (mainContentPane != null && mainContentPane.getScene() != null && mainContentPane.getScene().getWindow() != null) {
            dialog.initOwner(mainContentPane.getScene().getWindow());
        }

        ButtonType unloadBtnType = new ButtonType("Start Unload Sequence", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(unloadBtnType, ButtonType.CANCEL);

        VBox content = new VBox(14);
        content.setStyle("-fx-padding: 20px; -fx-background-color: #ffffff; -fx-min-width: 440px;");

        // Product selection
        Label lblProduct = new Label("Product Type to Unload:");
        lblProduct.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");
        ComboBox<String> cmbProduct = new ComboBox<>(FXCollections.observableArrayList(storedProducts));
        cmbProduct.setMaxWidth(Double.MAX_VALUE);
        cmbProduct.setStyle("-fx-font-size: 13px; -fx-background-radius: 6px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 4 8;");
        cmbProduct.getSelectionModel().selectFirst();

        // Live availability badge
        HBox boxAvail = new HBox(8);
        boxAvail.setAlignment(Pos.CENTER_LEFT);
        boxAvail.setStyle("-fx-background-color: #f0fdf4; -fx-padding: 10 14; -fx-background-radius: 8px; -fx-border-color: #bbf7d0; -fx-border-radius: 8px;");
        Label lblAvailIcon = new Label("📊");
        Label lblAvailText = new Label();
        lblAvailText.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #15803d;");
        boxAvail.getChildren().addAll(lblAvailIcon, lblAvailText);

        // Quantity input
        Label lblQty = new Label("Requested Unload Quantity:");
        lblQty.setStyle("-fx-font-weight: bold; -fx-text-fill: #1e293b; -fx-font-size: 13px;");
        TextField txtQuantity = new TextField("1");
        txtQuantity.setStyle("-fx-font-size: 13px; -fx-background-radius: 6px; -fx-border-color: #cbd5e1; -fx-border-radius: 6px; -fx-padding: 6 10;");

        // Validation banner
        Label lblValidation = new Label();
        lblValidation.setStyle("-fx-font-size: 12px; -fx-font-weight: 600;");
        lblValidation.setWrapText(true);

        Runnable updateAvailability = () -> {
            String selected = cmbProduct.getValue();
            if (selected != null) {
                int count = inventoryDao.getAvailableCountByProductType(selected);
                List<Integer> bays = inventoryDao.getBaysForProductType(selected);
                lblAvailText.setText(String.format("Available in Warehouse: %d unit(s)  [Bays: %s]",
                        count, bays.toString()));

                try {
                    int requested = Integer.parseInt(txtQuantity.getText().trim());
                    if (requested <= 0) {
                        lblValidation.setText("⚠️ Quantity must be greater than zero.");
                        lblValidation.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #b91c1c;");
                    } else if (requested > count) {
                        lblValidation.setText(String.format("❌ Requested (%d) exceeds available stock (%d). Unload will be rejected.", requested, count));
                        lblValidation.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #b91c1c;");
                    } else {
                        lblValidation.setText(String.format("✓ Valid request: Crane will retrieve %d pallet(s) sequentially to dispatch.", requested));
                        lblValidation.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #15803d;");
                    }
                } catch (NumberFormatException e) {
                    lblValidation.setText("⚠️ Please enter a valid integer quantity.");
                    lblValidation.setStyle("-fx-font-size: 12px; -fx-font-weight: 600; -fx-text-fill: #b91c1c;");
                }
            }
        };

        cmbProduct.valueProperty().addListener((obs, oldV, newV) -> updateAvailability.run());
        txtQuantity.textProperty().addListener((obs, oldV, newV) -> updateAvailability.run());
        updateAvailability.run();

        content.getChildren().addAll(lblProduct, cmbProduct, boxAvail, lblQty, txtQuantity, lblValidation);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(btn -> {
            if (btn == unloadBtnType) {
                String selectedProduct = cmbProduct.getValue();
                int requestedQty;
                try {
                    requestedQty = Integer.parseInt(txtQuantity.getText().trim());
                } catch (NumberFormatException e) {
                    showAlert("Invalid Input", "Please enter a valid integer number for quantity.");
                    return;
                }

                if (requestedQty <= 0) {
                    showAlert("Invalid Quantity", "Unload quantity must be at least 1 unit.");
                    return;
                }

                int available = inventoryDao.getAvailableCountByProductType(selectedProduct);
                if (requestedQty > available) {
                    showAlert("Unload Rejected", String.format("Requested quantity (%d) exceeds available stock (%d) for '%s'. Operation aborted.",
                            requestedQty, available, selectedProduct));
                    return;
                }

                if (asrsEngine == null) {
                    showAlert("Engine Error", "ASRS Automation Engine is not initialized.");
                    return;
                }

                boolean started = asrsEngine.requestBulkUnload(selectedProduct, requestedQty, (ok, msg) -> {
                    Platform.runLater(() -> {
                        if (!ok) {
                            showAlert("Unload Error", msg);
                        } else {
                            log(">> [BULK UNLOAD] " + msg);
                        }
                    });
                });

                if (started) {
                    logAudit("OT", String.format("Operator initiated automated unload of %d unit(s) of '%s'.", requestedQty, selectedProduct));
                    log(String.format(">> [DISPATCH] Started retrieval of %d unit(s) of '%s' to outfeed transfer.", requestedQty, selectedProduct));
                }
            }
        });
    }

    private void updateMasterStartButtonState(boolean running) {
        if (running) {
            String selected = cmbPutawayQty != null ? cmbPutawayQty.getValue() : "3 Pallets";
            int qty = parseQuantityString(selected, 0);
            if (qty > 0) {
                int done = asrsEngine != null ? asrsEngine.getCompletedPutawayCount() : 0;
                updatePutawayRunningUI(done, qty);
            } else {
                updateContinuousRunningUI();
            }
        } else {
            resetPutawayUI();
        }
    }

    @FXML
    private void handleEmergencyStop(ActionEvent event) {
        if (asrsEngine != null) {
            asrsEngine.emergencyStop();
        }
        if (btnStoreBatch != null) {
            btnStoreBatch.setText("Store");
            btnStoreBatch.setStyle("-fx-background-color: #15803d; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 12; -fx-cursor: hand;");
        }
        if (btnDispatchBatch != null) {
            btnDispatchBatch.setText("Dispatch");
            btnDispatchBatch.setStyle("-fx-background-color: #0284c7; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 12; -fx-cursor: hand;");
        }
        isSystemRunningAll = false;
        updateMasterStartButtonState(false);
        new Thread(() -> {
            try {
                ioService.writeCoil(AsrsAutomationEngine.COIL_LIGHT_START, false);
                ioService.writeCoil(AsrsAutomationEngine.COIL_LIGHT_STOP, true);
            } catch (Exception ignored) {}
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

    // =========================================================================
    // ASRS BATCH OPERATIONS (Putaway Batch & Bunch Dispatch)
    // =========================================================================

    private void setupBatchControls() {
        if (cmbPutawayQty != null) {
            cmbPutawayQty.setItems(FXCollections.observableArrayList(
                    "1 Pallet", "2 Pallets", "3 Pallets", "4 Pallets", "5 Pallets", "6 Pallets", "10 Pallets", "15 Pallets", "Continuous (No Limit)"
            ));
            cmbPutawayQty.setValue("3 Pallets");
            cmbPutawayQty.valueProperty().addListener((obs, oldVal, newVal) -> updatePutawayButtonLabels());
            updatePutawayButtonLabels();
        }
        if (cmbDispatchQty != null) {
            cmbDispatchQty.valueProperty().addListener((obs, oldVal, newVal) -> updateDispatchButtonLabel());
        }
        refreshDispatchBatchOptions();
    }

    public void refreshDispatchBatchOptions() {
        if (cmbDispatchQty != null) {
            int totalStored = inventoryDao != null ? inventoryDao.getTotalStoredCount() : 0;
            List<String> options = new ArrayList<>(List.of(
                    "1 Pallet", "2 Pallets", "3 Pallets", "4 Pallets", "5 Pallets", "6 Pallets", "10 Pallets", "15 Pallets"
            ));
            if (totalStored > 0) {
                options.add("All Stored (" + totalStored + ")");
            } else {
                options.add("All Stored");
            }
            String prev = cmbDispatchQty.getValue();
            cmbDispatchQty.setItems(FXCollections.observableArrayList(options));
            if (prev != null && options.contains(prev)) {
                cmbDispatchQty.setValue(prev);
            } else if (prev != null && prev.startsWith("All Stored")) {
                cmbDispatchQty.setValue(totalStored > 0 ? "All Stored (" + totalStored + ")" : "All Stored");
            } else if (options.contains("1 Pallet")) {
                cmbDispatchQty.setValue("1 Pallet");
            } else if (!options.isEmpty()) {
                cmbDispatchQty.getSelectionModel().selectFirst();
            }
            updateDispatchButtonLabel();
        }
    }

    private void updateDispatchButtonLabel() {
        if (btnDispatchBatch == null) return;
        String curText = btnDispatchBatch.getText();
        if (curText != null && curText.startsWith("Dispatching")) {
            return;
        }
        String selected = cmbDispatchQty != null ? cmbDispatchQty.getValue() : "1 Pallet";
        int qty = parseQuantityString(selected, 1);
        if (selected != null && selected.startsWith("All")) {
            int totalStored = inventoryDao != null ? inventoryDao.getTotalStoredCount() : 0;
            btnDispatchBatch.setText(totalStored > 0 ? "Dispatch (" + totalStored + ")" : "Dispatch All");
        } else if (qty > 0) {
            btnDispatchBatch.setText("Dispatch (" + qty + ")");
        } else {
            btnDispatchBatch.setText("Dispatch");
        }
    }

    private void updatePutawayButtonLabels() {
        if (isSystemRunningAll || (asrsEngine != null && (asrsEngine.isAutoMode() || asrsEngine.isBatchPutawayActive()))) {
            return;
        }
        String selected = cmbPutawayQty != null ? cmbPutawayQty.getValue() : "3 Pallets";
        int qty = parseQuantityString(selected, 0);

        String greenStyle = "-fx-background-color: #15803d; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 12; -fx-cursor: hand;";
        String masterGreenStyle = "-fx-background-color: #14532d; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7 18; -fx-cursor: hand;";
        String dashGreenStyle = "-fx-background-color: #14532d; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 9 16; -fx-cursor: hand;";

        if (qty > 0) {
            if (btnStoreBatch != null) {
                btnStoreBatch.setText("Store (" + qty + ")");
                btnStoreBatch.setStyle(greenStyle);
            }
            if (btnStartAll != null) {
                btnStartAll.setText("▶ Start (" + qty + ")");
                btnStartAll.setStyle(masterGreenStyle);
            }
            if (btnDashStartAll != null) {
                btnDashStartAll.setText("▶ Start (" + qty + ")");
                btnDashStartAll.setStyle(dashGreenStyle);
            }
            if (btnToggleAutoPutaway != null) {
                btnToggleAutoPutaway.setText("⚡ Putaway (" + qty + ")");
                btnToggleAutoPutaway.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-border-color: #bbf7d0; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7 16; -fx-cursor: hand;");
            }
        } else {
            if (btnStoreBatch != null) {
                btnStoreBatch.setText("Store (All)");
                btnStoreBatch.setStyle(greenStyle);
            }
            if (btnStartAll != null) {
                btnStartAll.setText("▶ Continuous");
                btnStartAll.setStyle(masterGreenStyle);
            }
            if (btnDashStartAll != null) {
                btnDashStartAll.setText("▶ Start All");
                btnDashStartAll.setStyle(dashGreenStyle);
            }
            if (btnToggleAutoPutaway != null) {
                btnToggleAutoPutaway.setText("⚡ Enable Auto-Putaway");
                btnToggleAutoPutaway.setStyle("-fx-background-color: #f0fdf4; -fx-text-fill: #166534; -fx-border-color: #bbf7d0; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7 16; -fx-cursor: hand;");
            }
        }
    }

    private void updatePutawayRunningUI(int done, int total) {
        String stopStyle = "-fx-background-color: #991b1b; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7 18; -fx-cursor: hand;";
        String dashStopStyle = "-fx-background-color: #991b1b; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 9 16; -fx-cursor: hand;";

        if (btnStoreBatch != null) {
            btnStoreBatch.setText(String.format("Storing %d/%d", done, total));
            btnStoreBatch.setStyle("-fx-background-color: #b45309; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 12; -fx-cursor: hand;");
        }
        if (btnStartAll != null) {
            btnStartAll.setText(String.format("⏹ Stop (%d/%d)", done, total));
            btnStartAll.setStyle(stopStyle);
        }
        if (btnDashStartAll != null) {
            btnDashStartAll.setText(String.format("⏹ Stop (%d/%d)", done, total));
            btnDashStartAll.setStyle(dashStopStyle);
        }
        if (btnToggleAutoPutaway != null) {
            btnToggleAutoPutaway.setText(String.format("⏹ Stop (%d/%d)", done, total));
            btnToggleAutoPutaway.setStyle("-fx-background-color: #991b1b; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7 16; -fx-cursor: hand;");
        }
        if (lblKpiLineState != null) {
            lblKpiLineState.setText(String.format("STORING %d/%d", done, total));
            lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #15803d;");
        }
    }

    private void updateContinuousRunningUI() {
        String stopStyle = "-fx-background-color: #991b1b; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7 18; -fx-cursor: hand;";
        String dashStopStyle = "-fx-background-color: #991b1b; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 9 16; -fx-cursor: hand;";

        if (btnStoreBatch != null) {
            btnStoreBatch.setText("⏹ Stop Store");
            btnStoreBatch.setStyle("-fx-background-color: #b45309; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 12; -fx-cursor: hand;");
        }
        if (btnStartAll != null) {
            btnStartAll.setText("⏹ Stop Auto");
            btnStartAll.setStyle(stopStyle);
        }
        if (btnDashStartAll != null) {
            btnDashStartAll.setText("⏹ Stop All");
            btnDashStartAll.setStyle(dashStopStyle);
        }
        if (btnToggleAutoPutaway != null) {
            btnToggleAutoPutaway.setText("⏹ Stop Putaway");
            btnToggleAutoPutaway.setStyle("-fx-background-color: #991b1b; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 12px; -fx-font-weight: bold; -fx-padding: 7 16; -fx-cursor: hand;");
        }
        if (lblKpiLineState != null) {
            lblKpiLineState.setText("SYSTEM RUNNING");
            lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #15803d;");
        }
    }

    private void resetPutawayUI() {
        updatePutawayButtonLabels();
        if (lblKpiLineState != null) {
            lblKpiLineState.setText("STANDBY");
            lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #1e293b;");
        }
    }

    private void startPutawayFromSelection() {
        String selected = cmbPutawayQty != null ? cmbPutawayQty.getValue() : "3 Pallets";
        int qty = parseQuantityString(selected, 0);

        int vacant = inventoryDao.getVacantCount(54);
        if (vacant <= 0) {
            showAlert("Warehouse Full", "All 54 rack bays are occupied. Cannot store new pallets.");
            return;
        }
        if (qty > 0 && qty > vacant) {
            showAlert("Insufficient Bays", String.format("Requested batch of %d exceeds available vacant bays (%d).", qty, vacant));
            return;
        }

        isSystemRunningAll = true;

        new Thread(() -> {
            try {
                // Panel indicators: START light (7), STOP light (9)
                ioService.writeCoil(AsrsAutomationEngine.COIL_LIGHT_START, true);
                ioService.writeCoil(AsrsAutomationEngine.COIL_LIGHT_STOP, false);
            } catch (Exception ignored) {}
        }).start();

        if (qty > 0) {
            // Deterministic Batch Putaway of exactly N units
            updatePutawayRunningUI(0, qty);

            boolean started = asrsEngine.startBatchPutaway(qty, (done, total) -> {
                Platform.runLater(() -> {
                    updatePutawayRunningUI(done, total);
                    log(String.format(">> [BATCH PUTAWAY] Progress: %d of %d pallets stored into rack.", done, total));
                });
            }, () -> {
                Platform.runLater(() -> {
                    isSystemRunningAll = false;
                    resetPutawayUI();
                    new Thread(() -> {
                        try {
                            ioService.writeCoil(AsrsAutomationEngine.COIL_LIGHT_START, false);
                            ioService.writeCoil(AsrsAutomationEngine.COIL_LIGHT_STOP, true);
                        } catch (Exception ignored) {}
                    }).start();

                    for (ModbusTag tag : tagManager.getActuatorTags()) {
                        int addr = tag.getAddress();
                        if (addr == AsrsAutomationEngine.COIL_LIGHT_START ||
                            addr == AsrsAutomationEngine.COIL_ENTRY_CONVEYOR ||
                            addr == AsrsAutomationEngine.COIL_LOAD_CONVEYOR) {
                            tag.setActive(false);
                            updateActuatorTileUI(tag, false);
                        } else if (addr == AsrsAutomationEngine.COIL_LIGHT_STOP) {
                            tag.setActive(true);
                            updateActuatorTileUI(tag, true);
                        }
                    }

                    logAudit("OT", String.format("Batch putaway of %d pallet(s) completed successfully. System returned to rest at Station 55.", qty));
                    log(String.format(">> [BATCH PUTAWAY] Finished: Exactly %d pallet(s) stored into rack. System safely at rest at Station 55.", qty));
                    refreshKpiMetrics();
                    refreshDispatchBatchOptions();
                    renderStorageMatrixGrid();
                    redrawMimic();
                });
            });

            if (started) {
                logAudit("OT", String.format("Operator started batch putaway of %d pallet(s). Soft-PLC active.", qty));
                log(String.format(">> [BATCH PUTAWAY] Started inbound batch of %d units. Crane & rollers coordinated.", qty));
            } else {
                isSystemRunningAll = false;
                resetPutawayUI();
            }
        } else {
            // Continuous Putaway (No limit)
            asrsEngine.setAutoMode(true);
            updateContinuousRunningUI();
            logAudit("OT", "Operator engaged continuous auto-putaway line mode.");
            log(">> [CONTINUOUS RUN] Continuous putaway engaged. System will store pallets until manually paused.");
        }
    }

    private void stopFullSystem() {
        isSystemRunningAll = false;
        if (asrsEngine != null) {
            asrsEngine.cancelBatchPutaway();
            asrsEngine.setAutoMode(false);
        }
        new Thread(() -> {
            try {
                ioService.writeCoil(AsrsAutomationEngine.COIL_LIGHT_START, false);
                ioService.writeCoil(AsrsAutomationEngine.COIL_LIGHT_STOP, true);
                ioService.writeCoil(AsrsAutomationEngine.COIL_ENTRY_CONVEYOR, false);
                ioService.writeCoil(AsrsAutomationEngine.COIL_LOAD_CONVEYOR, false);
                ioService.writeCoil(AsrsAutomationEngine.COIL_UNLOAD_CONVEYOR, false);
                ioService.writeCoil(AsrsAutomationEngine.COIL_EXIT_CONVEYOR, false);
            } catch (Exception ignored) {}
        }).start();

        Platform.runLater(() -> {
            resetPutawayUI();
            for (ModbusTag tag : tagManager.getActuatorTags()) {
                int addr = tag.getAddress();
                if (addr == AsrsAutomationEngine.COIL_LIGHT_START ||
                    addr == AsrsAutomationEngine.COIL_ENTRY_CONVEYOR ||
                    addr == AsrsAutomationEngine.COIL_LOAD_CONVEYOR ||
                    addr == AsrsAutomationEngine.COIL_UNLOAD_CONVEYOR ||
                    addr == AsrsAutomationEngine.COIL_EXIT_CONVEYOR) {
                    tag.setActive(false);
                    updateActuatorTileUI(tag, false);
                } else if (addr == AsrsAutomationEngine.COIL_LIGHT_STOP) {
                    tag.setActive(true);
                    updateActuatorTileUI(tag, true);
                }
            }
            lblKpiLineState.setText("STANDBY");
            lblKpiLineState.setStyle("-fx-font-size: 24px; -fx-text-fill: #1e293b;");
            log(">> [MASTER STOP] Stopped all line conveyors. ASRS entered Standby mode.");
            logAudit("OT", "Operator stopped putaway cycle. System entered Standby.");
            redrawMimic();
        });
    }

    @FXML
    private void handleStartBatchPutaway(ActionEvent event) {
        if (!ioService.isConnected()) {
            showAlert("Not Connected", "Please connect to Factory I/O Modbus TCP server first.");
            return;
        }
        if (asrsEngine == null) {
            showAlert("Engine Error", "ASRS Automation Engine is not initialized.");
            return;
        }

        if (isSystemRunningAll || asrsEngine.isAutoMode() || asrsEngine.isBatchPutawayActive()) {
            stopFullSystem();
        } else {
            startPutawayFromSelection();
        }
    }

    @FXML
    private void handleStartBunchDispatch(ActionEvent event) {
        if (!ioService.isConnected()) {
            showAlert("Not Connected", "Please connect to Factory I/O Modbus TCP server first.");
            return;
        }
        if (asrsEngine == null) {
            showAlert("Engine Error", "ASRS Automation Engine is not initialized.");
            return;
        }

        int totalStored = inventoryDao.getTotalStoredCount();
        if (totalStored <= 0) {
            showAlert("Warehouse Empty", "There are no stored pallets in the rack bays to dispatch.");
            return;
        }

        String selected = cmbDispatchQty != null ? cmbDispatchQty.getValue() : "1";
        int qty = parseQuantityString(selected, 1);
        if (selected != null && selected.startsWith("All")) {
            qty = totalStored;
        }

        if (qty > totalStored) {
            showAlert("Insufficient Stock", String.format("Requested dispatch of %d exceeds total stored inventory (%d).", qty, totalStored));
            return;
        }

        final int targetQty = qty;
        if (btnDispatchBatch != null) {
            btnDispatchBatch.setDisable(true);
            btnDispatchBatch.setText("Dispatching 0/" + targetQty);
            btnDispatchBatch.setStyle("-fx-background-color: #b45309; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 12; -fx-cursor: wait;");
        }

        boolean started = asrsEngine.requestBunchDispatch(targetQty, (done, total) -> {
            Platform.runLater(() -> {
                if (btnDispatchBatch != null) {
                    btnDispatchBatch.setText("Dispatching " + done + "/" + total);
                }
                log(String.format(">> [BUNCH DISPATCH] Progress: %d of %d pallets discharged via outfeed.", done, total));
                refreshKpiMetrics();
                refreshDispatchBatchOptions();
                renderStorageMatrixGrid();
                redrawMimic();
            });
        }, (ok, msg) -> {
            Platform.runLater(() -> {
                if (btnDispatchBatch != null) {
                    btnDispatchBatch.setDisable(false);
                    updateDispatchButtonLabel();
                    btnDispatchBatch.setStyle("-fx-background-color: #0284c7; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 12; -fx-cursor: hand;");
                }
                if (!ok) {
                    showAlert("Dispatch Warning", msg);
                } else {
                    log(">> [BUNCH DISPATCH] " + msg);
                    logAudit("OT", String.format("Bunch dispatch of %d pallet(s) completed. Crane parked at Station 55 at rest.", targetQty));
                }
                refreshKpiMetrics();
                refreshDispatchBatchOptions();
                renderStorageMatrixGrid();
                redrawMimic();
            });
        });

        if (started) {
            logAudit("OT", String.format("Operator started bunch dispatch of %d pallet(s) in FIFO order.", targetQty));
            log(String.format(">> [BUNCH DISPATCH] Retrieving and discharging %d pallets. Crane & outfeed active.", targetQty));
        } else {
            if (btnDispatchBatch != null) {
                btnDispatchBatch.setDisable(false);
                updateDispatchButtonLabel();
                btnDispatchBatch.setStyle("-fx-background-color: #0284c7; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 5 12; -fx-cursor: hand;");
            }
        }
    }

    private int parseQuantityString(String str, int defaultVal) {
        if (str == null || str.trim().isEmpty()) return defaultVal;
        try {
            String digits = str.replaceAll("[^0-9]", "");
            if (!digits.isEmpty()) {
                return Integer.parseInt(digits);
            }
        } catch (NumberFormatException ignored) {}
        return defaultVal;
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
    private void handleEditSelectedTag(ActionEvent event) {
        ModbusTag selected = tableTags.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select a hardware tag from the table to edit.");
            return;
        }
        openEditTagDialog(selected);
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
                if (b == activeBtn) {
                    b.setStyle("-fx-background-color: #14532d; -fx-text-fill: #ffffff; -fx-background-radius: 999px; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 4 10; -fx-cursor: hand;");
                } else {
                    b.setStyle("-fx-background-color: #ffffff; -fx-border-color: #e5e7eb; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-text-fill: #6b7280; -fx-font-size: 10px; -fx-padding: 4 10; -fx-cursor: hand;");
                }
            }
        }
    }

    private boolean shouldDisplayInFilter(String category) {
        if ("ALL".equalsIgnoreCase(currentAuditFilter)) return true;
        if ("OT".equalsIgnoreCase(currentAuditFilter) && category.toUpperCase().contains("OT")) return true;
        if ("IT".equalsIgnoreCase(currentAuditFilter) && (category.toUpperCase().contains("IT") || category.toUpperCase().contains("AUTH") || category.toUpperCase().contains("FINANCE"))) return true;
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
                lblKpiLowStockBadge.setStyle("-fx-background-color: #fee2e2; -fx-background-radius: 6px; -fx-text-fill: #991b1b; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8;");
            } else {
                lblKpiLowStockBadge.setText("Stock Normal");
                lblKpiLowStockBadge.setStyle("-fx-background-color: #f0fdf4; -fx-background-radius: 6px; -fx-text-fill: #166534; -fx-font-size: 11px; -fx-font-weight: bold; -fx-padding: 3 8;");
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

    // =========================================================================
    // Level 2E: High-Bay Rack N x M Storage Matrix & Digital Twin
    // =========================================================================

    private void initAsrsEngine() {
        this.asrsEngine = new AsrsAutomationEngine(this.ioService, this.inventoryDao);
        this.asrsEngine.setStateListener((state, msg) -> {
            Platform.runLater(() -> {
                if (lblAsrsStatusBadge != null) {
                    lblAsrsStatusBadge.setText("SOFT-PLC: " + state.name());
                    if (state == AsrsState.FAULT) {
                        lblAsrsStatusBadge.setStyle("-fx-background-color: #fee2e2; -fx-text-fill: #991b1b; -fx-background-radius: 999px; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8;");
                    } else if (state == AsrsState.IDLE) {
                        lblAsrsStatusBadge.setStyle("-fx-background-color: #f3f4f6; -fx-text-fill: #4b5563; -fx-background-radius: 999px; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8;");
                    } else {
                        lblAsrsStatusBadge.setStyle("-fx-background-color: #fef3c7; -fx-text-fill: #b45309; -fx-background-radius: 999px; -fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 3 8;");
                    }
                }
                if (lblCraneTelemetryInfo != null) {
                    lblCraneTelemetryInfo.setText("Crane Target Position: " + asrsEngine.getCurrentTargetPosition() + " (Bay " + asrsEngine.getActiveBay() + ") | Soft-PLC: " + state.name());
                }
                logAudit("OT", "🤖 ASRS Stacker Crane: " + msg);
                renderStorageMatrixGrid();
            });
        });

        this.asrsEngine.setInventoryRefreshCallback(() -> {
            Platform.runLater(() -> {
                refreshKpiMetrics();
                loadInventoryData();
                renderStorageMatrixGrid();
                refreshDispatchBatchOptions();
            });
        });

        this.asrsEngine.start();
    }

    private void setupStorageMatrix() {
        if (cmbMatrixCategoryFilter != null) {
            cmbMatrixCategoryFilter.setItems(FXCollections.observableArrayList(
                    "All Categories", "Packaging", "Sensors", "Electronics", "Logistics", "Raw Materials"
            ));
            cmbMatrixCategoryFilter.getSelectionModel().selectFirst();
            cmbMatrixCategoryFilter.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> renderStorageMatrixGrid());
        }

        if (txtMatrixSearch != null) {
            txtMatrixSearch.textProperty().addListener((obs, oldVal, newVal) -> renderStorageMatrixGrid());
        }

        renderStorageMatrixGrid();
    }

    @FXML
    private void handleToggleAutoPutaway(ActionEvent event) {
        if (!ioService.isConnected()) {
            showAlert("Not Connected", "Please connect to Factory I/O Modbus TCP server first.");
            return;
        }
        if (asrsEngine == null) {
            showAlert("Engine Error", "ASRS Automation Engine is not initialized.");
            return;
        }

        if (isSystemRunningAll || asrsEngine.isAutoMode() || asrsEngine.isBatchPutawayActive()) {
            stopFullSystem();
        } else {
            startPutawayFromSelection();
        }
    }

    @FXML
    private void handleTriggerInfeedStore(ActionEvent event) {
        int nextBay = inventoryDao.findNextAvailableBay(54);
        if (nextBay <= 0) {
            showAlert("Storage Rack Full", "The 54-bay storage matrix is 100% full. Cannot store new pallet.");
            return;
        }

        String assignedSku = "BOX-" + (100 + nextBay);
        inventoryDao.storeProductInBay(assignedSku, "Inbound Pallet #" + nextBay, "Packaging", 1, 14.50, nextBay);
        logAudit("OT", "📦 Pallet Infeed: Dispatched pallet to Bay " + nextBay + " (SKU: " + assignedSku + ")");
        refreshKpiMetrics();
        loadInventoryData();
        renderStorageMatrixGrid();
    }

    @FXML
    private void handleRefreshMatrix(ActionEvent event) {
        renderStorageMatrixGrid();
    }

    /**
     * Renders the 9-column x 6-level high-bay storage rack matrix.
     * Total 54 cells. Level 6 is top row, Level 1 is bottom row.
     */
    public synchronized void renderStorageMatrixGrid() {
        if (gridStorageMatrix == null) return;

        Map<Integer, Product> occupancy = inventoryDao.getBayOccupancyMap(54);
        int totalBays = 54;
        int occupiedCount = occupancy.size();
        int vacantCount = totalBays - occupiedCount;
        double utilization = (occupiedCount * 100.0) / totalBays;
        double totalValuation = occupancy.values().stream().mapToDouble(Product::getTotalValue).sum();

        if (lblMatrixTotalBays != null) lblMatrixTotalBays.setText(totalBays + " Bays");
        if (lblMatrixOccupiedBays != null) lblMatrixOccupiedBays.setText(occupiedCount + " Bays");
        if (lblMatrixVacantBays != null) lblMatrixVacantBays.setText(vacantCount + " Bays");
        if (lblMatrixUtilization != null) lblMatrixUtilization.setText(String.format("%.1f%%", utilization));
        if (lblMatrixValuation != null) lblMatrixValuation.setText(String.format("$%,.2f", totalValuation));

        String query = (txtMatrixSearch != null && txtMatrixSearch.getText() != null)
                ? txtMatrixSearch.getText().trim().toLowerCase() : "";
        String catFilter = (cmbMatrixCategoryFilter != null && cmbMatrixCategoryFilter.getValue() != null)
                ? cmbMatrixCategoryFilter.getValue() : "All Categories";

        gridStorageMatrix.getChildren().clear();

        int activeBayNum = (asrsEngine != null) ? asrsEngine.getActiveBay() : 0;

        // 6 vertical levels (row 0 = Level 6 ... row 5 = Level 1)
        for (int r = 0; r < 6; r++) {
            int level = 6 - r;
            // 9 horizontal columns (col 0 = Col 1 ... col 8 = Col 9)
            for (int c = 0; c < 9; c++) {
                int col = c + 1;
                int bayNumber = (level - 1) * 9 + col;
                Product product = occupancy.get(bayNumber);

                Node cellNode = createBayCell(bayNumber, level, col, product, activeBayNum, query, catFilter);
                gridStorageMatrix.add(cellNode, c, r);
            }
        }
    }

    private Node createBayCell(int bayNumber, int level, int col, Product product, int activeBayNum, String query, String catFilter) {
        boolean isOccupied = (product != null);
        boolean isActiveTarget = (bayNumber == activeBayNum);

        boolean matchesFilter = true;
        if (!query.isEmpty()) {
            boolean matchSku = isOccupied && product.getSku().toLowerCase().contains(query);
            boolean matchName = isOccupied && product.getName().toLowerCase().contains(query);
            boolean matchBay = String.valueOf(bayNumber).equals(query) || ("bay-" + bayNumber).contains(query) || ("bay " + bayNumber).contains(query);
            matchesFilter = matchSku || matchName || matchBay;
        }
        if (matchesFilter && !catFilter.equalsIgnoreCase("All Categories")) {
            matchesFilter = isOccupied && catFilter.equalsIgnoreCase(product.getCategory());
        }

        VBox cell = new VBox(3);
        cell.setPrefWidth(116);
        cell.setMinWidth(116);
        cell.setMaxWidth(116);
        cell.setPrefHeight(76);
        cell.setMinHeight(76);
        cell.setMaxHeight(76);

        if (!matchesFilter) {
            cell.setOpacity(0.28);
        } else {
            cell.setOpacity(1.0);
        }

        // Header Row: Bay Number & Level-Col coordinates
        HBox header = new HBox(4);
        header.setAlignment(Pos.CENTER_LEFT);

        Label lblBay = new Label(String.format("Bay %02d", bayNumber));
        lblBay.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label lblCoord = new Label("L" + level + "-C" + col);
        lblCoord.setStyle("-fx-font-size: 8px; -fx-font-weight: bold; -fx-text-fill: #9ca3af;");

        header.getChildren().addAll(lblBay, spacer, lblCoord);

        if (isOccupied) {
            // Occupied Cell
            String borderStyle = isActiveTarget
                    ? "-fx-border-color: #f59e0b; -fx-border-width: 2.5px; -fx-background-color: #fffbeb;"
                    : "-fx-border-color: #14532d; -fx-border-width: 1.5px; -fx-background-color: #ffffff;";

            cell.setStyle(borderStyle + " -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-padding: 6px 8px; -fx-cursor: hand; -fx-effect: dropshadow(three-pass-box, rgba(20,83,45,0.08), 6, 0, 0, 2);");

            Label lblSku = new Label(product.getSku());
            lblSku.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #166534; -fx-background-color: #dcfce7; -fx-padding: 1 5; -fx-background-radius: 4px;");

            HBox bottomRow = new HBox(4);
            bottomRow.setAlignment(Pos.CENTER_LEFT);

            Label lblValue = new Label(String.format("$%,.0f", product.getTotalValue()));
            lblValue.setStyle("-fx-font-size: 9px; -fx-font-weight: 800; -fx-text-fill: #111827;");

            Region spacer2 = new Region();
            HBox.setHgrow(spacer2, Priority.ALWAYS);

            Button btnRetrieve = new Button("🚀");
            btnRetrieve.setStyle("-fx-background-color: #f0fdf4; -fx-border-color: #bbf7d0; -fx-border-radius: 999px; -fx-background-radius: 999px; -fx-font-size: 8px; -fx-padding: 1 5; -fx-cursor: hand;");
            btnRetrieve.setTooltip(new Tooltip("Retrieve Pallet via Stacker Crane"));
            btnRetrieve.setOnAction(e -> {
                e.consume();
                triggerPalletRetrieval(bayNumber, product);
            });

            bottomRow.getChildren().addAll(lblValue, spacer2, btnRetrieve);
            cell.getChildren().addAll(header, lblSku, bottomRow);

            cell.setOnMouseClicked(e -> openOccupiedBayDialog(bayNumber, product));

        } else {
            // Vacant Cell
            String borderStyle = isActiveTarget
                    ? "-fx-border-color: #f59e0b; -fx-border-width: 2.5px; -fx-background-color: #fffbeb;"
                    : "-fx-border-color: #bbf7d0; -fx-border-width: 1px; -fx-background-color: #f0fdf4;";

            cell.setStyle(borderStyle + " -fx-border-radius: 10px; -fx-background-radius: 10px; -fx-padding: 6px 8px; -fx-cursor: hand;");

            Label lblStatus = new Label("VACANT");
            lblStatus.setStyle("-fx-font-size: 9px; -fx-font-weight: bold; -fx-text-fill: #15803d;");

            Label lblSub = new Label("+ Available");
            lblSub.setStyle("-fx-font-size: 8px; -fx-text-fill: #86efac;");

            cell.getChildren().addAll(header, lblStatus, lblSub);
            cell.setOnMouseClicked(e -> openVacantBayDialog(bayNumber));
        }

        return cell;
    }

    private void triggerPalletRetrieval(int bayNumber, Product product) {
        if (asrsEngine == null) return;
        asrsEngine.requestRetrieval(bayNumber);
        logAudit("OT", "🚀 Stacker Crane dispatched to RETRIEVE pallet from Bay " + bayNumber + " (" + product.getSku() + ")");
    }

    private void openOccupiedBayDialog(int bayNumber, Product product) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Bay " + String.format("%02d", bayNumber) + " - Occupied Storage Cell");
        dialog.setHeaderText("Pallet Inventory & Crane Retrieval Dispatch");

        ButtonType retrieveBtnType = new ButtonType("🚀 Retrieve Pallet (Dispatch Crane)", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeBtnType = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(retrieveBtnType, closeBtnType);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 14px; -fx-min-width: 360px;");

        Label skuLbl = new Label("SKU Code: " + product.getSku());
        skuLbl.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #111827;");

        Label nameLbl = new Label("Product: " + product.getName());
        nameLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");

        Label catLbl = new Label("Category: " + product.getCategory());
        catLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        Label qtyLbl = new Label("Stored Quantity: " + product.getQuantity() + " Pallet Unit");
        qtyLbl.setStyle("-fx-font-size: 12px; -fx-text-fill: #374151;");

        Label priceLbl = new Label(String.format("Unit Price: $%,.2f  |  Total Asset Value: $%,.2f", product.getUnitPrice(), product.getTotalValue()));
        priceLbl.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #14532d;");

        Label bayLocLbl = new Label("Physical Location: Bay " + String.format("%02d", bayNumber) + " (Factory I/O Target Position: " + bayNumber + ")");
        bayLocLbl.setStyle("-fx-font-size: 11px; -fx-font-family: 'Consolas', monospace; -fx-text-fill: #64748b;");

        content.getChildren().addAll(skuLbl, nameLbl, catLbl, qtyLbl, priceLbl, bayLocLbl);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(response -> {
            if (response == retrieveBtnType) {
                triggerPalletRetrieval(bayNumber, product);
            }
        });
    }

    private void openVacantBayDialog(int bayNumber) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Bay " + String.format("%02d", bayNumber) + " - Vacant Slot");
        dialog.setHeaderText("Allocate Pallet to High-Bay Storage Cell");

        ButtonType allocateBtnType = new ButtonType("📦 Allocate Pallet", ButtonBar.ButtonData.OK_DONE);
        ButtonType closeBtnType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(allocateBtnType, closeBtnType);

        VBox content = new VBox(10);
        content.setStyle("-fx-padding: 14px; -fx-min-width: 320px;");

        Label info = new Label("This storage slot is currently empty and available for automated putaway.");
        info.setStyle("-fx-font-size: 12px; -fx-text-fill: #6b7280;");

        TextField txtSku = new TextField("BOX-" + (100 + bayNumber));
        txtSku.setPromptText("Enter SKU...");
        txtSku.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 8px; -fx-padding: 6 10;");

        TextField txtName = new TextField("Industrial Pallet Unit #" + bayNumber);
        txtName.setStyle("-fx-background-color: #f9fafb; -fx-border-color: #e5e7eb; -fx-border-radius: 8px; -fx-padding: 6 10;");

        content.getChildren().addAll(info, new Label("SKU:"), txtSku, new Label("Description:"), txtName);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait().ifPresent(response -> {
            if (response == allocateBtnType) {
                String sku = txtSku.getText().trim();
                String name = txtName.getText().trim();
                if (!sku.isEmpty()) {
                    inventoryDao.storeProductInBay(sku, name, "Packaging", 1, 15.00, bayNumber);
                    logAudit("OT", "📦 Manual allocation: Stored " + sku + " in Bay " + bayNumber);
                    refreshKpiMetrics();
                    loadInventoryData();
                    renderStorageMatrixGrid();
                }
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
