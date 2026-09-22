package com.example.kanshiwarehousemanagementsystem.model;

import java.util.Objects;

/**
 * Represents an industrial SCADA Modbus Tag (Coil or Discrete Input).
 * Demonstrates Week 1 Core OOP encapsulation, enums, and constructors.
 */
public class ModbusTag {

    public enum TagType {
        COIL("Coil (Actuator / Output)"),
        DISCRETE_INPUT("Discrete Input (Sensor / Input)");

        private final String displayName;

        TagType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    private String id;
    private String name;
    private int address;
    private TagType type;
    private transient boolean active; // Transient: not saved in JSON configuration

    public ModbusTag() {
    }

    public ModbusTag(String id, String name, int address, TagType type) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.type = type;
        this.active = false;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAddress() {
        return address;
    }

    public void setAddress(int address) {
        this.address = address;
    }

    public TagType getType() {
        return type;
    }

    public void setType(TagType type) {
        this.type = type;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isActuator() {
        return type == TagType.COIL;
    }

    public boolean isSensor() {
        return type == TagType.DISCRETE_INPUT;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ModbusTag modbusTag = (ModbusTag) o;
        return address == modbusTag.address && type == modbusTag.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, type);
    }

    @Override
    public String toString() {
        return name + " [" + type.name() + " " + address + "]";
    }
}
