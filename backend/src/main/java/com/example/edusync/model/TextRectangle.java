package com.example.edusync.model;

import java.util.Objects;

/**
 * Represents a text bounding box in points on a document page.
 * Uses immutable coordinates for precision rendering.
 */
public class TextRectangle {

    private final float x;
    private final float y;
    private final float width;
    private final float height;

    public TextRectangle(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextRectangle that = (TextRectangle) o;
        return Float.compare(that.x, x) == 0 &&
                Float.compare(that.y, y) == 0 &&
                Float.compare(that.width, width) == 0 &&
                Float.compare(that.height, height) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, width, height);
    }

    @Override
    public String toString() {
        return "TextRectangle{" +
                "x=" + x +
                ", y=" + y +
                ", width=" + width +
                ", height=" + height +
                '}';
    }
}
