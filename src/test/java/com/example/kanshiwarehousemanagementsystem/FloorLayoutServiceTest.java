package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.model.FloorCellPlacement;
import com.example.kanshiwarehousemanagementsystem.model.FloorCellPlacement.AssetType;
import com.example.kanshiwarehousemanagementsystem.model.FloorCellPlacement.Direction;
import com.example.kanshiwarehousemanagementsystem.service.scada.FloorLayoutService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class FloorLayoutServiceTest {

    private File tempFile;
    private FloorLayoutService layoutService;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = File.createTempFile("test_layout", ".json");
        tempFile.deleteOnExit();
        layoutService = new FloorLayoutService(tempFile);
    }

    @AfterEach
    void tearDown() {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    void testDefaultLayoutInitialization() {
        List<FloorCellPlacement> list = layoutService.getAllPlacements();
        assertFalse(list.isEmpty(), "Default layout should have placements");
        assertEquals(6, list.size(), "Standard factory line has 6 cells");

        FloorCellPlacement infeed = layoutService.findPlacement(0, 0);
        assertNotNull(infeed);
        assertEquals(AssetType.INFEED, infeed.getAssetType());

        FloorCellPlacement depot = layoutService.findPlacement(1, 4);
        assertNotNull(depot);
        assertEquals(AssetType.DEPOT, depot.getAssetType());
    }

    @Test
    void testPlacementRotationAndRemoval() {
        FloorCellPlacement placement = layoutService.findPlacement(0, 1);
        assertNotNull(placement);
        assertEquals(Direction.EAST, placement.getDirection());

        placement.rotate();
        assertEquals(Direction.SOUTH, placement.getDirection());
        placement.rotate();
        assertEquals(Direction.WEST, placement.getDirection());

        layoutService.removePlacement(0, 1);
        assertNull(layoutService.findPlacement(0, 1), "Placement should be removed");

        // Add a new placement at (2, 2)
        FloorCellPlacement custom = new FloorCellPlacement(2, 2, AssetType.CONVEYOR, "coil_99", Direction.NORTH);
        layoutService.setPlacement(custom);

        FloorCellPlacement retrieved = layoutService.findPlacement(2, 2);
        assertNotNull(retrieved);
        assertEquals("coil_99", retrieved.getTagId());
        assertEquals(Direction.NORTH, retrieved.getDirection());

        // Test persistence
        layoutService.saveToFile();
        FloorLayoutService reloaded = new FloorLayoutService(tempFile);
        assertNotNull(reloaded.findPlacement(2, 2));
    }
}
