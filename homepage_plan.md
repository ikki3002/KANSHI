# Kanshi WMS & SCADA - Multi-Level Homepage Architecture & Plan

This document outlines the professional **3-Level Hierarchical Architecture** for the Kanshi Warehouse Management & SCADA System homepage and navigation framework.

---

## 🏛️ 3-Level System Architecture

Rather than flattening all functionality onto a single screen, the system uses an enterprise-grade multi-level hierarchy:

```
                       LEVEL 1: EXECUTIVE COMMAND DASHBOARD
                     (The Homepage - High-level KPIs & Module Hub)
                                        │
           ┌────────────────────────────┼────────────────────────────┐
           ▼                            ▼                            ▼
  LEVEL 2: MODULE 1            LEVEL 2: MODULE 2            LEVEL 2: MODULE 3
  OT SCADA Station             IT Inventory Ledger          Finance & Calculations
  • Live Conveyor Mimic        • Full Stock Table           • Stock Valuation
  • Actuator Controls          • SKU Search & Filters       • Billing / Invoices
  • Sensor Monitor & E-Stop    • Bin / Bay Locations        • Order Dispatch Ledger
           │                            │                            │
           ▼                            ▼                            ▼
  LEVEL 3: DETAILS             LEVEL 3: DETAILS             LEVEL 3: DETAILS
  • Hardware Tag Config        • Add / Edit Item Modal      • Invoice Print Preview
  • Modbus Packet Inspector    • Stock Adjustment Modal     • Financial Export (JSON/CSV)
  • Alarm History Detail       • Barcode / Batch View
```

---

## 🖥️ Level 1: The Executive Command Homepage

The Homepage serves as the high-level bird's-eye view for warehouse managers and operators.

### 1. Header Bar
- **Brand Title**: Clean, authoritative `KANSHI WMS` in Dark Cherry Red (`#7A0C1E`).
- **Live PLC Heartbeat**: Connection status badge (`● ONLINE: 502` / `● OFFLINE`).
- **Real-Time Clock**: Live updating date and time ticker.
- **Operator Profile**: Logged-in user email (`admin@gmail.com`) with a `[Logout]` button returning to the sign-in portal.

### 2. Executive KPI Cards (Top Row)
Four high-contrast white cards with dark red cherry accents:
1. **Total Warehouse Valuation**: Live calculated asset value (`Σ (Quantity × Unit Price)`).
2. **Total Inventory Count**: Total warehouse units stored in SQLite.
3. **Conveyor Line State**: Live PLC actuator telemetry (`RUNNING` / `IDLE` / `E-STOP`).
4. **Packages Processed Today**: Live counter incremented whenever `Vision Sensor 0` detects a package in Factory I/O.

### 3. Module Gateway Cards (Navigation Hub)
Large, clickable cards directing the user into Level 2 workspaces:
- **`[ 🏭 OT SCADA & Line Control ]`**: Enters live conveyor controls, 2D visual schematic, and emergency stop.
- **`[ 📦 Inventory & Stock Ledger ]`**: Enters the complete warehouse stock table with search and filtering.
- **`[ 💰 Finance & Invoicing ]`**: Enters billing, order bookings, and financial exports.
- **`[ ⚙️ Hardware Tag Settings ]`**: Enters the dynamic Modbus tag manager and JSON profiler.

### 4. Recent Activity & Audit Ticker (Bottom Stream)
Real-time converged audit log displaying incoming IT and OT events together:
- `[11:35:10] OT: Belt Conveyor 0 STARTED by operator admin`
- `[11:35:42] OT: Vision Sensor 0 triggered — Package detected`
- `[11:35:43] IT: SQLite updated — Stock SKU BOX-101 (+1 unit, Total: 143)`
- `[11:36:00] IT: Operator login recorded`

---

## 🏭 Level 2: Dedicated Workspaces

Operators can drill down into full-window workspaces via a **Collapsible Sidebar / Navigation Rail**:

### Level 2A: OT SCADA Control Station
- Full-width conveyor mimic (JavaFX Canvas / Shapes).
- Individual Start/Stop toggles for `Belt Conveyor 0`, `Belt Conveyor 1`, and `Curved Conveyor`.
- Real-time `Vision Sensor 0` indicator with package detection animation.
- Quick actions: `⚡ Auto Production Sequence` and `🛑 EMERGENCY STOP`.

### Level 2B: IT Warehouse Inventory Ledger
- Full-screen JavaFX `TableView` with real-time SQLite data binding (`ObservableList`).
- Columns: SKU, Item Name, Category, Quantity in Stock, Location (Aisle/Bay), Unit Price, Total Value.
- Search filter by SKU or name, plus category dropdown filtering.

### Level 2C: Financial & Order Processing
- Order booking ledger: Inbound receipts vs Outbound dispatches.
- Automated financial calculations (Subtotals, Tax, Freight charges).
- Real-time currency conversion rates (integrating external REST API for Week 7).

### Level 2D: Hardware Tag Configuration
- Interactive visual Tag Settings editor (connected to `warehouse_tags.json`).
- Manage PLC addresses, add new conveyor lines or sensors without rewriting code.

---

## 🔍 Level 3: Modal Action & Inspection Dialogs

Triggered on-demand from Level 2 workspaces:
- **Inventory Modals**: `Add Product`, `Edit Product`, `Stock Adjustment`, `Bin Transfer`.
- **SCADA Modals**: `Modbus Packet Inspector`, `Alarm Event Details`, `Emergency Stop Log`.
- **Finance Modals**: `Invoice Receipt Preview`, `Export Report (JSON / CSV)`.

---

## 🎨 Design System & Technical Constraints

- **Color Scheme**: Strict **White + Dark Red Cherry** combination:
  - Base Background: Clean, crisp canvas (`#FFFFFF` to `#EDF0F5` gradient).
  - Cards & Panels: Pure White (`#FFFFFF`) with subtle shadow and Dark Cherry Red accents (`#7A0C1E`).
  - Active Controls & Highlights: Dark Cherry Red (`#7A0C1E`, hover `#96152B`).
  - Inputs & Borders: Light Gray (`#D1D5DB`) with Dark Cherry Red focus ring.
- **Typography**: Industrially standard **Segoe UI** (`"Segoe UI", "Inter", sans-serif`).
- **Window Sizing**: Launches in full-screen window state (`stage.setMaximized(true)`).
- **Architecture**: Fat-free, non-modular JavaFX launched cleanly via `Launcher.java`.

---

## 📋 Course Syllabus Integration Matrix

| Level | Component | Syllabus Topic |
| :--- | :--- | :--- |
| **Level 1** | Top KPIs & Navigation Rail | **Week 3**: JavaFX Layouts (`BorderPane`, `VBox`, `HBox`), CSS Styling |
| **Level 2A** | OT SCADA & Sensor Polling | **Week 4**: Concurrency, `ScheduledExecutorService`, `Platform.runLater()` |
| **Level 2B** | Inventory Ledger Table | **Week 6**: Relational Database, SQLite JDBC, `PreparedStatement`, CRUD |
| **Level 2C** | Billing & Valuation Models | **Week 1**: Core OOP Syntax, Encapsulated Models (`Product`, `Invoice`) |
| **Level 2D** | Tag Settings & Config | **Week 7**: JSON Parsing (`warehouse_tags.json`, Gson) |

---

## 📌 Implementation Roadmap (When Ready to Code)

1. **Step 1**: Create `home-view.fxml` with the Left Navigation Rail and Level 1 Command Dashboard.
2. **Step 2**: Create `HomeController.java` handling user session, ticking clock, and module view switching.
3. **Step 3**: Integrate SQLite Inventory DAO with live KPI metric cards.
4. **Step 4**: Connect the OT Modbus background poller so Vision Sensor triggers update both the UI and SQLite stock automatically.
5. **Step 5**: Build Level 2 sub-views (Inventory Table, SCADA Console, Financial Ledger).
