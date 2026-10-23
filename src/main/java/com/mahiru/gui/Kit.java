package com.mahiru.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

/**
 * Immediate-mode widget kit for the studio: every frame the screen calls the widget functions, they draw and register
 * "hits"; mouse events are routed to the hits of the previous frame. Popups, dialogs, tooltips, scroll areas,
 * sliders and text inputs live here so the screen code only describes what it wants.
 *
 * Widget ids must be stable between frames (never contain scroll-dependent coordinates or changing values).
 */
public final class Kit {
    // ------------------------------------------------------------------ palette
    public static final int BG = 0xFF14141A, PANEL = 0xFF1A1A22, PANEL2 = 0xFF21212B, EDGE = 0xFF3A3A4A, EDGE_HI = 0xFF7A7AA0;
    public static final int TEXT = 0xFFE8E8F4, DIM = 0xFF9A9AB8, FAINT = 0xFF686880, ACCENT = 0xFFFFD84A, GOOD = 0xFF7BE07B, BAD = 0xFFFF6B6B;
    /** keyboard focus outline */
    public static final int FOCUS = 0xFF58B8FF;
    public static final int BTN = 0xFF2E2E3A, BTN_HOVER = 0xFF484862, BTN_DOWN = 0xFF6A6AA8, BTN_OFF = 0xFF202028;

    // ------------------------------------------------------------------ icons
    public static final int EYE = 1, EYE_OFF = 2, PLUS = 5, TRASH = 6, COPY = 7, UP = 8, DOWN = 9,
            ROT_L = 10, ROT_R = 11, FLIP = 12, RESET = 13, UNDO = 14, REDO = 15, MAGNET = 16, FIT = 17, ZOOM_IN = 18, ZOOM_OUT = 19,
            GEAR = 20, FOLDER = 21, HELP = 22, CHECK = 24, DICE = 26, CENTER_H = 27, CENTER_V = 28,
            CARET = 29, LAYERS = 30, ARROW_L = 31, ARROW_R = 32, PIN = 33, FLIP_V = 34;

    private static final Map<Integer, String[]> GLYPHS = new HashMap<>();

    static {
        GLYPHS.put(EYE, new String[] { "..#####..", ".#.....#.", "#...#...#", "#..###..#", "#...#...#", ".#.....#.", "..#####.." });
        GLYPHS.put(PLUS, new String[] { "...#...", "...#...", "...#...", "#######", "...#...", "...#...", "...#..." });
        GLYPHS.put(TRASH, new String[] { "..###..", "#######", ".#...#.", ".#.#.#.", ".#.#.#.", ".#.#.#.", ".#...#.", ".#####." });
        GLYPHS.put(COPY, new String[] { "#####...", "#...#...", "#.#####.", "#.#...#.", "###...#.", "..#...#.", "..#####." });
        GLYPHS.put(UP, new String[] { "...#...", "..###..", ".#####.", "#######" });
        GLYPHS.put(DOWN, new String[] { "#######", ".#####.", "..###..", "...#..." });
        GLYPHS.put(ROT_R, new String[] { "..####...", ".#....#..", "#......#.", "#.....###", "#......#.", ".#.......", "..#....#.", "...####.." });
        GLYPHS.put(ROT_L, new String[] { "...####..", "..#....#.", ".#......#", "###.....#", ".#......#", ".......#.", ".#....#..", "..####..." });
        GLYPHS.put(FLIP, new String[] { "...#.#...", "..##.##..", ".###.###.", "####.####", ".###.###.", "..##.##..", "...#.#..." });
        GLYPHS.put(RESET, new String[] { "..#.....", ".##.....", "########", ".##....#", "..#....#", ".......#", "..######" });
        GLYPHS.put(UNDO, new String[] { "..#.....", ".##.###.", "#######.", ".##...##", "..#....#", "......##", "..#####." });
        GLYPHS.put(REDO, new String[] { ".....#..", ".###.##.", ".#######", "##...##.", "#....#..", "##......", ".#####.." });
        GLYPHS.put(MAGNET, new String[] { "##...##", "##...##", "##...##", "##...##", ".#####.", "..###.." });
        GLYPHS.put(FIT, new String[] { "##...##", "#.....#", ".......", ".......", ".......", "#.....#", "##...##" });
        GLYPHS.put(ZOOM_IN, new String[] { ".###...", "#.#.#..", "#####..", "#.#.#..", ".###...", "....##.", ".....##" });
        GLYPHS.put(ZOOM_OUT, new String[] { ".###...", "#...#..", "#####..", "#...#..", ".###...", "....##.", ".....##" });
        GLYPHS.put(FOLDER, new String[] { "###.....", "########", "#......#", "#......#", "#......#", "########" });
        GLYPHS.put(CHECK, new String[] { "......#", ".....##", "#...##.", "##.##..", ".###...", "..#...." });
        GLYPHS.put(DICE, new String[] { "#######", "#.#.#.#", "#.....#", "#..#..#", "#.....#", "#.#.#.#", "#######" });
        GLYPHS.put(CENTER_H, new String[] { "#.....#", "#.###.#", "#.###.#", "#.###.#", "#.....#" });
        GLYPHS.put(CENTER_V, new String[] { "#######", ".......", "..###..", "..###..", "..###..", ".......", "#######" });
        GLYPHS.put(FLIP_V, new String[] { "..#####..", "...###...", "....#....", ".........", "....#....", "...###...", "..#####.." });
        GLYPHS.put(CARET, new String[] { "#####", ".###.", "..#.." });
        GLYPHS.put(LAYERS, new String[] { "..###..", ".#####.", "#######", ".#####.", "..###.." });
        GLYPHS.put(ARROW_L, new String[] { "...#.", "..##.", ".###.", "####.", ".###.", "..##.", "...#." });
        GLYPHS.put(ARROW_R, new String[] { ".#...", ".##..", ".###.", ".####", ".###.", ".##..", ".#..." });
        GLYPHS.put(PIN, new String[] { ".#####.", ".#...#.", ".#####.", "...#...", "...#...", "...#..." });
    }

    public static void icon(GuiGraphics g, int id, int x, int y, int c) {
        if (id == GEAR) {
            int cx = x + 4, cy = y + 4;
            for (int dy = -4; dy <= 4; dy++) {
                for (int dx = -4; dx <= 4; dx++) {
                    int r2 = dx * dx + dy * dy;
                    boolean ring = r2 <= 12 && r2 >= 3;
                    boolean tooth = r2 >= 9 && r2 <= 20 && (dx == 0 || dy == 0 || Math.abs(dx) == Math.abs(dy));
                    if (ring || tooth) g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, c);
                }
            }
            return;
        }
        if (id == HELP) {
            Font f = net.minecraft.client.Minecraft.getInstance().font;
            g.drawString(f, "?", x + 1, y, c, false);
            return;
        }
        if (id == EYE_OFF) {
            icon(g, EYE, x, y, (c & 0x00FFFFFF) | 0x70000000);
            for (int i = 0; i < 9; i++) g.fill(x + i, y + 8 - i, x + i + 1, y + 9 - i, c);
            return;
        }
        String[] rows = GLYPHS.get(id);
        if (rows == null) return;
        for (int r = 0; r < rows.length; r++) {
            String row = rows[r];
            int run = -1;
            for (int cI = 0; cI <= row.length(); cI++) {
                boolean on = cI < row.length() && row.charAt(cI) == '#';
                if (on && run < 0) run = cI;
                if (!on && run >= 0) { g.fill(x + run, y + r, x + cI, y + r + 1, c); run = -1; }
            }
        }
    }

    public static int iconW(int id) {
        if (id == GEAR || id == EYE_OFF) return 9;
        if (id == HELP) return 6;
        String[] rows = GLYPHS.get(id);
        return rows == null ? 0 : rows[0].length();
    }

    // ------------------------------------------------------------------ hits and drags
    public interface Drag {
        void move(double mx, double my);

        default void end(double mx, double my) { }
    }

    public interface DragStart {
        Drag begin(double mx, double my);
    }

    public static final class Hit {
        public int x, y, w, h, z;
        public int[] clip;
        public boolean enabled = true, repeat;
        public String tip = "", reason = "", id = "";
        public Runnable press, right, click;
        public DragStart drag, dragMid;
        public DoubleConsumer wheel;
        /** a value widget: the wheel changes it only with Shift / Ctrl held, otherwise it scrolls the panel */
        public boolean wheelNeedsMod;
        /** the hit is a text field: clicks go to this input */
        public String inputId;

        boolean inside(double mx, double my) {
            if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
            return clip == null || (mx >= clip[0] && mx < clip[2] && my >= clip[1] && my < clip[3]);
        }
    }

    public final Font font;
    private List<Hit> building = new ArrayList<>(), active = new ArrayList<>();
    public GuiGraphics g;
    public int mx, my;
    public boolean mouseDown;
    private final List<int[]> clips = new ArrayList<>();
    private int baseZ = 0;
    private Hit hoverCache;
    private boolean hoverValid;
    private final Set<String> seenInputs = new HashSet<>(), lastSeenInputs = new HashSet<>();

    public int modalZ = 0;

    /** the colour picker window (one at a time) */
    public final ColorPicker colors = new ColorPicker(this);

    public Kit(Font font) {
        this.font = font;
    }

    public void frame(GuiGraphics g, int mx, int my, boolean down) {
        this.g = g;
        this.mx = mx;
        this.my = my;
        this.mouseDown = down;
        building = new ArrayList<>();
        clips.clear();
        baseZ = 0;
        tipText = "";
        hoverValid = false;
        seenInputs.clear();
        padsBuilding = new HashMap<>();
    }

    /** Called when the whole frame is drawn: the hits registered now answer the next mouse events. */
    public void endFrame() {
        active = building;
        hoverValid = false;
        // a text input that was not drawn this frame cannot keep the keyboard
        lastSeenInputs.clear();
        lastSeenInputs.addAll(seenInputs);
        if (!focusId.isEmpty() && !seenInputs.contains(focusId)) setFocus("");
        pads = padsBuilding;
        // a slider / number that vanished cannot keep the arrow keys
        if (!padFocus.isEmpty() && !pads.containsKey(padFocus)) padFocus = "";
    }

    public void zLevel(int z) { baseZ = z; }

    /** an exception while drawing must not leave a scissor or a scroll area open for the popups */
    public void resetClips() {
        while (!clips.isEmpty()) popClip();
        scrolls.clear();
    }

    public Hit add(Hit h) {
        h.z += baseZ;
        if (!clips.isEmpty()) {
            int[] c = clips.get(clips.size() - 1);
            h.clip = c;
            // outside the visible area: nobody can click it, do not keep it
            if (h.x >= c[2] || h.x + h.w <= c[0] || h.y >= c[3] || h.y + h.h <= c[1]) return h;
        }
        building.add(h);
        return h;
    }

    public void pushClip(int x0, int y0, int x1, int y1) {
        int[] c = { x0, y0, x1, y1 };
        if (!clips.isEmpty()) {
            int[] p = clips.get(clips.size() - 1);
            c[0] = Math.max(c[0], p[0]); c[1] = Math.max(c[1], p[1]); c[2] = Math.min(c[2], p[2]); c[3] = Math.min(c[3], p[3]);
        }
        clips.add(c);
        g.enableScissor(x0, y0, Math.max(x0, x1), Math.max(y0, y1));
    }

    public void popClip() {
        clips.remove(clips.size() - 1);
        g.disableScissor();
    }

    /** the topmost hit under the mouse (modal levels hide everything below them) */
    public Hit hitAt(double mx, double my) {
        Hit best = null;
        int minZ = modalZ;
        for (Hit h : active) {
            if (h.z < minZ || !h.inside(mx, my)) continue;
            if (best == null || h.z >= best.z) best = h;
        }
        return best;
    }

    private Hit hover() {
        if (!hoverValid) { hoverCache = hitAt(mx, my); hoverValid = true; }
        return hoverCache;
    }

    private boolean isHover(String id) {
        Hit h = hover();
        return h != null && h.id.equals(id);
    }

    // ------------------------------------------------------------------ mouse routing
    private Hit pressedHit;
    private Drag drag;
    private int dragButton = -1;
    private double pressX, pressY;
    private Hit clickHit;
    private long heldSince, heldNext;
    private String pressedId = "";

    public boolean press(double mx, double my, int button) {
        if (popup != null) {
            Hit t = hitAt(mx, my);
            if (t == null || t.z < popupZ()) { closePopup(); return true; }
        }
        Hit h = hitAt(mx, my);
        // a dialog keeps its own focus (clicking its text or a checkbox must not silence its input)
        if (dialog == null && !focusId.isEmpty() && !(h != null && h.inputId != null && h.inputId.equals(focusId))) setFocus("");
        if (!padFocus.isEmpty() && (h == null || !h.id.equals(padFocus))) padFocus = "";
        if (h == null) return false;
        if (button == 1) {
            if (h.enabled && h.right != null) h.right.run();
            return true;
        }
        if (button == 2) {
            if (h.enabled && h.dragMid != null) { drag = h.dragMid.begin(mx, my); dragButton = 2; }
            return true;
        }
        if (button != 0) return true;
        if (!h.enabled) return true;
        if (pads.containsKey(h.id)) padFocus = h.id;
        if (h.inputId != null) {
            EditBox e = inputs.get(h.inputId);
            focusId = h.inputId;
            if (e != null) e.mouseClicked(mx, my, 0);
            return true;
        }
        pressedHit = h;
        pressedId = h.id;
        pressX = mx;
        pressY = my;
        if (h.drag != null) {
            drag = h.drag.begin(mx, my);
            dragButton = 0;
            clickHit = h.click != null ? h : null;
            return true;
        }
        if (h.press != null) {
            h.press.run();
            if (h.repeat) { heldSince = now(); heldNext = 0; } else pressedHit = null;
        }
        return true;
    }

    public boolean dragMove(double mx, double my, int button) {
        if (drag == null || button != dragButton) return false;
        drag.move(mx, my);
        return true;
    }

    public void release(double mx, double my, int button) {
        if (button == dragButton && drag != null) {
            Drag d = drag;
            Hit ch = clickHit;
            drag = null;
            dragButton = -1;
            clickHit = null;
            d.end(mx, my);
            if (button == 0 && ch != null && Math.abs(mx - pressX) < 3 && Math.abs(my - pressY) < 3) ch.click.run();
        }
        if (button == 0) { pressedHit = null; pressedId = ""; }
    }

    public boolean dragging() { return drag != null; }

    public String pressedId() { return pressedId; }

    public boolean wheel(double mx, double my, double sy) {
        Hit top = hitAt(mx, my);
        if (top == null) return false;
        boolean mod = Screen.hasShiftDown() || Screen.hasControlDown();
        Hit best = null;
        for (Hit h : active) {
            if (h.wheel == null || h.z < top.z || !h.inside(mx, my)) continue;
            if (h.wheelNeedsMod && !mod) continue;
            if (best == null || h.z >= best.z) best = h;
        }
        if (best == null) return false;
        best.wheel.accept(sy);
        return true;
    }

    /** hold-to-repeat: 350 ms delay, then every 90 ms, speeding up after 2 s */
    public void tickRepeat() {
        if (pressedHit == null || !pressedHit.repeat) return;
        if (!mouseDown) { pressedHit = null; return; }
        Hit target = null;
        for (Hit h : active) if (h.repeat && h.id.equals(pressedId)) target = h;
        if (target == null || !target.inside(mx, my) || target.press == null) return;
        long e = now() - heldSince;
        if (e < 350 || now() < heldNext) return;
        heldNext = now() + 90;
        int n = e < 2000 ? 1 : (int) Math.min(50, 1 + (e - 2000) / 100);
        for (int i = 0; i < n; i++) target.press.run();
    }

    private static long now() { return System.currentTimeMillis(); }

    // ------------------------------------------------------------------ text helpers
    public void text(String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    /** text that always fits; when it had to be cut the full text becomes the tooltip of the area */
    public void textFit(String s, int x, int y, int maxW, int color) {
        Ui.drawFit(g, font, s, x, y - 1, maxW, 10, false, color);
        if (font.width(s) > maxW && hoverArea(x, y - 1, maxW, 10)) tip(s);
    }

    /** plain geometric hover (no widget registered): respects the clip and anything modal on top */
    public boolean hoverArea(int x, int y, int w, int h) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        if (!clips.isEmpty()) {
            int[] c = clips.get(clips.size() - 1);
            if (mx < c[0] || mx >= c[2] || my < c[1] || my >= c[3]) return false;
        }
        Hit top = hover();
        return top == null || top.z <= baseZ;
    }

    public void rect(int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    public void border(int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    // ------------------------------------------------------------------ tooltips
    private String tipText = "";
    private String lastTip = "";
    private long tipSince;

    private void tip(String t) { if (tipText.isEmpty()) tipText = t; }

    /** Draws the tooltip of the hit under the mouse (after a short delay). Call once at the end of the frame. */
    public void drawTooltip(int screenW, int screenH) {
        Hit h = mouseDown ? null : hover();
        String t = tipText;
        if (h != null) {
            if (!h.enabled && !h.reason.isEmpty()) t = (h.tip.isEmpty() ? "" : h.tip + " - ") + h.reason;
            else if (!h.tip.isEmpty()) t = h.tip;
        }
        if (t.isEmpty() || mouseDown) { lastTip = ""; tipSince = 0; return; }
        if (!t.equals(lastTip)) { lastTip = t; tipSince = now(); }
        if (now() - tipSince < 450) return;
        List<FormattedCharSequence> lines = font.split(Component.literal(t), 190);
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
        int hh = lines.size() * 10 + 6;
        int x = Math.max(2, Math.min(screenW - w - 10, mx + 10)), y = Math.max(2, Math.min(screenH - hh - 4, my + 12));
        g.pose().pushPose();
        g.pose().translate(0, 0, 800);
        g.fill(x - 3, y - 3, x + w + 4, y + hh - 2, 0xF0101018);
        g.fill(x - 3, y - 3, x + w + 4, y - 2, EDGE_HI);
        int ty = y;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x, ty, 0xFFFFFFFF, false);
            ty += 10;
        }
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ buttons
    /** Fluent button description; {@link #show()} draws it and registers its hit. */
    public final class B {
        final int x, y, w, h;
        String label = "";
        int icon;
        String tip = "", reason = "";
        boolean on, enabled = true, accent, repeat, swState, hasSwitch, flat;
        Runnable press, right;
        int color = TEXT;
        String id;

        B(int x, int y, int w, int h) { this.x = x; this.y = y; this.w = w; this.h = h; }

        public B label(String s) { label = s; return this; }
        public B icon(int i) { icon = i; return this; }
        public B tip(String s) { tip = s; return this; }
        public B on(boolean v) { on = v; return this; }
        public B accent() { accent = true; return this; }
        public B flat() { flat = true; return this; }
        public B color(int c) { color = c; return this; }
        public B rep() { repeat = true; return this; }
        public B press(Runnable r) { press = r; return this; }
        public B right(Runnable r) { right = r; return this; }
        /** stable identity (needed when the label changes with the state, or in lists) */
        public B id(String s) { id = s; return this; }
        /** disabled with a reason that the tooltip shows */
        public B enabled(boolean v, String why) { enabled = v; reason = why; return this; }
        public B enabled(boolean v) { enabled = v; return this; }
        public B sw(boolean state) { hasSwitch = true; swState = state; return this; }

        public Hit show() {
            String hid = id != null ? id : label + "|" + tip;
            Hit hit = new Hit();
            hit.x = x; hit.y = y; hit.w = w; hit.h = h;
            hit.enabled = enabled;
            hit.press = press; hit.right = right; hit.repeat = repeat;
            hit.id = hid;
            String t = tip;
            int iw = icon != 0 ? iconW(icon) + 4 : 0;
            int swW = hasSwitch ? 22 : 0;
            int avail = w - 8 - iw - swW;
            if (!label.isEmpty() && font.width(label) > avail && avail > 0) t = tip.isEmpty() ? label : label + " - " + tip;
            hit.tip = t;
            hit.reason = reason;
            add(hit);

            boolean hover = enabled && isHover(hid) && hit.inside(mx, my);
            boolean down = hover && mouseDown && hid.equals(pressedId);
            int bg;
            if (flat) bg = !enabled ? 0 : down ? BTN_DOWN : hover ? BTN_HOVER : on ? 0xFF3A3A58 : 0;
            else bg = !enabled ? BTN_OFF : down ? BTN_DOWN : hover ? BTN_HOVER : accent ? 0xFF3C5A3C : on ? 0xFF3A3A58 : BTN;
            if (bg != 0) g.fill(x, y, x + w, y + h, bg);
            if (!flat) border(x, y, w, h, on ? ACCENT : hover ? 0xFFB8B8D8 : !enabled ? 0xFF2A2A32 : EDGE);
            int fg = !enabled ? FAINT : color;
            int tx = x + 4;
            if (icon != 0) {
                int iy = y + (h - 8) / 2;
                if (label.isEmpty()) tx = x + (w - iconW(icon)) / 2;
                Kit.icon(g, icon, tx, iy, enabled ? (on ? ACCENT : 0xFFEEEEF8) : FAINT);
                tx += iconW(icon) + 4;
            }
            if (!label.isEmpty()) {
                int rightEdge = x + w - 4 - swW;
                Ui.drawFit(g, font, label, tx, y, Math.max(4, rightEdge - tx), h, icon == 0 && !hasSwitch, fg);
            }
            if (hasSwitch) {
                int pw = 18, ph = 8, px = x + w - pw - 4, py = y + (h - ph) / 2;
                g.fill(px, py, px + pw, py + ph, swState ? 0xFF3FAF3F : 0xFF55555E);
                int kx = swState ? px + pw - 7 : px + 1;
                g.fill(kx, py + 1, kx + 6, py + ph - 1, enabled ? 0xFFEEEEEE : 0xFF999999);
            }
            return hit;
        }
    }

    public B b(int x, int y, int w, int h) { return new B(x, y, w, h); }

    /**
     * A setting that changes the look: shows "Label: option"; a click goes to the next option, a right click lists them all.
     * {@code options} are English texts, translated here.
     */
    public Hit choice(int x, int y, int w, int h, String id, String label, String[] options, IntSupplier cur, String tip, IntConsumer set) {
        int n = options.length;
        int i = Math.max(0, Math.min(n - 1, cur.getAsInt()));
        Runnable list = () -> {
            List<Item> items = new ArrayList<>();
            for (int k = 0; k < n; k++) {
                final int fk = k;
                items.add(Item.of(T.t(options[k]), () -> set.accept(fk)).check(cur.getAsInt() == k));
            }
            openPopup(mx, my, items);
        };
        return b(x, y, w, h).label(T.f("%s: %s", label, T.t(options[i]))).icon(ARROW_R).id(id)
                .tip(tip + " (" + T.t("click: next, right click: list") + ")")
                .press(() -> set.accept((cur.getAsInt() + 1) % n)).right(list).show();
    }

    /** an ON/OFF switch; the state is read when it is clicked, never from a captured value */
    public Hit toggle(int x, int y, int w, int h, String label, BooleanSupplier state, String tip, Consumer<Boolean> set) {
        return b(x, y, w, h).label(label).tip(tip).sw(state.getAsBoolean()).press(() -> set.accept(!state.getAsBoolean())).show();
    }

    // ------------------------------------------------------------------ keyboard focus of sliders and number fields
    private interface PadStep { void step(int dir, boolean big); }

    /** a slider / number field that can be nudged with the arrow keys once it was clicked */
    private static final class Pad {
        final boolean horizontal;
        final PadStep step;

        Pad(boolean horizontal, PadStep step) { this.horizontal = horizontal; this.step = step; }
    }

    private Map<String, Pad> padsBuilding = new HashMap<>(), pads = new HashMap<>();
    /** the slider / number field (hit id) that has the arrow keys; "" = none */
    public String padFocus = "";

    private boolean padKey(int key, int mods) {
        if (padFocus.isEmpty()) return false;
        Pad p = pads.get(padFocus);
        if (p == null) { padFocus = ""; return false; }
        if (key == GLFW.GLFW_KEY_ESCAPE) { padFocus = ""; return true; }
        if ((mods & GLFW.GLFW_MOD_CONTROL) != 0 || (mods & GLFW.GLFW_MOD_ALT) != 0) return false;
        int dir = key == GLFW.GLFW_KEY_UP ? 1 : key == GLFW.GLFW_KEY_DOWN ? -1
                : p.horizontal && key == GLFW.GLFW_KEY_RIGHT ? 1 : p.horizontal && key == GLFW.GLFW_KEY_LEFT ? -1 : 0;
        if (dir == 0) return false;
        p.step.step(dir, (mods & GLFW.GLFW_MOD_SHIFT) != 0);
        return true;
    }

    // ------------------------------------------------------------------ slider
    public interface Setter { void set(double v); }

    /**
     * A horizontal slider with the label on the left and the value text on the right.
     * Drag = set, wheel = step, Shift = fine, right-click = reset.
     */
    public Hit slider(String id, int x, int y, int w, int h, String label, DoubleSupplier cur, DoubleFunction<String> fmt,
                      double lo, double hi, double step, String tip, Setter set, Runnable begin, Runnable reset) {
        Hit hit = new Hit();
        hit.x = x; hit.y = y; hit.w = w; hit.h = h;
        hit.tip = tip;
        hit.id = "slider|" + id;
        final double range = Math.max(1e-9, hi - lo);
        hit.drag = (mx0, my0) -> {
            if (begin != null) begin.run();
            applySlider(set, x, w, mx0, lo, hi, step, false);
            return new Drag() {
                @Override
                public void move(double mx1, double my1) { applySlider(set, x, w, mx1, lo, hi, step, Screen.hasShiftDown()); }
            };
        };
        if (reset != null) hit.right = reset;
        hit.wheelNeedsMod = true;
        hit.wheel = sy -> {
            if (begin != null) begin.run();
            double st = step * (Screen.hasShiftDown() ? 5 : 1);
            set.set(Math.max(lo, Math.min(hi, cur.getAsDouble() + (sy > 0 ? st : -st))));
        };
        add(hit);
        padsBuilding.put(hit.id, new Pad(true, (dir, big) -> {
            if (begin != null) begin.run();
            set.set(Math.max(lo, Math.min(hi, cur.getAsDouble() + dir * step * (big ? 5 : 1))));
        }));
        double v = cur.getAsDouble();
        boolean hover = isHover(hit.id);
        g.fill(x, y, x + w, y + h, hover ? 0xFF2A2A38 : BTN);
        double f = Math.max(0, Math.min(1, (v - lo) / range));
        g.fill(x + 1, y + 1, x + 1 + (int) ((w - 2) * f), y + h - 1, 0xFF4A4A86);
        int hx = x + 1 + (int) ((w - 2) * f);
        g.fill(Math.max(x + 1, hx - 1), y + 1, Math.min(x + w - 1, hx + 1), y + h - 1, 0xFFB8B8F0);
        border(x, y, w, h, hover ? 0xFFB8B8D8 : EDGE);
        if (hit.id.equals(padFocus)) border(x - 1, y - 1, w + 2, h + 2, FOCUS);
        String valueText = fmt.apply(v);
        int vw = font.width(valueText);
        Ui.drawFit(g, font, label, x + 4, y, w - vw - 12, h, false, TEXT);
        g.drawString(font, valueText, x + w - vw - 4, y + (h - 8) / 2, TEXT, false);
        return hit;
    }

    private static void applySlider(Setter set, int x, int w, double mx, double lo, double hi, double step, boolean fine) {
        double f = Math.max(0, Math.min(1, (mx - x - 1) / Math.max(1.0, w - 2)));
        double v = lo + f * (hi - lo);
        double st = fine ? step / 5.0 : step;
        if (st > 0) v = lo + Math.round((v - lo) / st) * st;
        set.set(Math.max(lo, Math.min(hi, v)));
    }

    // ------------------------------------------------------------------ number field (drag to scrub, click to type)
    /**
     * A labelled number: drag sideways to change it (4 px per step; Shift x5 and past the normal range, Ctrl fine),
     * click without moving to type a value, wheel = step.
     */
    public Hit number(String id, int x, int y, int w, int h, String label, DoubleSupplier cur, DoubleFunction<String> fmt,
                      double step, double lo, double hi, double insaneHi, String tip, Setter set, Runnable begin) {
        String hid = "num|" + id;
        boolean editing = hid.equals(editId);
        Hit hit = new Hit();
        hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.tip = tip; hit.id = hid;
        if (editing) {
            hit.inputId = hid;
            seenInputs.add(hid);
            editStep = step; editLo = lo; editHi = insaneHi; editFmt = fmt;
        } else {
            hit.drag = (mx0, my0) -> new Drag() {
                double acc = 0;
                double lastX = mx0;
                boolean moved;
                final double start = cur.getAsDouble();

                @Override
                public void move(double mx1, double my1) {
                    if (!moved && Math.abs(mx1 - mx0) < 3) return;
                    if (!moved) { moved = true; if (begin != null) begin.run(); }
                    boolean shift = Screen.hasShiftDown();
                    boolean ctrl = Screen.hasControlDown();
                    double st = ctrl ? step / 5.0 : shift ? step * 5 : step;
                    acc += (mx1 - lastX) / 4.0 * st;
                    lastX = mx1;
                    double v = start + acc;
                    double q = step / 5.0;
                    if (q > 0) v = Math.round(v / q) * q;
                    set.set(Math.max(lo, Math.min(shift ? insaneHi : Math.max(hi, start), v)));
                }

                @Override
                public void end(double mx1, double my1) {
                    if (moved) return;
                    beginEdit(hid, x, y, w, h, fmt.apply(cur.getAsDouble()), s -> {
                        try {
                            double v = Double.parseDouble(s.trim().replace(',', '.'));
                            if (Double.isNaN(v)) return;
                            if (begin != null) begin.run();
                            set.set(Math.max(lo, Math.min(insaneHi, v)));
                        } catch (NumberFormatException ignored) { }
                    });
                }
            };
            hit.wheelNeedsMod = true;
            hit.wheel = sy -> {
                if (begin != null) begin.run();
                boolean shift = Screen.hasShiftDown();
                double st = step * (shift ? 5 : 1);
                double v = cur.getAsDouble();
                set.set(Math.max(lo, Math.min(shift ? insaneHi : Math.max(hi, v), v + (sy > 0 ? st : -st))));
            };
        }
        add(hit);
        padsBuilding.put(hid, new Pad(false, (dir, big) -> {
            if (begin != null) begin.run();
            double v = cur.getAsDouble();
            set.set(Math.max(lo, Math.min(big ? insaneHi : Math.max(hi, v), v + dir * step * (big ? 5 : 1))));
        }));
        boolean hover = isHover(hid);
        g.fill(x, y, x + w, y + h, editing ? 0xFF101018 : hover ? 0xFF3A3A50 : BTN);
        border(x, y, w, h, editing ? ACCENT : hover ? 0xFFB8B8D8 : EDGE);
        if (!editing && hid.equals(padFocus)) border(x - 1, y - 1, w + 2, h + 2, FOCUS);
        if (editing) {
            EditBox eb = inputs.get(hid);
            if (eb != null) {
                eb.setX(x + 3); eb.setY(y + (h - 8) / 2); eb.setWidth(w - 6);
                eb.render(g, mx, my, 0);
            }
        } else {
            double v = cur.getAsDouble();
            String text = fmt.apply(v);
            int vw = font.width(text);
            Ui.drawFit(g, font, label, x + 4, y, w - vw - 10, h, false, DIM);
            boolean over = v > hi + 1e-6 || v < lo - 1e-6;
            g.drawString(font, text, x + w - vw - 4, y + (h - 8) / 2, over ? 0xFFFF8A3A : TEXT, false);
        }
        return hit;
    }

    // ------------------------------------------------------------------ text inputs
    private final Map<String, EditBox> inputs = new HashMap<>();
    private final Set<String> responderSet = new HashSet<>();
    public String focusId = "";
    public String editId = "";
    private Consumer<String> editCommit;
    private double editStep, editLo, editHi;
    private DoubleFunction<String> editFmt;
    private final Map<String, Consumer<String>> changeListeners = new HashMap<>();

    private EditBox box(String id, boolean bordered) {
        return inputs.computeIfAbsent(id, k -> {
            EditBox e = new EditBox(font, 0, 0, 60, 14, Component.literal(k));
            e.setMaxLength(60);
            e.setBordered(bordered);
            return e;
        });
    }

    /** A free text input (library search, group name...). {@code onChange} gets every edit. */
    public String input(String id, int x, int y, int w, int h, String hint, Consumer<String> onChange) {
        EditBox eb = box(id, true);
        eb.setX(x); eb.setY(y); eb.setWidth(w); eb.setHeight(h);
        eb.setHint(Component.literal(hint).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        changeListeners.put(id, onChange);
        if (responderSet.add(id)) eb.setResponder(s -> { Consumer<String> c = changeListeners.get(id); if (c != null) c.accept(s); });
        Hit hit = new Hit();
        hit.x = x; hit.y = y; hit.w = w; hit.h = h; hit.id = "input|" + id;
        hit.inputId = id;
        hit.tip = hint;
        add(hit);
        seenInputs.add(id);
        boolean want = id.equals(focusId);
        if (eb.isFocused() != want) eb.setFocused(want);
        eb.render(g, mx, my, 0);
        return eb.getValue();
    }

    public String focusedInput() { return focusId; }

    public String value(String id) {
        EditBox e = inputs.get(id);
        return e == null ? "" : e.getValue();
    }

    public void setValue(String id, String v) {
        EditBox e = box(id, true);
        e.setValue(v);
    }

    /** moves the keyboard to another input ("" = none); a number being edited is committed */
    public void setFocus(String id) {
        if (id.equals(focusId)) return;
        String old = focusId;
        focusId = id;
        EditBox oe = inputs.get(old);
        if (oe != null) oe.setFocused(false);
        if (!old.isEmpty() && old.equals(editId)) commitEdit();
        if (!id.isEmpty()) {
            EditBox e = inputs.get(id);
            if (e != null) e.moveCursorToEnd(false);
        }
    }

    public void beginEdit(String id, int x, int y, int w, int h, String initial, Consumer<String> commit) {
        if (!editId.isEmpty() && !editId.equals(id)) commitEdit();
        EditBox eb = box(id, false);
        eb.setMaxLength(12);
        eb.setFilter(s -> s.matches("[-+0-9.,eE]*"));
        eb.setResponder(s -> { });
        eb.setValue(initial.replaceAll("[^0-9.,\\-]", ""));
        editId = id;
        editCommit = commit;
        focusId = id;
        eb.setFocused(true);
        eb.moveCursorToEnd(false);
        eb.setHighlightPos(0);
    }

    private void commitEdit() {
        String id = editId;
        Consumer<String> c = editCommit;
        editId = "";
        editCommit = null;
        EditBox e = inputs.get(id);
        if (c != null && e != null) c.accept(e.getValue());
    }

    public void cancelEdit() {
        String id = editId;
        editId = "";
        editCommit = null;
        if (focusId.equals(id)) focusId = "";
    }

    /** true when a text input has the keyboard */
    public boolean typing() { return !focusId.isEmpty(); }

    public boolean key(int key, int scan, int mods) {
        if (dialog != null) return dialogKeyRaw(key, scan, mods);
        if (popup != null) return popupKey(key);
        if (focusId.isEmpty()) return padKey(key, mods);
        if ((key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN) && focusId.equals(editId) && editFmt != null) { // step the typed number
            EditBox eb = inputs.get(editId);
            if (eb == null) return true;
            double v = 0;
            try { v = Double.parseDouble(eb.getValue().trim().replace(',', '.')); } catch (NumberFormatException ignored) { }
            boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0, ctrl = (mods & GLFW.GLFW_MOD_CONTROL) != 0;
            v += (key == GLFW.GLFW_KEY_UP ? 1 : -1) * editStep * (shift ? 5 : ctrl ? 0.2 : 1);
            double q = editStep / 5.0;
            if (q > 0) v = Math.round(v / q) * q;
            eb.setValue(editFmt.apply(Math.max(editLo, Math.min(editHi, v))).replaceAll("[^0-9.,\\-]", ""));
            eb.setHighlightPos(eb.getCursorPosition());
            return true;
        }
        if (key == 257 || key == 335) { // enter
            if (focusId.equals(editId)) { String id = focusId; focusId = ""; editId = id; commitEdit(); }
            else focusId = "";
            return true;
        }
        if (key == 256) { // escape
            if (focusId.equals(editId)) cancelEdit(); else focusId = "";
            return true;
        }
        EditBox e = inputs.get(focusId);
        if (e == null) return false;
        e.keyPressed(key, scan, mods);
        return true;
    }

    public boolean chr(char c, int mods) {
        if (focusId.isEmpty()) return false;
        EditBox e = inputs.get(focusId);
        if (e == null) return false;
        e.charTyped(c, mods);
        return true;
    }

    // ------------------------------------------------------------------ scroll areas
    public static final class Scroll {
        public int offset, content, view;
    }

    private static final class ScrollCtx {
        int x, y, w, h;
        Scroll s;
        String id;
    }

    private final List<ScrollCtx> scrolls = new ArrayList<>();

    /** Starts a clipped, scrollable area. Draw the content from the returned y (already shifted by the scroll). */
    public int beginScroll(String id, int x, int y, int w, int h, Scroll s, int step) {
        s.view = h;
        s.offset = Math.max(0, Math.min(s.offset, Math.max(0, s.content - h)));
        ScrollCtx c = new ScrollCtx();
        c.x = x; c.y = y; c.w = w; c.h = h; c.s = s; c.id = id;
        scrolls.add(c);
        pushClip(x, y, x + w, y + h);
        Hit wheelHit = new Hit();
        wheelHit.x = x; wheelHit.y = y; wheelHit.w = w; wheelHit.h = h;
        wheelHit.wheel = sy -> s.offset = Math.max(0, Math.min(Math.max(0, s.content - h), s.offset - (int) (sy * step)));
        wheelHit.id = "scroll|" + id;
        add(wheelHit);
        return y - s.offset;
    }

    /** Ends the area; {@code endY} is where the content stopped (the y after the last widget). */
    public void endScroll(int endY) {
        ScrollCtx c = scrolls.remove(scrolls.size() - 1);
        c.s.content = endY - (c.y - c.s.offset);
        popClip();
        if (c.s.content > c.h) {
            int tx = c.x + c.w - 3;
            int th = Math.max(12, c.h * c.h / c.s.content);
            int ty = c.y + (int) ((long) c.s.offset * (c.h - th) / Math.max(1, c.s.content - c.h));
            g.fill(tx, c.y, tx + 3, c.y + c.h, 0x55000000);
            g.fill(tx, ty, tx + 3, ty + th, 0xFF8A8AB0);
            Hit hit = new Hit();
            hit.x = c.x + c.w - 6; hit.y = c.y; hit.w = 6; hit.h = c.h; hit.id = "sb|" + c.id; hit.z = 1;
            final ScrollCtx cc = c;
            hit.drag = (mx0, my0) -> {
                int th0 = Math.max(12, cc.h * cc.h / Math.max(1, cc.s.content));
                int top0 = cc.y + (int) ((long) cc.s.offset * (cc.h - th0) / Math.max(1, cc.s.content - cc.h));
                // grab the thumb where it is; a click on the track centres it
                final double grab = my0 >= top0 && my0 < top0 + th0 ? my0 - top0 : th0 / 2.0;
                if (!(my0 >= top0 && my0 < top0 + th0)) dragScrollbar(cc, my0, grab);
                return (mx1, my1) -> dragScrollbar(cc, my1, grab);
            };
            add(hit);
        }
    }

    private void dragScrollbar(ScrollCtx c, double my, double grab) {
        int th = Math.max(12, c.h * c.h / Math.max(1, c.s.content));
        double f = (my - c.y - grab) / Math.max(1, c.h - th);
        c.s.offset = (int) Math.round(Math.max(0, Math.min(1, f)) * Math.max(0, c.s.content - c.h));
    }

    // ------------------------------------------------------------------ popups (dropdowns, context menus)
    public static final class Item {
        public String label = "";
        public Runnable run;
        public int check = -1;       // -1 none, 0 off, 1 on
        public int icon;
        public boolean enabled = true, sep, header;
        public String reason = "";

        public static Item of(String label, Runnable run) { Item i = new Item(); i.label = label; i.run = run; return i; }
        public static Item sep() { Item i = new Item(); i.sep = true; return i; }
        public static Item head(String label) { Item i = new Item(); i.label = label; i.header = true; return i; }
        public Item check(boolean on) { check = on ? 1 : 0; return this; }
        public Item icon(int ic) { icon = ic; return this; }
        public Item off(String why) { enabled = false; reason = why; return this; }
    }

    private List<Item> popup;
    private int popX, popY;
    /** highlighted row of the popup (-1 none); {@code popupKb}: it was moved by the keyboard, so it gets an outline */
    private int popupSel = -1, popMx, popMy;
    private boolean popupKb;

    private int popupZ() { return Math.max(100, modalZ + 100); }

    public void openPopup(int x, int y, List<Item> items) {
        popup = items.isEmpty() ? null : new ArrayList<>(items);
        popX = x;
        popY = y;
        popupSel = -1;
        popupKb = false;
        popMx = mx;
        popMy = my;
    }

    public void closePopup() { popup = null; }

    public boolean popupOpen() { return popup != null; }

    private static boolean pickable(Item i) { return !i.sep && !i.header && i.enabled; }

    private void popupMove(int dir) {
        int n = popup.size(), i = popupSel;
        if (i < 0 && dir < 0) i = 0;
        for (int t = 0; t < n; t++) {
            i = dir > 0 ? (i + 1) % n : (i - 1 + n) % n;
            if (pickable(popup.get(i))) { popupSel = i; popupKb = true; return; }
        }
    }

    private void popupEdge(boolean last) {
        int n = popup.size();
        for (int t = 0; t < n; t++) {
            int i = last ? n - 1 - t : t;
            if (pickable(popup.get(i))) { popupSel = i; popupKb = true; return; }
        }
    }

    /** an open popup owns the keyboard: Up / Down / Home / End move the highlight, Enter / Space run it, Esc closes */
    private boolean popupKey(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> closePopup();
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_TAB -> popupMove(1);
            case GLFW.GLFW_KEY_UP -> popupMove(-1);
            case GLFW.GLFW_KEY_HOME, GLFW.GLFW_KEY_PAGE_UP -> popupEdge(false);
            case GLFW.GLFW_KEY_END, GLFW.GLFW_KEY_PAGE_DOWN -> popupEdge(true);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                if (popupSel >= 0 && popupSel < popup.size() && pickable(popup.get(popupSel))) {
                    Item it = popup.get(popupSel);
                    closePopup();
                    if (it.run != null) it.run.run();
                }
            }
            default -> { }
        }
        return true;
    }

    /** Draws the open popup on top of everything. */
    public void drawPopup(int screenW, int screenH) {
        if (popup == null) return;
        int w = 80;
        for (Item i : popup) if (!i.sep) w = Math.max(w, font.width(i.label) + 34);
        int h = 4;
        for (Item i : popup) h += i.sep ? 5 : 13;
        int x = Math.max(2, Math.min(screenW - w - 2, popX)), y = Math.max(2, Math.min(screenH - h - 2, popY));
        int saveZ = baseZ;
        baseZ = popupZ();
        g.pose().pushPose();
        g.pose().translate(0, 0, 700);
        Hit box = new Hit();
        box.x = x; box.y = y; box.w = w; box.h = h; box.id = "popup-box";
        add(box);
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, EDGE_HI);
        g.fill(x, y, x + w, y + h, 0xFF16161E);
        int cy = y + 2;
        boolean moved = mx != popMx || my != popMy;
        popMx = mx;
        popMy = my;
        if (popupSel >= popup.size()) popupSel = -1;
        for (int idx = 0; idx < popup.size(); idx++) {
            Item i = popup.get(idx);
            if (i.sep) { g.fill(x + 3, cy + 2, x + w - 3, cy + 3, EDGE); cy += 5; continue; }
            Hit hit = new Hit();
            hit.x = x; hit.y = cy; hit.w = w; hit.h = 13;
            hit.id = "pop|" + i.label;
            hit.enabled = i.enabled && !i.header;
            hit.reason = i.reason;
            if (!i.header) hit.press = () -> { closePopup(); if (i.run != null) i.run.run(); };
            add(hit);
            // the mouse moves the highlight only when it really moved, so the keyboard can use it while the mouse rests
            if (moved && i.enabled && !i.header && isHover(hit.id)) { popupSel = idx; popupKb = false; }
            if (idx == popupSel && pickable(i)) {
                g.fill(x + 1, cy, x + w - 1, cy + 13, 0xFF3A3A5C);
                if (popupKb) border(x + 1, cy, w - 2, 13, FOCUS);
            }
            int tx = x + 6;
            if (i.check >= 0) {
                g.fill(tx, cy + 3, tx + 8, cy + 11, 0xFF8A8AB0);
                g.fill(tx + 1, cy + 4, tx + 7, cy + 10, 0xFF101018);
                if (i.check == 1) g.fill(tx + 2, cy + 5, tx + 6, cy + 9, GOOD);
                tx += 12;
            }
            if (i.icon != 0) { icon(g, i.icon, tx, cy + 2, i.enabled ? TEXT : FAINT); tx += iconW(i.icon) + 4; }
            Ui.drawFit(g, font, i.label, tx, cy, x + w - 4 - tx, 13, false, i.header ? DIM : i.enabled ? TEXT : FAINT);
            cy += 13;
        }
        g.pose().popPose();
        baseZ = saveZ;
    }

    // ------------------------------------------------------------------ dialogs
    public static final class Dialog {
        public String title = "", text = "", yes = "OK", no = "Cancel";
        public String[] checks = new String[0];
        public boolean[] vals = new boolean[0];
        public String inputId = "", inputHint = "";
        /** what was typed, filled in right before {@link #onYes} runs */
        public String input = "";
        public Runnable onYes;
        public boolean info;
        /** shortcut sheet instead of plain text: {tokens, description} rows, {"#", heading} for headings; see {@link Kit#helpRow} */
        public List<String[]> help;
        public final Scroll helpScroll = new Scroll();
    }

    public Dialog dialog;
    /** keyboard focus inside the dialog: [text input] [checkboxes...] [yes] [no] */
    private int dlgFocus;

    private static int dlgYes(Dialog d) { return (d.inputId.isEmpty() ? 0 : 1) + d.checks.length; }

    private static int dlgCount(Dialog d) { return dlgYes(d) + (d.info ? 1 : 2); }

    private void dlgMove(Dialog d, int dir) {
        int n = dlgCount(d);
        dlgFocus = Math.floorMod((focusId.equals(d.inputId) && !d.inputId.isEmpty() ? 0 : dlgFocus) + dir, n);
        setFocus(!d.inputId.isEmpty() && dlgFocus == 0 ? d.inputId : "");
    }

    public void openDialog(Dialog d) {
        colors.close();
        if (d.vals.length < d.checks.length) d.vals = java.util.Arrays.copyOf(d.vals, d.checks.length);
        dialog = d;
        modalZ = 200;
        closePopup();
        padFocus = "";
        // typing starts at once when there is an input, otherwise Enter lands on the confirm button
        dlgFocus = d.inputId.isEmpty() ? dlgYes(d) : 0;
        setFocus(d.inputId);
        if (!d.inputId.isEmpty()) {
            box(d.inputId, true);
            setValue(d.inputId, "");
        }
    }

    public void closeDialog() {
        dialog = null;
        modalZ = 0;
        setFocus("");
    }

    private void confirmDialog(Dialog d) {
        if (dialog != d) return;
        d.input = d.inputId.isEmpty() ? "" : value(d.inputId);
        Runnable a = d.onYes;
        closeDialog();
        if (a != null) a.run();
    }

    private int[] dlgRect(Dialog d, int sw, int sh) {
        int w = Math.min(sw - 20, 320);
        int lines = d.text.isEmpty() ? 0 : font.split(Component.literal(d.text), w - 20).size();
        int h = 40 + lines * 10 + (d.checks.length > 0 ? d.checks.length * 14 + 6 : 0) + (d.inputId.isEmpty() ? 0 : 20) + 26;
        h = Math.min(h, sh - 6);
        return new int[] { (sw - w) / 2, (sh - h) / 2, w, h, lines };
    }

    public void drawDialog(int sw, int sh) {
        Dialog d = dialog;
        if (d == null) return;
        int saveZ = baseZ;
        baseZ = 200;
        int[] r = dlgRect(d, sw, sh);
        if (d.help != null) {
            int hw = Math.min(sw - 20, 430), hh = Math.min(sh - 12, 330);
            r = new int[] { (sw - hw) / 2, (sh - hh) / 2, hw, hh, 0 };
        }
        g.pose().pushPose();
        g.pose().translate(0, 0, 750);
        Hit back = new Hit();
        back.x = 0; back.y = 0; back.w = sw; back.h = sh; back.id = "dlg-back";
        add(back);
        g.fill(0, 0, sw, sh, 0xB0000000);
        g.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1, EDGE_HI);
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFF1C1C26);
        g.drawString(font, d.title, r[0] + 10, r[1] + 10, ACCENT, false);
        int y = r[1] + 28;
        if (d.help != null) {
            int bx = r[0] + 8, bw0 = r[2] - 16, by0 = r[1] + 24, bh0 = r[3] - 24 - 28;
            int cy = beginScroll("help", bx, by0, bw0, bh0, d.helpScroll, 14);
            for (String[] row : d.help) cy = helpRow(bx + 2, cy, bw0 - 10, row);
            endScroll(cy + 2);
        } else if (!d.text.isEmpty()) {
            for (var line : font.split(Component.literal(d.text), r[2] - 20)) {
                if (y > r[1] + r[3] - 34 - d.checks.length * 14) break;
                g.drawString(font, line, r[0] + 10, y, 0xFFDDDDEE, false);
                y += 10;
            }
        }
        y += 6;
        for (int i = 0; i < d.checks.length; i++) {
            final int k = i;
            int rowY = y + i * 14;
            Hit row = new Hit();
            row.x = r[0] + 8; row.y = rowY - 2; row.w = r[2] - 16; row.h = 14; row.id = "dlg-check" + i;
            final int fidx = (d.inputId.isEmpty() ? 0 : 1) + i;
            row.press = () -> { d.vals[k] = !d.vals[k]; dlgFocus = fidx; setFocus(""); };
            add(row);
            g.fill(r[0] + 10, rowY, r[0] + 20, rowY + 10, 0xFF8A8AB0);
            g.fill(r[0] + 11, rowY + 1, r[0] + 19, rowY + 9, 0xFF101018);
            if (d.vals[i]) g.fill(r[0] + 12, rowY + 2, r[0] + 18, rowY + 8, GOOD);
            g.drawString(font, d.checks[i], r[0] + 26, rowY + 1, 0xFFDDDDEE, false);
            if (dlgFocus == fidx && !focusId.equals(d.inputId)) border(r[0] + 8, rowY - 2, r[2] - 16, 14, FOCUS);
        }
        if (d.checks.length > 0) y += d.checks.length * 14;
        if (!d.inputId.isEmpty()) input(d.inputId, r[0] + 10, y, r[2] - 20, 14, d.inputHint, s -> { });
        int by = r[1] + r[3] - 22, bw = 90;
        int yx = r[0] + r[2] / 2 - bw - 6, nx = r[0] + r[2] / 2 + 6;
        if (d.info) yx = r[0] + (r[2] - bw) / 2;
        b(yx, by, bw, 16).label(d.yes).id("dlg-yes").accent().press(() -> confirmDialog(d)).show();
        if (!d.info) b(nx, by, bw, 16).label(d.no).id("dlg-no").press(() -> { if (dialog == d) closeDialog(); }).show();
        if (!focusId.equals(d.inputId) || d.inputId.isEmpty()) {
            if (dlgFocus == dlgYes(d)) border(yx - 1, by - 1, bw + 2, 18, FOCUS);
            else if (!d.info && dlgFocus == dlgYes(d) + 1) border(nx - 1, by - 1, bw + 2, 18, FOCUS);
        }
        g.pose().popPose();
        baseZ = saveZ;
    }

    private static final int KEYCAP = 0xFF3A3A4E, KEYCAP_EDGE = 0xFF121218, KEYCAP_TOP = 0xFF5A5A74;

    /** a key drawn as a small cap; returns the x after it */
    private int keycap(String t, int x, int y) {
        int w = Math.max(13, font.width(t) + 8);
        g.fill(x, y + 1, x + w, y + 14, KEYCAP_EDGE);
        g.fill(x, y, x + w, y + 12, KEYCAP_TOP);
        g.fill(x + 1, y + 1, x + w - 1, y + 12, KEYCAP);
        g.drawString(font, t, x + (w - font.width(t)) / 2, y + 2, 0xFFEDEDF8, false);
        return x + w;
    }

    /** a mouse: 'L' left, 'R' right, 'M' middle, 'W' wheel (with up/down marks), 'D' left button held (with arrows) */
    private int mouseIcon(char b, int x, int y) {
        int hi = ACCENT, body = 0xFF6A6A84, fill = 0xFF22222E;
        g.fill(x, y, x + 11, y + 15, body);
        g.fill(x + 1, y + 1, x + 10, y + 14, fill);
        g.fill(x + 1, y + 6, x + 10, y + 7, body);         // buttons / palm split
        g.fill(x + 5, y + 1, x + 6, y + 7, body);          // left / right split
        switch (b) {
            case 'L', 'D' -> g.fill(x + 1, y + 1, x + 5, y + 6, hi);
            case 'R' -> g.fill(x + 6, y + 1, x + 10, y + 6, hi);
            case 'M', 'W' -> g.fill(x + 4, y + 1, x + 7, y + 6, hi);
            default -> { }
        }
        int nx = x + 14;
        if (b == 'W') { g.drawString(font, "↑↓", nx, y + 3, 0xFFB0B0C8, false); nx += font.width("↑↓") + 1; }
        if (b == 'D') { g.drawString(font, "↔", nx, y + 3, 0xFFB0B0C8, false); nx += font.width("↔") + 1; }
        return nx;
    }

    /** one line of the shortcut sheet: tokens like "Ctrl", "+", "@L" (mouse) then the description */
    private int helpRow(int x, int y, int w, String[] row) {
        if ("#".equals(row[0])) return header(x, y + 4, w, row[1]) + 3;
        int colW = Math.min(170, w * 2 / 5);
        int cx = x;
        for (String tok : row[0].split("[|]")) {
            if (tok.isEmpty()) continue;
            if (tok.startsWith("@") && tok.length() == 2) cx = mouseIcon(tok.charAt(1), cx, y) + 3;
            else if (tok.equals("plus")) cx = keycap("+", cx, y + 1) + 3;
            else if (tok.equals("minus")) cx = keycap("-", cx, y + 1) + 3;
            else if (tok.equals("+")) { g.drawString(font, "+", cx, y + 3, DIM, false); cx += font.width("+") + 3; }
            else cx = keycap(tok, cx, y + 1) + 3;
        }
        int tx = x + colW;
        var lines = font.split(Component.literal(row[1]), w - colW);
        int ly = y + 3;
        for (var line : lines) { g.drawString(font, line, tx, ly, 0xFFDDDDEE, false); ly += 10; }
        return Math.max(y + 17, ly + 4);
    }

    /**
     * Keys of an open dialog. Esc cancels, Enter confirms (or cancels when the "no" button has the focus), Tab / Shift+Tab
     * move between input, checkboxes and buttons (Left / Right / Up / Down too, unless the text input has the keyboard),
     * Space toggles the checkbox or presses the button. The shortcut sheet scrolls with Up / Down / PgUp / PgDn / Home / End.
     */
    public boolean dialogKeyRaw(int key, int scan, int mods) {
        Dialog d = dialog;
        if (d == null) return false;
        boolean hasIn = !d.inputId.isEmpty(), typing = hasIn && focusId.equals(d.inputId);
        if (typing) dlgFocus = 0;
        if (key == GLFW.GLFW_KEY_ESCAPE) { closeDialog(); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            if (!d.info && dlgFocus == dlgYes(d) + 1) closeDialog(); else confirmDialog(d);
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) { dlgMove(d, (mods & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1); return true; }
        if (d.help != null) {
            Scroll sc = d.helpScroll;
            switch (key) {
                case GLFW.GLFW_KEY_UP -> sc.offset -= 14;
                case GLFW.GLFW_KEY_DOWN -> sc.offset += 14;
                case GLFW.GLFW_KEY_PAGE_UP -> sc.offset -= Math.max(14, sc.view - 14);
                case GLFW.GLFW_KEY_PAGE_DOWN -> sc.offset += Math.max(14, sc.view - 14);
                case GLFW.GLFW_KEY_HOME -> sc.offset = 0;
                case GLFW.GLFW_KEY_END -> sc.offset = Integer.MAX_VALUE / 2;
                default -> { }
            }
            return true;
        }
        if (typing) {
            EditBox e = inputs.get(d.inputId);
            if (e != null) e.keyPressed(key, scan, mods);
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_UP -> dlgMove(d, -1);
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_DOWN -> dlgMove(d, 1);
            case GLFW.GLFW_KEY_SPACE -> {
                int ci = dlgFocus - (hasIn ? 1 : 0);
                if (ci >= 0 && ci < d.checks.length) d.vals[ci] = !d.vals[ci];
                else if (dlgFocus == dlgYes(d)) confirmDialog(d);
                else if (!d.info && dlgFocus == dlgYes(d) + 1) closeDialog();
            }
            default -> { }
        }
        return true;
    }

    // ------------------------------------------------------------------ misc widgets
    /** wrapped text; returns the y below it */
    public int wrap(String text, int x, int y, int w, int color, int maxLines) {
        int n = 0;
        for (FormattedCharSequence line : font.split(Component.literal(text), w)) {
            if (n++ >= maxLines) break;
            g.drawString(font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    /** a small section header line */
    public int header(int x, int y, int w, String title) {
        g.fill(x, y + 4, x + 3, y + 5, EDGE_HI);
        int tw = font.width(title);
        g.drawString(font, title, x + 6, y, DIM, false);
        g.fill(x + 10 + tw, y + 4, x + w, y + 5, EDGE);
        return y + 12;
    }
}
