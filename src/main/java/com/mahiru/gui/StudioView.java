package com.mahiru.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Everything the studio draws around the canvas: top bar, library, layers, inspector, menu picker, status bar. */
final class StudioView {
    private static final int RH = 14;      // standard row height
    private static final int TOP = Studio.TOP, BOT = Studio.BOT;

    // the art being dragged out of the library (ghost under the cursor)
    private static Arts.Art ghost;
    private static double ghostX, ghostY;

    /** library grid cell height and gap (the keyboard selection scrolls with them) */
    static final int GRID_CH = 44, GRID_GAP = 3;

    private StudioView() {}

    static void clearGhost() { ghost = null; }

    private static int clampI(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ------------------------------------------------------------------ frame

    static void draw(Studio s, GuiGraphics g, int mx, int my, float pt) {
        Kit k = s.kit;
        int W = s.width, H = s.height;
        boolean compact = W < 400;
        int leftW = compact ? 0 : clampI(Math.round(W * 0.25f), 104, 190);
        int rightW = clampI(Math.round(W * 0.30f), 118, 240);
        k.rect(0, 0, W, H, Kit.BG);

        // the canvas first, so that its hits are the lowest ones
        s.canvas.draw(g, k, leftW, TOP, W - leftW - rightW, H - TOP - BOT, pt);

        if (!compact) library(s, k, 0, TOP, leftW, H - TOP - BOT);
        right(s, k, W - rightW, TOP, rightW, H - TOP - BOT);
        statusBar(s, k, W, H);
        if (compact && s.libOverlay) {
            k.zLevel(50);
            g.pose().pushPose();
            g.pose().translate(0, 0, 400);
            library(s, k, 0, TOP, Math.min(150, W - 40), H - TOP - BOT);
            g.pose().popPose();
            k.zLevel(0);
        }
        topBar(s, k, W, compact);
        if (s.pickerOpen) picker(s, k, W, H);

        if (ghost != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 600);
            int tw = 28, th = Math.max(8, Math.round(28f * ghost.sh / ghost.sw));
            if (th > 40) { th = 40; tw = Math.max(8, Math.round(40f * ghost.sw / ghost.sh)); }
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            g.setColor(1f, 1f, 1f, 0.8f);
            ghost.drawSmall(g, (int) ghostX - tw / 2, (int) ghostY - th / 2, tw, th, false);
            g.setColor(1f, 1f, 1f, 1f);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ top bar

    private static void topBar(Studio s, Kit k, int W, boolean compact) {
        k.rect(0, 0, W, TOP, Kit.PANEL2);
        k.rect(0, TOP - 1, W, 1, Kit.EDGE);
        int x = 4, y = 2, h = TOP - 4;
        String title = compact ? "Zaec" : "Zaec Studio";
        k.text(title, x, y + 3, Kit.ACCENT);
        x += k.font.width(title) + 6;
        Settings st = s.st();
        k.b(x, y, 50, h).label("Mod").tip(T.t("Master switch: OFF = the mod draws and moves nothing (F10 also toggles it in the game)"))
                .sw(st.enabled).id("master").press(() -> { st.enabled = !st.enabled; s.touch(); s.flush(); }).show();
        x += 54;
        if (compact) {
            k.b(x, y, 14, h).icon(Kit.LAYERS).tip(T.t("Show or hide the library of arts")).on(s.libOverlay).id("lib-toggle")
                    .press(() -> s.libOverlay = !s.libOverlay).show();
            x += 18;
        }
        // menu picker: [<] [Menu name v] [>]
        int pickW = clampI(W - x - 4 - (compact ? 146 : 192), 70, 150);
        k.b(x, y, 12, h).icon(Kit.ARROW_L).tip(T.t("Previous menu")).id("menu-prev").press(() -> s.stepMenu(-1)).show();
        String name = Studio.menuTitle(s.menuKey);
        k.b(x + 14, y, pickW, h).label(name).icon(Kit.CARET).tip(T.t("Choose the menu to edit")).id("menu-pick")
                .on(s.pickerOpen).press(() -> {
                    s.pickerOpen = !s.pickerOpen;
                    s.pickerScroll.offset = 0;
                    if (s.pickerOpen) { s.kit.setValue("picker-search", ""); s.kit.setFocus("picker-search"); s.pickerSel = s.menuKey; s.pickerQuery = ""; s.pickerReveal = true; }
                }).show();
        k.b(x + 16 + pickW, y, 12, h).icon(Kit.ARROW_R).tip(T.t("Next menu")).id("menu-next").press(() -> s.stepMenu(1)).show();
        x += pickW + 32;
        k.b(x, y, 14, h).icon(Kit.UNDO).tip(s.undoCount() > 1 ? T.f("Undo (Ctrl+Z), steps: %s", s.undoCount()) : T.t("Undo (Ctrl+Z)")).enabled(s.canUndo(), T.t("Nothing to undo")).id("undo").press(s::undo).show();
        k.b(x + 16, y, 14, h).icon(Kit.REDO).tip(s.redoCount() > 1 ? T.f("Redo (Ctrl+Y), steps: %s", s.redoCount()) : T.t("Redo (Ctrl+Y)")).enabled(s.canRedo(), T.t("Nothing to redo")).id("redo").press(s::redo).show();
        x += 34;
        // right side: Options, ?, Close
        int rx = W - 4;
        rx -= 40;
        k.b(rx, y, 40, h).label(T.t("Close")).tip(T.t("Back to the game (Esc or F9)")).id("close").press(s::onClose).show();
        rx -= 16;
        k.b(rx, y, 14, h).icon(Kit.HELP).tip(T.t("Shortcuts (F1)")).id("help").press(() -> helpDialog(s)).show();
        rx -= 18;
        int ow = compact ? 14 : 62;
        rx -= ow - 14;
        k.b(rx, y, ow, h).icon(Kit.GEAR).label(compact ? "" : T.t("Options")).tip(T.t("Skin, gear button, export and import")).id("options")
                .press(() -> k.openPopup(k.mx - 20, y + h + 2, optionsMenu(s))).show();
    }

    private static List<Kit.Item> optionsMenu(Studio s) {
        Settings st = s.st();
        List<Kit.Item> l = new ArrayList<>();
        l.add(Kit.Item.of(T.t("Gear button in menus"), () -> { st.showGear = !st.showGear; s.touch(); }).check(st.showGear));
        l.add(Kit.Item.of(T.t("Put the gear button back"), () -> { st.gearFx = Settings.GEAR_DEFAULT; st.gearFy = Settings.GEAR_DEFAULT; s.touch(); }));
        l.add(Kit.Item.sep());
        Kit.Item discard = Kit.Item.of(T.t("Discard the changes of this session"), s::discardDialog).icon(Kit.RESET);
        if (!s.changedThisSession()) discard.off(T.t("Nothing has changed yet"));
        l.add(discard);
        l.add(Kit.Item.sep());
        l.add(Kit.Item.of(T.t("Export setup (zip)"), s::doExport).icon(Kit.FOLDER));
        l.add(Kit.Item.of(T.t("Open the exports folder"), () -> SetupZip.openFolder(SetupZip.exportDir())).icon(Kit.FOLDER));
        l.add(Kit.Item.head(T.t("Drop a setup .zip or pictures on the window to import")));
        l.add(Kit.Item.sep());
        l.add(Kit.Item.of(T.t("Shortcuts"), () -> helpDialog(s)).icon(Kit.HELP));
        return l;
    }

    static void helpDialog(Studio s) {
        Kit.Dialog d = new Kit.Dialog();
        d.title = T.t("Shortcuts");
        d.info = true;
        d.yes = T.t("Close");
        List<String[]> h = new ArrayList<>();
        h.add(new String[] { "#", T.t("Mouse") });
        h.add(new String[] { "@D", T.t("Drag the art to move it. Corner handles resize, the round handle rotates") });
        h.add(new String[] { "@W", T.t("Zoom the view") });
        h.add(new String[] { "Ctrl|+|@W", T.t("Scale the art") });
        h.add(new String[] { "Shift|+|@W", T.t("Rotate the art") });
        h.add(new String[] { "@M", T.t("Hold the middle button and drag to pan the view") });
        h.add(new String[] { "@R", T.t("Menu of the layer, the art or the canvas") });
        h.add(new String[] { "@D", T.t("Drag an art from the library onto the menu: a new layer") });
        h.add(new String[] { "#", T.t("The selected art") });
        h.add(new String[] { "←|↑|↓|→", T.t("Nudge (Shift = 5 pixels)") });
        h.add(new String[] { "plus|minus", T.t("Scale") });
        h.add(new String[] { "[|]", T.t("Rotate") });
        h.add(new String[] { "H|V", T.t("Flip left-right / top-bottom") });
        h.add(new String[] { "R", T.t("Another random art for the layer") });
        h.add(new String[] { ",|.", T.t("Previous / next art of the pool") });
        h.add(new String[] { "#", T.t("Layers and menus") });
        h.add(new String[] { "PgUp|PgDn", T.t("Move the layer up / down") });
        h.add(new String[] { "Ctrl|+|↑|↓", T.t("Select the layer above / below") });
        h.add(new String[] { "Ctrl|+|D", T.t("Duplicate the layer") });
        h.add(new String[] { "Del", T.t("Delete the layer") });
        h.add(new String[] { "Ctrl|+|←|→", T.t("Previous / next menu") });
        h.add(new String[] { "N", T.t("Next menu where this art has no saved position") });
        h.add(new String[] { "#", T.t("General") });
        h.add(new String[] { "Ctrl|+|Z", T.t("Undo (every menu has its own history)") });
        h.add(new String[] { "Ctrl|+|Y", T.t("Redo") });
        h.add(new String[] { "Tab", T.t("Player view: the menu as it looks in the game") });
        h.add(new String[] { "0", T.t("Fit the view to the window") });
        h.add(new String[] { "F1", T.t("Open / close this sheet") });
        h.add(new String[] { "F9", T.t("Close the studio") });
        h.add(new String[] { "Esc", T.t("Close the top layer first: menu, window, text field, menu picker; then the studio") });
        h.add(new String[] { "#", T.t("Keyboard in lists and windows") });
        h.add(new String[] { "↑|↓", T.t("Menu picker, dropdowns and context menus: move the highlight (type to search in the picker)") });
        h.add(new String[] { "Enter", T.t("Choose the highlighted item") });
        h.add(new String[] { "Space", T.t("Dropdowns and context menus: choose the item. Windows: tick the box or press the button") });
        h.add(new String[] { "Tab|/|Shift|+|Tab", T.t("Window: move between the text field, boxes and buttons (the arrow keys too)") });
        h.add(new String[] { "Enter", T.t("Window: confirm. With the Cancel button outlined it cancels") });
        h.add(new String[] { "↑|↓|PgUp|PgDn", T.t("This sheet: scroll") });
        h.add(new String[] { "@L|+|↑|↓", T.t("After a click on a slider or number (it gets an outline): one step; Shift = 5 steps. Sliders also take Left and Right") });
        h.add(new String[] { "↑|↓", T.t("While typing a number: change the typed value (Shift = 5 steps, Ctrl = fine)") });
        h.add(new String[] { "←|↑|↓|→", T.t("Mouse over the library (outlined): select the neighbour art, Shift extends the selection. Elsewhere these keys nudge the art") });
        h.add(new String[] { "#", T.t("Files") });
        h.add(new String[] { "@D", T.t("Drop pictures (PNG, JPG, GIF) on the window to import them as arts, or a setup .zip to merge it") });
        h.add(new String[] { "#", T.t("Good to know") });
        h.add(new String[] { "", T.t("Undo covers layers, positions, groups and the pool switch.") });
        d.help = h;
        s.kit.openDialog(d);
    }

    // ------------------------------------------------------------------ menu picker

    /** picker sections: {heading, keys...}; menus that share a layout sit together, "@" entries are actions, not menus */
    private static final String[][] PICKER = {
            { "Main menu", Studio.TITLE_KEY },
            { "Inventory", "inventory", "creative", "creative_inventory" },
            { "Furnaces (same layout)", "furnace", "blast_furnace", "smoker" },
            { "Chests (same layout)", "generic_54_1", "generic_54_2", "generic_54_3", "generic_54_4", "generic_54_5", "generic_54_6", "shulker_box" },
            { "Crafting", "crafting_table", "crafter", "stonecutter", "loom", "smithing", "grindstone", "cartography_table" },
            { "Workstations", "anvil", "enchanting_table", "brewing_stand", "beacon" },
            { "Containers", "dispenser", "hopper" },
            { "Mobs", "villager", "horse" },
            { "Special", "@share", "@unshare" } };

    /** brings the keyboard-highlighted row into view (the scroll takes effect from the next frame) */
    private static void reveal(Studio s, String key, int rowY, int base, int view, Kit.Scroll sc) {
        if (!s.pickerReveal || !key.equals(s.pickerSel)) return;
        s.pickerReveal = false;
        int rel = rowY - base;
        if (rel - 13 < sc.offset) sc.offset = Math.max(0, rel - 13);
        else if (rel + 13 > sc.offset + view) sc.offset = rel + 13 - view;
    }

    private static String specialTitle(String key) {
        return "@share".equals(key) ? T.t("Inventory style for all menus") : T.t("Remove shared inventory layers");
    }

    /** an action row of the "Special" section */
    private static int specialRow(Studio s, Kit k, String key, int x, int cy, int w, boolean sel) {
        boolean share = "@share".equals(key);
        boolean on = share ? s.canShareInventory() : s.hasSharedInventory();
        Kit.Hit hit = new Kit.Hit();
        hit.x = x; hit.y = cy; hit.w = w - 6; hit.h = 13; hit.id = "pk|" + key;
        hit.enabled = on;
        hit.reason = share ? T.t("Select a layer in a menu that has an inventory block first") : T.t("No shared inventory layers yet");
        hit.tip = share ? T.t("Changes the inventory block in all menus: copies the selected layer over it everywhere. Other layers are kept")
                : T.t("Removes the layers that were spread over the inventory blocks of all menus");
        hit.press = () -> { s.pickerOpen = false; if (share) s.shareInventoryDialog(); else s.unshareInventoryDialog(); };
        k.add(hit);
        if (sel && on) { k.rect(x + 1, cy, w - 8, 13, 0xFF2C2C42); k.border(x + 1, cy, w - 8, 13, Kit.FOCUS); }
        k.rect(x + 9, cy + 4, 5, 5, on ? Kit.ACCENT : 0xFF55556A);
        Ui.drawFit(k.g, k.font, specialTitle(key), x + 18, cy, w - 24, 13, false, on ? Kit.TEXT : Kit.FAINT);
        return cy + 13;
    }

    /** the rows the picker shows for a search text, in order: menu keys and the "@" actions that can run now */
    private static List<String> pickerList(Studio s, String q) {
        List<String> out = new ArrayList<>();
        for (String[] grp : PICKER) {
            boolean special = grp[1].startsWith("@");
            for (int gi = 1; gi < grp.length; gi++) {
                String key = grp[gi];
                String title = special ? specialTitle(key) : Studio.menuTitle(key);
                if (!q.isEmpty() && !title.toLowerCase(java.util.Locale.ROOT).contains(q)) continue;
                if (special && !("@share".equals(key) ? s.canShareInventory() : s.hasSharedInventory())) continue;
                out.add(key);
            }
        }
        return out;
    }

    /**
     * Keys of the open menu picker (the search box keeps the typing): Up / Down / PgUp / PgDn move the highlighted row,
     * Home / End jump while the search is empty, Enter picks it, Esc closes.
     */
    static boolean pickerKey(Studio s, int key, int mods) {
        Kit k = s.kit;
        if (key == GLFW.GLFW_KEY_ESCAPE) { s.pickerOpen = false; k.setFocus(""); return true; }
        String q = k.value("picker-search").trim().toLowerCase(java.util.Locale.ROOT);
        List<String> vis = pickerList(s, q);
        int i = vis.indexOf(s.pickerSel), n = vis.size();
        int to = -2;
        switch (key) {
            case GLFW.GLFW_KEY_DOWN -> to = i < 0 ? 0 : (i + 1) % Math.max(1, n);
            case GLFW.GLFW_KEY_UP -> to = i < 0 ? n - 1 : (i - 1 + n) % Math.max(1, n);
            case GLFW.GLFW_KEY_PAGE_DOWN -> to = Math.min(n - 1, Math.max(0, i) + 8);
            case GLFW.GLFW_KEY_PAGE_UP -> to = Math.max(0, Math.max(0, i) - 8);
            case GLFW.GLFW_KEY_HOME -> { if (q.isEmpty()) to = 0; }
            case GLFW.GLFW_KEY_END -> { if (q.isEmpty()) to = n - 1; }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (i >= 0) pickerPick(s, vis.get(i));
                return true;
            }
            default -> { }
        }
        if (to == -2) return false;
        if (n > 0 && to >= 0 && to < n) { s.pickerSel = vis.get(to); s.pickerReveal = true; }
        return true;
    }

    private static void pickerPick(Studio s, String key) {
        s.pickerOpen = false;
        s.kit.setFocus("");
        if ("@share".equals(key)) s.shareInventoryDialog();
        else if ("@unshare".equals(key)) s.unshareInventoryDialog();
        else s.selectMenu(key);
    }

    private static void picker(Studio s, Kit k, int W, int H) {
        // everything outside the picker closes it
        k.zLevel(99);
        Kit.Hit back = new Kit.Hit();
        back.x = 0; back.y = 0; back.w = W; back.h = H; back.id = "picker-back";
        back.press = () -> s.pickerOpen = false;
        k.add(back);
        k.zLevel(100);
        int w = 190, x = Math.max(4, Math.min(W - w - 4, 70));
        int y = TOP;
        int rows = Studio.menuKeys().size() + PICKER.length + 3;
        int h = Math.min(H - TOP - BOT - 4, 18 + rows * 13 + 4);
        Kit.Hit box = new Kit.Hit();
        box.x = x; box.y = y; box.w = w; box.h = h; box.id = "picker-box";
        k.add(box);
        s.kit.g.pose().pushPose();
        s.kit.g.pose().translate(0, 0, 400);
        k.rect(x - 1, y - 1, w + 2, h + 2, Kit.EDGE_HI);
        k.rect(x, y, w, h, 0xFF16161E);
        // typing always goes to the search box while the picker is open (a dialog or popup may have taken the focus away)
        if (k.focusId.isEmpty() && k.dialog == null && !k.popupOpen()) k.setFocus("picker-search");
        String q = k.input("picker-search", x + 3, y + 3, w - 6, 12, T.t("search the menus"), v -> { }).trim().toLowerCase(java.util.Locale.ROOT);
        List<String> vis = pickerList(s, q);
        if (!q.equals(s.pickerQuery)) { s.pickerQuery = q; s.pickerSel = vis.isEmpty() ? "" : vis.get(0); s.pickerReveal = true; }
        else if (!vis.contains(s.pickerSel)) s.pickerSel = vis.isEmpty() ? "" : vis.get(0);
        boolean moved = k.mx != s.pickerMx || k.my != s.pickerMy;
        s.pickerMx = k.mx;
        s.pickerMy = k.my;
        int ly = y + 18;
        int base = k.beginScroll("picker", x, ly, w, h - 18, s.pickerScroll, 13);
        int cy = base;
        for (String[] grp : PICKER) {
            boolean special = grp[1].startsWith("@");
            List<String> shown = new ArrayList<>();
            for (int gi = 1; gi < grp.length; gi++) {
                String key = grp[gi];
                String title = special ? specialTitle(key) : Studio.menuTitle(key);
                if (q.isEmpty() || title.toLowerCase(java.util.Locale.ROOT).contains(q)) shown.add(key);
            }
            if (shown.isEmpty()) continue;
            k.text(T.t(grp[0]).toUpperCase(java.util.Locale.ROOT), x + 5, cy + 3, Kit.DIM);
            cy += 13;
            for (String key : shown) {
                if (special) {
                    if (moved && k.hitAt(k.mx, k.my) != null && k.hitAt(k.mx, k.my).id.equals("pk|" + key)) s.pickerSel = key;
                    reveal(s, key, cy, base, h - 18, s.pickerScroll);
                    cy = specialRow(s, k, key, x, cy, w, key.equals(s.pickerSel));
                    continue;
                }
                String title = Studio.menuTitle(key);
                boolean cur = key.equals(s.menuKey);
                boolean disabled = !Studio.TITLE_KEY.equals(key) && s.st().menuView(key).disabled;
                int[] cal = Studio.TITLE_KEY.equals(key) ? null : s.calibration(key);
                final String fk = key;
                Kit.Hit hit = new Kit.Hit();
                hit.x = x; hit.y = cy; hit.w = w - 6; hit.h = 13; hit.id = "pk|" + key;
                hit.press = () -> { s.selectMenu(fk); s.pickerOpen = false; };
                hit.tip = disabled ? T.t("Art is switched off in this menu") : "";
                k.add(hit);
                boolean hov = k.hitAt(k.mx, k.my) != null && k.hitAt(k.mx, k.my).id.equals(hit.id);
                if (moved && hov) s.pickerSel = key;
                reveal(s, key, cy, base, h - 18, s.pickerScroll);
                boolean sel = key.equals(s.pickerSel);
                if (cur) k.rect(x + 1, cy, w - 8, 13, 0xFF3A3A58);
                else if (sel) k.rect(x + 1, cy, w - 8, 13, 0xFF2C2C42);
                if (sel) k.border(x + 1, cy, w - 8, 13, Kit.FOCUS);
                int dot = disabled ? Kit.BAD : cal == null ? Kit.DIM : cal[1] > 0 && cal[0] >= cal[1] ? Kit.GOOD : cal[0] > 0 ? Kit.ACCENT : 0xFF55556A;
                k.rect(x + 9, cy + 4, 5, 5, dot);
                String badge = cal == null ? "" : cal[0] + "/" + cal[1];
                int bw = k.font.width(badge);
                Ui.drawFit(k.g, k.font, title, x + 18, cy, w - 18 - bw - 14, 13, false, cur ? Kit.ACCENT : disabled ? Kit.FAINT : Kit.TEXT);
                if (!badge.isEmpty()) k.text(badge, x + w - 10 - bw, cy + 3, Kit.DIM);
                cy += 13;
            }
        }
        k.endScroll(cy);
        s.kit.g.pose().popPose();
        k.zLevel(0);
    }

    // ------------------------------------------------------------------ library (left)

    private static void library(Studio s, Kit k, int x, int y, int w, int h) {
        k.rect(x, y, w, h, Kit.PANEL);
        k.rect(x + w - 1, y, 1, h, Kit.EDGE);
        Settings st = s.st();
        s.libRect = new int[] { x, y, x + w, y + h };
        int ix = x + 3, iw = w - 7, cy = y + 3;
        // group row: [<] [group v] [>] [+]
        List<String> groups = new ArrayList<>(st.groups.keySet());
        int gi = Math.max(0, groups.indexOf(s.group));
        k.b(ix, cy, 12, RH).icon(Kit.ARROW_L).tip(T.t("Previous group")).id("grp-prev")
                .press(() -> { s.group = groups.get(Math.floorMod(gi - 1, groups.size())); s.multi.clear(); s.gridScroll.offset = 0; }).show();
        int nameW = iw - 12 - 12 - 14 - 6;
        int inGroup = 0;
        for (Arts.Art a : Arts.all()) if (st.artView(a.id).group.equals(s.group)) inGroup++;
        boolean groupOn = st.groups.getOrDefault(s.group, true);
        k.b(ix + 14, cy, nameW, RH).label(s.group + " (" + inGroup + ")").icon(Kit.CARET).id("grp-pick")
                .color(groupOn ? Kit.TEXT : Kit.FAINT)
                .tip(groupOn ? T.t("Group of arts. Click to choose or manage groups") : T.t("This group is OFF: its arts are not used by random layers"))
                .press(() -> k.openPopup(k.mx, k.my, groupMenu(s))).show();
        k.b(ix + 16 + nameW, cy, 12, RH).icon(Kit.ARROW_R).tip(T.t("Next group")).id("grp-next")
                .press(() -> { s.group = groups.get(Math.floorMod(gi + 1, groups.size())); s.multi.clear(); s.gridScroll.offset = 0; }).show();
        k.b(ix + 30 + nameW, cy, 14, RH).icon(Kit.PLUS).tip(T.t("New group")).id("grp-new").press(s::newGroupDialog).show();
        cy += RH + 3;
        // search + filter chips
        if (!k.value("lib-search").equals(s.search)) k.setValue("lib-search", s.search);
        k.input("lib-search", ix, cy, iw, 13, T.t("search arts"), v -> { s.search = v; s.gridScroll.offset = 0; });
        cy += 16;
        int cw = (iw - 4) / 3;
        String[] chipNames = { T.t("All"), T.t("Pool"), T.t("Off") };
        for (int i = 0; i < 3; i++) {
            final int fi = i;
            k.b(ix + i * (cw + 2), cy, cw, 12).label(chipNames[i]).on(s.filter == i).id("chip" + i)
                    .tip(i == 0 ? T.t("Show every art of the group") : i == 1 ? T.t("Show only arts in the random pool") : T.t("Show only arts that are switched off"))
                    .press(() -> { s.filter = fi; s.gridScroll.offset = 0; }).show();
        }
        cy += 15;
        // info block at the bottom
        int infoH = 44;
        int gridH = Math.max(30, y + h - infoH - cy - 2);
        grid(s, k, ix - 1, cy, iw + 1, gridH);
        // the mouse rests on the library: the arrow keys walk through the arts (Shift extends the selection)
        if (k.hoverArea(x, y, w - 1, h) && !k.typing()) k.border(ix - 1, cy, iw + 1, gridH, Kit.FOCUS);
        int iy = cy + gridH + 3;
        Arts.Art a = s.libArt;
        String line = a == null ? T.t("No art selected") : a.id + (s.multi.size() > 1 ? " +" + (s.multi.size() - 1) : "");
        k.textFit(line, ix, iy, iw, Kit.TEXT);
        iy += 11;
        boolean rot = a != null && st.inRotation(a.id);
        k.b(ix, iy, iw, RH).label(T.t("In the random pool")).tip(T.t("Whether random layers may pick this art (applies to all selected arts)"))
                .sw(a != null && st.artView(a.id).rotation).enabled(a != null, T.t("Select an art first")).id("art-rot").press(s::toggleRotation).show();
        iy += RH + 2;
        k.b(ix, iy, iw, RH).label(T.t("Move to group")).icon(Kit.CARET).tip(T.t("Move the selected arts to another group"))
                .enabled(a != null, T.t("Select an art first")).id("art-move").press(() -> k.openPopup(k.mx, k.my, moveMenu(s))).show();
    }

    private static List<Kit.Item> groupMenu(Studio s) {
        Settings st = s.st();
        List<Kit.Item> l = new ArrayList<>();
        for (String g : st.groups.keySet()) {
            int n = 0;
            for (Arts.Art a : Arts.all()) if (st.artView(a.id).group.equals(g)) n++;
            final String fg = g;
            Kit.Item it = Kit.Item.of(g + " (" + n + ")", () -> { s.group = fg; s.multi.clear(); s.gridScroll.offset = 0; });
            l.add(it.check(g.equals(s.group)));
        }
        l.add(Kit.Item.sep());
        boolean on = st.groups.getOrDefault(s.group, true);
        l.add(Kit.Item.of(T.t("This group is in the random pool"), () -> { s.histLib(); st.groups.put(s.group, !on); s.touch(); }).check(on));
        l.add(Kit.Item.of(T.t("New group..."), s::newGroupDialog).icon(Kit.PLUS));
        return l;
    }

    private static List<Kit.Item> moveMenu(Studio s) {
        List<Kit.Item> l = new ArrayList<>();
        l.add(Kit.Item.head(T.t("Move the selected arts to:")));
        for (String g : s.st().groups.keySet()) {
            final String fg = g;
            l.add(Kit.Item.of(g, () -> s.moveTargetsTo(fg)).check(g.equals(s.group)));
        }
        l.add(Kit.Item.sep());
        l.add(Kit.Item.of(T.t("New group..."), s::newGroupDialog).icon(Kit.PLUS));
        return l;
    }

    private static void grid(Studio s, Kit k, int x, int y, int w, int h) {
        k.rect(x, y, w, h, 0xFF16161C);
        List<Arts.Art> list = s.artsInView();
        int cell = 34, gap = GRID_GAP, ch = GRID_CH;
        int cols = Math.max(1, (w - 6) / (cell + gap));
        s.gridCols = cols;
        int base = k.beginScroll("grid", x, y, w, h, s.gridScroll, 24);
        Settings st = s.st();
        Settings.Layer sl = s.isTitle() ? null : s.sel();
        if (list.isEmpty() && Arts.all().isEmpty()) {
            k.text(T.t("No pictures yet"), x + 5, base + 6, Kit.ACCENT);
            k.wrap(T.t("Drop PNG, JPG or GIF files onto this window to add your own. They appear here and start falling on the main menu."), x + 5, base + 19, w - 12, Kit.DIM, 9);
        } else if (list.isEmpty()) {
            k.text(T.t("No arts here"), x + 5, base + 5, Kit.FAINT);
            k.text(T.t("(change the filter, search or group)"), x + 5, base + 16, Kit.FAINT);
        }
        for (int i = 0; i < list.size(); i++) {
            Arts.Art a = list.get(i);
            int cx = x + 3 + (i % cols) * (cell + gap), cy = base + 3 + (i / cols) * (ch + gap);
            if (cy + ch < y || cy > y + h) continue;   // scrolled out of view: no texture upload, no hit
            final Arts.Art fa = a;
            Kit.Hit hit = new Kit.Hit();
            hit.x = cx; hit.y = cy; hit.w = cell; hit.h = ch; hit.id = "art|" + a.id;
            hit.tip = a.id;
            hit.click = () -> s.pickArt(fa, Screen.hasShiftDown(), Screen.hasControlDown());
            hit.drag = (mx0, my0) -> new LibDrag(s, fa, mx0, my0);
            hit.right = () -> { if (!s.isPicked(fa) || s.multi.size() <= 1) { s.multi.clear(); s.pickArt(fa, false, false); } k.openPopup(k.mx, k.my, artMenu(s, fa)); };
            k.add(hit);
            boolean picked = s.isPicked(a), main = a == s.libArt;
            boolean hov = k.hitAt(k.mx, k.my) != null && k.hitAt(k.mx, k.my).id.equals(hit.id);
            boolean rot = st.inRotation(a.id);
            k.rect(cx, cy, cell, ch, picked ? 0xFF3A3A58 : hov ? 0xFF2C2C3C : 0xFF24242C);
            float sc = Math.min((cell - 4f) / a.sw, (ch - 8f) / a.sh);
            int tw = Math.max(1, Math.round(a.sw * sc)), th = Math.max(1, Math.round(a.sh * sc));
            com.mojang.blaze3d.systems.RenderSystem.enableBlend();
            k.g.setColor(1f, 1f, 1f, rot ? 1f : 0.3f);
            a.drawSmall(k.g, cx + (cell - tw) / 2, cy + 2 + (ch - 8 - th) / 2, tw, th, false);
            k.g.setColor(1f, 1f, 1f, 1f);
            com.mojang.blaze3d.systems.RenderSystem.disableBlend();
            k.rect(cx, cy + ch - 3, cell, 3, rot ? 0xFF3FAF3F : 0xFFAA3A3A);
            if (main) k.border(cx, cy, cell, ch, Kit.ACCENT);
            else if (picked) k.border(cx, cy, cell, ch, 0xFFB89A30);
            if (sl != null && sl.random() && sl.hasSavedSpot(a.id)) k.rect(cx + cell - 6, cy + 2, 4, 4, Kit.GOOD);
        }
        int rows = (list.size() + cols - 1) / cols;
        k.endScroll(base + 3 + rows * (ch + gap));
    }

    private static List<Kit.Item> artMenu(Studio s, Arts.Art a) {
        Settings st = s.st();
        List<Kit.Item> l = new ArrayList<>();
        int n = s.targets().size();
        l.add(Kit.Item.head(n > 1 ? T.f("%s arts selected", n) : a.id));
        l.add(Kit.Item.of(T.t("In the random pool"), s::toggleRotation).check(st.artView(a.id).rotation));
        if (!s.isTitle() && s.mask() != null) {
            l.add(Kit.Item.of(T.t("Add as a layer"), () -> { s.libOverlay = false; s.addLayer(false); }).icon(Kit.PLUS));
            l.add(Kit.Item.of(T.t("Use for the selected layer"), s::useLibraryArt));
        }
        l.add(Kit.Item.sep());
        for (String g : st.groups.keySet()) {
            final String fg = g;
            if (g.equals(st.artView(a.id).group)) continue;
            l.add(Kit.Item.of(T.f("Move to '%s'", g), () -> s.moveTargetsTo(fg)));
        }
        l.add(Kit.Item.of(T.t("Select all in the group"), s::selectAllInView));
        if (a.imported) {
            l.add(Kit.Item.sep());
            l.add(Kit.Item.of(T.t("Delete imported art"), () -> s.deleteImportedDialog(a)).icon(Kit.TRASH));
        }
        return l;
    }

    /** press on a library art: a vertical drag scrolls the grid, a drag towards the canvas carries the art there */
    private static final class LibDrag implements Kit.Drag {
        final Studio s;
        final Arts.Art art;
        final double mx0, my0;
        int mode;           // 0 undecided, 1 scroll, 2 carry
        int startOffset;

        LibDrag(Studio s, Arts.Art art, double mx0, double my0) {
            this.s = s; this.art = art; this.mx0 = mx0; this.my0 = my0;
            this.startOffset = s.gridScroll.offset;
        }

        @Override
        public void move(double mx, double my) {
            if (mode == 0) {
                double dx = mx - mx0, dy = my - my0;
                if (Math.abs(dx) + Math.abs(dy) >= 6) {
                    // mostly vertical = scroll the grid; anything else = carry the art (not on the main menu)
                    if (Math.abs(dy) > 2 * Math.abs(dx) || s.isTitle()) mode = 1;
                    else {
                        // carrying must not retarget the selected layer: only the library highlight follows
                        mode = 2;
                        if (!s.isPicked(art)) { s.multi.clear(); s.anchorId = art.id; s.setLib(art, false); }
                        s.libOverlay = false;
                    }
                }
            }
            if (mode == 1) {
                int max = Math.max(0, s.gridScroll.content - s.gridScroll.view);
                s.gridScroll.offset = Math.max(0, Math.min(max, startOffset - (int) (my - my0)));
            } else if (mode == 2) {
                ghost = art;
                ghostX = mx;
                ghostY = my;
            }
        }

        @Override
        public void end(double mx, double my) {
            if (mode == 2) {
                ghost = null;
                if (s.canvas.contains(mx, my)) s.addDroppedLayer(art, s.canvas.panelX(mx), s.canvas.panelY(my));
            }
            ghost = null;
        }
    }

    // ------------------------------------------------------------------ right dock: layers + inspector

    private static void right(Studio s, Kit k, int x, int y, int w, int h) {
        k.rect(x, y, w, h, Kit.PANEL);
        k.rect(x, y, 1, h, Kit.EDGE);
        if (s.isTitle()) {
            inspector(s, k, x + 1, y, w - 1, h);
            return;
        }
        int layersH = clampI(Math.round(h * 0.34f), 58, 110);
        layersPanel(s, k, x + 1, y, w - 1, layersH);
        k.rect(x + 1, y + layersH, w - 1, 1, Kit.EDGE);
        inspector(s, k, x + 1, y + layersH + 1, w - 1, h - layersH - 1);
    }

    private static void layersPanel(Studio s, Kit k, int x, int y, int w, int h) {
        List<Settings.Layer> ls = s.layers();
        int cy = y + 2;
        k.text(T.t("LAYERS"), x + 4, cy + 3, Kit.DIM);
        int bx = x + w - 4;
        Settings.Layer sel = s.sel();
        bx -= 14;
        k.b(bx, cy, 14, 13).icon(Kit.TRASH).tip(T.t("Delete the selected layer (Del)")).enabled(sel != null, T.t("Select a layer first")).id("l-del").press(s::deleteLayer).show();
        bx -= 15;
        k.b(bx, cy, 14, 13).icon(Kit.COPY).tip(T.t("Duplicate the selected layer (Ctrl+D)")).enabled(sel != null && ls.size() < Settings.MAX_LAYERS, sel == null ? T.t("Select a layer first") : T.t("Too many layers")).id("l-dup").press(s::duplicateLayer).show();
        bx -= 15;
        k.b(bx, cy, 14, 13).icon(Kit.DICE).tip(T.t("Add a random layer")).enabled(ls.size() < Settings.MAX_LAYERS, T.t("Too many layers")).id("l-rand").press(() -> s.addLayer(true)).show();
        bx -= 15;
        k.b(bx, cy, 14, 13).icon(Kit.PLUS).tip(T.t("Add the art selected in the library as a layer"))
                .enabled(s.libArt != null && ls.size() < Settings.MAX_LAYERS, T.t("Pick an art in the library first")).id("l-add").press(() -> s.addLayer(false)).show();
        int ly = y + 17, lh = h - 17;
        int base = k.beginScroll("layers", x, ly, w, lh, s.layersScroll, 13);
        int cyy = base;
        for (int vi = ls.size() - 1; vi >= 0; vi--) {
            final Settings.Layer l = ls.get(vi);
            final int idx = vi;
            boolean selected = l.id.equals(s.selId);
            Kit.Hit hit = new Kit.Hit();
            hit.x = x + 14; hit.y = cyy; hit.w = w - 14 - 6; hit.h = 13; hit.id = "layer|" + l.id;
            hit.tip = T.t("Select the layer. Drag to change the order, right-click for more.");
            hit.press = null;
            final int rowTop = hit.y;
            final int rowOrigin = base;
            hit.drag = (mx0, my0) -> {
                s.selectLayer(l.id);
                return new Kit.Drag() {
                    @Override
                    public void move(double mx, double my) {
                        // rows are listed top layer first: the row under the mouse tells the wanted place
                        int n = s.layers().size();
                        int row = (int) Math.floor((my - rowOrigin) / 13.0);
                        int want = n - 1 - Math.max(0, Math.min(n - 1, row));
                        int now = s.selIndex();
                        if (now >= 0 && want != now) s.moveLayer(want > now ? 1 : -1);
                    }
                };
            };
            hit.click = () -> s.selectLayer(l.id);
            hit.right = () -> { s.selectLayer(l.id); openLayerMenu(s, k.mx, k.my); };
            k.add(hit);
            boolean hov = k.hitAt(k.mx, k.my) != null && k.hitAt(k.mx, k.my).id.equals(hit.id);
            k.rect(x + 1, cyy, w - 8, 13, selected ? 0xFF3A3A58 : hov ? 0xFF2A2A3C : 0);
            // eye
            k.b(x + 2, cyy + 1, 11, 11).icon(l.visible ? Kit.EYE : Kit.EYE_OFF).flat().tip(l.visible ? T.t("Hide this layer") : T.t("Show this layer"))
                    .id("eye|" + l.id).press(() -> s.toggleVisible(l)).show();
            int tx = x + 16;
            Kit.icon(k.g, l.random() ? Kit.DICE : Kit.LAYERS, tx, cyy + 3, l.visible ? 0xFFBBBBD8 : Kit.FAINT);
            tx += 10;
            String name = layerName(l);
            Ui.drawFit(k.g, k.font, name, tx, cyy, x + w - 8 - tx, 13, false, !l.visible ? Kit.FAINT : selected ? Kit.ACCENT : Kit.TEXT);
            cyy += 13;
        }
        k.endScroll(cyy);
    }

    static String layerName(Settings.Layer l) {
        String base = layerBaseName(l);
        return "inv".equals(l.link) ? T.f("%s (inventory)", base) : base;
    }

    private static String layerBaseName(Settings.Layer l) {
        return switch (l.src) {
            case Settings.SRC_ART -> l.art;
            case Settings.SRC_GROUP -> T.f("Random: %s", l.group);
            default -> T.t("Random: pool");
        };
    }

    static void openLayerMenu(Studio s, int x, int y) {
        Settings.Layer l = s.sel();
        if (l == null) return;
        List<Kit.Item> m = new ArrayList<>();
        m.add(Kit.Item.head(layerName(l)));
        m.add(Kit.Item.of(T.t("Flip left-right (H)"), s::flip).icon(Kit.FLIP));
        m.add(Kit.Item.of(T.t("Flip top-bottom (V)"), () -> s.flip(true)).icon(Kit.FLIP_V));
        m.add(Kit.Item.of(T.t("Visible"), () -> s.toggleVisible(l)).check(l.visible));
        m.add(Kit.Item.of(T.t("Duplicate (Ctrl+D)"), s::duplicateLayer).icon(Kit.COPY));
        Kit.Item share = Kit.Item.of(T.t("Inventory style for all menus"), s::shareInventoryDialog);
        if (!s.canShareInventory()) share.off(T.t("This menu has no inventory block"));
        m.add(share);
        m.add(Kit.Item.sep());
        m.add(Kit.Item.of(T.t("Bring to front"), () -> s.moveLayerTo(true)).icon(Kit.UP));
        m.add(Kit.Item.of(T.t("Move up (PgUp)"), () -> s.moveLayer(1)));
        m.add(Kit.Item.of(T.t("Move down (PgDn)"), () -> s.moveLayer(-1)));
        m.add(Kit.Item.of(T.t("Send to back"), () -> s.moveLayerTo(false)).icon(Kit.DOWN));
        m.add(Kit.Item.sep());
        Arts.Art cur = s.sample(l);
        if (l.random()) m.add(Kit.Item.of(cur == null ? T.t("Make this layer one fixed art") : T.f("Make this layer one fixed art (%s)", cur.id),
                () -> s.setSource(l, Settings.SRC_ART, "")));
        else m.add(Kit.Item.of(T.t("Make it random (pool)"), () -> s.setSource(l, Settings.SRC_POOL, "")));
        Arts.Art a = s.sample(l);
        Kit.Item auto = Kit.Item.of(T.t("Back to the automatic position"), s::resetAuto).icon(Kit.RESET);
        if (a == null || LayerEngine.isAuto(l, a)) auto.off(T.t("Already automatic"));
        m.add(auto);
        m.add(Kit.Item.of(T.t("Delete layer (Del)"), s::deleteLayer).icon(Kit.TRASH));
        s.kit.openPopup(x, y, m);
    }

    // ------------------------------------------------------------------ inspector

    private static void inspector(Studio s, Kit k, int x, int y, int w, int h) {
        int ix = x + 4, iw = w - 4 - 6;
        int base = k.beginScroll("insp", x, y, w, h, s.inspScroll, 14);
        int cy = base + 2;
        if (s.isTitle()) cy = titleSection(s, k, ix, cy, iw);
        else {
            cy = layerSection(s, k, ix, cy, iw);
            cy = calibrateSection(s, k, ix, cy + 2, iw);
            cy = menuSection(s, k, ix, cy + 2, iw);
        }
        cy = allMenusSection(s, k, ix, cy + 2, iw);
        k.endScroll(cy + 4);
    }

    private static int layerSection(Studio s, Kit k, int x, int cy, int w) {
        Settings.Layer l = s.sel();
        if (l == null) return cy;
        Settings st = s.st();
        Arts.Art a = s.sample(l);
        Masks.Mask m = s.mask();
        cy = k.header(x, cy, w, T.t("LAYER"));
        // source
        String srcName = l.src.equals(Settings.SRC_ART) ? T.t("This art (fixed)")
                : l.src.equals(Settings.SRC_GROUP) ? T.f("Random from group '%s'", l.group) : T.t("Random from the pool");
        k.b(x, cy, w, RH).label(srcName).icon(Kit.CARET).tip(T.t("Where this layer takes its art from")).id("src")
                .press(() -> k.openPopup(k.mx, k.my, sourceMenu(s, l))).show();
        cy += RH + 3;
        if (l.random()) {
            List<Arts.Art> pool = LayerEngine.poolOf(l);
            int saved = 0;
            for (Arts.Art p : pool) if (l.hasSavedSpot(p.id)) saved++;
            String stats = pool.isEmpty() ? T.t("No art in the pool: this layer shows nothing")
                    : T.f("%s arts: %s positioned, %s automatic", pool.size(), saved, pool.size() - saved);
            k.textFit(stats, x, cy + 1, w, pool.isEmpty() ? Kit.BAD : saved == pool.size() ? Kit.GOOD : Kit.DIM);
            cy += 12;
            String sampleText = a == null ? "-" : a.id + (LayerEngine.isAuto(l, a) ? " (" + T.t("auto") + ")" : " (" + T.t("saved") + ")");
            k.b(x, cy, 14, RH).icon(Kit.ARROW_L).tip(T.t("Previous art of the pool ( , )")).id("smp-prev").press(() -> s.stepSample(-1)).show();
            k.b(x + 16, cy, w - 16 - 32, RH).label(T.f("Editing art: %s", sampleText)).tip(T.t("The art you are editing. Changes save the position of this art only.")).flat().id("smp-lbl").show();
            k.b(x + w - 30, cy, 14, RH).icon(Kit.ARROW_R).tip(T.t("Next art of the pool ( . )")).id("smp-next").press(() -> s.stepSample(1)).show();
            k.b(x + w - 14, cy, 14, RH).icon(Kit.DICE).tip(T.t("Another random art (R)")).id("smp-roll").press(s::roll).show();
            cy += RH + 2;
            if (a != null && pool.size() > 1)
                cy = k.wrap(T.f("Only %s changes. The other %s arts keep their own position.", a.id, pool.size() - 1), x, cy, w, Kit.FAINT, 2) + 2;
            boolean any = saved < pool.size();
            k.b(x, cy, w, RH).label(T.t("Next art without a position")).tip(T.t("Jump to the next art of this layer that still has an automatic position"))
                    .enabled(any, T.t("Every art of this layer has a saved position")).id("next-unsaved").press(s::nextUnsaved).show();
            cy += RH + 3;
        } else {
            k.textFit(T.f("Art: %s", l.art), x, cy + 1, w, Kit.DIM);
            cy += 12;
            k.b(x, cy, w, RH).label(T.t("Use the art selected in the library")).tip(T.t("Replace the art of this layer by the one selected in the library"))
                    .enabled(s.libArt != null && !s.libArt.id.equals(l.art), s.libArt == null ? T.t("Pick an art in the library first") : T.t("This art is already used"))
                    .id("use-lib").press(s::useLibraryArt).show();
            cy += RH + 3;
        }
        // position and size
        Placement p = s.placementOf(l);
        if (p != null && m != null && a != null) {
            cy = k.header(x, cy, w, T.t("POSITION"));
            int hw = (w - 3) / 2;
            k.number("x", x, cy, hw, RH, "X", () -> p.x, v -> String.valueOf((int) Math.round(v)), 1, -400, 400, 4000, T.t("Left edge (drag sideways or click to type)"),
                    v -> s.setPos((int) Math.round(v), null), () -> { });
            k.number("y", x + hw + 3, cy, hw, RH, "Y", () -> p.y, v -> String.valueOf((int) Math.round(v)), 1, -400, 400, 4000, T.t("Top edge"),
                    v -> s.setPos(null, (int) Math.round(v)), () -> { });
            cy += RH + 2;
            k.number("w", x, cy, hw, RH, "W", () -> p.w, v -> String.valueOf((int) Math.round(v)), 1, 8, 400, 1200, T.t("Width (the height follows the proportions)"),
                    v -> s.setSize((int) Math.round(v), true), () -> { });
            k.number("h", x + hw + 3, cy, hw, RH, "H", () -> p.h, v -> String.valueOf((int) Math.round(v)), 1, 8, 400, 1200, T.t("Height (the width follows the proportions)"),
                    v -> s.setSize((int) Math.round(v), false), () -> { });
            cy += RH + 2;
            k.slider("rot", x, cy, w, RH, T.t("Rotate"), () -> p.rot, v -> Math.round(v) + "°", -180, 180, 1, T.t("Rotate the art. Right-click = 0. Keys [ and ]"),
                    v -> s.setRotation((int) Math.round(v)), null, () -> s.setRotation(0));
            cy += RH + 2;
            int q = (w - 6) / 4;
            int[] angles = { 0, 90, 180, -90 };
            for (int i = 0; i < 4; i++) {
                final int ang = angles[i];
                k.b(x + i * (q + 2), cy, q, 12).label(ang + "°").on(p.rot == ang).tip(T.t("Rotate to this angle")).id("ang" + ang).press(() -> s.setRotation(ang)).show();
            }
            cy += 14;
            int bw = (w - 6) / 3;
            k.b(x, cy, bw, RH).icon(Kit.FLIP).tip(T.t("Mirror left-right (H). Undo with Ctrl+Z")).on(p.flip).id("flip").press(() -> s.flip(false)).show();
            k.b(x + bw + 3, cy, bw, RH).icon(Kit.FLIP_V).tip(T.t("Mirror top-bottom (V). Undo with Ctrl+Z")).on(p.flipY).id("flipv").press(() -> s.flip(true)).show();
            k.b(x + 2 * (bw + 3), cy, bw, RH).icon(Kit.RESET).label(T.t("Auto")).tip(T.t("Forget the saved position of this art and let the mod place it"))
                    .enabled(!LayerEngine.isAuto(l, a), T.t("Already automatic")).id("reset-auto").press(s::resetAuto).show();
            cy += RH + 3;
        }
        // look
        cy = k.header(x, cy, w, T.t("LOOK"));
        float menuOp = st.opacityFor(s.cfg());
        k.slider("lop", x, cy, w, RH, T.t("Opacity") + (l.opacity >= 0 ? " *" : ""), () -> l.opacity >= 0 ? l.opacity : menuOp, v -> Math.round(v * 100) + "%", 0, 1, 0.05,
                T.t("Opacity of this layer. * = its own value; right-click follows the menu again."), v -> s.setOpacity(l, (float) v), null, () -> s.followMenuOpacity(l));
        cy += RH + 2;
        String modeName = l.mode < 0 ? T.t("as the menu") : T.t(Settings.MODE_NAMES[l.mode]);
        k.b(x, cy, w, RH).label(T.f("Layer mode: %s", modeName)).icon(Kit.CARET).tip(T.t("How the layer sits in the menu: behind the slots, visible inside them, on the window or on top of everything")).id("mode")
                .press(() -> k.openPopup(k.mx, k.my, modeMenu(s, l.mode, true, v -> s.setMode(l, v)))).show();
        cy += RH + 3;
        return cy;
    }

    private static int calibrateSection(Studio s, Kit k, int x, int cy, int w) {
        Settings.Layer l = s.sel();
        Arts.Art a = l == null ? s.libArt : s.sample(l);
        cy = k.header(x, cy, w, T.t("CALIBRATE"));
        boolean rnd = l != null && l.random();
        int[] pm = a == null ? new int[] { 0, 0 } : s.positionedMenus(a);
        k.textFit(a == null ? T.t("No art selected") : T.f("%s is positioned in %s of %s menus", a.id, pm[0], pm[1]), x, cy + 1, w,
                a != null && pm[0] >= pm[1] ? Kit.GOOD : Kit.DIM);
        cy += 12;
        boolean todo = a != null && pm[0] < pm[1];
        k.b(x, cy, w, RH).label(T.t("Next menu to position (N)")).tip(T.t("Jump to the next menu where this art has no saved position"))
                .enabled(todo, T.t("This art has a saved position in every menu")).id("next-menu").press(s::nextMenuToCalibrate).show();
        cy += RH + 2;
        k.b(x, cy, w, RH).label(T.t("Apply to similar menus")).tip(T.t("Furnace / blast furnace / smoker and chest / shulker share a layout: copy this position to them"))
                .enabled(rnd && s.hasFamily(), !rnd ? T.t("Select a random layer first") : T.t("No similar menus for this one")).id("similar").press(s::applyToSimilar).show();
        cy += RH + 2;
        boolean inv = s.canShareInventory();
        k.b(x, cy, w, RH).label(T.t("Inventory style for all menus")).tip(T.t("Copy this layer over the player-inventory block of every menu that has one. Other layers of those menus are kept"))
                .enabled(inv, T.t("This menu has no inventory block")).id("share-inv").press(s::shareInventoryDialog).show();
        cy += RH + 2;
        if (s.hasSharedInventory()) {
            k.b(x, cy, w, RH).label(T.t("Remove shared inventory layers")).tip(T.t("Delete the layers spread by 'Inventory style for all menus' from every menu"))
                    .id("unshare-inv").press(s::unshareInventoryDialog).show();
            cy += RH + 2;
        }
        cy += 1;
        return cy;
    }

    private static List<Kit.Item> modeMenu(Studio s, int current, boolean withFollow, java.util.function.IntConsumer set) {
        List<Kit.Item> l = new ArrayList<>();
        if (withFollow) l.add(Kit.Item.of(T.t("As the menu"), () -> set.accept(-1)).check(current < 0));
        for (int i = 0; i < Settings.MODE_NAMES.length; i++) {
            final int fi = i;
            l.add(Kit.Item.of(T.t(Settings.MODE_NAMES[i]), () -> set.accept(fi)).check(current == i));
        }
        return l;
    }

    private static List<Kit.Item> sourceMenu(Studio s, Settings.Layer l) {
        List<Kit.Item> m = new ArrayList<>();
        m.add(Kit.Item.of(T.t("Random from the pool"), () -> s.setSource(l, Settings.SRC_POOL, "")).check(Settings.SRC_POOL.equals(l.src)));
        for (String g : s.st().groups.keySet()) {
            final String fg = g;
            m.add(Kit.Item.of(T.f("Random from group '%s'", g), () -> s.setSource(l, Settings.SRC_GROUP, fg))
                    .check(Settings.SRC_GROUP.equals(l.src) && g.equals(l.group)));
        }
        m.add(Kit.Item.sep());
        m.add(Kit.Item.of(T.t("This art (fixed)"), () -> s.setSource(l, Settings.SRC_ART, "")).check(Settings.SRC_ART.equals(l.src)));
        return m;
    }

    private static int menuSection(Studio s, Kit k, int x, int cy, int w) {
        Settings st = s.st();
        Settings.MenuCfg c = s.cfg();
        cy = k.header(x, cy, w, T.t("THIS MENU"));
        k.choice(x, cy, w, RH, "m-art", T.t("Art in this menu"), SHOWN, () -> c.disabled ? 1 : 0, T.t("Turn all art off or on for this menu only"), v -> { s.hist(false); c.disabled = v == 1; s.touch(); });
        cy += RH + 2;
        k.slider("mop", x, cy, w, RH, T.t("Opacity") + (c.opacity >= 0 ? " *" : ""), () -> st.opacityFor(c), v -> Math.round(v * 100) + "%", 0, 1, 0.05,
                T.t("Opacity for this menu (layers without their own value follow it). * = own; right-click follows all menus."),
                v -> { s.hist(true); c.opacity = (float) v; s.touch(); }, null, () -> { s.hist(false); c.opacity = -1f; s.touch(); });
        cy += RH + 2;
        String mn = c.mode < 0 ? T.t("as all menus") : T.t(Settings.MODE_NAMES[c.mode]);
        k.b(x, cy, w, RH).label(T.f("Layer mode: %s", mn)).icon(Kit.CARET).tip(T.t("Default placement of the layers of this menu")).id("mmode")
                .press(() -> k.openPopup(k.mx, k.my, modeMenu(s, c.mode, true, v -> { s.hist(false); c.mode = v; s.touch(); }))).show();
        cy += RH + 3;
        k.b(x, cy, w, RH).label(T.t("Reset this menu")).icon(Kit.RESET).tip(T.t("Remove all layers and settings of this menu (asks first, undoable)")).id("reset-menu").press(s::resetMenuDialog).show();
        cy += RH + 3;
        return cy;
    }

    private static int allMenusSection(Studio s, Kit k, int x, int cy, int w) {
        Settings st = s.st();
        cy = k.header(x, cy, w, T.t("ALL MENUS"));
        k.slider("gop", x, cy, w, RH, T.t("Opacity"), () -> st.opacity, v -> Math.round(v * 100) + "%", 0, 1, 0.05, T.t("Default opacity of every menu"),
                v -> { st.opacity = (float) v; s.touch(); }, null, null);
        cy += RH + 2;
        k.b(x, cy, w, RH).label(T.f("Layer mode: %s", T.t(Settings.MODE_NAMES[st.mode]))).icon(Kit.CARET).tip(T.t("Default placement of the art in every menu")).id("gmode")
                .press(() -> k.openPopup(k.mx, k.my, modeMenu(s, st.mode, false, v -> { st.mode = v; s.touch(); }))).show();
        cy += RH + 2;
        return cy;
    }

    // ------------------------------------------------------------------ main menu inspector

    private static int titleSection(Studio s, Kit k, int x, int cy, int w) {
        Settings st = s.st();
        Settings.Title t = st.title;
        cy = k.header(x, cy, w, T.t("MAIN MENU BACKGROUND"));
        k.b(x, cy, w, RH).label(T.t(Settings.TITLE_MODES[t.mode])).icon(Kit.CARET).tip(T.t("What fills the main menu background")).id("t-mode")
                .press(() -> {
                    List<Kit.Item> l = new ArrayList<>();
                    for (int i = 0; i < Settings.TITLE_MODES.length; i++) {
                        final int fi = i;
                        l.add(Kit.Item.of(T.t(Settings.TITLE_MODES[i]), () -> { s.hist(false); t.mode = fi; s.touch(); }).check(t.mode == i));
                    }
                    k.openPopup(k.mx, k.my, l);
                }).show();
        cy += RH + 3;
        Runnable beg = () -> s.hist(true);
        cy = backgroundSection(s, k, x, cy, w, beg);
        cy = tnum(s, k, x, cy, w, "t-max", T.t("Drops on screen"), () -> t.rainMax, 2, 1, 80, 1000, T.t("Most drops on screen at once. Shift while dragging goes past the limit."), v -> t.rainMax = (int) Math.round(v), "%.0f", beg);
        cy = tnum(s, k, x, cy, w, "t-rate", T.t("New drops / sec"), () -> t.rainRate, 0.2, 0.2, 12, 200, T.t("How often a new art starts falling"), v -> t.rainRate = (float) v, "%.1f", beg);
        cy = tnum(s, k, x, cy, w, "t-speed", T.t("Speed"), () -> t.rainSpeed, 0.1, 0.2, 4, 20, T.t("Fall speed multiplier"), v -> t.rainSpeed = (float) v, "x%.1f", beg);
        cy = tnum(s, k, x, cy, w, "t-size", T.t("Size"), () -> t.rainSize, 0.1, 0.4, 3, 10, T.t("Drop size multiplier"), v -> t.rainSize = (float) v, "x%.1f", beg);
        cy = tnum(s, k, x, cy, w, "t-op", T.t("Rain opacity"), () -> t.rainOpacity, 0.05, 0.1, 1, 1, T.t("Drop opacity"), v -> t.rainOpacity = (float) v, "%.2f", beg);
        cy += 3;
        cy = k.header(x, cy, w, T.t("LOGO AND MASCOT"));
        cy = tnum(s, k, x, cy, w, "t-logo", T.t("Logo shift"), () -> t.logoShift, 5, 0, 140, 140, T.t("How far the logo and buttons move down (shrinks on small screens)"), v -> t.logoShift = (int) Math.round(v), "%.0f px", beg);
        k.choice(x, cy, w, RH, "t-mascot", T.t("Mascot"), SHOWN, () -> t.mascot ? 0 : 1, T.t("The sitting mascot on the logo"), v -> { s.hist(false); t.mascot = v == 0; s.touch(); });
        cy += RH + 2;
        cy = tnum(s, k, x, cy, w, "t-mx", T.t("Mascot x"), () -> t.mascotDx, 2, -999, 999, 999, T.t("Move the mascot left / right"), v -> t.mascotDx = (int) Math.round(v), "%.0f", beg);
        cy = tnum(s, k, x, cy, w, "t-my", T.t("Mascot y"), () -> t.mascotDy, 2, -999, 999, 999, T.t("Move the mascot up / down"), v -> t.mascotDy = (int) Math.round(v), "%.0f", beg);
        k.choice(x, cy, w, RH, "t-flip", T.t("Static art"), SIDES, () -> t.flipArt ? 1 : 0, T.t("Show the big art of the main menu as it is or mirrored left-right"), v -> { s.hist(false); t.flipArt = v == 1; s.touch(); });
        cy += RH + 2;
        return cy;
    }

    private static final String[] SHOWN = { "Shown", "Hidden" };
    private static final String[] SIDES = { "Normal", "Mirrored" };

    /** what is painted behind the art of the main menu: type, one or two colours (with the picker), direction */
    private static int backgroundSection(Studio s, Kit k, int x, int cy, int w, Runnable beg) {
        Settings.Title t = s.st().title;
        k.choice(x, cy, w, RH, "t-bgtype", T.t("Backdrop"), Settings.BG_TYPES, () -> t.bgType,
                T.t("What is painted behind the art: a gradient, one colour, or the game's own panorama"), v -> { s.hist(false); t.bgType = v; s.touch(); });
        cy += RH + 2;
        if (t.bgType != Settings.BG_PANORAMA) {
            cy = swatch(s, k, x, cy, w, "t-bga", t.bgType == Settings.BG_SOLID ? T.t("Colour") : T.t("Colour 1"), () -> t.bgA, v -> t.bgA = v, beg);
            if (t.bgType == Settings.BG_GRADIENT) {
                cy = swatch(s, k, x, cy, w, "t-bgb", T.t("Colour 2"), () -> t.bgB, v -> t.bgB = v, beg);
                k.choice(x, cy, w, RH, "t-bgdir", T.t("Direction"), Settings.BG_DIRS, () -> t.bgDir,
                        T.t("Which way the gradient runs, from colour 1 to colour 2"), v -> { s.hist(false); t.bgDir = v; s.touch(); });
                cy += RH + 2;
            }
            k.b(x, cy, w, RH).label(T.t("Original colours")).icon(Kit.RESET).tip(T.t("Back to the warm colours the mod started with"))
                    .enabled(t.bgA != Settings.BG_A_DEFAULT || t.bgB != Settings.BG_B_DEFAULT || t.bgDir != 0, T.t("Already the original colours")).id("t-bgreset")
                    .press(() -> { s.hist(false); t.bgA = Settings.BG_A_DEFAULT; t.bgB = Settings.BG_B_DEFAULT; t.bgDir = 0; s.touch(); }).show();
            cy += RH + 2;
        }
        return cy + 3;
    }

    /** a button that shows a colour and opens the colour picker */
    private static int swatch(Studio s, Kit k, int x, int cy, int w, String id, String label, java.util.function.IntSupplier cur, java.util.function.IntConsumer set, Runnable beg) {
        k.b(x, cy, w, RH).label(label).tip(T.t("Click to pick a colour: a wheel, brightness, or a hex code")).id(id)
                .press(() -> k.colors.open(k.mx - 40, k.my + 10, cur.getAsInt(), beg, v -> { set.accept(v); s.touch(); })).show();
        k.rect(x + w - 24, cy + 2, 20, RH - 4, 0xFF000000 | cur.getAsInt());
        k.border(x + w - 24, cy + 2, 20, RH - 4, 0xFF8A8AB0);
        return cy + RH + 2;
    }

    private static int tnum(Studio s, Kit k, int x, int cy, int w, String id, String label, java.util.function.DoubleSupplier cur, double step,
                            double lo, double hi, double insane, String tip, java.util.function.DoubleConsumer set, String fmt, Runnable begin) {
        k.number(id, x, cy, w, RH, label, cur, v -> String.format(fmt, v), step, lo, hi, insane, tip, v -> { set.accept(v); s.touch(); }, begin);
        return cy + RH + 2;
    }

    // ------------------------------------------------------------------ status bar

    private static void statusBar(Studio s, Kit k, int W, int H) {
        k.rect(0, H - BOT, W, BOT, Kit.PANEL2);
        k.rect(0, H - BOT, W, 1, Kit.EDGE);
        String saveState = s.pendingSave ? T.t("saving...") : (Studio.now() - s.savedAt < 3500 && s.savedAt > 0) ? T.t("Saved") : T.t("All changes are saved");
        int sw = k.font.width(saveState);
        Settings.Layer sl = s.isTitle() ? null : s.sel();
        Arts.Art sa = sl == null ? null : s.sample(sl);
        String hint = s.isTitle() ? T.t("Main menu: pick a mode and tune it on the right. Changes save by themselves.")
                : sl != null && sl.random() && sa != null ? T.f("Dragging saves the position of %s only. Wheel = zoom, Tab = player view, F1 = shortcuts.", sa.id)
                : T.t("Drag an art from the library onto the menu. Wheel = zoom, Tab = player view, F1 = shortcuts.");
        k.textFit(hint, 4, H - BOT + 2, W - sw - 16, 0xFFB8B8D0);
        k.text(saveState, W - sw - 4, H - BOT + 2, s.pendingSave ? Kit.ACCENT : Kit.GOOD);
    }
}
