package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.ChameleonPose;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Radial pose picker. Opened with the pose-wheel key; the centre is STAND (clear),
 * the ring holds the server's allowed poses. Hover by mouse angle, left-click to
 * pick (which applies + syncs the visual pose), Esc to cancel. Each option shows a
 * code-drawn pictogram of the pose plus its label.
 */
public class PoseWheelScreen extends Screen {

    private static final int DEADZONE = 34;
    private static final int RING_RADIUS = 92;
    private static final int BOX_W = 58;
    private static final int BOX_H = 50;

    private final List<ChameleonPose> options = new ArrayList<>();
    private ChameleonPose hovered = ChameleonPose.STAND;

    public PoseWheelScreen() {
        super(Component.translatable("screen.chameleon.pose_wheel"));
        for (ChameleonPose p : ChameleonPose.VALUES) {
            if (p.selectable() && ClientPoses.isAllowed(p)) {
                options.add(p);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x55000000); // light dim to read over the world

        int cx = this.width / 2;
        int cy = this.height / 2;
        int n = options.size();

        double dx = mouseX - cx;
        double dy = mouseY - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist < DEADZONE || n == 0) {
            hovered = ChameleonPose.STAND;
        } else {
            double step = (Math.PI * 2) / n;
            double start = -Math.PI / 2;
            double a = Math.atan2(dy, dx);
            int idx = Math.floorMod((int) Math.round((a - start) / step), n);
            hovered = options.get(idx);
        }

        g.drawCenteredString(this.font, this.title, cx, 18, 0xFFFFFFFF);

        drawOption(g, cx, cy, ChameleonPose.STAND, hovered == ChameleonPose.STAND);
        double step = (Math.PI * 2) / Math.max(1, n);
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + i * step;
            int px = cx + (int) Math.round(Math.cos(a) * RING_RADIUS);
            int py = cy + (int) Math.round(Math.sin(a) * RING_RADIUS);
            ChameleonPose p = options.get(i);
            drawOption(g, px, py, p, hovered == p);
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawOption(GuiGraphics g, int cx, int cy, ChameleonPose pose, boolean hot) {
        int x = cx - BOX_W / 2;
        int y = cy - BOX_H / 2;
        int bg = hot ? 0xFF2E7D32 : 0xCC202020;
        int border = hot ? 0xFFFFFFFF : 0xFF000000;
        g.fill(x - 1, y - 1, x + BOX_W + 1, y + BOX_H + 1, border);
        g.fill(x, y, x + BOX_W, y + BOX_H, bg);

        int icon = hot ? 0xFFFFFFFF : 0xFFB0B0B0;
        drawPictogram(g, cx, y + 17, pose, icon);

        String label = Component.translatable(pose.key()).getString();
        g.drawCenteredString(this.font, label, cx, y + BOX_H - 11, hot ? 0xFFFFFFFF : 0xFFCCCCCC);
    }

    /** Minimal stick-figure silhouette per pose, centred on (cx, cy), drawn with rects. */
    private void drawPictogram(GuiGraphics g, int cx, int cy, ChameleonPose pose, int c) {
        switch (pose) {
            case STAND -> {
                g.fill(cx - 2, cy - 10, cx + 2, cy - 6, c);  // head
                g.fill(cx - 2, cy - 6, cx + 2, cy + 3, c);   // body
                g.fill(cx - 3, cy + 3, cx - 1, cy + 10, c);  // left leg
                g.fill(cx + 1, cy + 3, cx + 3, cy + 10, c);  // right leg
            }
            case CROUCH -> {
                g.fill(cx - 2, cy - 5, cx + 2, cy - 1, c);   // head (lowered)
                g.fill(cx - 3, cy - 1, cx + 3, cy + 4, c);   // squat body
                g.fill(cx - 5, cy + 4, cx - 1, cy + 8, c);   // bent left leg
                g.fill(cx + 1, cy + 4, cx + 5, cy + 8, c);   // bent right leg
            }
            case SIT -> {
                g.fill(cx - 6, cy - 8, cx - 2, cy - 4, c);   // head
                g.fill(cx - 6, cy - 4, cx - 2, cy + 4, c);   // back
                g.fill(cx - 6, cy + 4, cx + 7, cy + 8, c);   // seat / legs forward
            }
            case CRAWL -> {
                g.fill(cx - 11, cy - 3, cx - 7, cy + 1, c);  // head (raised, looking ahead)
                g.fill(cx - 7, cy + 1, cx + 9, cy + 5, c);   // prone body
                g.fill(cx - 6, cy - 3, cx - 2, cy + 1, c);   // arm reaching forward
            }
            case LIE -> {
                g.fill(cx - 11, cy - 1, cx - 7, cy + 3, c);  // head (level)
                g.fill(cx - 7, cy - 1, cx + 10, cy + 3, c);  // flat body
            }
            default -> {
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ClientPoses.choose(hovered);
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
