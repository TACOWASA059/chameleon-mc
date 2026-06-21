package com.github.tacowasa059.chameleon;

import com.github.tacowasa059.chameleon.platform.Services;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Tiny cross-loader config (a {@code config/chameleon.properties} file). Loaded once
 * at startup ({@link ChameleonMod#init()}); written with defaults if missing.
 *
 * <p>Holds the two debounce intervals (ticks; 20 ticks = 1 second) and the set of
 * poses the server lets players use. Can be changed at runtime with {@code /chameleon}.
 */
public final class ChameleonConfig {

    /** Client: minimum ticks between skin uploads while painting (debounce). */
    public static int sendIntervalTicks = 10;   // ~0.5s
    /** Server: ticks between batched skin saves to disk. */
    public static int saveIntervalTicks = 100;  // ~5s
    /** Server: which selectable poses players may use (bitmask of ChameleonPose). */
    public static int allowedPoseMask = ChameleonPose.defaultMask();

    private static boolean loaded;

    private ChameleonConfig() {
    }

    private static Path path() {
        return Services.PLATFORM.getConfigDir().resolve("chameleon.properties");
    }

    /** Load once at startup (writes defaults if the file is missing). */
    public static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        readFrom(path());
    }

    /** Re-read the file (for the {@code /chameleon reload} command). */
    public static void reload() {
        readFrom(path());
    }

    private static void readFrom(Path p) {
        try {
            if (!Files.exists(p)) {
                save(); // first run: write the defaults
                return;
            }
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(p)) {
                props.load(in);
            }
            sendIntervalTicks = readInt(props, "sendIntervalTicks", sendIntervalTicks);
            saveIntervalTicks = readInt(props, "saveIntervalTicks", saveIntervalTicks);
            allowedPoseMask = readPoseMask(props, "allowedPoses", allowedPoseMask);
        } catch (Exception e) {
            Constants.LOG.warn("Failed to load chameleon config, using current values: {}", e.toString());
        }
    }

    /** Set the server save interval and persist (used by the command). */
    public static void setSaveInterval(int ticks) {
        saveIntervalTicks = Math.max(1, ticks);
        save();
    }

    /** Set the client send interval and persist (used by the command). */
    public static void setSendInterval(int ticks) {
        sendIntervalTicks = Math.max(1, ticks);
        save();
    }

    /** Allow or disallow a single selectable pose and persist (used by the command). */
    public static void setPoseAllowed(ChameleonPose pose, boolean allowed) {
        if (!pose.selectable()) {
            return;
        }
        if (allowed) {
            allowedPoseMask |= pose.bit();
        } else {
            allowedPoseMask &= ~pose.bit();
        }
        save();
    }

    /** The currently-allowed poses as a comma list (for the command's readout). */
    public static String allowedPosesString() {
        return formatPoseMask(allowedPoseMask);
    }

    private static int readInt(Properties props, String key, int def) {
        String v = props.getProperty(key);
        if (v != null) {
            try {
                return Math.max(1, Integer.parseInt(v.trim())); // never below 1 tick
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return def;
    }

    private static int readPoseMask(Properties props, String key, int def) {
        String v = props.getProperty(key);
        if (v == null) {
            return def;
        }
        int m = 0;
        for (String part : v.split(",")) {
            if (part.trim().isEmpty()) {
                continue;
            }
            ChameleonPose p = ChameleonPose.byName(part);
            if (p != null && p.selectable()) {
                m |= p.bit();
            }
        }
        return m;
    }

    private static String formatPoseMask(int mask) {
        StringBuilder sb = new StringBuilder();
        for (ChameleonPose p : ChameleonPose.VALUES) {
            if (p.selectable() && (mask & p.bit()) != 0) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(p.name().toLowerCase(Locale.ROOT));
            }
        }
        return sb.toString();
    }

    public static void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());
            Properties props = new Properties();
            props.setProperty("sendIntervalTicks", Integer.toString(sendIntervalTicks));
            props.setProperty("saveIntervalTicks", Integer.toString(saveIntervalTicks));
            props.setProperty("allowedPoses", formatPoseMask(allowedPoseMask));
            try (OutputStream out = Files.newOutputStream(p)) {
                props.store(out, "Chameleon config. Intervals in ticks (20 ticks = 1 second, minimum 1).\n"
                        + "sendIntervalTicks: client - min ticks between skin uploads while painting.\n"
                        + "saveIntervalTicks: server - ticks between batched skin saves to disk.\n"
                        + "allowedPoses: comma list of poses players may use (crouch,crawl,sit,lie).");
            }
        } catch (Exception e) {
            Constants.LOG.warn("Failed to write chameleon config: {}", e.toString());
        }
    }
}
