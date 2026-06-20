package com.github.tacowasa059.chameleon.client.editor;

import com.github.tacowasa059.chameleon.skin.ChameleonSkin;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Pixel operations on a 64x64 ARGB buffer: dab/line/rect/ellipse/gradient/fill.
 * All respect an optional "lock alpha" (only paint over existing opaque pixels).
 */
public final class PaintOps {

    public static final int N = ChameleonSkin.SIZE;

    private PaintOps() {
    }

    public static void set(int[] px, int x, int y, int color, boolean lockAlpha) {
        if (x < 0 || x >= N || y < 0 || y >= N) {
            return;
        }
        if (lockAlpha && (px[y * N + x] >>> 24) == 0) {
            return;
        }
        px[y * N + x] = color;
    }

    /** Square brush dab of side (2*brush-1). */
    public static void dab(int[] px, int tx, int ty, int color, int brush, boolean lockAlpha) {
        dab(px, tx, ty, color, brush, lockAlpha, 0, 0, N, N);
    }

    /**
     * Square brush dab clipped to [u0,u1) x [v0,v1) so a stroke never bleeds past
     * the face it was painted on (each face is a separate 3D surface).
     */
    public static void dab(int[] px, int tx, int ty, int color, int brush, boolean lockAlpha,
                           int u0, int v0, int u1, int v1) {
        int rad = brush - 1;
        for (int dx = -rad; dx <= rad; dx++) {
            for (int dy = -rad; dy <= rad; dy++) {
                int x = tx + dx, y = ty + dy;
                if (x < u0 || x >= u1 || y < v0 || y >= v1) {
                    continue;
                }
                set(px, x, y, color, lockAlpha);
            }
        }
    }

    public static void line(int[] px, int x0, int y0, int x1, int y1, int color, int brush, boolean lockAlpha) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            dab(px, x0, y0, color, brush, lockAlpha);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public static void rect(int[] px, int x0, int y0, int x1, int y1, int color, boolean filled, boolean lockAlpha) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1);
        int ay = Math.min(y0, y1), by = Math.max(y0, y1);
        for (int y = ay; y <= by; y++) {
            for (int x = ax; x <= bx; x++) {
                boolean edge = x == ax || x == bx || y == ay || y == by;
                if (filled || edge) {
                    set(px, x, y, color, lockAlpha);
                }
            }
        }
    }

    public static void ellipse(int[] px, int x0, int y0, int x1, int y1, int color, boolean filled, boolean lockAlpha) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1);
        int ay = Math.min(y0, y1), by = Math.max(y0, y1);
        float ccx = (ax + bx) / 2f, ccy = (ay + by) / 2f;
        float rx = Math.max(0.5f, (bx - ax) / 2f), ry = Math.max(0.5f, (by - ay) / 2f);
        for (int y = ay; y <= by; y++) {
            for (int x = ax; x <= bx; x++) {
                float nx = (x - ccx) / rx, ny = (y - ccy) / ry;
                float v = nx * nx + ny * ny;
                if (v > 1f) {
                    continue;
                }
                if (filled) {
                    set(px, x, y, color, lockAlpha);
                } else {
                    // boundary: inside but a 4-neighbour is outside
                    boolean inside = neighborsInside(x, y, ccx, ccy, rx, ry);
                    if (!inside) {
                        set(px, x, y, color, lockAlpha);
                    }
                }
            }
        }
    }

    private static boolean neighborsInside(int x, int y, float ccx, float ccy, float rx, float ry) {
        return ell(x + 1, y, ccx, ccy, rx, ry) && ell(x - 1, y, ccx, ccy, rx, ry)
                && ell(x, y + 1, ccx, ccy, rx, ry) && ell(x, y - 1, ccx, ccy, rx, ry);
    }

    private static boolean ell(int x, int y, float ccx, float ccy, float rx, float ry) {
        float nx = (x - ccx) / rx, ny = (y - ccy) / ry;
        return nx * nx + ny * ny <= 1f;
    }

    /** Linear gradient from colorA (start) to colorB (end), within the drag bbox. */
    public static void gradient(int[] px, int x0, int y0, int x1, int y1, int colorA, int colorB, boolean lockAlpha) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1);
        int ay = Math.min(y0, y1), by = Math.max(y0, y1);
        float vx = x1 - x0, vy = y1 - y0;
        float len2 = vx * vx + vy * vy;
        if (len2 < 1e-3f) {
            return;
        }
        for (int y = ay; y <= by; y++) {
            for (int x = ax; x <= bx; x++) {
                float t = ((x - x0) * vx + (y - y0) * vy) / len2;
                t = Math.max(0f, Math.min(1f, t));
                set(px, x, y, lerp(colorA, colorB, t), lockAlpha);
            }
        }
    }

    private static int lerp(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    /** Flood fill bounded to a rectangle (one face), 4-connected. */
    public static void fill(int[] px, int sx, int sy, int newColor, int u0, int v0, int u1, int v1, boolean lockAlpha) {
        if (sx < u0 || sx >= u1 || sy < v0 || sy >= v1) {
            return;
        }
        int target = px[sy * N + sx];
        if (target == newColor) {
            return;
        }
        if (lockAlpha && (target >>> 24) == 0) {
            return;
        }
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{sx, sy});
        while (!stack.isEmpty()) {
            int[] p = stack.pop();
            int x = p[0], y = p[1];
            if (x < u0 || x >= u1 || y < v0 || y >= v1 || px[y * N + x] != target) {
                continue;
            }
            px[y * N + x] = newColor;
            stack.push(new int[]{x + 1, y});
            stack.push(new int[]{x - 1, y});
            stack.push(new int[]{x, y + 1});
            stack.push(new int[]{x, y - 1});
        }
    }
}
