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

    private static boolean wasInWorld = false;
    private static boolean uploadedThisServer = false;

    private ChameleonClient() {
    }

    /** Per-tick client hook (called by each loader): open-editor key + join apply. */
    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        ClientNetwork.refreshServerHasMod(); // keep the in-world gate current
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
    }

    public static void openEditor() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.setScreen(new SkinEditorScreen());
        }
    }
}
