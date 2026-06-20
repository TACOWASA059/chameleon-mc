package com.github.tacowasa059.chameleon.skin;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * A custom 64x64 player skin stored as ARGB pixels (0xAARRGGBB).
 * The wire format is intentionally simple and self-validating so the server can
 * relay it without decoding pixels itself.
 */
public final class ChameleonSkin {

    public static final int SIZE = 64;
    public static final int VERSION = 2;
    /** version(1) + slim(1) + width(2) + height(2) + SIZE*SIZE*4 */
    public static final int BYTES = 1 + 1 + 2 + 2 + SIZE * SIZE * 4;

    private final int[] pixels; // ARGB, length SIZE*SIZE
    private final boolean slim; // true = Alex (3px arms), false = Steve

    public ChameleonSkin(int[] pixels) {
        this(pixels, false);
    }

    public ChameleonSkin(int[] pixels, boolean slim) {
        if (pixels.length != SIZE * SIZE) {
            throw new IllegalArgumentException("Expected " + (SIZE * SIZE) + " pixels, got " + pixels.length);
        }
        this.pixels = pixels;
        this.slim = slim;
    }

    /** Whether this skin should render with the slim (Alex) arm model. */
    public boolean slim() {
        return slim;
    }

    public static ChameleonSkin blank() {
        int[] p = new int[SIZE * SIZE];
        Arrays.fill(p, 0x00000000); // fully transparent canvas (RGBA 0,0,0,0)
        return new ChameleonSkin(p);
    }

    public int get(int x, int y) {
        return pixels[y * SIZE + x];
    }

    public void set(int x, int y, int argb) {
        pixels[y * SIZE + x] = argb;
    }

    /** Backing array (ARGB). Mutating it mutates the skin. */
    public int[] raw() {
        return pixels;
    }

    public byte[] toBytes() {
        ByteBuffer b = ByteBuffer.allocate(BYTES);
        b.put((byte) VERSION);
        b.put((byte) (slim ? 1 : 0));
        b.putShort((short) SIZE);
        b.putShort((short) SIZE);
        for (int p : pixels) {
            b.putInt(p);
        }
        return b.array();
    }

    public static ChameleonSkin fromBytes(byte[] data) {
        if (data == null || data.length != BYTES) {
            throw new IllegalArgumentException("Bad skin length: " + (data == null ? -1 : data.length));
        }
        ByteBuffer b = ByteBuffer.wrap(data);
        int v = b.get() & 0xFF;
        boolean slim = (b.get() & 0xFF) != 0;
        int w = b.getShort() & 0xFFFF;
        int h = b.getShort() & 0xFFFF;
        if (v != VERSION || w != SIZE || h != SIZE) {
            throw new IllegalArgumentException("Unsupported skin v" + v + " " + w + "x" + h);
        }
        int[] p = new int[SIZE * SIZE];
        for (int i = 0; i < p.length; i++) {
            p[i] = b.getInt();
        }
        return new ChameleonSkin(p, slim);
    }
}
