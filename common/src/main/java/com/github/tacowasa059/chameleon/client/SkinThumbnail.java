package com.github.tacowasa059.chameleon.client;

import com.github.tacowasa059.chameleon.client.editor.SkinGeometry;
import com.github.tacowasa059.chameleon.skin.ChameleonSkin;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.Arrays;
import java.util.List;

/**
 * Renders a small front-facing 3D preview of a skin into a GUI rect, so the
 * bookmark list shows each saved skin's actual 3D appearance.
 */
public final class SkinThumbnail {

    private static final SkinGeometry GEO = new SkinGeometry(false);
    private static final SkinGeometry GEO_SLIM = new SkinGeometry(true);

    private SkinThumbnail() {
    }

    public static void render(GuiGraphics g, ChameleonSkin skin, ResourceLocation tex, int x, int y, int size) {
        if (skin == null || tex == null) {
            return;
        }
        SkinGeometry geo = skin.slim() ? GEO_SLIM : GEO;
        List<SkinGeometry.Face> faces = geo.faces();
        int n = faces.size();

        float cx = x + size / 2f, cy = y + size / 2f;
        float zoom = size * 0.78f / 32f; // model is ~32 texels tall
        float yaw = -0.5f, pitch = 0.12f;
        float[] light = norm(-0.3f, -0.4f, -1.0f);

        float[][][] s = new float[n][][];
        int[] shade = new int[n];
        float[] depth = new float[n];
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            SkinGeometry.Face f = faces.get(i);
            float[][] sc = new float[4][];
            float d = 0;
            for (int k = 0; k < 4; k++) {
                float[] r = SkinGeometry.rotate(f.c[k][0], f.c[k][1], f.c[k][2], yaw, pitch);
                sc[k] = SkinGeometry.project(r, zoom, cx, cy);
                d += sc[k][2];
            }
            s[i] = sc;
            depth[i] = d / 4f;
            order[i] = i;
            float[] rn = SkinGeometry.rotateDir(f.n[0], f.n[1], f.n[2], yaw, pitch);
            float dd = Math.max(0f, rn[0] * light[0] + rn[1] * light[1] + rn[2] * light[2]);
            shade[i] = Math.round((0.6f + 0.4f * dd) * 255f);
        }
        Arrays.sort(order, (a, b) -> Float.compare(depth[a], depth[b]));

        g.enableScissor(x, y, x + size, y + size);
        Matrix4f mat = g.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, tex);
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int oi : order) {
            SkinGeometry.Face f = faces.get(oi);
            int sh = shade[oi];
            float[][] sc = s[oi];
            for (int k = 0; k < 4; k++) {
                bb.vertex(mat, sc[k][0], sc[k][1], 0f)
                        .uv(f.uv[k][0] / (float) ChameleonSkin.SIZE, f.uv[k][1] / (float) ChameleonSkin.SIZE)
                        .color(sh, sh, sh, 255)
                        .endVertex();
            }
        }
        Tesselator.getInstance().end();
        RenderSystem.enableCull();
        g.disableScissor();
    }

    private static float[] norm(float x, float y, float z) {
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        return new float[]{x / l, y / l, z / l};
    }
}
