package com.unique.examine.module.dashboard.domain;

public record DashboardGrid(
        int x,
        int y,
        int width,
        int height
) {
    public static final int COLUMNS = 12;
    public static final int MAX_ROWS = 100;

    public boolean withinBounds() {
        return x >= 0 && y >= 0 && width > 0 && height > 0
                && (long) x + width <= COLUMNS
                && (long) y + height <= MAX_ROWS;
    }

    public boolean overlaps(DashboardGrid other) {
        if (other == null) {
            return false;
        }
        return (long) x < (long) other.x + other.width
                && (long) other.x < (long) x + width
                && (long) y < (long) other.y + other.height
                && (long) other.y < (long) y + height;
    }
}
