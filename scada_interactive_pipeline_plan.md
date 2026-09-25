# Implementation Plan: Interactive SCADA Pipeline with Direct In-Diagram Controls & Modular Drag-and-Drop (Option B)

Transform the SCADA Control Station from separate drawings and bulky lower cards into a **unified, interactive physical machine pipeline** where controls, animated rollers, status LEDs, and detection counters are embedded **directly into each machine block**, with support for **drag-and-drop reordering**.

---

## 🏛️ Architecture & UI Wireframe

### Before vs. After Concept

```
[ BEFORE: Disjointed Layout ]
┌────────────────────────────────────────────────────────┐
│ 2D Canvas (Passive Drawing Only - No Clickable Buttons)│
└────────────────────────────────────────────────────────┘
┌───────────────────────┐ ┌──────────────────────────────┐
│ [ Large Card Coil 0 ] │ │ [ Large Card Sensor 0 ]      │
│ [ Large Start/Stop ]  │ │ [ LED & Detection Count ]    │
└───────────────────────┘ └──────────────────────────────┘

[ AFTER: Unified Interactive Industrial SCADA Pipeline ]
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│  LIVE INTERACTIVE LINE SCHEMATIC (Drag to Reorder Stations)                                  │
│                                                                                              │
│  ┌────────┐   ┌───────────────────────────┐   ┌─────────────────┐   ┌───────────────────────┐│
│  │ INFEED │──►│ CONVEYOR 0       [COIL 0] │──►│ VISION SENSOR 0 │──►│ CONVEYOR 1   [COIL 1] ││
│  │ Feeder │   │ [▶ START / ⏹ STOP] Toggle │   │ ◉ DETECTED (14) │   │ [▶ START / ⏹ STOP]    ││
│  │ (Drag) │   │ [⚡ 2s Test]   ● RUNNING  │   │ Laser & Package │   │ ● IDLE                ││
│  └────────┘   └───────────────────────────┘   └─────────────────┘   └───────────────────────┘│
│                                                                                              │
│  [🛑 EMERGENCY STOP]  [⚡ RUN ALL LINE]  [↺ RESET ORDER]       Status: ONLINE (Port 502)     │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
┌──────────────────────────────────────────────────────────────────────────────────────────────┐
│ REAL-TIME MODBUS TELEMETRY & EVENT STREAM                                                    │
│ [18:55:10] [COIL 0] Conveyor 0 STARTED by operator                                          │
│ [18:55:12] [INPUT 0] Vision Sensor 0 triggered: package detected                            │
└──────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 🎯 Core Features of Option B

### 1. Direct In-Diagram Equipment Controls
- **No Bulky Lower Cards**: All controls live directly inside each equipment station.
- **Actuator Machine Block (`Conveyor 0`, `Conveyor 1`, `Curved Conveyor`)**:
  - Embedded **Coil Address Pill** (`Coil 0`, `Coil 1`, `Coil 2`).
  - Embedded **Start/Stop Toggle Button** with active cherry color change (`#7A0C1E` when running, `#E5E7EB` when stopped).
  - Micro-animated roller hash markings indicating running belt motion.
  - Per-conveyor quick test button (`[2s Test]`).
- **Sensor Machine Block (`Vision Sensor 0`)**:
  - Visual optical cone with laser beam.
  - Live emerald LED (`#22C55E` when object present).
  - Real-time detection counter badge (`Count: 14`).
  - Dynamic cardboard package box visual appearing when an object is detected in Factory I/O.

### 2. Drag-and-Drop Modular Reordering
- Each machine block is wrapped in a draggable container (`HBox` / `StackPane`) on a pipeline track.
- **Mouse Drag Gestures**:
  - `setOnMousePressed`: Highlights the selected equipment block with an industrial grabbing shadow (`-fx-cursor: closed_hand; -fx-opacity: 0.85;`).
  - `setOnMouseDragged`: Moves the ghost block visually along the conveyor line track.
  - `setOnMouseReleased`: Snaps into the new position in the sequence, automatically updating line layout and pipeline arrows (`──►`).
  - Includes a **`[ ↺ Reset Line Order ]`** button to restore the default factory sequence (`Infeed -> Conveyor 0 -> Sensor 0 -> Conveyor 1 -> Curved -> Depot`).

### 3. Clean Single-Screen Layout
- The entire SCADA workspace fits on screen without tedious vertical scrolling.
- The bottom section is dedicated to high-density, real-time telemetry logging, connection diagnostics, and line safety overrides.

---

## 🛠️ Step-by-Step Implementation Steps

### Step 1: Create Custom Equipment Widget Components
- Create modular JavaFX composite components:
  - `ConveyorBlockNode`: Combines machine graphic, animated belt strip, coil badge, and direct START/STOP toggle.
  - `SensorBlockNode`: Combines sensor head, optical cone, glowing LED, and counter badge.
  - `ChuteNode`: Represents the Infeed and Outfeed Depot terminals.

### Step 2: Implement Drag-and-Drop Reordering Logic
- Attach JavaFX drag event handlers to each machine block:
  - Calculate reorder indices within the pipeline `HBox`.
  - Re-render connecting pipeline arrows (`──►`) between stations automatically.

### Step 3: Streamline `main-app-view.fxml` & `MainAppController.java`
- Remove the duplicate lower `actuatorsContainer` and `sensorsContainer` FlowPanes.
- Bind the new in-diagram buttons directly to `FactoryIOService.writeTag(...)`.
- Link sensor detection callbacks directly to the in-diagram `SensorBlockNode` LED and counter.
- Preserve and connect line-wide Emergency Stop and Auto Sequence controls.

### Step 4: Verification & Automated Testing
- Ensure automated test suites pass without regression (`mvnw clean test`).
- Test interactive clicking on each conveyor directly on the diagram.
- Test dragging blocks to reorder the pipeline.
- Verify live communication with Factory I/O physics simulation.

---

## 🔍 Feedback & Confirmation

Does this plan accurately capture your vision for **Option B**? Once approved, we will begin implementation immediately.
