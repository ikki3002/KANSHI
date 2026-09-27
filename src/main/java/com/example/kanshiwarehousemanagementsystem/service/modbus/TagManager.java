package com.example.kanshiwarehousemanagementsystem.service.modbus;

import com.example.kanshiwarehousemanagementsystem.model.ModbusTag;
import com.example.kanshiwarehousemanagementsystem.model.ModbusTag.TagType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages industrial Modbus tags and handles JSON persistence.
 * Demonstrates Week 7 JSON Parsing and configuration handling.
 */
public class TagManager {

    private static final String DEFAULT_FILE_NAME = "warehouse_tags.json";
    private final File configFile;
    private final Gson gson;
    private final List<ModbusTag> tags = new ArrayList<>();

    public TagManager(File configFile) {
        this.configFile = configFile;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        init();
    }

    public TagManager() {
        this(new File(DEFAULT_FILE_NAME));
    }

    private void init() {
        if (configFile.exists() && configFile.length() > 0) {
            boolean loaded = loadFromFile();
            if (!loaded || tags.isEmpty()) {
                resetToDefaults();
                saveToFile();
            }
        } else {
            resetToDefaults();
            saveToFile();
        }
    }

    /**
     * Resets tag list to the exact Factory I/O Automated Warehouse driver scene configuration.
     */
    public synchronized void resetToDefaults() {
        tags.clear();
        // 15 Discrete Inputs (0..14)
        tags.add(new ModbusTag("input_0", "At Entry", 0, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_1", "At Load", 1, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_2", "At Left", 2, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_3", "At Middle", 3, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_4", "At Right", 4, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_5", "At Unload", 5, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_6", "At Exit", 6, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_7", "Moving X", 7, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_8", "Moving Z", 8, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_9", "Start", 9, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_10", "Reset", 10, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_11", "Stop", 11, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_12", "Emergency stop", 12, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_13", "Auto", 13, TagType.DISCRETE_INPUT));
        tags.add(new ModbusTag("input_14", "FACTORY I/O (Running)", 14, TagType.DISCRETE_INPUT));

        // 10 Coils (0..9)
        tags.add(new ModbusTag("coil_0", "Entry Conveyor", 0, TagType.COIL));
        tags.add(new ModbusTag("coil_1", "Load Conveyor", 1, TagType.COIL));
        tags.add(new ModbusTag("coil_2", "Forks Left", 2, TagType.COIL));
        tags.add(new ModbusTag("coil_3", "Forks Right", 3, TagType.COIL));
        tags.add(new ModbusTag("coil_4", "Lift", 4, TagType.COIL));
        tags.add(new ModbusTag("coil_5", "Unload Conveyor", 5, TagType.COIL));
        tags.add(new ModbusTag("coil_6", "Exit Conveyor", 6, TagType.COIL));
        tags.add(new ModbusTag("coil_7", "Start light", 7, TagType.COIL));
        tags.add(new ModbusTag("coil_8", "Reset light", 8, TagType.COIL));
        tags.add(new ModbusTag("coil_9", "Stop light", 9, TagType.COIL));

        // Holding Register 0
        tags.add(new ModbusTag("reg_0", "Target Position", 0, TagType.HOLDING_REGISTER));
    }

    /**
     * Loads tags from the JSON configuration file.
     */
    public synchronized boolean loadFromFile() {
        if (!configFile.exists()) {
            return false;
        }

        try (FileReader reader = new FileReader(configFile)) {
            Type listType = new TypeToken<List<ModbusTag>>() {}.getType();
            List<ModbusTag> loaded = gson.fromJson(reader, listType);
            if (loaded != null) {
                tags.clear();
                tags.addAll(loaded);
                return true;
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to load tags from JSON: " + e.getMessage());
        }
        return false;
    }

    /**
     * Saves the current tag list into the JSON configuration file.
     */
    public synchronized boolean saveToFile() {
        try (FileWriter writer = new FileWriter(configFile)) {
            gson.toJson(tags, writer);
            return true;
        } catch (IOException e) {
            System.err.println("[ERROR] Failed to save tags to JSON: " + e.getMessage());
            return false;
        }
    }

    public synchronized List<ModbusTag> getAllTags() {
        return new ArrayList<>(tags);
    }

    public synchronized List<ModbusTag> getActuatorTags() {
        return tags.stream()
                .filter(ModbusTag::isActuator)
                .collect(Collectors.toList());
    }

    public synchronized List<ModbusTag> getSensorTags() {
        return tags.stream()
                .filter(ModbusTag::isSensor)
                .collect(Collectors.toList());
    }

    public synchronized ModbusTag findTagByName(String name) {
        if (name == null) return null;
        return tags.stream()
                .filter(t -> t.getName().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElse(null);
    }

    public synchronized ModbusTag findTagByAddress(int address, TagType type) {
        return tags.stream()
                .filter(t -> t.getAddress() == address && t.getType() == type)
                .findFirst()
                .orElse(null);
    }

    public synchronized ModbusTag findTagById(String id) {
        if (id == null) return null;
        return tags.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public synchronized void addTag(String name, int address, TagType type) {
        String id = "tag_" + type.name().toLowerCase() + "_" + address + "_" + UUID.randomUUID().toString().substring(0, 4);
        ModbusTag tag = new ModbusTag(id, name, address, type);
        tags.add(tag);
        saveToFile();
    }

    public synchronized boolean removeTag(String id) {
        boolean removed = tags.removeIf(tag -> tag.getId().equals(id));
        if (removed) {
            saveToFile();
        }
        return removed;
    }

    public synchronized boolean updateTag(String id, String newName, int newAddress, TagType newType) {
        ModbusTag tag = findTagById(id);
        if (tag != null) {
            tag.setName(newName);
            tag.setAddress(newAddress);
            if (newType != null) {
                tag.setType(newType);
            }
            saveToFile();
            return true;
        }
        return false;
    }

    public File getConfigFile() {
        return configFile;
    }
}
