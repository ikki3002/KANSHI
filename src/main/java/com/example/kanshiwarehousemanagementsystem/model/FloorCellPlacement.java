package com.example.kanshiwarehousemanagementsystem.model;

import java.util.Objects;

/**
 * Represents a single equipment placement or terminal on the 2D SCADA factory grid.
 */
public class FloorCellPlacement {

    public enum AssetType {
        INFEED("Infeed Chute", "Entry", "📥"),
        CONVEYOR("Belt Conveyor", "Linear", "⚙️"),
        CURVED_CONVEYOR("Curved Conveyor", "90° Turn", "↷"),
        SENSOR("Vision Sensor", "Optical", "👁️"),
        DEPOT("Depot Chute", "Outfeed", "📦"),
        BRIDGE("Conveyor Bridge", "Flow Link", "──►");

        private final String displayName;
        private final String subTitle;
        private final String icon;

        AssetType(String displayName, String subTitle, String icon) {
            this.displayName = displayName;
            this.subTitle = subTitle;
            this.icon = icon;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSubTitle() {
            return subTitle;
        }

        public String getIcon() {
            return icon;
        }
    }

    public enum Direction {
        EAST("→ East", 0),
        SOUTH("↓ South", 90),
        WEST("← West", 180),
        NORTH("↑ North", 270);

        private final String label;
        private final int angleDegrees;

        Direction(String label, int angleDegrees) {
            this.label = label;
            this.angleDegrees = angleDegrees;
        }

        public String getLabel() {
            return label;
        }

        public int getAngleDegrees() {
            return angleDegrees;
        }

        public Direction next() {
            Direction[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private int row;
    private int col;
    private AssetType assetType;
    private String tagId; // nullable for Infeed, Depot, Bridge
    private Direction direction;

    public FloorCellPlacement() {
    }

    public FloorCellPlacement(int row, int col, AssetType assetType, String tagId, Direction direction) {
        this.row = row;
        this.col = col;
        this.assetType = assetType;
        this.tagId = tagId;
        this.direction = direction != null ? direction : Direction.EAST;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public int getCol() {
        return col;
    }

    public void setCol(int col) {
        this.col = col;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public void setAssetType(AssetType assetType) {
        this.assetType = assetType;
    }

    public String getTagId() {
        return tagId;
    }

    public void setTagId(String tagId) {
        this.tagId = tagId;
    }

    public Direction getDirection() {
        return direction != null ? direction : Direction.EAST;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public void rotate() {
        this.direction = getDirection().next();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FloorCellPlacement that = (FloorCellPlacement) o;
        return row == that.row && col == that.col;
    }

    @Override
    public int hashCode() {
        return Objects.hash(row, col);
    }
}
