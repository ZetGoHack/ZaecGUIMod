package com.mahiru.gui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ColorPickerTest {
    @Test
    void rgbHsvRoundTripIsExactForSampledColours() {
        for (int r = 0; r < 256; r += 15)
            for (int g = 0; g < 256; g += 15)
                for (int b = 0; b < 256; b += 15) {
                    int rgb = (r << 16) | (g << 8) | b;
                    float[] hsv = ColorPicker.rgbToHsv(rgb);
                    assertEquals(rgb, ColorPicker.hsvToRgb(hsv[0], hsv[1], hsv[2]), "colour " + Integer.toHexString(rgb));
                }
    }

    @Test
    void knownColours() {
        assertArrayEquals(new float[] { 0f, 1f, 1f }, ColorPicker.rgbToHsv(0xFF0000), 1e-6f);
        assertArrayEquals(new float[] { 1f / 3, 1f, 1f }, ColorPicker.rgbToHsv(0x00FF00), 1e-6f);
        assertArrayEquals(new float[] { 0f, 0f, 0f }, ColorPicker.rgbToHsv(0x000000), 1e-6f);
        assertEquals(0xFFFFFF, ColorPicker.hsvToRgb(0.3f, 0f, 1f));
        assertEquals(0x0000FF, ColorPicker.hsvToRgb(2f / 3, 1f, 1f));
    }

    @Test
    void hueWrapsAround() {
        assertEquals(ColorPicker.hsvToRgb(0.25f, 1f, 1f), ColorPicker.hsvToRgb(1.25f, 1f, 1f));
        assertEquals(ColorPicker.hsvToRgb(0.25f, 1f, 1f), ColorPicker.hsvToRgb(-0.75f, 1f, 1f));
    }

    @Test
    void hueIsInUnitRange() {
        for (int rgb : new int[] { 0xFF0001, 0xFE00FF, 0x123456, 0xFF00FF }) {
            float h = ColorPicker.rgbToHsv(rgb)[0];
            assertTrue(h >= 0f && h <= 1f, "hue " + h);
        }
    }
}
