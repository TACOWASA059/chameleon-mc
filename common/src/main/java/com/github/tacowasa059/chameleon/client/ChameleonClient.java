package com.github.tacowasa059.chameleon.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Shared client state. The key mapping is created here and registered by each
 * loader's client initializer.
 */
public final class ChameleonClient {

    public static final String CATEGORY = "key.categories.chameleon";

    public static final KeyMapping OPEN_EDITOR = new KeyMapping(
            "key.chameleon.open_editor",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY);

    public static final KeyMapping OPEN_INWORLD_PAINT = new KeyMapping(
            "key.chameleon.inworld_paint",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            CATEGORY);

    // Used only inside the in-world paint screen (checked via matches there), but
    // registered so it shows up in Controls and can be rebound.
    public static final KeyMapping TOGGLE_GUIDE = new KeyMapping(
            "key.chameleon.toggle_guide",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    private static boolean wasInWorld = false;
    private static boolean uploadedThisServer = false;

    private ChameleonClient() {
    }

    /** Per-tick client hook (called by each loader): open-editor key + join apply. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientNetwork.refreshServerHasMod(); // keep the in-world gate current
        ClientNetwork.tick();                // flush debounced skin sends
        boolean inWorld = mc.player != null && mc.getConnection() != null;
        if (inWorld && !wasInWorld) {
            SelfSkin.applyLocal();      // editor & (if modded) in-world get the saved skin
            uploadedThisServer = false;
        }
        if (inWorld && ClientNetwork.serverHasMod() && !uploadedThisServer) {
            SelfSkin.upload();          // share with others once the modded server is ready
            uploadedThisServer = true;
        }
        wasInWorld = inWorld;

        while (OPEN_EDITOR.consumeClick()) {
            openEditor();
        }
        while (OPEN_INWORLD_PAINT.consumeClick()) {
            if (mc.player != null && mc.screen == null) {
                InWorldPaint.open();
            }
        }
    }

    public static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new SkinEditorScreen());
        }
    }
}
