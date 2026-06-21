package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.Constants;
import com.github.tacowasa059.chameleon.client.editor.ColorPicker;
import com.github.tacowasa059.chameleon.client.editor.ColorUtil;
import com.github.tacowasa059.chameleon.client.editor.PaintOps;
import com.github.tacowasa059.chameleon.client.editor.SkinGeometry;
import com.github.tacowasa059.chameleon.client.editor.SkinIO;
import com.github.tacowasa059.chameleon.skin.ChameleonSkin;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * Full skin editor: 2D UV map and 3D model shown together (paint on either,
 * synced), with pen/eraser/fill/eyedropper/line/rect/ellipse/gradient, palette
 * and color history, per-part visibility, lock-alpha, per-pixel grid, Steve/Alex
 * models and PNG import/export. 3D render and pick share one projection (WYSIWYG).
 */
public class SkinEditorScreen extends Screen {

    private static final int N = ChameleonSkin.SIZE;
    private static final int TRANSPARENT = 0x00000000;
    private static final int UNDO_LIMIT = 40;

    private static final int T_PEN = 0, T_ERASER = 1, T_FILL = 2, T_PICK = 3,
            T_LINE = 4, T_RECT = 5, T_ELLIPSE = 6, T_GRADIENT = 7;
    // Localized via editor.chameleon.tool.<key> so labels follow the JP/EN setting.
    private static final String[] TOOL_KEYS = {"pen", "erase", "fill", "pick", "line", "rect", "oval", "grad"};

    // Parts laid out as a front-facing humanoid: {partId, relX, relY, w, h}.
    // Screen-left = the model's right (mirror of a person facing you).
    private static final int[][] PART_FIGURE = {
            {SkinGeometry.PART_HEAD, 39, 0, 14, 13},
            {SkinGeometry.PART_RIGHT_ARM, 29, 15, 7, 22},
            {SkinGeometry.PART_BODY, 38, 15, 16, 22},
            {SkinGeometry.PART_LEFT_ARM, 56, 15, 7, 22},
            {SkinGeometry.PART_RIGHT_LEG, 38, 39, 7, 18},
            {SkinGeometry.PART_LEFT_LEG, 47, 39, 7, 18},
    };

    // Option toggles, in row order. Label = editor.chameleon.opt.<key>,
    // tooltip = editor.chameleon.tip.<key>; both follow the JP/EN setting.
    private static final String[] OPT_KEYS = {"mir", "ovl", "lock", "grid", "slim", "fill"};

    // Editor-preview-only pose presets: [preset][partId][rx,ry,rz] radians.
    // Part order by id: RLEG, LLEG, BODY, RARM, LARM, HEAD.
    private static final String[] POSE_KEYS = {"rest", "tpose", "up", "sit", "walk"};
    private static final float[][][] POSES = {
            new float[6][3], // rest
            {{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, deg(90)}, {0, 0, deg(-90)}, {0, 0, 0}},   // T-pose
            {{0, 0, 0}, {0, 0, 0}, {0, 0, 0}, {0, 0, deg(160)}, {0, 0, deg(-160)}, {0, 0, 0}}, // arms up
            {{deg(-90), 0, 0}, {deg(-90), 0, 0}, {0, 0, 0}, {deg(-12), 0, 0}, {deg(-12), 0, 0}, {0, 0, 0}}, // sit
            {{deg(25), 0, 0}, {deg(-25), 0, 0}, {0, 0, 0}, {deg(-30), 0, 0}, {deg(30), 0, 0}, {0, 0, 0}},   // walk
    };

    private static float deg(double d) {
        return (float) Math.toRadians(d);
    }

    // Joint sliders under the pose row, laid out 2 columns (R | L) x 5 rows.
    // Each entry = {partId, axis} (axis 0=rx pitch, 1=ry yaw, 2=rz roll).
    private static final int[][] JOINTS = {
            {SkinGeometry.PART_RIGHT_ARM, 0}, {SkinGeometry.PART_LEFT_ARM, 0}, // arm swing (fwd/back)
            {SkinGeometry.PART_RIGHT_ARM, 2}, {SkinGeometry.PART_LEFT_ARM, 2}, // arm spread (independent)
            {SkinGeometry.PART_RIGHT_LEG, 0}, {SkinGeometry.PART_LEFT_LEG, 0}, // leg swing
            {SkinGeometry.PART_RIGHT_LEG, 2}, {SkinGeometry.PART_LEFT_LEG, 2}, // leg spread (independent)
            {SkinGeometry.PART_HEAD, 1}, {SkinGeometry.PART_HEAD, 0},          // head turn | head nod
    };
    private static final String[] JOINT_ROW_KEYS = {"arm_sw", "arm_sp", "leg_sw", "leg_sp", "head"};
    private static final float JOINT_MAX = (float) Math.toRadians(150);
    private static final int SL_W = 32; // slider width

    private static final int LW = 110;
    private static final int RW = 128;

    private final UUID uuid;
    private final int[] pixels;
    private final ColorPicker picker = new ColorPicker();

    private SkinGeometry geo;
    private boolean slim;
    // Per-part layer view (indexed by part id): 2 = base+overlay, 1 = base only, 0 = hidden.
    private final int[] partLayer = {2, 2, 2, 2, 2, 2};
    // Derived each frame from partLayer + the global overlay master toggle.
    private final boolean[] showBase = new boolean[6];
    private final boolean[] showOver = new boolean[6];

    // Undo/redo are kept STATIC so history survives closing & reopening the editor.
    private static final Deque<int[]> UNDO = new ArrayDeque<>();
    private static final Deque<int[]> REDO = new ArrayDeque<>();

    private int tool = T_PEN;
    private int prevTool = T_PEN; // tool to return to after a one-shot eyedrop
    private int brushSize = 1;
    private boolean mirror = false;
    private boolean overlay = true;
    private boolean lockAlpha = false;
    private boolean grid = true;
    private boolean shapeFill = true;

    // Working colours + history are SHARED with the in-world paint mode (PaintPalette).
    private static final Deque<Integer> history = PaintPalette.COLORS;

    // 3D camera + region (front faces the viewer at yaw 0)
    private float yaw = 0.5f, pitch = 0.0f, zoom = 7f, cx3, cy3;
    private int r3x0, r3y0, r3x1, r3y1;
    // 2D map region
    private int ox2, oy2, r2x0, r2y0, r2x1, r2y1;
    private float scale2 = 6f;

    private boolean rotating, painting, shaping, pickerActive, paintOn2d;
    private int sx, sy, curx, cury; // shape start/current texel
    private double lastMouseX, lastMouseY;
    private int pickHover; // eyedropper: screen colour under the cursor (read each frame)

    private static final int POSE_WALK = 4;
    private int poseIndex = POSE_WALK;           // default pose = walk
    private final float[][] pose = copyPose(POSES[POSE_WALK]); // live, slider-editable
    private int sliderDrag = -1;                 // joint slider being dragged, or -1

    private static float[][] copyPose(float[][] p) {
        float[][] r = new float[p.length][];
        for (int i = 0; i < p.length; i++) {
            r[i] = p[i].clone();
        }
        return r;
    }

    // left panel layout
    private int toolsY, brushY, optsY, partsY, poseY;
    private int bmY; // right-panel bookmark row (set during render)
    private EditBox hexField;
    private boolean updatingHex = false;

    public SkinEditorScreen() {
        super(Component.translatable("screen.chameleon.editor"));
        Minecraft mc = Minecraft.getInstance();
        PaintPalette.load(); // restore A/B colours + history (shared, persisted)
        this.uuid = mc.player != null ? mc.player.getUUID() : new UUID(0, 0);
        ChameleonSkin existing = ClientSkins.get(uuid);
        this.slim = existing != null ? existing.slim()
                : (mc.player != null && "slim".equals(mc.player.getModelName()));
        this.geo = new SkinGeometry(slim);
        // No fully transparent skins: a new canvas starts as an opaque white body.
        this.pixels = existing != null ? existing.raw().clone() : whiteBodyPixels();
        picker.setFromArgb(PaintPalette.primary);
    }

    @Override
    protected void init() {
        toolsY = 24;
        brushY = toolsY + 4 * 20 + 14;
        optsY = brushY + 16 + 14;
        partsY = optsY + 3 * 16 + 22; // extra room for the 2-line parts header
        poseY = partsY + 60; // below the part figure (~57px tall)

        layoutRegions();

        int px = this.width - RW + 4;
        picker.setPos(px, 22);

        hexField = new EditBox(this.font, px, 22 + picker.height() + 4, picker.width(), 14, Component.literal("hex"));
        hexField.setMaxLength(7);
        hexField.setValue("#" + ColorUtil.toHex(PaintPalette.primary));
        hexField.setResponder(this::onHexChanged);
        addRenderableWidget(hexField);

        // action buttons pinned at the bottom of the right panel. No Save button:
        // edits apply immediately, undo/redo always available, Done just closes.
        int bw = picker.width();
        int half = bw / 2 - 2;
        int by = this.height - 6;
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.done"), b -> onClose())
                .bounds(px, by - 18, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.clear"), b -> {
            snapshot();
            System.arraycopy(whiteBodyPixels(), 0, pixels, 0, pixels.length);
            syncSkin();
        }).bounds(px + half + 4, by - 18, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.undo"), b -> doUndo())
                .bounds(px, by - 38, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.redo"), b -> doRedo())
                .bounds(px + half + 4, by - 38, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.import"), b -> doImport())
                .bounds(px, by - 56, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.export"), b -> SkinIO.exportPng(pixels))
                .bounds(px + half + 4, by - 56, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.default"), b -> importDefaultSkin())
                .bounds(px, by - 74, bw, 16).build());

        markDirty();
    }

    private void layoutRegions() {
        int centerL = LW + 4;
        int centerR = this.width - RW - 4;
        int centerW = centerR - centerL;
        int w3 = (int) (centerW * 0.5f) - 6;
        r3x0 = centerL;
        r3x1 = centerL + w3;
        r3y0 = 20;
        r3y1 = this.height - 20;
        cx3 = (r3x0 + r3x1) / 2f;
        cy3 = (r3y0 + r3y1) / 2f;
        zoom = Math.max(4f, Math.min(11f, Math.min(w3, r3y1 - r3y0) / 38f));

        r2x0 = r3x1 + 12;
        r2x1 = centerR;
        r2y0 = 20;
        r2y1 = this.height - 20;
        int avail = Math.min(r2x1 - r2x0, r2y1 - r2y0) - 24;
        scale2 = Math.max(2f, avail / (float) N);
        int size = Math.round(N * scale2);
        ox2 = r2x0 + ((r2x1 - r2x0) - size) / 2;
        oy2 = r2y0 + ((r2y1 - r2y0) - size) / 2;
    }

    /** Recompute which layers each part shows. {@code overlay} is the global master. */
    private void refreshLayerView() {
        for (int p = 0; p < 6; p++) {
            showBase[p] = partLayer[p] >= 1;
            showOver[p] = overlay && partLayer[p] >= 2;
        }
    }

    // ---- rendering ----------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        refreshLayerView();
        this.renderBackground(g);
        g.fill(0, 0, LW, this.height, 0xC0101014);
        g.fill(this.width - RW, 0, this.width, this.height, 0xC0101014);

        int[] view = pixels;
        if (shaping) {
            view = pixels.clone();
            applyShape(view);
        }

        drawModel(g, view);
        draw2d(g, view);
        drawLeftPanel(g);
        drawRightPanel(g);

        Component hint = Component.translatable("screen.chameleon.hint");
        g.drawString(this.font, hint, LW + 4, this.height - 11, 0xFF8890A0, false);

        super.render(g, mouseX, mouseY, partialTick);
        drawTooltips(g, mouseX, mouseY);

        // Eyedropper reads the composited screen, so sample at the very end (after
        // the 2D map / 3D model have been drawn). flush() rasterises the still-
        // batched GUI draws into the framebuffer first, or the read would miss them.
        if (tool == T_PICK) {
            g.flush();
            pickHover = ScreenColor.readPixel(mouseX, mouseY);
        } else {
            pickHover = 0;
        }
    }

    /** Hover help for the option toggles, so Lock/Grid/etc. explain themselves. */
    private void drawTooltips(GuiGraphics g, int mx, int my) {
        for (int i = 0; i < 6; i++) {
            int rx = 8 + (i % 2) * 50;
            int ry = optsY + (i / 2) * 16;
            if (in(mx, my, rx, ry, 48, 14)) {
                g.renderTooltip(this.font, Component.translatable("editor.chameleon.tip." + OPT_KEYS[i]), mx, my);
                return;
            }
        }
        int bw = BM_COLS * BM_TILE + (BM_COLS - 1) * BM_GAP;
        if (in(mx, my, this.width - RW + 4, bmY, bw, 2 * BM_TILE + BM_GAP)) {
            g.renderTooltip(this.font, Component.translatable("editor.chameleon.bookmarks.help"), mx, my);
        }
    }

    private void drawLeftPanel(GuiGraphics g) {
        g.drawString(this.font, Component.translatable("editor.chameleon.tools"), 8, 12, 0xFFFFFFFF, false);
        for (int i = 0; i < 8; i++) {
            int rx = 8 + (i % 2) * 50;
            int ry = toolsY + (i / 2) * 20;
            cell(g, rx, ry, 48, 18, Component.translatable("editor.chameleon.tool." + TOOL_KEYS[i]).getString(), tool == i);
        }
        g.drawString(this.font, Component.translatable("editor.chameleon.brush"), 8, brushY - 10, 0xFFBBBBBB, false);
        for (int i = 0; i < 3; i++) {
            int bx = 8 + i * 32;
            boolean sel = brushSize == i + 1;
            g.fill(bx, brushY, bx + 30, brushY + 14, sel ? 0xFF3A6EA5 : 0xFF2A2A30);
            g.renderOutline(bx, brushY, 30, 14, sel ? 0xFFFFD040 : 0xFF000000);
            int d = (i + 1) * 2; // dot size 2/4/6 = brush thickness, shown at a glance
            int cxc = bx + 15, cyc = brushY + 7;
            g.fill(cxc - d / 2, cyc - d / 2, cxc - d / 2 + d, cyc - d / 2 + d, 0xFFFFFFFF);
        }
        // option toggles (compact 2x3)
        boolean[] on = {mirror, overlay, lockAlpha, grid, slim, shapeFill};
        for (int i = 0; i < 6; i++) {
            int rx = 8 + (i % 2) * 50;
            int ry = optsY + (i / 2) * 16;
            miniToggle(g, rx, ry, 48, 14, Component.translatable("editor.chameleon.opt." + OPT_KEYS[i]).getString(), on[i]);
        }
        // part layers, drawn as a little body figure. Two-line header.
        g.drawString(this.font, Component.translatable("editor.chameleon.parts"), 8, partsY - 20, 0xFFBBBBBB, false);
        g.drawString(this.font, Component.translatable("editor.chameleon.parts.hint"), 8, partsY - 10, 0xFF8890A0, false);
        for (int[] pf : PART_FIGURE) {
            int rx = 8 + pf[1];
            int ry = partsY + pf[2];
            int w = pf[3];
            int h = pf[4];
            int mode = partLayer[pf[0]];
            int bg = mode == 2 ? 0xFF3A6EA5 : mode == 1 ? 0xFF2E7D45 : 0xFF44262C;
            g.fill(rx, ry, rx + w, ry + h, bg);
            g.renderOutline(rx, ry, w, h, mode == 0 ? 0xFF7A3038 : 0xFF101014);
            String tag = mode == 2 ? "2" : mode == 1 ? "1" : "-";
            g.drawCenteredString(this.font, tag, rx + w / 2, ry + h / 2 - 4,
                    mode == 0 ? 0xFFB08088 : 0xFFFFFFFF);
        }
        // pose preset cycle (editor preview only)
        String poseName = Component.translatable("editor.chameleon.pose." + POSE_KEYS[poseIndex]).getString();
        cell(g, 8, poseY, 96, 14,
                Component.translatable("editor.chameleon.pose").getString() + ": " + poseName, poseIndex != 0);
        // joint sliders (2 columns R | L, 5 rows) editing the live pose
        for (int row = 0; row < JOINT_ROW_KEYS.length; row++) {
            int sy = poseY + 18 + row * 12;
            g.drawString(this.font,
                    Component.translatable("editor.chameleon.joint." + JOINT_ROW_KEYS[row]).getString(),
                    8, sy + 1, 0xFFB0B0B0, false);
            for (int col = 0; col < 2; col++) {
                int i = row * 2 + col;
                int x = sliderX(i);
                g.fill(x, sy, x + SL_W, sy + 10, 0xFF24242C);
                g.renderOutline(x, sy, SL_W, 10, 0xFF000000);
                float t = jointRead(i) / JOINT_MAX * 0.5f + 0.5f; // [-MAX,MAX] -> [0,1]
                int hx = x + Math.round(t * (SL_W - 4));
                g.fill(hx, sy, hx + 4, sy + 10, 0xFFFFD040);
            }
        }
    }

    private void drawRightPanel(GuiGraphics g) {
        int px = this.width - RW + 4;
        g.drawString(this.font, this.title, px, 8, 0xFFFFFFFF, false);
        picker.render(g);
        // A/B swatches
        int swY = 22 + picker.height() + 4 + 14 + 4;
        drawSwatch(g, px, swY, "A", PaintPalette.primary, PaintPalette.activeSwatch == 0);
        drawSwatch(g, px + 62, swY, "B", PaintPalette.secondary, PaintPalette.activeSwatch == 1);
        // recent color history
        int hisY = swY + 28;
        g.drawString(this.font, Component.translatable("editor.chameleon.history"), px, hisY - 9, 0xFF999999, false);
        int i = 0;
        for (Integer c : history) {
            if (i >= 8) {
                break;
            }
            int rx = px + i * 15;
            g.fill(rx, hisY, rx + 13, hisY + 13, c);
            g.renderOutline(rx, hisY, 13, 13, 0xFF000000);
            i++;
        }
        // bookmarks: inline grid of 3D skin thumbnails. Left-click load,
        // right-click / empty slot = save current.
        bmY = hisY + 26;
        g.drawString(this.font, Component.translatable("editor.chameleon.bookmarks"), px, bmY - 9, 0xFF999999, false);
        for (int s = 0; s < SkinBookmarks.SLOTS; s++) {
            int tx = px + (s % BM_COLS) * (BM_TILE + BM_GAP);
            int ty = bmY + (s / BM_COLS) * (BM_TILE + BM_GAP);
            g.fill(tx, ty, tx + BM_TILE, ty + BM_TILE, 0xFF24242C);
            if (SkinBookmarks.has(s)) {
                SkinThumbnail.render(g, SkinBookmarks.get(s), SkinBookmarks.texture(s), tx, ty, BM_TILE);
            } else {
                g.drawCenteredString(this.font, "+", tx + BM_TILE / 2, ty + BM_TILE / 2 - 4, 0xFF55555C);
            }
            g.renderOutline(tx, ty, BM_TILE, BM_TILE, 0xFF000000);
        }
    }

    private void cell(GuiGraphics g, int x, int y, int w, int h, String label, boolean sel) {
        g.fill(x, y, x + w, y + h, sel ? 0xFF3A6EA5 : 0xFF2A2A30);
        g.renderOutline(x, y, w, h, sel ? 0xFFFFD040 : 0xFF000000);
        g.drawCenteredString(this.font, label, x + w / 2, y + (h - 8) / 2, 0xFFFFFFFF);
    }

    private void miniToggle(GuiGraphics g, int x, int y, int w, int h, String label, boolean on) {
        g.fill(x, y, x + w, y + h, on ? 0xFF2E7D32 : 0xFF3A2A2A);
        g.renderOutline(x, y, w, h, 0xFF000000);
        g.drawCenteredString(this.font, label, x + w / 2, y + 3, 0xFFFFFFFF);
    }

    private void drawSwatch(GuiGraphics g, int x, int y, String label, int color, boolean active) {
        g.fill(x, y, x + 56, y + 16, color | 0xFF000000);
        g.renderOutline(x, y, 56, 16, active ? 0xFFFFD040 : 0xFF000000);
        g.drawString(this.font, label, x + 2, y + 4, 0xFFFFFFFF, true);
    }

    // ---- 2D map -------------------------------------------------------------

    private void draw2d(GuiGraphics g, int[] view) {
        int size = Math.round(N * scale2);
        for (int ty = 0; ty < N; ty++) {
            for (int tx = 0; tx < N; tx++) {
                int x = ox2 + Math.round(tx * scale2);
                int y = oy2 + Math.round(ty * scale2);
                int x2 = ox2 + Math.round((tx + 1) * scale2);
                int y2 = oy2 + Math.round((ty + 1) * scale2);
                int argb = view[ty * N + tx];
                if ((argb >>> 24) == 0) {
                    boolean dark = ((tx >> 1) + (ty >> 1)) % 2 == 0;
                    g.fill(x, y, x2, y2, dark ? 0xFF3A3A40 : 0xFF4A4A52);
                } else {
                    g.fill(x, y, x2, y2, argb);
                }
            }
        }
        // per-pixel grid (always)
        for (int i = 0; i <= N; i++) {
            int x = ox2 + Math.round(i * scale2);
            int y = oy2 + Math.round(i * scale2);
            int c = (i % 8 == 0) ? 0x60FFFFFF : 0x30FFFFFF;
            g.fill(x, oy2, x + 1, oy2 + size, c);
            g.fill(ox2, y, ox2 + size, y + 1, c);
        }
        // face/layer outlines: base = green, 2nd layer = amber
        for (SkinGeometry.Face f : geo.faces()) {
            if (!SkinGeometry.shown(f, showBase, showOver)) {
                continue;
            }
            int[] r = SkinGeometry.rectOf(f);
            // derive both edges from the same rounding the grid uses, so they align
            int x = ox2 + Math.round(r[0] * scale2);
            int y = oy2 + Math.round(r[1] * scale2);
            int w = ox2 + Math.round(r[2] * scale2) - x;
            int h = oy2 + Math.round(r[3] * scale2) - y;
            g.renderOutline(x, y, w, h, f.overlay ? 0xC0FFB020 : 0xC030D060);
        }
        g.renderOutline(ox2 - 1, oy2 - 1, size + 2, size + 2, 0xFF000000);
        g.drawString(this.font, Component.translatable("editor.chameleon.legend"), ox2, oy2 + size + 3, 0xFFB0B0B0, false);
    }

    private int[] texel2d(double mx, double my) {
        int tx = (int) Math.floor((mx - ox2) / scale2);
        int ty = (int) Math.floor((my - oy2) / scale2);
        if (tx < 0 || tx >= N || ty < 0 || ty >= N) {
            return null;
        }
        return new int[]{tx, ty};
    }

    private boolean in2dRegion(double mx, double my) {
        return mx >= r2x0 && mx < r2x1 && my >= r2y0 && my < r2y1;
    }

    private boolean in3dRegion(double mx, double my) {
        return mx >= r3x0 && mx < r3x1 && my >= r3y0 && my < r3y1;
    }

    // ---- 3D model -----------------------------------------------------------

    private void drawModel(GuiGraphics g, int[] view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ClientSkins.setLocal(uuid, new ChameleonSkin(view));
        ResourceLocation rl = ClientSkins.textureFor(mc.player);
        if (rl == null) {
            return;
        }
        List<SkinGeometry.Face> all = geo.faces();
        float[][][] screen = new float[all.size()][][];
        int[] shadeArr = new int[all.size()];
        float[] nzArr = new float[all.size()];
        float[] light = norm(-0.3f, -0.5f, -1.0f);
        float[][] ps = pose;
        List<float[]> order = new ArrayList<>();
        for (int idx = 0; idx < all.size(); idx++) {
            SkinGeometry.Face f = all.get(idx);
            if (!SkinGeometry.shown(f, showBase, showOver)) {
                continue;
            }
            float[][] s = new float[4][];
            float depth = 0;
            for (int k = 0; k < 4; k++) {
                float[] pc = SkinGeometry.posed(f.part, f.c[k], ps);
                float[] r = SkinGeometry.rotate(pc[0], pc[1], pc[2], yaw, pitch);
                s[k] = SkinGeometry.project(r, zoom, cx3, cy3);
                depth += s[k][2];
            }
            float[] pn = SkinGeometry.posedDir(f.part, f.n, ps);
            float[] rn = SkinGeometry.rotateDir(pn[0], pn[1], pn[2], yaw, pitch);
            screen[idx] = s;
            shadeArr[idx] = shade(rn, light);
            nzArr[idx] = rn[2]; // <0 = facing the viewer (front)
            order.add(new float[]{idx, depth / 4f});
        }
        order.sort((a, b) -> Float.compare(a[1], b[1]));

        Matrix4f mat = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        // ghost pass
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] o : order) {
            int idx = (int) o[0];
            int sh = Math.round(shadeArr[idx] * 0.6f);
            float[][] s = screen[idx];
            for (int k = 0; k < 4; k++) {
                bb.vertex(mat, s[k][0], s[k][1], 0f).color(sh, sh, sh, 235).endVertex();
            }
        }
        Tesselator.getInstance().end();

        // texture pass
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, rl);
        bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] o : order) {
            int idx = (int) o[0];
            int sh = shadeArr[idx];
            SkinGeometry.Face f = all.get(idx);
            float[][] s = screen[idx];
            for (int k = 0; k < 4; k++) {
                bb.vertex(mat, s[k][0], s[k][1], 0f)
                        .uv(f.uv[k][0] / (float) N, f.uv[k][1] / (float) N)
                        .color(sh, sh, sh, 255)
                        .endVertex();
            }
        }
        Tesselator.getInstance().end();

        // grid pass: one frame per skin texel, drawn on the front-facing visible
        // faces only. Uses thin QUADS (not GL_LINES, which drivers thin to nothing)
        // so every pixel boundary is actually visible on the 3D model.
        if (grid) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (float[] o : order) {
                int idx = (int) o[0];
                if (nzArr[idx] >= 0f) {
                    continue; // back-facing: would bleed through, skip
                }
                SkinGeometry.Face f = all.get(idx);
                if (!f.overlay && showOver[f.part]) {
                    continue; // base sits under the 2nd layer; grid only the outer one
                }
                int[] r = SkinGeometry.rectOf(f);
                int cols = r[2] - r[0], rows = r[3] - r[1];
                float[][] s = screen[idx];
                float cell = (float) Math.hypot(s[1][0] - s[0][0], s[1][1] - s[0][1]) / Math.max(1, cols);
                if (cell < 1.5f) {
                    continue; // too small to read; would just be noise
                }
                float hw = Math.min(0.7f, cell * 0.12f); // half line width in px
                for (int c = 0; c <= cols; c++) {
                    float t = c / (float) cols;
                    gridLine(bb, mat, lerp(s[0], s[1], t), lerp(s[3], s[2], t), hw);
                }
                for (int rr = 0; rr <= rows; rr++) {
                    float t = rr / (float) rows;
                    gridLine(bb, mat, lerp(s[0], s[3], t), lerp(s[1], s[2], t), hw);
                }
            }
            Tesselator.getInstance().end();
        }
        RenderSystem.enableCull();
    }

    /** A grid line as a thin screen-space quad so its width is driver-independent. */
    private static void gridLine(BufferBuilder bb, Matrix4f mat, float[] a, float[] b, float hw) {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) {
            return;
        }
        float px = -dy / len * hw, py = dx / len * hw; // perpendicular offset
        int cr = 18, cg = 18, cb = 22, ca = 100; // dark, fairly transparent lines
        bb.vertex(mat, a[0] + px, a[1] + py, 0f).color(cr, cg, cb, ca).endVertex();
        bb.vertex(mat, b[0] + px, b[1] + py, 0f).color(cr, cg, cb, ca).endVertex();
        bb.vertex(mat, b[0] - px, b[1] - py, 0f).color(cr, cg, cb, ca).endVertex();
        bb.vertex(mat, a[0] - px, a[1] - py, 0f).color(cr, cg, cb, ca).endVertex();
    }

    private static float[] lerp(float[] a, float[] b, float t) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    private static int shade(float[] rn, float[] light) {
        float d = Math.max(0f, rn[0] * light[0] + rn[1] * light[1] + rn[2] * light[2]);
        return Math.round((0.55f + 0.45f * d) * 255f);
    }

    private static float[] norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }

    // ---- input --------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        refreshLayerView();
        lastMouseX = mx;
        lastMouseY = my;

        if (button == 0 && picker.mouseDown(mx, my)) {
            pickerActive = true;
            setActiveColor(picker.getArgb(), false);
            return true;
        }
        if (button == 0 && handleRightPanelClick(mx, my)) {
            return true;
        }
        if (button == 0 && handleHistoryClick(mx, my)) {
            return true;
        }
        if (handleBookmarkClick(mx, my, button)) {
            return true;
        }
        if (button == 0 && handleSliderPress(mx, my)) {
            return true;
        }
        if (button == 0 && handleLeftPanelClick(mx, my)) {
            return true;
        }

        // Eyedropper now samples the on-screen colour (the displayed 2D map, the
        // shaded 3D model, anything rendered) instead of only the raw skin pixel.
        if (button == 0 && tool == T_PICK && (in2dRegion(mx, my) || in3dRegion(mx, my))) {
            int c = pickHover != 0 ? pickHover : ScreenColor.readPixel(mx, my);
            if (c != 0) {
                setActiveColor(c);
                tool = prevTool; // one-shot: return to the previous tool after picking
            }
            return true;
        }

        // surfaces
        int[] t = null;
        boolean on2d = false;
        float[] modelPoint = null;
        SkinGeometry.Face face = null;
        if (in2dRegion(mx, my)) {
            t = texel2d(mx, my);
            on2d = true;
            if (t != null) {
                face = geo.faceAtTexel(t[0], t[1], showBase, showOver);
                if (face != null) {
                    modelPoint = SkinGeometry.pointAtTexel(face, t[0], t[1]); // enables mirror on 2D
                }
            }
        } else if (in3dRegion(mx, my)) {
            SkinGeometry.Pick p = geo.pick(mx, my, yaw, pitch, zoom, cx3, cy3, showBase, showOver, pose);
            if (p != null) {
                t = new int[]{p.tx, p.ty};
                modelPoint = p.point;
                face = p.face;
            }
        }

        if (button == 1) { // right click
            if (in3dRegion(mx, my)) {
                rotating = true;
            } else if (t != null && tool == T_PICK) {
                sample(t[0], t[1]);
                tool = prevTool;
            }
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (t == null) {
            if (in3dRegion(mx, my)) {
                rotating = true;
            }
            return true;
        }

        if (isShape(tool)) {
            if (on2d) {
                shaping = true;
                sx = t[0];
                sy = t[1];
                curx = t[0];
                cury = t[1];
            }
            return true;
        }
        switch (tool) {
            case T_PEN, T_ERASER -> {
                snapshot();
                painting = true;
                paintOn2d = on2d;
                if (tool == T_PEN) {
                    pushHistory(activeColor());
                }
                paintFreehand(t[0], t[1], modelPoint, face);
            }
            case T_FILL -> {
                if (face != null) {
                    int[] r = SkinGeometry.rectOf(face);
                    snapshot();
                    pushHistory(activeColor());
                    PaintOps.fill(pixels, t[0], t[1], activeColor(), r[0], r[1], r[2], r[3], lockAlpha);
                    syncSkin();
                }
            }
            case T_PICK -> sample(t[0], t[1]);
            default -> {
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (sliderDrag >= 0) {
            setSliderFromMouse(mx);
            return true;
        }
        if (pickerActive) {
            picker.drag(mx, my);
            setActiveColor(picker.getArgb(), false);
            return true;
        }
        if (rotating) {
            // drag follows the grabbed surface: drag right -> model's near face
            // moves right (so you see its other side), drag down -> tilt toward you.
            yaw -= (float) (mx - lastMouseX) * 0.01f;
            pitch += (float) (my - lastMouseY) * 0.01f;
            pitch = Math.max(-1.55f, Math.min(1.55f, pitch));
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }
        if (shaping) {
            int[] t = texel2d(mx, my);
            if (t != null) {
                curx = t[0];
                cury = t[1];
            }
            return true;
        }
        if (painting) {
            if (paintOn2d) {
                int[] t = texel2d(mx, my);
                if (t != null) {
                    SkinGeometry.Face f2 = geo.faceAtTexel(t[0], t[1], showBase, showOver);
                    float[] mp = (mirror && f2 != null) ? SkinGeometry.pointAtTexel(f2, t[0], t[1]) : null;
                    paintFreehand(t[0], t[1], mp, f2);
                }
            } else {
                SkinGeometry.Pick p = geo.pick(mx, my, yaw, pitch, zoom, cx3, cy3, showBase, showOver, pose);
                if (p != null) {
                    paintFreehand(p.tx, p.ty, p.point, p.face);
                }
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean committed = painting || shaping;
        if (shaping) {
            snapshot();
            pushHistory(tool == T_GRADIENT ? PaintPalette.primary : activeColor());
            applyShape(pixels);
            shaping = false;
        }
        painting = false;
        rotating = false;
        pickerActive = false;
        sliderDrag = -1;
        if (committed) {
            syncSkin(); // push the finished stroke to the server (immediate apply)
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (in2dRegion(mx, my)) {
            scale2 *= delta > 0 ? 1.12f : 0.89f;
            scale2 = Math.max(2f, Math.min(16f, scale2));
            int size = Math.round(N * scale2);
            ox2 = r2x0 + ((r2x1 - r2x0) - size) / 2;
            oy2 = r2y0 + ((r2y1 - r2y0) - size) / 2;
        } else {
            zoom *= delta > 0 ? 1.12f : 0.89f;
            zoom = Math.max(2.5f, Math.min(24f, zoom));
        }
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scancode, int mods) {
        boolean ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && key == GLFW.GLFW_KEY_Z) {
            if ((mods & GLFW.GLFW_MOD_SHIFT) != 0) {
                doRedo();
            } else {
                doUndo();
            }
            return true;
        }
        if (ctrl && key == GLFW.GLFW_KEY_Y) {
            doRedo();
            return true;
        }
        return super.keyPressed(key, scancode, mods);
    }

    private boolean handleLeftPanelClick(double mx, double my) {
        for (int i = 0; i < 8; i++) {
            int rx = 8 + (i % 2) * 50;
            int ry = toolsY + (i / 2) * 20;
            if (in(mx, my, rx, ry, 48, 18)) {
                if (i == T_PICK && tool != T_PICK) {
                    prevTool = tool; // remember where to return after the eyedrop
                }
                tool = i;
                return true;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (in(mx, my, 8 + i * 32, brushY, 30, 14)) {
                brushSize = i + 1;
                return true;
            }
        }
        for (int i = 0; i < 6; i++) {
            int rx = 8 + (i % 2) * 50;
            int ry = optsY + (i / 2) * 16;
            if (in(mx, my, rx, ry, 48, 14)) {
                switch (i) {
                    case 0 -> mirror = !mirror;
                    case 1 -> overlay = !overlay;
                    case 2 -> lockAlpha = !lockAlpha;
                    case 3 -> grid = !grid;
                    case 4 -> {
                        slim = !slim;
                        geo = new SkinGeometry(slim);
                        syncSkin(); // others should see the slim/wide change immediately
                    }
                    case 5 -> shapeFill = !shapeFill;
                }
                return true;
            }
        }
        for (int[] pf : PART_FIGURE) {
            if (in(mx, my, 8 + pf[1], partsY + pf[2], pf[3], pf[4])) {
                partLayer[pf[0]] = (partLayer[pf[0]] + 2) % 3; // cycle 2 -> 1 -> 0 -> 2
                return true;
            }
        }
        if (in(mx, my, 8, poseY, 96, 14)) {
            poseIndex = (poseIndex + 1) % POSES.length;
            for (int i = 0; i < 6; i++) {
                System.arraycopy(POSES[poseIndex][i], 0, pose[i], 0, 3);
            }
            return true;
        }
        return false;
    }

    private int sliderX(int i) {
        return (i % 2 == 0) ? 36 : 70; // R column | L column
    }

    private int sliderY(int i) {
        return poseY + 18 + (i / 2) * 12;
    }

    /** A joint slider's current value (radians) read from the live pose. */
    private float jointRead(int i) {
        return pose[JOINTS[i][0]][JOINTS[i][1]];
    }

    private void jointWrite(int i, float v) {
        pose[JOINTS[i][0]][JOINTS[i][1]] = v;
    }

    private boolean handleSliderPress(double mx, double my) {
        for (int i = 0; i < JOINTS.length; i++) {
            if (in(mx, my, sliderX(i), sliderY(i), SL_W, 10)) {
                sliderDrag = i;
                setSliderFromMouse(mx);
                return true;
            }
        }
        return false;
    }

    private void setSliderFromMouse(double mx) {
        int x = sliderX(sliderDrag);
        float t = (float) Math.max(0, Math.min(1, (mx - x) / (SL_W - 4)));
        jointWrite(sliderDrag, (t * 2f - 1f) * JOINT_MAX);
    }

    private boolean handleRightPanelClick(double mx, double my) {
        int px = this.width - RW + 4;
        int swY = 22 + picker.height() + 4 + 14 + 4;
        if (in(mx, my, px, swY, 56, 16)) {
            PaintPalette.activeSwatch = 0;
            picker.setFromArgb(PaintPalette.primary);
            return true;
        }
        if (in(mx, my, px + 62, swY, 56, 16)) {
            PaintPalette.activeSwatch = 1;
            picker.setFromArgb(PaintPalette.secondary);
            return true;
        }
        return false;
    }

    private boolean handleHistoryClick(double mx, double my) {
        int px = this.width - RW + 4;
        int swY = 22 + picker.height() + 4 + 14 + 4;
        int hisY = swY + 28;
        int i = 0;
        for (Integer c : history) {
            if (i >= 8) {
                break;
            }
            int rx = px + i * 15;
            if (in(mx, my, rx, hisY, 13, 13)) {
                setActiveColor(c);
                return true;
            }
            i++;
        }
        return false;
    }

    // inline bookmark grid (right panel) layout
    private static final int BM_COLS = 4, BM_TILE = 18, BM_GAP = 2;

    private boolean handleBookmarkClick(double mx, double my, int button) {
        int px = this.width - RW + 4;
        for (int s = 0; s < SkinBookmarks.SLOTS; s++) {
            int tx = px + (s % BM_COLS) * (BM_TILE + BM_GAP);
            int ty = bmY + (s / BM_COLS) * (BM_TILE + BM_GAP);
            if (in(mx, my, tx, ty, BM_TILE, BM_TILE)) {
                if (button == 1 || !SkinBookmarks.has(s)) {
                    SkinBookmarks.set(s, new ChameleonSkin(pixels.clone(), slim)); // save current
                } else {
                    loadBookmark(s);
                }
                return true;
            }
        }
        return false;
    }

    private void loadBookmark(int slot) {
        ChameleonSkin s = SkinBookmarks.get(slot);
        if (s == null) {
            return;
        }
        snapshot();
        System.arraycopy(s.raw(), 0, pixels, 0, pixels.length);
        if (s.slim() != slim) {
            slim = s.slim();
            geo = new SkinGeometry(slim);
        }
        syncSkin();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static boolean isShape(int t) {
        return t == T_LINE || t == T_RECT || t == T_ELLIPSE || t == T_GRADIENT;
    }

    // ---- painting -----------------------------------------------------------

    private void paintFreehand(int tx, int ty, float[] modelPoint, SkinGeometry.Face face) {
        int color = tool == T_ERASER ? TRANSPARENT : activeColor();
        boolean lock = tool != T_ERASER && lockAlpha;
        dabClipped(tx, ty, color, lock, face);
        if (mirror && modelPoint != null && face != null) {
            // Use the texel CENTRE (robust near face edges) and keep the mirror on
            // the SAME layer as the painted face (base->base, overlay->overlay).
            float[] mc = SkinGeometry.pointAtTexel(face, tx, ty);
            boolean[] mb = new boolean[6];
            boolean[] mo = new boolean[6];
            if (face.overlay) {
                System.arraycopy(showOver, 0, mo, 0, 6);
            } else {
                System.arraycopy(showBase, 0, mb, 0, 6);
            }
            SkinGeometry.Pick m = geo.texelAtPoint(new float[]{-mc[0], mc[1], mc[2]}, mb, mo);
            if (m != null) {
                dabClipped(m.tx, m.ty, color, lock, m.face);
            }
        }
        markDirty();
    }

    /** Brush dab clipped to the face's texel rect (so it can't bleed onto another face). */
    private void dabClipped(int tx, int ty, int color, boolean lock, SkinGeometry.Face face) {
        if (face != null) {
            int[] r = SkinGeometry.rectOf(face);
            PaintOps.dab(pixels, tx, ty, color, brushSize, lock, r[0], r[1], r[2], r[3]);
        } else {
            PaintOps.dab(pixels, tx, ty, color, brushSize, lock);
        }
    }

    private void applyShape(int[] buf) {
        switch (tool) {
            case T_LINE -> PaintOps.line(buf, sx, sy, curx, cury, activeColor(), brushSize, lockAlpha);
            case T_RECT -> PaintOps.rect(buf, sx, sy, curx, cury, activeColor(), shapeFill, lockAlpha);
            case T_ELLIPSE -> PaintOps.ellipse(buf, sx, sy, curx, cury, activeColor(), shapeFill, lockAlpha);
            case T_GRADIENT -> PaintOps.gradient(buf, sx, sy, curx, cury, PaintPalette.primary, PaintPalette.secondary, lockAlpha);
            default -> {
            }
        }
    }

    private void sample(int tx, int ty) {
        int c = pixels[ty * N + tx];
        if ((c >>> 24) != 0) {
            setActiveColor(c | 0xFF000000);
        }
    }

    // ---- color / history ----------------------------------------------------

    /** Colour currently painted with: A (PaintPalette.primary) or B (PaintPalette.secondary) per the active swatch. */
    private int activeColor() {
        return PaintPalette.activeSwatch == 0 ? PaintPalette.primary : PaintPalette.secondary;
    }

    private void setActiveColor(int argb) {
        setActiveColor(argb, true);
    }

    /**
     * @param updatePicker push the color back into the picker. Must be FALSE when
     *     the picker itself drove the change, otherwise a grayscale (s=0) or black
     *     (v=0) color round-trips and resets the hue (cursor jumps back to red).
     */
    private void setActiveColor(int argb, boolean updatePicker) {
        argb = argb | 0xFF000000;
        if (PaintPalette.activeSwatch == 0) {
            PaintPalette.primary = argb;
        } else {
            PaintPalette.secondary = argb;
        }
        if (updatePicker) {
            picker.setFromArgb(argb);
        }
        if (hexField != null) {
            updatingHex = true;
            hexField.setValue("#" + ColorUtil.toHex(argb));
            updatingHex = false;
        }
    }

    private void pushHistory(int color) {
        PaintPalette.push(color);
    }

    private void onHexChanged(String text) {
        if (updatingHex) {
            return;
        }
        int c = ColorUtil.parseHex(text);
        if (c != -1) {
            if (PaintPalette.activeSwatch == 0) {
                PaintPalette.primary = c;
            } else {
                PaintPalette.secondary = c;
            }
            picker.setFromArgb(c);
        }
    }

    // ---- undo / preview / save ---------------------------------------------

    private void snapshot() {
        UNDO.push(pixels.clone());
        if (UNDO.size() > UNDO_LIMIT) {
            UNDO.removeLast();
        }
        REDO.clear();
    }

    private void doUndo() {
        if (UNDO.isEmpty()) {
            return;
        }
        REDO.push(pixels.clone());
        System.arraycopy(UNDO.pop(), 0, pixels, 0, pixels.length);
        syncSkin();
    }

    private void doRedo() {
        if (REDO.isEmpty()) {
            return;
        }
        UNDO.push(pixels.clone());
        System.arraycopy(REDO.pop(), 0, pixels, 0, pixels.length);
        syncSkin();
    }

    private void doImport() {
        int[] loaded = SkinIO.importPng();
        if (loaded != null) {
            snapshot();
            System.arraycopy(loaded, 0, pixels, 0, pixels.length);
            syncSkin();
        }
    }

    /** Live local preview (per-pixel, no network). Carries the slim flag too. */
    private void markDirty() {
        ClientSkins.setLocal(uuid, new ChameleonSkin(pixels, slim));
    }

    /** Commit an edit: apply locally, save it client-side, and push to the server. */
    private void syncSkin() {
        ChameleonSkin skin = new ChameleonSkin(pixels.clone(), slim);
        ClientSkins.setLocal(uuid, skin);
        SelfSkin.save(skin);            // keep it client-side (works without a server)
        ClientNetwork.queueSkin(skin.toBytes()); // share it (debounced) if connected
    }

    /** Default canvas: an opaque white body, so a skin is never fully transparent. */
    private int[] whiteBodyPixels() {
        int[] p = new int[N * N];
        for (SkinGeometry.Face f : geo.faces()) {
            if (f.overlay) {
                continue; // only the base layer is filled; overlay stays transparent
            }
            int[] r = SkinGeometry.rectOf(f);
            for (int y = Math.max(0, r[1]); y < Math.min(N, r[3]); y++) {
                for (int x = Math.max(0, r[0]); x < Math.min(N, r[2]); x++) {
                    p[y * N + x] = 0xFFFFFFFF;
                }
            }
        }
        return p;
    }

    @Override
    public void onClose() {
        syncSkin();
        ClientNetwork.flush(); // send the final skin immediately on close
        PaintPalette.save();
        this.minecraft.setScreen(null);
    }

    /** Load the player's real (vanilla / Mojang) skin into the editor canvas. */
    private void importDefaultSkin() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // The real model/skin come from PlayerInfo (getModelName there is NOT
        // overridden by our mixin, unlike AbstractClientPlayer.getModelName()).
        net.minecraft.client.multiplayer.PlayerInfo info =
                mc.getConnection() != null ? mc.getConnection().getPlayerInfo(uuid) : null;
        ResourceLocation loc;
        boolean s;
        if (info != null) {
            loc = info.getSkinLocation();
            s = "slim".equals(info.getModelName());
        } else {
            loc = mc.getSkinManager().getInsecureSkinLocation(mc.player.getGameProfile());
            s = slim;
        }
        int[] px = readSkinTexture(loc);
        if (px == null) {
            return;
        }
        snapshot();
        System.arraycopy(px, 0, pixels, 0, pixels.length);
        if (s != slim) {
            slim = s;
            geo = new SkinGeometry(slim);
        }
        syncSkin();
    }

    /** Read a 64x64 skin texture back from the GPU into ARGB pixels. */
    private static int[] readSkinTexture(ResourceLocation loc) {
        try {
            Minecraft mc = Minecraft.getInstance();
            net.minecraft.client.renderer.texture.AbstractTexture tex = mc.getTextureManager().getTexture(loc);
            if (tex == null) {
                return null;
            }
            tex.bind();
            com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(N, N, false);
            img.downloadTexture(0, false);
            int[] out = new int[N * N];
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    int a = (abgr >>> 24) & 0xFF, b = (abgr >> 16) & 0xFF, g = (abgr >> 8) & 0xFF, r = abgr & 0xFF;
                    out[y * N + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
            img.close();
            return out;
        } catch (Exception e) {
            Constants.LOG.warn("Failed to read default skin texture: {}", e.toString());
            return null;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
