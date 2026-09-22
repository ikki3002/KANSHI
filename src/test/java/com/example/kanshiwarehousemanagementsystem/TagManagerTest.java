package com.example.kanshiwarehousemanagementsystem;

import com.example.kanshiwarehousemanagementsystem.model.ModbusTag;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag.TagType;
import com.example.kanshiwarehousemanagementsystem.service.modbus.TagManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TagManagerTest {

    private File tempFile;
    private TagManager tagManager;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = File.createTempFile("test_tags", ".json");
        tempFile.deleteOnExit();
        tagManager = new TagManager(tempFile);
    }

    @AfterEach
    void tearDown() {
        if (tempFile != null && tempFile.exists()) {
            tempFile.delete();
        }
    }

    @Test
    void testDefaultTagsMatchFactoryIO() {
        List<ModbusTag> actuators = tagManager.getActuatorTags();
        List<ModbusTag> sensors = tagManager.getSensorTags();

        assertEquals(3, actuators.size(), "Should have 3 default conveyor actuators");
        assertEquals(1, sensors.size(), "Should have 1 default vision sensor");

        assertEquals(0, actuators.get(0).getAddress());
        assertEquals(1, actuators.get(1).getAddress());
        assertEquals(2, actuators.get(2).getAddress());
        assertEquals(0, sensors.get(0).getAddress());
    }

    @Test
    void testAddAndRemoveTag() {
        int initialCount = tagManager.getAllTags().size();

        tagManager.addTag("Diverter Arm 1", 4, TagType.COIL);
        assertEquals(initialCount + 1, tagManager.getAllTags().size());

        ModbusTag added = tagManager.getAllTags().stream()
                .filter(t -> t.getName().equals("Diverter Arm 1"))
                .findFirst()
                .orElse(null);

        assertNotNull(added);
        assertEquals(4, added.getAddress());
        assertEquals(TagType.COIL, added.getType());

        // Remove tag
        boolean removed = tagManager.removeTag(added.getId());
        assertTrue(removed);
        assertEquals(initialCount, tagManager.getAllTags().size());
    }

    @Test
    void testJsonPersistence() {
        tagManager.addTag("Sorting Pusher", 5, TagType.COIL);
        tagManager.saveToFile();

        // Reload in brand new TagManager instance
        TagManager reloaded = new TagManager(tempFile);
        boolean found = reloaded.getAllTags().stream()
                .anyMatch(t -> t.getName().equals("Sorting Pusher") && t.getAddress() == 5);

        assertTrue(found, "Tag should be persisted and reloaded from JSON file");
    }
}
