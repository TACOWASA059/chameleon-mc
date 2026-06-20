package com.github.tacowasa059.chameleon.skin;

import com.github.tacowasa059.chameleon.Constants;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Persists the server-side {@link SkinStore} to the world save so painted skins
 * survive a server (or single-player world) restart. One raw {@code <uuid>.skin}
 * file per player under {@code <world>/chameleon/skins/}.
 */
public final class SkinPersistence {

    private SkinPersistence() {
    }

    private static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("chameleon").resolve("skins");
    }

    /** Load every saved skin into the in-memory store. Call on server start. */
    public static void load(MinecraftServer server) {
        Path d = dir(server);
        if (!Files.isDirectory(d)) {
            return;
        }
        try (Stream<Path> files = Files.list(d)) {
            files.filter(p -> p.getFileName().toString().endsWith(".skin")).forEach(p -> {
                String fn = p.getFileName().toString();
                try {
                    UUID id = UUID.fromString(fn.substring(0, fn.length() - ".skin".length()));
                    byte[] data = Files.readAllBytes(p);
                    ChameleonSkin.fromBytes(data); // validate before trusting
                    SkinStore.put(id, data);
                } catch (Exception e) {
                    Constants.LOG.warn("Skipped bad saved skin {}: {}", fn, e.toString());
                }
            });
        } catch (IOException e) {
            Constants.LOG.warn("Failed to load saved skins: {}", e.toString());
        }
        Constants.LOG.info("Loaded {} saved chameleon skin(s)", SkinStore.all().size());
    }

    /** Remove a player's saved skin file (they reverted to their default skin). */
    public static void delete(MinecraftServer server, UUID id) {
        if (server == null) {
            return;
        }
        try {
            Files.deleteIfExists(dir(server).resolve(id.toString() + ".skin"));
        } catch (IOException e) {
            Constants.LOG.warn("Failed to delete saved skin for {}: {}", id, e.toString());
        }
    }

    /** Write one player's skin to disk. Call whenever the store changes. */
    public static void save(MinecraftServer server, UUID id, byte[] data) {
        if (server == null) {
            return;
        }
        try {
            Path d = dir(server);
            Files.createDirectories(d);
            Files.write(d.resolve(id.toString() + ".skin"), data);
        } catch (IOException e) {
            Constants.LOG.warn("Failed to save skin for {}: {}", id, e.toString());
        }
    }
}
