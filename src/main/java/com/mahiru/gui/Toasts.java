package com.mahiru.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Short feedback messages ("Saved", "Reset", ...) drawn at the top of the studio. */
public final class Toasts {
    private record Toast(String text, int color, long until, int count) {}

    private static final List<Toast> LIST = new ArrayList<>();

    public static void ok(String text) { add(text, 0xFF7BE07B); }
    public static void warn(String text) { add(text, 0xFFFFD84A); }
    public static void error(String text) { add(text, 0xFFFF6B6B); }

    private static synchronized void add(String text, int color) {
        // the same message again ("Undone" ten times): one toast with a counter instead of a column of copies
        int count = 1;
        for (Iterator<Toast> it = LIST.iterator(); it.hasNext(); ) {
            Toast t = it.next();
            if (t.text.equals(text) && t.until > System.currentTimeMillis()) { count = t.count + 1; it.remove(); }
        }
        LIST.add(new Toast(text, color, System.currentTimeMillis() + 2500 + Math.min(4000, text.length() * 35L), count));
        while (LIST.size() > 3) LIST.remove(0);
    }

    public static synchronized void render(GuiGraphics g, int screenWidth) {
        Font font = Minecraft.getInstance().font;
        long now = System.currentTimeMillis();
        int y = 24;
        for (Iterator<Toast> it = LIST.iterator(); it.hasNext(); ) {
            Toast t = it.next();
            if (t.until < now) { it.remove(); continue; }
            String tag = t.count > 1 ? " x" + t.count : "";
            String shown = font.plainSubstrByWidth(t.text, Math.max(40, screenWidth - 40 - font.width(tag))) + tag;
            int w = font.width(shown) + 14;
            int x = (screenWidth - w) / 2;
            g.fill(x, y, x + w, y + 16, 0xE0101018);
            g.fill(x, y, x + 2, y + 16, t.color);
            g.drawString(font, shown, x + 8, y + 4, 0xFFFFFFFF, false);
            y += 19;
        }
    }

    private Toasts() {}
}
