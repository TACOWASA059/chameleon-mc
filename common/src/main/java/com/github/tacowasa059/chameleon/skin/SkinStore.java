package com.github.tacowasa059.chameleon.skin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side authoritative store of every player's painted skin (raw bytes).
 * In-memory only for now; persistence can be added later.
 */
public final class SkinStore {

    private static final Map<UUID, byte[]> SKINS = new ConcurrentHashMap<>();

    private SkinStore() {
    }

    public static void put(UUID id, byte[] data) {
        SKINS.put(id, data);
    }

    public static byte[] get(UUID id) {
        return SKINS.get(id);
    }

    public static Map<UUID, byte[]> all() {
        return SKINS;
    }

    public static void remove(UUID id) {
        SKINS.remove(id);
    }
}
