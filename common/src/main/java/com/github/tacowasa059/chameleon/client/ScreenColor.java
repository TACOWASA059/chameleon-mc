package com.github.tacowasa059.chameleon.client;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;

/**
 * Reads the actual on-screen colour under the cursor from the main framebuffer.
 * This lets the eyedropper sample anything that is rendered -- the world behind
 * the player, a block you want to blend into, the 3D preview -- not just the
 * skin's own pixels.
 *
 * <p>Must be called on the render thread WHILE a screen is rendering (the main
 * render target is bound then), so the read sees the composited scene.
 */
public final class ScreenColor {

    // 1x1 RGBA read; reused on the single render thread.
    private static final ByteBuffer PIXEL = BufferUtils.createByteBuffer(4);

    private ScreenColor() {
    }

    /**
     * Colour under a GUI-space point as opaque ARGB, or 0 if it is off-screen.
     * (x,y) are screen/GUI coordinates like those a Screen receives.
     */
    public static int readPixel(double guiX, double guiY) {
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        double scale = w.getGuiScale();
        int fbX = (int) Math.round(guiX * scale);
        int fbY = (int) Math.round(w.getHeight() - guiY * scale); // GL origin is bottom-left
        if (fbX < 0 || fbY < 0 || fbX >= w.getWidth() || fbY >= w.getHeight()) {
            return 0;
        }
        RenderSystem.assertOnRenderThread();
        PIXEL.clear();
        GL11.glReadPixels(fbX, fbY, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, PIXEL);
        int r = PIXEL.get(0) & 0xFF;
        int g = PIXEL.get(1) & 0xFF;
        int b = PIXEL.get(2) & 0xFF;
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
