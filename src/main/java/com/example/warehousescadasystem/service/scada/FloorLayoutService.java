package com.example.warehousescadasystem.service.scada;

import com.example.warehousescadasystem.model.FloorCellPlacement;
import com.example.warehousescadasystem.model.FloorCellPlacement.AssetType;
import com.example.warehousescadasystem.model.FloorCellPlacement.Direction;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Service to manage and persist 2D SCADA factory floor layout configurations.
 * Handles storage of dynamic grid dimensions (rows x cols) and machine placements in scada_layout.json.
 */
public class FloorLayoutService {

    public static final int DEFAULT_ROWS = 4;
    public static final int DEFAULT_COLS = 6;
    public static final int MIN_ROWS = 2;
    public static final int MIN_COLS = 3;
    public static final int MAX_ROWS = 15;
    public static final int MAX_COLS = 20;

    private static final String DEFAULT_FILE_NAME = "scada_layout.json";

    public static class LayoutData {
        public int gridRows = DEFAULT_ROWS;
        public int gridCols = DEFAULT_COLS;
        public List<FloorCellPlacement> placements = new ArrayList<>();
    }

    private final File layoutFile;
    private final Gson gson;
    private int gridRows = DEFAULT_ROWS;
    private int gridCols = DEFAULT_COLS;
    private final List<FloorCellPlacement> placements = new ArrayList<>();

    public FloorLayoutService(File layoutFile) {
        this.layoutFile = layoutFile;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        init();
    }

    public FloorLayoutService() {
        this(new File(DEFAULT_FILE_NAME));
    }

    private void init() {
        if (layoutFile.exists() && layoutFile.length() > 0) {
            boolean loaded = loadFromFile();
            if (!loaded || placements.isEmpty()) {
                resetToDefaults();
                saveToFile();
            }
        } else {
            resetToDefaults();
            saveToFile();
        }
    }

    /**
     * Resets floor layout to standard initial factory scene:
     * Row 0: Infeed (0,0) -> Conveyor 0 (0,1) -> Vision Sensor (0,2) -> Conveyor 1 (0,3) -> Curved Conveyor (0,4)
     * Row 1: Depot (1,4)
     */
    public synchronized void resetToDefaults() {
        gridRows = DEFAULT_ROWS;
        gridCols = DEFAULT_COLS;
        placements.clear();

        // Row 0: Infeed Line (Entry Chute -> Entry Conveyor -> At Entry Sensor -> Load Conveyor -> At Load Sensor -> Turn)
        placements.add(new FloorCellPlacement(0, 0, AssetType.INFEED, null, Direction.EAST));
        placements.add(new FloorCellPlacement(0, 1, AssetType.CONVEYOR, "coil_0", Direction.EAST));
        placements.add(new FloorCellPlacement(0, 2, AssetType.SENSOR, "input_0", Direction.EAST));
        placements.add(new FloorCellPlacement(0, 3, AssetType.CONVEYOR, "coil_1", Direction.EAST));
        placements.add(new FloorCellPlacement(0, 4, AssetType.SENSOR, "input_1", Direction.EAST));
        placements.add(new FloorCellPlacement(0, 5, AssetType.CURVED_CONVEYOR, "coil_1", Direction.SOUTH));

        // Row 1: High-Bay Storage Racks (54 bays), Stacker Crane (Reg 0), and Station 55 Transfer
        placements.add(new FloorCellPlacement(1, 0, AssetType.STORAGE_RACK, null, Direction.EAST));
        placements.add(new FloorCellPlacement(1, 1, AssetType.STORAGE_RACK, null, Direction.EAST));
        placements.add(new FloorCellPlacement(1, 2, AssetType.STORAGE_RACK, null, Direction.EAST));
        placements.add(new FloorCellPlacement(1, 3, AssetType.STACKER_CRANE, "reg_0", Direction.EAST));
        placements.add(new FloorCellPlacement(1, 4, AssetType.BRIDGE, null, Direction.EAST));
        placements.add(new FloorCellPlacement(1, 5, AssetType.CURVED_CONVEYOR, "coil_5", Direction.WEST));

        // Row 2: Outfeed Discharge Line (Exit Depot <- Exit Conveyor <- At Exit Sensor <- Unload Conveyor <- At Unload Sensor)
        placements.add(new FloorCellPlacement(2, 1, AssetType.DEPOT, null, Direction.WEST));
        placements.add(new FloorCellPlacement(2, 2, AssetType.CONVEYOR, "coil_6", Direction.WEST));
        placements.add(new FloorCellPlacement(2, 3, AssetType.SENSOR, "input_6", Direction.WEST));
        placements.add(new FloorCellPlacement(2, 4, AssetType.CONVEYOR, "coil_5", Direction.WEST));
        placements.add(new FloorCellPlacement(2, 5, AssetType.SENSOR, "input_5", Direction.WEST));

        // Row 3: Industrial Control Console & Pilot Indicator Panel
        placements.add(new FloorCellPlacement(3, 2, AssetType.CONTROL_PANEL, "input_9", Direction.EAST));
        placements.add(new FloorCellPlacement(3, 3, AssetType.CONTROL_PANEL, "coil_7", Direction.EAST));
    }

    public synchronized boolean loadFromFile() {
        if (!layoutFile.exists()) return false;
        try (FileReader reader = new FileReader(layoutFile)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                if (obj.has("gridRows")) this.gridRows = Math.max(MIN_ROWS, Math.min(MAX_ROWS, obj.get("gridRows").getAsInt()));
                if (obj.has("gridCols")) this.gridCols = Math.max(MIN_COLS, Math.min(MAX_COLS, obj.get("gridCols").getAsInt()));
                if (obj.has("placements")) {
                    Type listType = new TypeToken<List<FloorCellPlacement>>() {}.getType();
                    List<FloorCellPlacement> loaded = gson.fromJson(obj.get("placements"), listType);
                    if (loaded != null) {
                        placements.clear();
                        placements.addAll(loaded);
                        return true;
                    }
                }
            } else if (root.isJsonArray()) {
                // Backwards-compatibility with raw list format
                Type listType = new TypeToken<List<FloorCellPlacement>>() {}.getType();
                List<FloorCellPlacement> loaded = gson.fromJson(root, listType);
                if (loaded != null) {
                    placements.clear();
                    placements.addAll(loaded);
                    this.gridRows = DEFAULT_ROWS;
                    this.gridCols = DEFAULT_COLS;
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading " + layoutFile.getName() + ": " + e.getMessage());
        }
        return false;
    }

    public synchronized boolean saveToFile() {
        LayoutData data = new LayoutData();
        data.gridRows = this.gridRows;
        data.gridCols = this.gridCols;
        data.placements = new ArrayList<>(this.placements);

        try (FileWriter writer = new FileWriter(layoutFile)) {
            gson.toJson(data, writer);
            return true;
        } catch (IOException e) {
            System.err.println("Error saving to " + layoutFile.getName() + ": " + e.getMessage());
            return false;
        }
    }

    public synchronized int getGridRows() {
        return gridRows;
    }

    public synchronized int getGridCols() {
        return gridCols;
    }

    public synchronized boolean addCol() {
        if (gridCols < MAX_COLS) {
            gridCols++;
            return true;
        }
        return false;
    }

    public synchronized boolean removeCol() {
        if (gridCols > MIN_COLS && isColEmpty(gridCols - 1)) {
            gridCols--;
            return true;
        }
        return false;
    }

    public synchronized boolean addRow() {
        if (gridRows < MAX_ROWS) {
            gridRows++;
            return true;
        }
        return false;
    }

    public synchronized boolean removeRow() {
        if (gridRows > MIN_ROWS && isRowEmpty(gridRows - 1)) {
            gridRows--;
            return true;
        }
        return false;
    }

    public synchronized boolean isColEmpty(int col) {
        return placements.stream().noneMatch(p -> p.getCol() == col);
    }

    public synchronized boolean isRowEmpty(int row) {
        return placements.stream().noneMatch(p -> p.getRow() == row);
    }

    public synchronized List<FloorCellPlacement> getAllPlacements() {
        return new ArrayList<>(placements);
    }

    public synchronized FloorCellPlacement findPlacement(int row, int col) {
        for (FloorCellPlacement p : placements) {
            if (p.getRow() == row && p.getCol() == col) {
                return p;
            }
        }
        return null;
    }

    public synchronized void setPlacement(FloorCellPlacement placement) {
        removePlacement(placement.getRow(), placement.getCol());
        placements.add(placement);
    }

    public synchronized void removePlacement(int row, int col) {
        placements.removeIf(p -> p.getRow() == row && p.getCol() == col);
    }

    public synchronized void removePlacementByTagId(String tagId) {
        if (tagId == null) return;
        placements.removeIf(p -> tagId.equals(p.getTagId()));
    }

    public synchronized boolean isTagPlaced(String tagId) {
        if (tagId == null) return false;
        return placements.stream().anyMatch(p -> tagId.equals(p.getTagId()));
    }
}
