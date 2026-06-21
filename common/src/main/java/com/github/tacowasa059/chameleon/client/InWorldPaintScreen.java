package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.client.editor.ColorPicker;
import com.github.tacowasa059.chameleon.client.editor.ColorUtil;
import com.github.tacowasa059.chameleon.client.editor.PaintOps;
import com.github.tacowasa059.chameleon.client.editor.SkinGeometry;
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
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * In-world direct paint overlay: paint on your real player entity as it stands
 * in the world. The world renders live behind the (undimmed) screen; a compact
 * HUD on the left reuses the editor's color picker and brush tools. Left-drag on
 * the body paints, right-drag orbits the camera, the wheel zooms.
 */
public class InWorldPaintScreen extends Screen {

    private static final int N = ChameleonSkin.SIZE;
    private static final int TRANSPARENT = 0x00000000;
    private static final int PANEL = 128;
    private static final int UNDO_LIMIT = 40;

    private static final int T_PEN = 0, T_ERASER = 1, T_FILL = 2, T_PICK = 3;
    private static final String[] TOOL_KEYS = {"pen", "erase", "fill", "pick"};

    private final UUID uuid;
    private final int[] pixels;
    private boolean slim;
    private SkinGeometry geo;
    private final ColorPicker picker = new ColorPicker();

    // Persist across opens so the tool/colour you left with come back.
    private static int tool = T_PEN;
    private static int prevTool = T_PEN; // tool to return to after a one-shot eyedrop
    private static int brushSize = 1;
    private static boolean mirror = false;
    private static boolean overlay = false;

    // Recent colours -- the SAME list the editor uses (shared across both modes).
    private static final Deque<Integer> history = PaintPalette.COLORS;

    private static final Deque<int[]> UNDO = new ArrayDeque<>();
    private static final Deque<int[]> REDO = new ArrayDeque<>();

    private boolean painting, orbiting, pickerActive;
    private double lastX, lastY;
    private int hoverColor; // eyedropper: live screen colour under the cursor
    private static boolean showGuide = false; // G: overlay the cyan paint-guide wireframe

    private EditBox hexField;
    private boolean updatingHex;

    // HUD layout (filled in init)
    private int toolsY, brushY, optsY, pickerY, swatchY;

    public InWorldPaintScreen() {
        super(Component.translatable("screen.chameleon.inworld"));
        PaintPalette.load(); // restore shared A/B colours + history (persisted)
        Minecraft mc = Minecraft.getInstance();
        this.uuid = mc.player != null ? mc.player.getUUID() : new UUID(0, 0);
        ChameleonSkin existing = ClientSkins.get(uuid);
        this.slim = existing != null ? existing.slim()
                : (mc.player != null && "slim".equals(mc.player.getModelName()));
        this.geo = new SkinGeometry(slim);
        this.pixels = existing != null ? existing.raw().clone() : whiteBodyPixels();
        picker.setFromArgb(activeColor());
    }

    @Override
    protected void init() {
        toolsY = 24;
        brushY = toolsY + 2 * 20 + 8;
        optsY = brushY + 14 + 8;
        pickerY = optsY + 14 + 10;
        swatchY = pickerY + picker.height() + 6;

        picker.setPos(4, pickerY);
        hexField = new EditBox(this.font, 4, swatchY + 20, picker.width(), 14, Component.literal("hex"));
        hexField.setMaxLength(7);
        hexField.setValue("#" + ColorUtil.toHex(activeColor()));
        hexField.setResponder(this::onHexChanged);
        addRenderableWidget(hexField);

        int bw = picker.width();
        int half = bw / 2 - 2;
        int by = this.height - 6;
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.done"), b -> onClose())
                .bounds(4, by - 18, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.clear"), b -> {
            snapshot();
            System.arraycopy(whiteBodyPixels(), 0, pixels, 0, pixels.length);
            syncSkin();
        }).bounds(4 + half + 4, by - 18, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.undo"), b -> doUndo())
                .bounds(4, by - 38, half, 16).build());
        addRenderableWidget(Button.builder(Component.translatable("button.chameleon.redo"), b -> doRedo())
                .bounds(4 + half + 4, by - 38, half, 16).build());
    }

    // ---- rendering ----------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No renderBackground(): the live world must stay visible behind us.
        // Eyedropper: read the scene under the cursor NOW, before we draw any HUD,
        // so it samples the real world/body (not our panel or highlight).
        hoverColor = (tool == T_PICK && mouseX >= PANEL) ? compensateLight(ScreenColor.readPixel(mouseX, mouseY)) : 0;

        if (tool != T_PICK) {
            drawHighlight(g, mouseX, mouseY, partialTick);
        }
        if (showGuide) {
            drawModelWireframe(g, partialTick); // cyan paint-guide: where clicks will land
        }
        g.fill(0, 0, PANEL, this.height, 0xC0101014);
        drawPanel(g);

        super.render(g, mouseX, mouseY, partialTick); // hex field + buttons

        if (hoverColor != 0) {
            drawEyedropPreview(g, mouseX, mouseY);
        }
        g.drawString(this.font, Component.translatable("screen.chameleon.inworld.hint"),
                PANEL + 6, this.height - 11, 0xFFE0E0E0, true);
    }

    /** Small swatch + hex of the colour the eyedropper would grab, offset from the cursor. */
    private void drawEyedropPreview(GuiGraphics g, int mx, int my) {
        int x = Math.min(mx + 12, this.width - 58);
        int y = Math.min(my + 12, this.height - 20);
        g.fill(x, y, x + 56, y + 18, 0xFF000000);
        g.fill(x + 1, y + 1, x + 17, y + 17, hoverColor);
        g.drawString(this.font, "#" + ColorUtil.toHex(hoverColor), x + 20, y + 5, 0xFFFFFFFF, false);
    }

    /**
     * Outline the exact texel cell under the cursor on the in-world body. We
     * project the texel's four corners (posed, in perspective) and draw the real
     * cell, so the highlighted pixel sits precisely under the cursor instead of a
     * box at the texel centre (which read as an offset when zoomed in).
     */
    private void drawHighlight(GuiGraphics g, int mx, int my, float partialTick) {
        if (mx < PANEL) {
            return;
        }
        InWorldPaint.Hit hit = InWorldPaint.pick(geo, mx, my, overlay, this.width, this.height, partialTick);
        if (hit == null) {
            return;
        }
        int part = hit.face.part;
        float[] a = InWorldPaint.projectPosed(part, SkinGeometry.pointAtUV(hit.face, hit.tx, hit.ty), partialTick, this.width, this.height);
        float[] b = InWorldPaint.projectPosed(part, SkinGeometry.pointAtUV(hit.face, hit.tx + 1, hit.ty), partialTick, this.width, this.height);
        float[] c = InWorldPaint.projectPosed(part, SkinGeometry.pointAtUV(hit.face, hit.tx + 1, hit.ty + 1), partialTick, this.width, this.height);
        float[] d = InWorldPaint.projectPosed(part, SkinGeometry.pointAtUV(hit.face, hit.tx, hit.ty + 1), partialTick, this.width, this.height);
        if (a == null || b == null || c == null || d == null) {
            return;
        }
        Matrix4f mat = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // translucent fill of the cell
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (float[] p : new float[][]{a, b, c, d}) {
            bb.vertex(mat, p[0], p[1], 0f).color(255, 255, 255, 70).endVertex();
        }
        Tesselator.getInstance().end();

        // crisp border
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        hlEdge(bb, mat, a, b);
        hlEdge(bb, mat, b, c);
        hlEdge(bb, mat, c, d);
        hlEdge(bb, mat, d, a);
        Tesselator.getInstance().end();
    }

    /**
     * Diagnostic: draw the whole picker-space body as a cyan wireframe (projected
     * the exact way clicks are tested). If this does NOT sit on the rendered body,
     * the gap is the cursor error -- and its direction/size pinpoints the cause.
     * Toggle with G.
     */
    private void drawModelWireframe(GuiGraphics g, float partialTick) {
        Matrix4f mat = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (SkinGeometry.Face f : geo.faces()) {
            if (f.overlay) {
                continue;
            }
            float[] a = InWorldPaint.projectPosed(f.part, f.c[0], partialTick, this.width, this.height);
            float[] b = InWorldPaint.projectPosed(f.part, f.c[1], partialTick, this.width, this.height);
            float[] c = InWorldPaint.projectPosed(f.part, f.c[2], partialTick, this.width, this.height);
            float[] d = InWorldPaint.projectPosed(f.part, f.c[3], partialTick, this.width, this.height);
            if (a == null || b == null || c == null || d == null) {
                continue;
            }
            wireEdge(bb, mat, a, b);
            wireEdge(bb, mat, b, c);
            wireEdge(bb, mat, c, d);
            wireEdge(bb, mat, d, a);
        }
        Tesselator.getInstance().end();
    }

    private static void wireEdge(BufferBuilder bb, Matrix4f mat, float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) {
            return;
        }
        float hw = 0.6f;
        float px = -dy / len * hw, py = dx / len * hw;
        bb.vertex(mat, a[0] + px, a[1] + py, 0f).color(0, 220, 255, 150).endVertex();
        bb.vertex(mat, b[0] + px, b[1] + py, 0f).color(0, 220, 255, 150).endVertex();
        bb.vertex(mat, b[0] - px, b[1] - py, 0f).color(0, 220, 255, 150).endVertex();
        bb.vertex(mat, a[0] - px, a[1] - py, 0f).color(0, 220, 255, 150).endVertex();
    }

    /** A cell-border segment as a thin screen-space quad (driver-independent width). */
    private static void hlEdge(BufferBuilder bb, Matrix4f mat, float[] a, float[] b) {
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-4f) {
            return;
        }
        float hw = 1.0f; // half width in px
        float px = -dy / len * hw, py = dx / len * hw;
        bb.vertex(mat, a[0] + px, a[1] + py, 0f).color(255, 230, 64, 235).endVertex();
        bb.vertex(mat, b[0] + px, b[1] + py, 0f).color(255, 230, 64, 235).endVertex();
        bb.vertex(mat, b[0] - px, b[1] - py, 0f).color(255, 230, 64, 235).endVertex();
        bb.vertex(mat, a[0] - px, a[1] - py, 0f).color(255, 230, 64, 235).endVertex();
    }

    private void drawPanel(GuiGraphics g) {
        g.drawString(this.font, this.title, 6, 8, 0xFFFFFFFF, false);
        for (int i = 0; i < TOOL_KEYS.length; i++) {
            int rx = 6 + (i % 2) * 60;
            int ry = toolsY + (i / 2) * 20;
            cell(g, rx, ry, 58, 18,
                    Component.translatable("editor.chameleon.tool." + TOOL_KEYS[i]).getString(), tool == i);
        }
        g.drawString(this.font, Component.translatable("editor.chameleon.brush"), 6, brushY - 10, 0xFFBBBBBB, false);
        for (int i = 0; i < 3; i++) {
            int bx = 6 + i * 40;
            boolean sel = brushSize == i + 1;
            g.fill(bx, brushY, bx + 38, brushY + 14, sel ? 0xFF3A6EA5 : 0xFF2A2A30);
            g.renderOutline(bx, brushY, 38, 14, sel ? 0xFFFFD040 : 0xFF000000);
            int d = (i + 1) * 2;
            int cxc = bx + 19, cyc = brushY + 7;
            g.fill(cxc - d / 2, cyc - d / 2, cxc - d / 2 + d, cyc - d / 2 + d, 0xFFFFFFFF);
        }
        miniToggle(g, 6, optsY, 58, 14, Component.translatable("editor.chameleon.opt.mir").getString(), mirror);
        miniToggle(g, 66, optsY, 58, 14, Component.translatable("editor.chameleon.opt.ovl").getString(), overlay);

        picker.render(g);
        drawSwatch(g, 4, swatchY, "A", PaintPalette.primary, PaintPalette.activeSwatch == 0);
        drawSwatch(g, 64, swatchY, "B", PaintPalette.secondary, PaintPalette.activeSwatch == 1);

        // recent colours
        int hisY = swatchY + 38;
        int i = 0;
        for (Integer c : history) {
            int hx = 4 + i * 15;
            g.fill(hx, hisY, hx + 13, hisY + 13, c | 0xFF000000);
            g.renderOutline(hx, hisY, 13, 13, 0xFF000000);
            i++;
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

    private void drawSwatch(GuiGraphics g, int x, int y, String label, int c, boolean active) {
        g.fill(x, y, x + 56, y + 16, c | 0xFF000000);
        g.renderOutline(x, y, 56, 16, active ? 0xFFFFD040 : 0xFF000000);
        g.drawString(this.font, label, x + 2, y + 4, 0xFFFFFFFF, true);
    }

    // ---- input --------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        lastX = mx;
        lastY = my;

        if (mx < PANEL) {
            if (button == 0 && picker.mouseDown(mx, my)) {
                pickerActive = true;
                setColor(picker.getArgb(), false);
                return true;
            }
            return handlePanelClick(mx, my);
        }

        // on the body / world
        if (button == 1) {
            orbiting = true;
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (tool == T_PICK) {
            // Sample the on-screen colour anywhere (the world, a block, your body),
            // corrected for the current light so the painted skin reproduces it.
            int c = hoverColor != 0 ? hoverColor : compensateLight(ScreenColor.readPixel(mx, my));
            if (c != 0) {
                setColor(c, true);
                pushHistory(c);
                tool = prevTool; // one-shot: return to the previous tool after picking
            }
            return true;
        }
        InWorldPaint.Hit hit = pickNow(mx, my);
        if (hit == null) {
            orbiting = true; // empty space -> let the drag orbit instead
            return true;
        }
        if (tool == T_FILL) {
            int[] r = SkinGeometry.rectOf(hit.face);
            snapshot();
            PaintOps.fill(pixels, hit.tx, hit.ty, activeColor(), r[0], r[1], r[2], r[3], false);
            pushHistory(activeColor());
            syncSkin();
            return true;
        }
        snapshot();
        painting = true;
        if (tool == T_PEN) {
            pushHistory(activeColor());
        }
        paint(hit);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (pickerActive) {
            picker.drag(mx, my);
            setColor(picker.getArgb(), false);
            return true;
        }
        if (orbiting) {
            InWorldPaint.addOrbit(mx - lastX, my - lastY);
            lastX = mx;
            lastY = my;
            return true;
        }
        if (painting) {
            InWorldPaint.Hit hit = pickNow(mx, my);
            if (hit != null) {
                paint(hit);
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        boolean committed = painting;
        painting = false;
        orbiting = false;
        pickerActive = false;
        if (committed) {
            syncSkin();
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        InWorldPaint.addZoom(delta);
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
        if (ChameleonClient.TOGGLE_GUIDE.matches(key, scancode)) {
            showGuide = !showGuide; // toggle the paint-guide wireframe
            return true;
        }
        return super.keyPressed(key, scancode, mods);
    }

    private boolean handlePanelClick(double mx, double my) {
        for (int i = 0; i < TOOL_KEYS.length; i++) {
            int rx = 6 + (i % 2) * 60;
            int ry = toolsY + (i / 2) * 20;
            if (in(mx, my, rx, ry, 58, 18)) {
                if (i == T_PICK && tool != T_PICK) {
                    prevTool = tool; // remember where to return after the eyedrop
                }
                tool = i;
                return true;
            }
        }
        for (int i = 0; i < 3; i++) {
            if (in(mx, my, 6 + i * 40, brushY, 38, 14)) {
                brushSize = i + 1;
                return true;
            }
        }
        if (in(mx, my, 6, optsY, 58, 14)) {
            mirror = !mirror;
            return true;
        }
        if (in(mx, my, 66, optsY, 58, 14)) {
            overlay = !overlay;
            return true;
        }
        // A / B swatches
        if (in(mx, my, 4, swatchY, 56, 16)) {
            PaintPalette.activeSwatch = 0;
            picker.setFromArgb(PaintPalette.primary);
            return true;
        }
        if (in(mx, my, 64, swatchY, 56, 16)) {
            PaintPalette.activeSwatch = 1;
            picker.setFromArgb(PaintPalette.secondary);
            return true;
        }
        // recent colours
        int hisY = swatchY + 38;
        int i = 0;
        for (Integer c : history) {
            if (in(mx, my, 4 + i * 15, hisY, 13, 13)) {
                setColor(c, true);
                return true;
            }
            i++;
        }
        return true; // swallow clicks anywhere on the panel
    }

    private InWorldPaint.Hit pickNow(double mx, double my) {
        return InWorldPaint.pick(geo, mx, my, overlay, this.width, this.height,
                Minecraft.getInstance().getFrameTime());
    }

    // ---- painting -----------------------------------------------------------

    private void paint(InWorldPaint.Hit hit) {
        int c = tool == T_ERASER ? TRANSPARENT : activeColor();
        int[] r = SkinGeometry.rectOf(hit.face);
        PaintOps.dab(pixels, hit.tx, hit.ty, c, brushSize, false, r[0], r[1], r[2], r[3]);
        if (mirror) {
            float[] mp = SkinGeometry.pointAtTexel(hit.face, hit.tx, hit.ty);
            // Mirror stays on the SAME layer (base->base, overlay->overlay).
            boolean ho = hit.face.overlay;
            boolean[] showBase = {!ho, !ho, !ho, !ho, !ho, !ho};
            boolean[] showOver = {ho, ho, ho, ho, ho, ho};
            SkinGeometry.Pick m = geo.texelAtPoint(new float[]{-mp[0], mp[1], mp[2]}, showBase, showOver);
            if (m != null) {
                int[] mr = SkinGeometry.rectOf(m.face);
                PaintOps.dab(pixels, m.tx, m.ty, c, brushSize, false, mr[0], mr[1], mr[2], mr[3]);
            }
        }
        markDirty();
    }

    /** Colour currently painted with: A or B per the active swatch. */
    private int activeColor() {
        return PaintPalette.activeSwatch == 0 ? PaintPalette.primary : PaintPalette.secondary;
    }

    private void setColor(int argb, boolean updatePicker) {
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

    private static void pushHistory(int c) {
        PaintPalette.push(c);
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

    // ---- undo / sync --------------------------------------------------------

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

    /** Live local preview only (per-pixel, no network). */
    private void markDirty() {
        ClientSkins.setLocal(uuid, new ChameleonSkin(pixels, slim));
    }

    /** Commit: apply locally, save client-side, and share with the server. */
    private void syncSkin() {
        ChameleonSkin skin = new ChameleonSkin(pixels.clone(), slim);
        ClientSkins.setLocal(uuid, skin);
        SelfSkin.save(skin);
        ClientNetwork.queueSkin(skin.toBytes()); // share it (debounced) if connected
    }

    private int[] whiteBodyPixels() {
        int[] p = new int[N * N];
        for (SkinGeometry.Face f : geo.faces()) {
            if (f.overlay) {
                continue;
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

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /**
     * Divide a sampled (already-lit) colour by the body's current brightness, so
     * that painting it and re-lighting the body at render time reproduces what was
     * sampled. This is the pick-time fix for double lighting (no fullbright).
     */
    private static int compensateLight(int argb) {
        if (argb == 0) {
            return 0;
        }
        int[] l = InWorldPaint.bodyLightRGB(); // per-channel lightmap multiplier, 0..255
        int r = clamp255(((argb >> 16) & 0xFF) * 255 / l[0]);
        int g = clamp255(((argb >> 8) & 0xFF) * 255 / l[1]);
        int bl = clamp255((argb & 0xFF) * 255 / l[2]);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private static int clamp255(int v) {
        return Math.max(0, Math.min(255, v));
    }

    @Override
    public void onClose() {
        syncSkin();
        ClientNetwork.flush(); // send the final skin immediately on close
        PaintPalette.save();
        InWorldPaint.close();
        this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
