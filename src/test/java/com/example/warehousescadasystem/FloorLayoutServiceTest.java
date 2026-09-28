package com.example.warehousescadasystem;

import com.example.warehousescadasystem.model.FloorCellPlacement;
import com.example.warehousescadasystem.model.FloorCellPlacement.AssetType;
import com.example.warehousescadasystem.model.FloorCellPlacement.Direction;
import com.example.warehousescadasystem.service.scada.FloorLayoutService;
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
        assertEquals(19, list.size(), "Automated Warehouse line has 19 cells");
        assertEquals(4, layoutService.getGridRows());
        assertEquals(6, layoutService.getGridCols());

        FloorCellPlacement infeed = layoutService.findPlacement(0, 0);
        assertNotNull(infeed);
        assertEquals(AssetType.INFEED, infeed.getAssetType());

        FloorCellPlacement depot = layoutService.findPlacement(2, 1);
        assertNotNull(depot);
        assertEquals(AssetType.DEPOT, depot.getAssetType());

        FloorCellPlacement crane = layoutService.findPlacement(1, 3);
        assertNotNull(crane);
        assertEquals(AssetType.STACKER_CRANE, crane.getAssetType());
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

        // Add a new placement at (2, 0)
        FloorCellPlacement custom = new FloorCellPlacement(2, 0, AssetType.CONVEYOR, "coil_99", Direction.NORTH);
        layoutService.setPlacement(custom);

        FloorCellPlacement retrieved = layoutService.findPlacement(2, 0);
        assertNotNull(retrieved);
        assertEquals("coil_99", retrieved.getTagId());
        assertEquals(Direction.NORTH, retrieved.getDirection());

        // Test persistence
        layoutService.saveToFile();
        FloorLayoutService reloaded = new FloorLayoutService(tempFile);
        assertNotNull(reloaded.findPlacement(2, 0));
    }

    @Test
    void testDynamicGridDimensionExpansionAndShrinking() {
        assertEquals(4, layoutService.getGridRows());
        assertEquals(6, layoutService.getGridCols());

        // Expand columns and rows
        assertTrue(layoutService.addCol());
        assertEquals(7, layoutService.getGridCols());

        assertTrue(layoutService.addRow());
        assertEquals(5, layoutService.getGridRows());

        // Column 6 and Row 4 are empty, so they can be removed
        assertTrue(layoutService.removeCol());
        assertEquals(6, layoutService.getGridCols());

        assertTrue(layoutService.removeRow());
        assertEquals(4, layoutService.getGridRows());

        // In the Automated Warehouse layout, Column 5 contains active equipment at (0,5), (1,5), (2,5).
        // Removing column 5 MUST fail to protect user equipment!
        assertFalse(layoutService.removeCol(), "Should not remove column containing placed equipment");
        assertEquals(6, layoutService.getGridCols());

        // Test persistence of custom dimensions
        layoutService.addCol(); // now 7
        layoutService.addRow(); // now 5
        layoutService.saveToFile();
        FloorLayoutService reloaded = new FloorLayoutService(tempFile);
        assertEquals(5, reloaded.getGridRows());
        assertEquals(7, reloaded.getGridCols());
    }
}
