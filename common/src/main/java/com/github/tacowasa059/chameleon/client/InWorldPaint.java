package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.client.editor.SkinGeometry;
import com.github.tacowasa059.chameleon.mixin.client.LightTextureAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.Arrays;

/**
 * State and math for the in-world direct paint mode: you paint on your own real
 * player entity as it stands in the world (chameleon-style WYSIWYG against the
 * real surroundings), instead of inside the editor panel.
 *
 * <p>While active the camera orbits the player and the cursor is free. Rather than
 * RECONSTRUCT the render transform (which can't cover poses like swimming/crawling),
 * picking projects the skin faces with the EXACT matrices the renderer used this
 * frame -- captured live: the model-root matrix (LivingEntityRendererMixin), the
 * projection (LevelRendererMixin) and each part's transform (PlayerModelMixin):
 *
 * <pre>
 *   clip = capturedProj * capturedRoot * translate(pos/16)
 *                       * rotationZYX(part) * ((corner - pivot) / 16)
 * </pre>
 *
 * Then a 2D barycentric hit-test on the projected quads (the editor's WYSIWYG trick,
 * in perspective). Because the matrices come straight from the render, the click
 * target matches the body in every pose.
 */
public final class InWorldPaint {

    private static boolean active;
    private static float yaw;    // orbit camera yaw   (degrees, MC convention)
    private static float pitch;  // orbit camera pitch (degrees, +down)
    private static float dist = 2.4f;
    private static CameraType prevCamera;

    // The player's live per-part transforms, captured every frame by PlayerModelMixin.
    // Indexed by SkinGeometry part id; each row is {x, y, z, xRot, yRot, zRot} exactly
    // as the rendered ModelPart had them, so the pick matches the body's real pose.
    private static final float[][] partPose = new float[6][6];
    private static boolean poseReady;

    private InWorldPaint() {
    }

    public static boolean isActive() {
        return active;
    }

    /** Whether the given entity is the local player (the only paint target). */
    public static boolean isSelf(Object entity) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && entity == mc.player;
    }

    /**
     * The exact per-channel lightmap multiplier (0..255 each) the local player's
     * body is currently lit with -- read straight from the live lightmap cell for
     * the player's (block, sky) light. The eyedropper divides the sampled colour
     * by this PER CHANNEL, so re-lighting the painted skin reproduces the sampled
     * colour in both brightness AND hue (block light is warm, sky cool; a single
     * scalar would fix brightness but skew the hue). Normal body lighting stays.
     */
    public static int[] bodyLightRGB() {
        int[] white = {255, 255, 255};
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return white;
        }
        try {
            BlockPos pos = BlockPos.containing(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
            int blockLight = mc.level.getBrightness(LightLayer.BLOCK, pos);
            int skyLight = mc.level.getBrightness(LightLayer.SKY, pos);
            NativeImage img = ((LightTextureAccessor) mc.gameRenderer.lightTexture()).getLightPixels();
            int abgr = img.getPixelRGBA(blockLight, skyLight); // lightmap is indexed (block, sky)
            int r = abgr & 0xFF;
            int g = (abgr >> 8) & 0xFF;
            int b = (abgr >> 16) & 0xFF;
            // floor so a near-black sample doesn't divide to absurd values
            return new int[]{Math.max(13, r), Math.max(13, g), Math.max(13, b)};
        } catch (Exception e) {
            return white;
        }
    }

    // ---- pose (written every frame by PlayerModelMixin) ---------------------

    public static void setPoseReady(boolean ready) {
        poseReady = ready;
    }

    /** Store one part's live transform (called every frame from the model render). */
    public static void capturePart(int part, float x, float y, float z,
                                   float xRot, float yRot, float zRot) {
        float[] p = partPose[part];
        p[0] = x;
        p[1] = y;
        p[2] = z;
        p[3] = xRot;
        p[4] = yRot;
        p[5] = zRot;
    }

    /** Enter paint mode: force third person and open the overlay screen. */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        yaw = Mth.wrapDegrees(mc.player.yBodyRot + 180f); // start facing the player's front
        pitch = 0f;
        dist = 2.4f;
        poseReady = false;     // recapture the live pose on the next render
        projReady = false;     // recapture the projection + model root next frame
        rootReady = false;
        active = true;
        prevCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK); // so the player model renders
        mc.setScreen(new InWorldPaintScreen());
    }

    /** Leave paint mode and restore the previous camera view. */
    public static void close() {
        active = false;
        Minecraft mc = Minecraft.getInstance();
        if (prevCamera != null) {
            mc.options.setCameraType(prevCamera);
            prevCamera = null;
        }
    }

    public static void addOrbit(double dx, double dy) {
        yaw += (float) dx * 0.4f;
        pitch += (float) dy * 0.4f;
        pitch = Mth.clamp(pitch, -89f, 89f);
    }

    public static void addZoom(double delta) {
        dist = Mth.clamp(dist - (float) delta * 0.3f, 1.2f, 6f);
    }

    public static float cameraYaw() {
        return yaw;
    }

    public static float cameraPitch() {
        return pitch;
    }

    /**
     * Orbit camera position (called from CameraMixin, which alone can set it).
     * Camera sits {@code dist} behind the look target along the view vector.
     */
    public static double[] cameraPosition(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 t = entityRenderPos(partialTick).add(0, mc.player.getBbHeight() * 0.6, 0);
        double yawR = Math.toRadians(yaw);
        double pitchR = Math.toRadians(pitch);
        double fx = -Math.sin(yawR) * Math.cos(pitchR);
        double fy = -Math.sin(pitchR);
        double fz = Math.cos(yawR) * Math.cos(pitchR);
        return new double[]{t.x - fx * dist, t.y - fy * dist, t.z - fz * dist};
    }

    // ---- picking / projection ----------------------------------------------

    /** A skin texel and the face it lies on, found under the cursor. */
    public static final class Hit {
        public final int tx;
        public final int ty;
        public final SkinGeometry.Face face;

        Hit(int tx, int ty, SkinGeometry.Face face) {
            this.tx = tx;
            this.ty = ty;
            this.face = face;
        }
    }

    // Captured straight from the renderer this frame, so the pick can't drift via
    // ANY transform we'd otherwise reconstruct: the projection, AND the whole body
    // model-root (camera view, entity position, body yaw, and the swim/crawl/crouch/
    // sleep tilts from setupRotations -- which reconstruction simply can't cover).
    private static Matrix4f capturedRoot; // model root in EYE space
    private static Matrix4f capturedProj; // projection
    private static boolean projReady;
    private static boolean rootReady;

    /** Projection matrix the world render used (from LevelRendererMixin). */
    public static void captureProjection(Matrix4f proj) {
        capturedProj = new Matrix4f(proj);
        projReady = true;
    }

    /** The body model-root matrix in eye space (from LivingEntityRendererMixin). */
    public static void captureModelRoot(Matrix4f root) {
        capturedRoot = new Matrix4f(root);
        rootReady = true;
    }

    private static boolean ready() {
        return projReady && rootReady;
    }

    /**
     * The part's matrix in EYE space: {@code capturedRoot * translate(pos/16) *
     * rotationZYX(zRot,yRot,xRot)} -- the same chain vanilla ModelPart applies on
     * top of the model root. capturedRoot already carries the view, entity position
     * and every body-level rotation, so nothing here is reconstructed.
     */
    private static Matrix4f partEye(int part) {
        float[] pv = SkinGeometry.PIVOT[part];
        float px, py, pz, xr, yr, zr;
        if (poseReady) {
            float[] pp = partPose[part];
            px = pp[0];
            py = pp[1];
            pz = pp[2];
            xr = pp[3];
            yr = pp[4];
            zr = pp[5];
        } else {
            px = pv[0];
            py = pv[1];
            pz = pv[2];
            xr = 0f;
            yr = 0f;
            zr = 0f;
        }
        return new Matrix4f(capturedRoot)
                .translate(px / 16f, py / 16f, pz / 16f)
                .rotate(new Quaternionf().rotationZYX(zr, yr, xr));
    }

    /** Project one rest-space corner of {@code part}, given that part's eye matrix. */
    private static float[] projectCorner(int part, float[] cornerPixel, Matrix4f partEye, int guiW, int guiH) {
        float[] pv = SkinGeometry.PIVOT[part];
        // Vertex relative to the part origin (vanilla cube coords), in block units.
        Vector4f v = new Vector4f(
                (cornerPixel[0] - pv[0]) / 16f,
                (cornerPixel[1] - pv[1]) / 16f,
                (cornerPixel[2] - pv[2]) / 16f, 1f);
        partEye.transform(v);      // -> eye space
        float eyeZ = v.z;          // camera looks down -z; nearer = larger (closer to 0)
        capturedProj.transform(v); // -> clip space
        if (v.w <= 1e-4f) {
            return null;           // behind the camera
        }
        return new float[]{(v.x / v.w * 0.5f + 0.5f) * guiW, (0.5f - 0.5f * v.y / v.w) * guiH, eyeZ};
    }

    /**
     * Project a rest-space point on {@code part} to the GUI-scaled screen. Returns
     * {screenX, screenY, eyeZ} (eyeZ: larger = nearer) or null if not captured yet /
     * behind the camera.
     */
    public static float[] projectPosed(int part, float[] restPixel, float partialTick, int guiW, int guiH) {
        if (!ready()) {
            return null;
        }
        return projectCorner(part, restPixel, partEye(part), guiW, guiH);
    }

    /** Entity position interpolated the way the renderer does (used for the camera target). */
    private static Vec3 entityRenderPos(float partialTick) {
        net.minecraft.client.player.LocalPlayer p = Minecraft.getInstance().player;
        return new Vec3(
                Mth.lerp(partialTick, p.xOld, p.getX()),
                Mth.lerp(partialTick, p.yOld, p.getY()),
                Mth.lerp(partialTick, p.zOld, p.getZ()));
    }

    /**
     * Pick the skin texel under the cursor on the in-world player model. Returns
     * null when the cursor isn't over the body.
     */
    public static Hit pick(SkinGeometry geo, double mx, double my, boolean overlay,
                           int guiW, int guiH, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !ready()) {
            return null;
        }
        Matrix4f[] parts = new Matrix4f[6];
        for (int p = 0; p < 6; p++) {
            parts[p] = partEye(p);
        }
        boolean[] showBase = new boolean[6];
        boolean[] showOver = new boolean[6];
        Arrays.fill(showBase, true);
        Arrays.fill(showOver, overlay);

        float bestDepth = -Float.MAX_VALUE;
        Hit best = null;
        for (SkinGeometry.Face f : geo.faces()) {
            if (!SkinGeometry.shown(f, showBase, showOver)) {
                continue;
            }
            float[][] s = new float[4][];
            boolean ok = true;
            for (int k = 0; k < 4; k++) {
                s[k] = projectCorner(f.part, f.c[k], parts[f.part], guiW, guiH);
                if (s[k] == null) {
                    ok = false;
                    break;
                }
            }
            if (!ok) {
                continue;
            }
            int t1 = 1, t2 = 2;
            float[] bary = baryHit(mx, my, s, 0, 1, 2);
            if (bary == null) {
                bary = baryHit(mx, my, s, 0, 2, 3);
                t1 = 2;
                t2 = 3;
            }
            if (bary == null) {
                continue;
            }
            float depth = bary[0] * s[0][2] + bary[1] * s[t1][2] + bary[2] * s[t2][2];
            if (depth > bestDepth) {
                bestDepth = depth;
                float pu = bary[0] * f.uv[0][0] + bary[1] * f.uv[t1][0] + bary[2] * f.uv[t2][0];
                float pv = bary[0] * f.uv[0][1] + bary[1] * f.uv[t1][1] + bary[2] * f.uv[t2][1];
                best = new Hit(clamp((int) Math.floor(pu)), clamp((int) Math.floor(pv)), f);
            }
        }
        return best;
    }

    private static int clamp(int t) {
        return Math.max(0, Math.min(com.github.tacowasa059.chameleon.skin.ChameleonSkin.SIZE - 1, t));
    }

    private static float[] baryHit(double px, double py, float[][] s, int i, int j, int k) {
        float ax = s[i][0], ay = s[i][1];
        float bx = s[j][0], by = s[j][1];
        float cx = s[k][0], cy = s[k][1];
        float d = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        if (Math.abs(d) < 1e-6f) {
            return null;
        }
        float l1 = ((by - cy) * ((float) px - cx) + (cx - bx) * ((float) py - cy)) / d;
        float l2 = ((cy - ay) * ((float) px - cx) + (ax - cx) * ((float) py - cy)) / d;
        float l3 = 1 - l1 - l2;
        if (l1 < -0.0001f || l2 < -0.0001f || l3 < -0.0001f) {
            return null;
        }
        return new float[]{l1, l2, l3};
    }
}
