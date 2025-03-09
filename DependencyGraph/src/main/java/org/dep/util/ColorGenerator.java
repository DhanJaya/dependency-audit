package org.dep.util;

import org.dep.model.ColorTracker;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ColorGenerator {

    public static String hslToRgb(float h, float s, float l) {
        s /= 100.0f;
        l /= 100.0f;

        float c = (1 - Math.abs(2 * l - 1)) * s;
        float x = c * (1 - Math.abs((h / 60) % 2 - 1));
        float m = l - c / 2;

        float r = 0;
        float g = 0;
        float b = 0;

        if (h < 60) {
            r = c;
            g = x;
        } else if (h < 120) {
            r = x;
            g = c;
        } else if (h < 180) {
            g = c;
            b = x;
        } else if (h < 240) {
            g = x;
            b = c;
        } else if (h < 300) {
            r = x;
            b = c;
        } else {
            r = c;
            b = x;
        }

        int red = Math.round((r + m) * 255);
        int green = Math.round((g + m) * 255);
        int blue = Math.round((b + m) * 255);

        Color color = new Color(red, green, blue);
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static Map<String, ColorTracker> generateColors(Map<String, Integer> duplicateNodes) {
        Map<String, ColorTracker> generateColors = new HashMap<>();

        // number of colors to generate
        int incrementForColor = 360 / (duplicateNodes.size() + 1);
        AtomicInteger currentColor = new AtomicInteger(0);
        duplicateNodes.forEach((key, value) -> {
            // generate shades
            List<String> colors = new ArrayList<>();
            int incrementForShade = 45 / (value + 1);
            int highlight = 50;
            for (int i = 0; i < value; i++) {
                colors.add(hslToRgb(currentColor.get(), 60, highlight));
                highlight += incrementForShade;
            }
            currentColor.getAndAdd(incrementForColor);
            ColorTracker colorTracker = new ColorTracker(colors, 0);
            generateColors.put(key, colorTracker);
        });
        return generateColors;
    }
}
