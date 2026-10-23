package com.mahiru.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Text helper shared by the studio widgets. */
public final class Ui {
    /** Text that always fits: shrinks down to 70%, then cuts with "..". */
    public static void drawFit(GuiGraphics g, Font font, String text, int x, int y, int maxW, int h, boolean center, int color) {
        int tw = font.width(text);
        float sc = 1f;
        if (tw > maxW) sc = Math.max(0.7f, (float) maxW / tw);
        if (tw * sc > maxW) {
            int lo = 1, hi = text.length();
            while (lo < hi) {
                int mid = (lo + hi + 1) >>> 1;
                if (font.width(text.substring(0, mid) + "..") * sc <= maxW) lo = mid; else hi = mid - 1;
            }
            text = text.substring(0, Math.max(1, lo)) + "..";
            tw = font.width(text);
        }
        float w = tw * sc;
        float dx = center ? x + (maxW - w) / 2f : x;
        float dy = y + (h - 8 * sc) / 2f;
        g.pose().pushPose();
        g.pose().translate(dx, dy, 0);
        g.pose().scale(sc, sc, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private Ui() {}
}
