package com.github.tacowasa059.chameleon.client.editor;

import net.minecraft.client.gui.GuiGraphics;

/** HSV color picker: a saturation/value square + a hue bar. */
public final class ColorPicker {

    public static final int SV = 100;
    public static final int HUE_W = 14;
    private static final int GAP = 6;
    private static final int STEP = 2;

    private int x;
    private int y;
    private float h = 0f;
    private float s = 1f;
    private float v = 1f;
    private int grab = 0; // 0 none, 1 = SV square, 2 = hue bar

    public void setPos(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int width() {
        return SV + GAP + HUE_W;
    }

    public int height() {
        return SV;
    }

    public int getArgb() {
        return ColorUtil.hsvToArgb(h, s, v);
    }

    public void setFromArgb(int argb) {
        float[] hsv = ColorUtil.argbToHsv(argb);
        this.h = hsv[0];
        this.s = hsv[1];
        this.v = hsv[2];
    }

    public void render(GuiGraphics g) {
        // SV square
        for (int sx = 0; sx < SV; sx += STEP) {
            for (int sy = 0; sy < SV; sy += STEP) {
                float ss = sx / (float) SV;
                float vv = 1f - sy / (float) SV;
                g.fill(x + sx, y + sy, x + sx + STEP, y + sy + STEP, ColorUtil.hsvToArgb(h, ss, vv));
            }
        }
        g.renderOutline(x - 1, y - 1, SV + 2, SV + 2, 0xFF000000);
        // SV cursor
        int cxp = x + Math.round(s * SV);
        int cyp = y + Math.round((1f - v) * SV);
        g.renderOutline(cxp - 3, cyp - 3, 6, 6, 0xFFFFFFFF);
        g.renderOutline(cxp - 2, cyp - 2, 4, 4, 0xFF000000);

        // Hue bar
        int hx = x + SV + GAP;
        for (int sy = 0; sy < SV; sy += STEP) {
            float hh = sy / (float) SV;
            g.fill(hx, y + sy, hx + HUE_W, y + sy + STEP, ColorUtil.hsvToArgb(hh, 1f, 1f));
        }
        g.renderOutline(hx - 1, y - 1, HUE_W + 2, SV + 2, 0xFF000000);
        int hcy = y + Math.round(h * SV);
        g.renderOutline(hx - 1, hcy - 2, HUE_W + 2, 4, 0xFFFFFFFF);
    }

    /** Returns true if the click was consumed (and updated the color). */
    public boolean mouseDown(double mx, double my) {
        if (mx >= x && mx < x + SV && my >= y && my < y + SV) {
            grab = 1;
            updateSV(mx, my);
            return true;
        }
        int hx = x + SV + GAP;
        if (mx >= hx && mx < hx + HUE_W && my >= y && my < y + SV) {
            grab = 2;
            updateHue(my);
            return true;
        }
        grab = 0;
        return false;
    }

    /**
     * Continue an in-progress drag, clamped to the grabbed control. Keeps the SV
     * square / hue bar responsive even when the cursor leaves the widget (e.g. at
     * the left edge s just clamps to 0 instead of the drag dying).
     */
    public void drag(double mx, double my) {
        if (grab == 1) {
            updateSV(mx, my);
        } else if (grab == 2) {
            updateHue(my);
        }
    }

    private void updateSV(double mx, double my) {
        s = clamp01((float) (mx - x) / SV);
        v = 1f - clamp01((float) (my - y) / SV);
    }

    private void updateHue(double my) {
        h = clamp01((float) (my - y) / SV);
    }

    private static float clamp01(float f) {
        return Math.max(0f, Math.min(1f, f));
    }
}
