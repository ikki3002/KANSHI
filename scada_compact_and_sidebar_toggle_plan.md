# Implementation Plan: Compact SCADA Equipment Cards & Collapsible Sidebar Toggle

## 1. Problem Diagnosis & Root Cause Analysis

### Issue A: Cards Stretching Vertically Into Massive White Empty Cards
- **Root Cause**: In the physical L-shape layout, `pipelineTrack` is a JavaFX `HBox`. By default, JavaFX `HBox` has `fillHeight = true`. When the rightmost corner column (`cornerColumn`) stacks 3 elements vertically (`Curved Conveyor` + `Vision Sensor` + `Depot` ~380px tall), the `HBox` forcibly stretches **all other siblings** (`Infeed`, `Belt Conveyor 0`, `Belt Conveyor 1`) to match the exact same 380px height! This resulted in the giant, empty white cards shown in the screenshot.
- **Solution**:
  1. Set `fillHeight="false"` and `alignment="TOP_LEFT"` on `pipelineTrack`.
  2. Set `maxHeight="Region.USE_PREF_SIZE"` and a crisp height bound on each machine card (~115px - 125px).
  3. Ensure `Infeed` and `Depot` terminals have locked compact dimensions.

### Issue B: Cards Are Too Wide for Large Equipment Counts
- **Root Cause**: Each station card was hardcoded to `min-width: 220px; max-width: 240px;` with heavy 14px padding. In a factory with 8 to 15+ machines, this quickly exceeds screen width.
- **Solution**:
  1. Reduce card width from ~230px to **compact industrial tile width: 155px** (32% narrower).
  2. Reduce padding from 12px/14px to **8px 10px**.
  3. Compact belt canvas from 195x32 to **145x24**.
  4. Streamline buttons into micro-action strips (`[▶ RUN]`, `[■ STOP]`, `[⚡2s]`).

### Issue C: Need More Screen Space (Collapsible Sidebar)
- **Root Cause**: The left navigation rail (`.nav-rail`) is permanently locked at `230px` width.
- **Solution**:
  1. Add a **Sidebar Toggle Button** (`[☰]` / `[◀]`) in the header next to the brand.
  2. Support two states:
     - **Expanded (230px)**: Full categories, brand subtitle, and text labels (`📊 Overview Dashboard`, etc.).
     - **Collapsed (64px)**: Minimalist icon-only rail (`📊`, `🏭`, `📦`, `💰`, `🏷️`) with hover tooltips (`Tooltip.install`), category headers hidden.
  3. Immediately reclaims **166px of horizontal screen width** for the SCADA factory pipeline.

---

## 2. Before vs. After UI Specification

```
[ BEFORE: Giant Stretched Cards & Fixed Sidebar ]
┌──────────────┬────────────────────────────────────────────────────────────────────────┐
│ Fixed 230px  │ [Infeed] ──► [Conveyor 0] ──► [Conveyor 1] ──► [Curved ↷]              │
│ Nav Rail     │ │      │     │          │     │          │     │        │              │
│ (Wastes 230px│ │      │     │  EMPTY   │     │  EMPTY   │     ▼        │              │
│  space)      │ │      │     │  SPACE   │     │  SPACE   │    [Sensor]  │              │
│              │ │      │     │  380px   │     │  380px   │     ▼        │              │
│              │ └──────┘     └──────────┘     └──────────┘    [Depot]   │              │
└──────────────┴────────────────────────────────────────────────────────────────────────┘

[ AFTER: High-Density Compact Pipeline + Collapsible Icon Rail ]
┌──────┬────────────────────────────────────────────────────────────────────────────────┐
│ [☰]  │ 🏭 FACTORY I/O PIPELINE (High-Density L-Shape Layout)           [↺ Reset Order]│
│      │                                                                                │
│ [📊] │ [📥] ─► [Conv 0] ─► [Conv 1] ─► [Conv 2] ─► [Sensor 0] ─► [Curved ↷]           │
│ [🏭] │         (155px)     (155px)     (155px)     (155px)    │ (155px)  │            │
│ [📦] │         [▶] [⚡]     [▶] [⚡]     [▶] [⚡]     ● DETECT   ▼          │            │
│ [💰] │                                                        [📦 DEPOT]              │
│ [🏷️] │                                                                                │
│ 64px │ +166px EXTRA HORIZONTAL SPACE FOR EQUIPMENT PIPELINE                           │
└──────┴────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Dimensional Comparison Table

| Property | Current (Bulky) | Proposed (Compact High-Density) | Gain / Improvement |
| :--- | :--- | :--- | :--- |
| **Card Width** | `220px - 240px` | **`155px`** | **-32% width**, fits ~7 machines per row |
| **Card Height** | Stretched to ~`380px` | **`~118px` (Fixed / Constrained)** | **-70% height**, eliminates giant empty gaps |
| **Belt Canvas** | `195px × 32px` | **`145px × 22px`** | Sleek roller strip |
| **Curved Belt Canvas** | `195px × 50px` | **`145px × 40px`** | Compact 90° radial arc |
| **Card Padding** | `12px 14px` | **`8px 10px`** | Tighter industrial ergonomics |
| **Infeed / Depot** | `90px × 380px` (stretched) | **`65px × 90px`** | Compact chassis terminal |
| **Nav Sidebar** | Fixed `230px` | **Toggle: `230px` ⮂ `64px`** | **+166px** dynamic workspace width |
| **Equipment Capacity**| 3–4 machines before scroll | **8–12+ machines comfortably** | **+200% capacity** |

---

## 4. Technical Implementation Steps

### Step 1: Fix `HBox` Vertical Stretching & Sizing in FXML & CSS
- In `main-app-view.fxml`:
  - Update `pipelineTrack` to: `<HBox fx:id="pipelineTrack" styleClass="pipeline-track" alignment="TOP_LEFT" fillHeight="false" />`.
  - Add `btnToggleSidebar` (`[☰]`) to the top header bar next to brand title.
- In `industrial-dark.css`:
  - Update `.pipeline-block`:
    `-fx-min-width: 155px; -fx-max-width: 165px; -fx-padding: 8px 10px; -fx-spacing: 6px;`
  - Update `.pipeline-terminal`:
    `-fx-min-width: 65px; -fx-max-width: 72px; -fx-padding: 10px 6px;`
  - Add styles for `.nav-rail-collapsed`, `.sidebar-toggle-btn`.

### Step 2: Implement Sidebar Collapsing Logic in `MainAppController.java`
- Inject `@FXML private VBox navRail;` and `@FXML private Button btnToggleSidebar;`.
- Add `private boolean isSidebarCollapsed = false;`.
- Add `@FXML private void handleToggleSidebar(ActionEvent event)`:
  - If collapsing:
    - Shrink `navRail` to width `64px`.
    - Hide category header labels (`CORE COMMAND`, `WORKSPACES`).
    - Switch button text to icons only (`📊`, `🏭`, `📦`, `💰`, `🏷️`).
    - Attach descriptive `Tooltip`s so operators see names on hover.
    - Change toggle button icon to `☰` / `▶`.
  - If expanding:
    - Restore width to `230px`.
    - Show category headers.
    - Restore full button labels (`📊 Overview Dashboard`, etc.).
    - Change toggle button icon to `◀`.

### Step 3: Refactor In-Block Machine Widgets to Compact Footprint
- In `createConveyorBlock(ModbusTag tag)`:
  - Width: `155px`, height locked with `setMaxHeight(Region.USE_PREF_SIZE)`.
  - Canvas: `145px × 22px` (straight), `145px × 40px` (curved).
  - Truncated label: e.g. "Belt Conv 0", "Curved CW".
  - Compact action buttons: `[▶ RUN]` / `[■ STOP]` (font 10px, padding `3 6`) + `[⚡2s]` (font 10px, padding `3 5`).
- In `createSensorBlock(ModbusTag tag)`:
  - Width: `155px`, height locked.
  - Canvas: `145px × 30px` (mini optical cone & box payload).
  - Inline LED circle (radius 6) + status label + compact counter pill.
- In `cornerColumn`:
  - Set `cornerColumn.setAlignment(Pos.TOP_CENTER);`.
  - Set `cornerColumn.setFillWidth(false);`.

### Step 4: Verification & Automated Testing
- Compile and run all unit tests: `mvnw test`.
- Verify in running app:
  1. No vertical stretching on straight conveyor cards or infeed terminal.
  2. Toggle sidebar button (`[☰]`) smoothly collapses/expands the left rail.
  3. Drag-and-drop reordering, direct START/STOP toggles, and 2s pulses remain 100% functional.
