package com.github.tacowasa059.chameleon.client.editor;

/** Small color helpers: HSV/ARGB conversion and hex parsing. */
public final class ColorUtil {

    private ColorUtil() {
    }

    /** h,s,v in 0..1 -> 0xFFRRGGBB (opaque). */
    public static int hsvToArgb(float h, float s, float v) {
        float r, g, b;
        if (s <= 0f) {
            r = g = b = v;
        } else {
            float hh = (h - (float) Math.floor(h)) * 6f;
            int i = (int) hh;
            float f = hh - i;
            float p = v * (1 - s);
            float q = v * (1 - s * f);
            float t = v * (1 - s * (1 - f));
            switch (i) {
                case 0 -> { r = v; g = t; b = p; }
                case 1 -> { r = q; g = v; b = p; }
                case 2 -> { r = p; g = v; b = t; }
                case 3 -> { r = p; g = q; b = v; }
                case 4 -> { r = t; g = p; b = v; }
                default -> { r = v; g = p; b = q; }
            }
        }
        int ri = Math.round(r * 255);
        int gi = Math.round(g * 255);
        int bi = Math.round(b * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    /** 0xAARRGGBB -> {h,s,v} each 0..1. */
    public static float[] argbToHsv(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float v = max;
        float d = max - min;
        float s = max <= 0f ? 0f : d / max;
        float h = 0f;
        if (d > 0f) {
            if (max == r) {
                h = ((g - b) / d) % 6f;
            } else if (max == g) {
                h = (b - r) / d + 2f;
            } else {
                h = (r - g) / d + 4f;
            }
            h /= 6f;
            if (h < 0f) {
                h += 1f;
            }
        }
        return new float[]{h, s, v};
    }

    /** Parse "#RRGGBB" or "RRGGBB" -> 0xFFRRGGBB, or -1 if invalid. */
    public static int parseHex(String text) {
        if (text == null) {
            return -1;
        }
        String t = text.trim();
        if (t.startsWith("#")) {
            t = t.substring(1);
        }
        if (t.length() != 6) {
            return -1;
        }
        try {
            int rgb = Integer.parseInt(t, 16);
            return 0xFF000000 | rgb;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    public static String toHex(int argb) {
        return String.format("%06X", argb & 0xFFFFFF);
    }
}
