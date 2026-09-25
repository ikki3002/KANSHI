package com.example.kanshiwarehousemanagementsystem.service.scada;

import com.example.kanshiwarehousemanagementsystem.model.FloorCellPlacement;
import com.example.kanshiwarehousemanagementsystem.model.FloorCellPlacement.AssetType;
import com.example.kanshiwarehousemanagementsystem.model.FloorCellPlacement.Direction;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * Handles storage in scada_layout.json.
 */
public class FloorLayoutService {

    public static final int GRID_ROWS = 4;
    public static final int GRID_COLS = 6;
    private static final String DEFAULT_FILE_NAME = "scada_layout.json";

    private final File layoutFile;
    private final Gson gson;
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
        placements.clear();
        placements.add(new FloorCellPlacement(0, 0, AssetType.INFEED, null, Direction.EAST));
        placements.add(new FloorCellPlacement(0, 1, AssetType.CONVEYOR, "coil_0", Direction.EAST));
        placements.add(new FloorCellPlacement(0, 2, AssetType.SENSOR, "input_0", Direction.EAST));
        placements.add(new FloorCellPlacement(0, 3, AssetType.CONVEYOR, "coil_1", Direction.EAST));
        placements.add(new FloorCellPlacement(0, 4, AssetType.CURVED_CONVEYOR, "coil_2", Direction.EAST));
        placements.add(new FloorCellPlacement(1, 4, AssetType.DEPOT, null, Direction.SOUTH));
    }

    public synchronized boolean loadFromFile() {
        if (!layoutFile.exists()) return false;
        try (FileReader reader = new FileReader(layoutFile)) {
            Type listType = new TypeToken<List<FloorCellPlacement>>() {}.getType();
            List<FloorCellPlacement> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                placements.clear();
                placements.addAll(loaded);
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error reading " + layoutFile.getName() + ": " + e.getMessage());
        }
        return false;
    }

    public synchronized boolean saveToFile() {
        try (FileWriter writer = new FileWriter(layoutFile)) {
            gson.toJson(placements, writer);
            return true;
        } catch (IOException e) {
            System.err.println("Error saving to " + layoutFile.getName() + ": " + e.getMessage());
            return false;
        }
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
