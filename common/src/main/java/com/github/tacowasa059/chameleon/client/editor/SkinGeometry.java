package com.github.tacowasa059.chameleon.client.editor;

import com.github.tacowasa059.chameleon.skin.ChameleonSkin;

import java.util.ArrayList;
import java.util.List;

/**
 * Humanoid model for the skin editor, ported 1:1 from vanilla
 * {@code net.minecraft.client.model.geom.ModelPart.Cube} / {@code PlayerModel}.
 *
 * <p>Box positions, texture offsets and the per-face UV unwrap exactly match
 * vanilla, so a texel painted in the editor lands on the same place as the real
 * in-world player render. Rendering and click-picking share the projection here,
 * so painting is WYSIWYG.
 *
 * <p>Coordinate convention matches vanilla model space: +X right, +Y DOWN,
 * +Z back (the face/front is at -Z). 1 unit = 1 texel.
 */
public final class SkinGeometry {

    public static final int PART_RIGHT_LEG = 0;
    public static final int PART_LEFT_LEG = 1;
    public static final int PART_BODY = 2;
    public static final int PART_RIGHT_ARM = 3;
    public static final int PART_LEFT_ARM = 4;
    public static final int PART_HEAD = 5;

    /** Vertical center of the model (head top y=-8, feet y=24). */
    public static final float PIVOT_Y = 8f;

    public static final class Face {
        public final float[][] c = new float[4][3];  // model-space corners
        public final float[][] uv = new float[4][2]; // texel coords (0..64)
        public final float[] n = new float[3];       // outward normal
        public final boolean overlay;
        public final int part;

        Face(boolean overlay, int part) {
            this.overlay = overlay;
            this.part = part;
        }
    }

    public static final class Pick {
        public final int tx;
        public final int ty;
        public final float[] point;
        public final Face face;

        Pick(int tx, int ty, float[] point, Face face) {
            this.tx = tx;
            this.ty = ty;
            this.point = point;
            this.face = face;
        }
    }

    private final boolean slim;
    private final List<Face> faces = new ArrayList<>();

    public SkinGeometry(boolean slim) {
        this.slim = slim;
        int aw = slim ? 3 : 4;        // arm width
        float ay = slim ? 0.5f : 0f;  // arm vertical offset (slim sits 0.5 lower)

        // Right arm sits just left of the body: world x = -4-aw .. -4 (vanilla
        // rightArm setPos(-5) + cube; left arm world x = 4 .. 4+aw).
        float rax = -4 - aw;

        // ---- base layer (texU, texV, originX,Y,Z, dimX,Y,Z, grow, part) ----
        addCube(0, 0, -4, -8, -4, 8, 8, 8, 0f, PART_HEAD, false);
        addCube(16, 16, -4, 0, -2, 8, 12, 4, 0f, PART_BODY, false);
        addCube(40, 16, rax, ay, -2, aw, 12, 4, 0f, PART_RIGHT_ARM, false);
        addCube(32, 48, 4, ay, -2, aw, 12, 4, 0f, PART_LEFT_ARM, false);
        addCube(0, 16, -3.9f, 12, -2, 4, 12, 4, 0f, PART_RIGHT_LEG, false);
        addCube(16, 48, -0.1f, 12, -2, 4, 12, 4, 0f, PART_LEFT_LEG, false);

        // ---- overlay (2nd) layer ----
        addCube(32, 0, -4, -8, -4, 8, 8, 8, 0.5f, PART_HEAD, true);
        addCube(16, 32, -4, 0, -2, 8, 12, 4, 0.25f, PART_BODY, true);
        addCube(40, 32, rax, ay, -2, aw, 12, 4, 0.25f, PART_RIGHT_ARM, true);
        addCube(48, 48, 4, ay, -2, aw, 12, 4, 0.25f, PART_LEFT_ARM, true);
        addCube(0, 32, -3.9f, 12, -2, 4, 12, 4, 0.25f, PART_RIGHT_LEG, true);
        addCube(0, 48, -0.1f, 12, -2, 4, 12, 4, 0.25f, PART_LEFT_LEG, true);
    }

    public boolean isSlim() {
        return slim;
    }

    public List<Face> faces() {
        return faces;
    }

    /**
     * Port of vanilla ModelPart.Cube + Polygon (mirror=false). Generates the 6
     * faces with the exact vanilla UV unwrap.
     */
    private void addCube(int tu, int tv, float ox, float oy, float oz,
                         int dx, int dy, int dz, float grow, int part, boolean overlay) {
        float x0 = ox - grow, y0 = oy - grow, z0 = oz - grow;
        float x1 = ox + dx + grow, y1 = oy + dy + grow, z1 = oz + dz + grow;

        // 8 corners (vanilla naming)
        float[] p7 = {x0, y0, z0};
        float[] p = {x1, y0, z0};
        float[] p1 = {x1, y1, z0};
        float[] p2 = {x0, y1, z0};
        float[] p3 = {x0, y0, z1};
        float[] p4 = {x1, y0, z1};
        float[] p5 = {x1, y1, z1};
        float[] p6 = {x0, y1, z1};

        // UV edges (use texel dims, not grown)
        float u4 = tu, u5 = tu + dz, u6 = tu + dz + dx, u7 = tu + dz + dx + dx;
        float u8 = tu + dz + dx + dz, u9 = tu + dz + dx + dz + dx;
        float v10 = tv, v11 = tv + dz, v12 = tv + dz + dy;

        // direction.step() normals
        addFace(part, overlay, p4, p3, p7, p, u5, v10, u6, v11, 0, -1, 0);   // DOWN
        addFace(part, overlay, p1, p2, p6, p5, u6, v11, u7, v10, 0, 1, 0);   // UP
        addFace(part, overlay, p7, p3, p6, p2, u4, v11, u5, v12, -1, 0, 0);  // WEST (-X)
        addFace(part, overlay, p, p7, p2, p1, u5, v11, u6, v12, 0, 0, -1);   // NORTH (-Z, front)
        addFace(part, overlay, p4, p, p1, p5, u6, v11, u8, v12, 1, 0, 0);    // EAST (+X)
        addFace(part, overlay, p3, p4, p5, p6, u8, v11, u9, v12, 0, 0, 1);   // SOUTH (+Z, back)
    }

    /** Polygon UV assignment: v0=(u2,v1) v1=(u1,v1) v2=(u1,v2) v3=(u2,v2). */
    private void addFace(int part, boolean overlay,
                         float[] a, float[] b, float[] c, float[] d,
                         float u1, float v1, float u2, float v2,
                         float nx, float ny, float nz) {
        Face f = new Face(overlay, part);
        copy(f.c[0], a);
        copy(f.c[1], b);
        copy(f.c[2], c);
        copy(f.c[3], d);
        f.uv[0][0] = u2; f.uv[0][1] = v1;
        f.uv[1][0] = u1; f.uv[1][1] = v1;
        f.uv[2][0] = u1; f.uv[2][1] = v2;
        f.uv[3][0] = u2; f.uv[3][1] = v2;
        f.n[0] = nx; f.n[1] = ny; f.n[2] = nz;
        faces.add(f);
    }

    private static void copy(float[] dst, float[] src) {
        dst[0] = src[0];
        dst[1] = src[1];
        dst[2] = src[2];
    }

    // ---- pose (editor preview only): per-part joint rotation ----------------

    /** Joint pivot per part id (model space), matching vanilla limb attach points. */
    public static final float[][] PIVOT = {
            {-1.9f, 12f, 0f}, // RIGHT_LEG
            {1.9f, 12f, 0f},  // LEFT_LEG
            {0f, 0f, 0f},     // BODY
            {-5f, 2f, 0f},    // RIGHT_ARM
            {5f, 2f, 0f},     // LEFT_ARM
            {0f, 0f, 0f},     // HEAD
    };

    /**
     * Apply a part's joint rotation (radians, order Z then X then Y) around its
     * pivot to a rest-space corner. pose may be null (= rest). Used only for the
     * editor preview; paint coordinates stay in rest space.
     */
    public static float[] posed(int part, float[] c, float[][] pose) {
        if (pose == null) {
            return c;
        }
        float[] a = pose[part];
        if (a == null || (a[0] == 0f && a[1] == 0f && a[2] == 0f)) {
            return c;
        }
        float[] pv = PIVOT[part];
        float[] r = rotXYZ(c[0] - pv[0], c[1] - pv[1], c[2] - pv[2], a);
        return new float[]{r[0] + pv[0], r[1] + pv[1], r[2] + pv[2]};
    }

    /** Same rotation as {@link #posed} but for a direction (no pivot translation). */
    public static float[] posedDir(int part, float[] n, float[][] pose) {
        if (pose == null) {
            return n;
        }
        float[] a = pose[part];
        if (a == null || (a[0] == 0f && a[1] == 0f && a[2] == 0f)) {
            return n;
        }
        return rotXYZ(n[0], n[1], n[2], a);
    }

    private static float[] rotXYZ(float x, float y, float z, float[] a) {
        float cz = (float) Math.cos(a[2]), sz = (float) Math.sin(a[2]);
        float x1 = x * cz - y * sz, y1 = x * sz + y * cz, z1 = z;
        float cx = (float) Math.cos(a[0]), sx = (float) Math.sin(a[0]);
        float y2 = y1 * cx - z1 * sx, z2 = y1 * sx + z1 * cx, x2 = x1;
        float cy = (float) Math.cos(a[1]), sy = (float) Math.sin(a[1]);
        float x3 = x2 * cy + z2 * sy, z3 = -x2 * sy + z2 * cy, y3 = y2;
        return new float[]{x3, y3, z3};
    }

    // ---- shared orthographic projection (viewer along -Z, so front is near) --

    public static float[] rotate(float x, float y, float z, float yaw, float pitch) {
        y = y - PIVOT_Y;
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float x1 = x * cy + z * sy;
        float z1 = -x * sy + z * cy;
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float y2 = y * cp - z1 * sp;
        float z2 = y * sp + z1 * cp;
        return new float[]{x1, y2, z2};
    }

    public static float[] rotateDir(float x, float y, float z, float yaw, float pitch) {
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float x1 = x * cy + z * sy;
        float z1 = -x * sy + z * cy;
        float cp = (float) Math.cos(pitch), sp = (float) Math.sin(pitch);
        float y2 = y * cp - z1 * sp;
        float z2 = y * sp + z1 * cp;
        return new float[]{x1, y2, z2};
    }

    /**
     * Project a rotated point. Screen Y grows down (matches +Y-down model), and
     * depth is -z so that the front face (-Z) is nearest the viewer.
     * Returns {screenX, screenY, depth} (larger depth = nearer).
     */
    public static float[] project(float[] r, float zoom, float cx, float cy) {
        return new float[]{cx + zoom * r[0], cy + zoom * r[1], -r[2]};
    }

    /**
     * A face is shown when its part's layer view allows it: {@code showBase[part]}
     * gates base faces, {@code showOver[part]} gates overlay (2nd-layer) faces.
     */
    public static boolean shown(Face f, boolean[] showBase, boolean[] showOver) {
        return f.overlay ? showOver[f.part] : showBase[f.part];
    }

    public Pick pick(double mouseX, double mouseY, float yaw, float pitch, float zoom, float cx, float cy,
                     boolean[] showBase, boolean[] showOver, float[][] pose) {
        float bestDepth = -Float.MAX_VALUE;
        Pick best = null;
        for (Face f : faces) {
            if (!shown(f, showBase, showOver)) {
                continue;
            }
            float[][] s = new float[4][];
            for (int i = 0; i < 4; i++) {
                float[] pc = posed(f.part, f.c[i], pose);
                s[i] = project(rotate(pc[0], pc[1], pc[2], yaw, pitch), zoom, cx, cy);
            }
            int t1 = 1, t2 = 2;
            float[] bary = baryHit(mouseX, mouseY, s, 0, 1, 2);
            if (bary == null) {
                bary = baryHit(mouseX, mouseY, s, 0, 2, 3);
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
                float[] mp = new float[3];
                for (int k = 0; k < 3; k++) {
                    mp[k] = bary[0] * f.c[0][k] + bary[1] * f.c[t1][k] + bary[2] * f.c[t2][k];
                }
                best = new Pick(clamp((int) Math.floor(pu)), clamp((int) Math.floor(pv)), mp, f);
            }
        }
        return best;
    }

    private static boolean[] baseMaskWide;
    private static boolean[] baseMaskSlim;

    /**
     * A mask of every texel belonging to the BASE (inner) layer for this model
     * width. Used to render the inner layer fully opaque without touching the
     * stored skin data. Cached per slim/wide.
     */
    public static boolean[] baseMask(boolean slim) {
        if (slim) {
            if (baseMaskSlim == null) {
                baseMaskSlim = buildBaseMask(true);
            }
            return baseMaskSlim;
        }
        if (baseMaskWide == null) {
            baseMaskWide = buildBaseMask(false);
        }
        return baseMaskWide;
    }

    private static boolean[] buildBaseMask(boolean slim) {
        boolean[] m = new boolean[ChameleonSkin.SIZE * ChameleonSkin.SIZE];
        for (Face f : new SkinGeometry(slim).faces()) {
            if (f.overlay) {
                continue;
            }
            int[] r = rectOf(f);
            for (int y = Math.max(0, r[1]); y < Math.min(ChameleonSkin.SIZE, r[3]); y++) {
                for (int x = Math.max(0, r[0]); x < Math.min(ChameleonSkin.SIZE, r[2]); x++) {
                    m[y * ChameleonSkin.SIZE + x] = true;
                }
            }
        }
        return m;
    }

    public static int[] rectOf(Face f) {
        int u0 = (int) Math.floor(Math.min(Math.min(f.uv[0][0], f.uv[1][0]), Math.min(f.uv[2][0], f.uv[3][0])));
        int u1 = (int) Math.ceil(Math.max(Math.max(f.uv[0][0], f.uv[1][0]), Math.max(f.uv[2][0], f.uv[3][0])));
        int v0 = (int) Math.floor(Math.min(Math.min(f.uv[0][1], f.uv[1][1]), Math.min(f.uv[2][1], f.uv[3][1])));
        int v1 = (int) Math.ceil(Math.max(Math.max(f.uv[0][1], f.uv[1][1]), Math.max(f.uv[2][1], f.uv[3][1])));
        return new int[]{u0, v0, u1, v1};
    }

    public Face faceAtTexel(int tx, int ty, boolean[] showBase, boolean[] showOver) {
        Face overlayHit = null;
        for (Face f : faces) {
            if (!shown(f, showBase, showOver)) {
                continue;
            }
            int[] r = rectOf(f);
            if (tx >= r[0] && tx < r[2] && ty >= r[1] && ty < r[3]) {
                if (f.overlay) {
                    overlayHit = f;
                } else {
                    return f;
                }
            }
        }
        return overlayHit;
    }

    public Pick texelAtPoint(float[] p, boolean[] showBase, boolean[] showOver) {
        for (Face f : faces) {
            if (!shown(f, showBase, showOver)) {
                continue;
            }
            float[] e1 = sub(f.c[1], f.c[0]);
            float[] e2 = sub(f.c[3], f.c[0]);
            float[] dp = sub(p, f.c[0]);
            float d1 = dot(e1, e1), d2 = dot(e2, e2);
            if (d1 < 1e-6f || d2 < 1e-6f) {
                continue;
            }
            float a = dot(dp, e1) / d1;
            float b = dot(dp, e2) / d2;
            if (a < -0.001f || a > 1.001f || b < -0.001f || b > 1.001f) {
                continue;
            }
            float[] recon = {
                    f.c[0][0] + a * e1[0] + b * e2[0],
                    f.c[0][1] + a * e1[1] + b * e2[1],
                    f.c[0][2] + a * e1[2] + b * e2[2]};
            float dist2 = 0;
            for (int k = 0; k < 3; k++) {
                float dd = recon[k] - p[k];
                dist2 += dd * dd;
            }
            if (dist2 > 0.25f) {
                continue;
            }
            a = Math.max(0, Math.min(1, a));
            b = Math.max(0, Math.min(1, b));
            float pu = (1 - a) * (1 - b) * f.uv[0][0] + a * (1 - b) * f.uv[1][0] + a * b * f.uv[2][0] + (1 - a) * b * f.uv[3][0];
            float pv = (1 - a) * (1 - b) * f.uv[0][1] + a * (1 - b) * f.uv[1][1] + a * b * f.uv[2][1] + (1 - a) * b * f.uv[3][1];
            return new Pick(clamp((int) Math.floor(pu)), clamp((int) Math.floor(pv)), p, f);
        }
        return null;
    }

    /**
     * Model-space point at the centre of texel (tx,ty) on face {@code f}. Each
     * face maps to an axis-aligned UV rectangle, so this inverts the bilinear UV
     * map and bilerps the corners. Used to mirror 2D-map paint across x=0.
     */
    public static float[] pointAtTexel(Face f, int tx, int ty) {
        return pointAtUV(f, tx + 0.5f, ty + 0.5f);
    }

    /**
     * Model-space point at a continuous texel coordinate (u,v) on face {@code f}.
     * Like {@link #pointAtTexel} but lets callers ask for a texel's corners
     * (integer u,v) as well as its centre, e.g. to outline the exact texel cell.
     */
    public static float[] pointAtUV(Face f, float u, float v) {
        float u2 = f.uv[0][0], u1 = f.uv[1][0];
        float v1 = f.uv[0][1], v2 = f.uv[2][1];
        float a = Math.abs(u2 - u1) < 1e-4f ? 0f : (u2 - u) / (u2 - u1);
        float b = Math.abs(v2 - v1) < 1e-4f ? 0f : (v - v1) / (v2 - v1);
        a = Math.max(0f, Math.min(1f, a));
        b = Math.max(0f, Math.min(1f, b));
        float[] p = new float[3];
        for (int k = 0; k < 3; k++) {
            p[k] = (1 - a) * (1 - b) * f.c[0][k] + a * (1 - b) * f.c[1][k]
                    + a * b * f.c[2][k] + (1 - a) * b * f.c[3][k];
        }
        return p;
    }

    public void clearPart(int[] pixels, int part, boolean includeOverlay) {
        for (Face f : faces) {
            if (f.part != part || (f.overlay && !includeOverlay)) {
                continue;
            }
            int[] r = rectOf(f);
            for (int y = r[1]; y < r[3]; y++) {
                for (int x = r[0]; x < r[2]; x++) {
                    if (x >= 0 && x < ChameleonSkin.SIZE && y >= 0 && y < ChameleonSkin.SIZE) {
                        pixels[y * ChameleonSkin.SIZE + x] = 0x00000000;
                    }
                }
            }
        }
    }

    private static int clamp(int t) {
        return Math.max(0, Math.min(ChameleonSkin.SIZE - 1, t));
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
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
