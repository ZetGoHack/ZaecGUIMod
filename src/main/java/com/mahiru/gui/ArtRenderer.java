package com.mahiru.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/** Draws an art inside a menu panel; the same code paints real menus and the studio preview. */
public final class ArtRenderer {
    /**
     * @param ox,oy   panel origin in the current pose
     * @param scissor absolute gui rect {x0,y0,x1,y1} to clip to, or null
     */
    private static float absX, absY, absScale = 1f;

    /**
     * @param absX,absY absolute screen position of the panel origin (scissors ignore the pose), absScale pose scale
     */
    public static void draw(GuiGraphics g, Masks.Mask mask, Arts.Art a, Placement p, float alpha, int layer,
                            int ox, int oy, float absPanelX, float absPanelY, float scale, int[] scissor, float z, boolean flip) {
        absX = absPanelX; absY = absPanelY; absScale = scale;
        if (a == null || p == null || p.w <= 0 || p.h <= 0 || alpha <= 0f) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, z);
        if (scissor != null) g.enableScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
        try {
            RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, alpha);
            if ((layer == Settings.MODE_BEHIND || layer == Settings.MODE_THROUGH) && mask != null) {
                drawAroundSlots(g, mask, a, p, ox, oy, layer == Settings.MODE_THROUGH, flip);
            } else {
                drawArt(g, a, p, ox, oy, flip);
            }
        } finally {
            g.setColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
            if (scissor != null) g.disableScissor();
            g.pose().popPose();
        }
    }

    /** Art with the 18x18 slot cells cut out, so slots stay crisp and art shows in the gaps. */
    /** The art box, turned about its centre when the placement has a rotation. */
    private static void drawArt(GuiGraphics g, Arts.Art a, Placement p, int ox, int oy, boolean flip) {
        if (p.rot == 0) { a.drawSmall(g, ox + p.x, oy + p.y, p.w, p.h, flip, p.flipY); return; }
        g.pose().pushPose();
        g.pose().translate(ox + p.x + p.w / 2f, oy + p.y + p.h / 2f, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(p.rot));
        g.pose().translate(-p.w / 2f, -p.h / 2f, 0);
        a.drawSmall(g, 0, 0, p.w, p.h, flip, p.flipY);
        g.pose().popPose();
    }

    private static void drawAroundSlots(GuiGraphics g, Masks.Mask mask, Arts.Art art, Placement real, int ox, int oy, boolean ringsOnly, boolean flip) {
        Placement p = real.bounds();
        List<int[]> cells = new ArrayList<>();
        TreeSet<Integer> ys = new TreeSet<>();
        ys.add(p.y);
        ys.add(p.y + p.h);
        for (int i = 0; i < mask.rects.length; i++) {
            if (mask.kinds[i] != 's') continue;
            int[] r = mask.rects[i];
            int cx0 = r[0] - 1, cy0 = r[1] - 1, cx1 = r[0] + r[2] + 1, cy1 = r[1] + r[3] + 1;
            if (cx1 <= p.x || cx0 >= p.x + p.w || cy1 <= p.y || cy0 >= p.y + p.h) continue;
            if (ringsOnly) {
                // only the 1px outline around the slot stays crisp; the square itself shows the art
                addBlock(cells, ys, p, cx0, cy0, cx1, cy0 + 1);
                addBlock(cells, ys, p, cx0, cy1 - 1, cx1, cy1);
                addBlock(cells, ys, p, cx0, cy0 + 1, cx0 + 1, cy1 - 1);
                addBlock(cells, ys, p, cx1 - 1, cy0 + 1, cx1, cy1 - 1);
            } else {
                addBlock(cells, ys, p, cx0, cy0, cx1, cy1);
            }
        }
        Integer[] bands = ys.toArray(new Integer[0]);
        for (int b = 0; b + 1 < bands.length; b++) {
            int y0 = bands[b], y1 = bands[b + 1];
            if (y1 <= y0) continue;
            List<int[]> blocked = new ArrayList<>();
            for (int[] c : cells) if (c[1] < y1 && c[3] > y0) blocked.add(new int[] { c[0], c[2] });
            blocked.sort((u, v) -> Integer.compare(u[0], v[0]));
            int x = p.x;
            for (int[] bl : blocked) {
                if (bl[0] > x) blit(g, art, real, p, ox, oy, x, y0, Math.min(bl[0], p.x + p.w), y1, flip);
                x = Math.max(x, bl[1]);
            }
            if (x < p.x + p.w) blit(g, art, real, p, ox, oy, x, y0, p.x + p.w, y1, flip);
        }
    }

    private static void addBlock(List<int[]> cells, TreeSet<Integer> ys, Placement p, int x0, int y0, int x1, int y1) {
        cells.add(new int[] { x0, y0, x1, y1 });
        ys.add(Math.max(p.y, y0));
        ys.add(Math.min(p.y + p.h, y1));
    }

    private static void blit(GuiGraphics g, Arts.Art a, Placement real, Placement p, int ox, int oy, int x0, int y0, int x1, int y1, boolean flip) {
        // clamp to the art box and draw the full art clipped to this rectangle: no UV slicing, nothing can leak
        x0 = Math.max(x0, p.x); y0 = Math.max(y0, p.y);
        x1 = Math.min(x1, p.x + p.w); y1 = Math.min(y1, p.y + p.h);
        if (x1 <= x0 || y1 <= y0) return;
        g.enableScissor(Math.round(absX + absScale * x0), Math.round(absY + absScale * y0),
                Math.round(absX + absScale * x1), Math.round(absY + absScale * y1));
        try {
            drawArt(g, a, real, ox, oy, flip);
        } finally {
            g.disableScissor();
        }
    }

    private ArtRenderer() {}
}
