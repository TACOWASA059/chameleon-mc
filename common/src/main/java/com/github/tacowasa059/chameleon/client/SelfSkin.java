package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.Constants;
import com.github.tacowasa059.chameleon.skin.ChameleonSkin;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The local player's own painted skin, stored client-side so editing works
 * without any server and the skin follows the player to every world. It is
 * applied locally on join and (re)sent to whichever server the player connects
 * to, so it is never lost just because a server doesn't know it yet.
 */
public final class SelfSkin {

    private SelfSkin() {
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("chameleon").resolve("self.skin");
    }

    /** Persist the player's own skin to disk (survives restarts, follows servers). */
    public static void save(ChameleonSkin skin) {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.write(p, skin.toBytes());
        } catch (Exception e) {
            Constants.LOG.warn("Failed to save self skin: {}", e.toString());
        }
    }

    /** Forget the saved skin (player reverted to default), so it isn't re-applied. */
    public static void delete() {
        try {
            Files.deleteIfExists(path());
        } catch (Exception e) {
            Constants.LOG.warn("Failed to delete self skin: {}", e.toString());
        }
    }

    /** Load the player's own saved skin, or null if none / unreadable. */
    public static ChameleonSkin load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                return ChameleonSkin.fromBytes(Files.readAllBytes(p));
            }
        } catch (Exception e) {
            Constants.LOG.warn("Failed to load self skin: {}", e.toString());
        }
        return null;
    }

    /**
     * Make the saved skin available locally (so the editor opens with it, and the
     * in-world model can show it if the server has the mod). No network.
     */
    public static void applyLocal() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        ChameleonSkin skin = load();
        if (skin != null) {
            ClientSkins.setLocal(mc.player.getUUID(), skin);
        }
    }

    /** Upload the saved skin to the (mod-having) server so others see it. */
    public static void upload() {
        ChameleonSkin skin = load();
        if (skin != null) {
            ClientNetwork.sendSkin(skin.toBytes());
        }
    }
}
