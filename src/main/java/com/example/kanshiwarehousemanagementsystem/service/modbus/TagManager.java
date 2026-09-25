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
     * Resets tag list to the exact Factory I/O driver scene configuration.
     */
    public synchronized void resetToDefaults() {
        tags.clear();
        tags.add(new ModbusTag("coil_0", "Belt Conveyor (6m) 0", 0, TagType.COIL));
        tags.add(new ModbusTag("coil_1", "Belt Conveyor (6m) 1", 1, TagType.COIL));
        tags.add(new ModbusTag("coil_2", "Curved Belt Conveyor 0 CW", 2, TagType.COIL));
        tags.add(new ModbusTag("input_0", "Vision Sensor 0", 0, TagType.DISCRETE_INPUT));
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

    public File getConfigFile() {
        return configFile;
    }
}
