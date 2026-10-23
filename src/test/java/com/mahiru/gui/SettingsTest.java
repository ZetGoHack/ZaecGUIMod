package com.mahiru.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.mahiru.gui.Settings.Layer;
import com.mahiru.gui.Settings.MenuCfg;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Settings has a private constructor and reads files through FMLPaths, so everything here goes through Settings.parse. */
class SettingsTest {
    private static Settings parse(String json) {
        Settings s = Settings.parse(json);
        assertNotNull(s, "parse returned null for: " + json);
        return s;
    }

    private static Layer only(Settings s, String menu) {
        List<Layer> l = s.menus.get(menu).layers;
        assertEquals(1, l.size(), menu + " layers");
        return l.get(0);
    }

    // ------------------------------------------------------------------ format 2

    @Test
    void parsesAFormat2File() {
        Settings s = parse("""
            {"format":2,"enabled":false,"opacity":0.75,"mode":2,"gearFx":100,
             "title":{"mode":2,"rainMax":50,"bgType":1,"bgA":16766430},
             "groups":{"main":true,"extra":false},
             "arts":{"a001":{"group":"extra","rotation":false}},
             "menus":{"furnace":{"opacity":0.4,"mode":1,"layers":[
               {"id":"base","src":"pool","visible":true},
               {"id":"face","src":"art","art":"a001","x":10,"y":20,"w":30,"h":40,"rot":15,"flip":true,"link":"inv"},
               {"id":"rnd","src":"group","group":"extra","spots":{"a001":[1,2,3,4,5,1,1]}}]}}}
            """);
        assertFalse(s.enabled);
        assertEquals(0.75f, s.opacity);
        assertEquals(2, s.mode);
        assertEquals(100, s.gearFx);
        assertEquals(2, s.title.mode);
        assertEquals(50, s.title.rainMax);
        assertEquals(Settings.BG_SOLID, s.title.bgType);
        assertEquals(16766430, s.title.bgA);
        assertEquals("extra", s.arts.get("a001").group);
        assertFalse(s.arts.get("a001").rotation);

        MenuCfg m = s.menus.get("furnace");
        assertEquals(0.4f, m.opacity);
        assertEquals(1, m.mode);
        assertEquals(3, m.layers.size());
        Layer face = m.layers.get(1);
        assertEquals(Settings.SRC_ART, face.src);
        assertFalse(face.random());
        assertEquals("inv", face.link);
        assertEquals(15, face.rot);
        assertTrue(face.flip);
        Layer rnd = m.layers.get(2);
        assertTrue(rnd.random());
        assertTrue(rnd.hasSavedSpot("a001"));
        assertNotNull(rnd.spot("a001"));
        assertTrue(rnd.spot("a001").flipY);
    }

    @Test
    void emptyObjectGivesDefaults() {
        Settings s = parse("{}");
        assertTrue(s.enabled);
        assertEquals(0.5f, s.opacity);
        assertEquals(Settings.MODE_BEHIND, s.mode);
        assertTrue(s.menus.isEmpty());
        assertEquals(Boolean.TRUE, s.groups.get("main"));
        assertEquals(Boolean.FALSE, s.groups.get("unsorted"));
    }

    // ------------------------------------------------------------------ format 1 -> 2

    @Test
    void legacyPinnedArtWithPlacementAndFlip() {
        Settings s = parse("""
            {"layer":2,
             "menus":{"chest":{"layer":1,"pinned":"a007","flips":{"a007":true}}},
             "placements":{"chest":{"a007":[11,12,130,140,25],"a008":[1,2,3,4]}}}
            """);
        assertEquals(2, s.mode);
        assertNull(s.layer);
        assertNull(s.placements);
        MenuCfg m = s.menus.get("chest");
        assertEquals(1, m.mode);
        assertNull(m.pinned);
        assertNull(m.flips);
        Layer l = only(s, "chest");
        assertEquals(Settings.SRC_ART, l.src);
        assertEquals("a007", l.art);
        assertEquals(11, l.x);
        assertEquals(12, l.y);
        assertEquals(130, l.w);
        assertEquals(140, l.h);
        assertEquals(25, l.rot);
        assertTrue(l.flip);
        assertTrue(l.spots.isEmpty(), "a pinned art has one transform, not per-art spots");
    }

    @Test
    void legacyPlacementsBecomeSpotsOfARandomLayer() {
        Settings s = parse("""
            {"menus":{"hopper":{"flips":{"a002":true,"a003":true}}},
             "placements":{"hopper":{"a001":[5,6,70,80,-10],"a002":[1,2,30,40]}}}
            """);
        Layer l = only(s, "hopper");
        assertEquals(Settings.SRC_POOL, l.src);
        assertArrayEquals(new int[] { 5, 6, 70, 80, -10, 0, 0 }, l.spots.get("a001"));
        assertArrayEquals(new int[] { 1, 2, 30, 40, 0, 1, 0 }, l.spots.get("a002"));
        // flipped art without a saved position: automatic placement (w = 0), mirrored
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 1, 0 }, l.spots.get("a003"));
        assertTrue(l.hasSavedSpot("a001"));
        assertFalse(l.hasSavedSpot("a003"));
    }

    @Test
    void legacyExtrasBecomeLayers() {
        Settings s = parse("""
            {"menus":{"anvil":{"pinned":"a010","extras":[
               {"art":"a011","x":1,"y":2,"w":20,"h":30,"rot":5,"flip":true,"visible":false,"opacity":0.3,"layer":3},
               {"group":"cats","art":"a012","x":4,"y":5,"w":60,"h":70,"rot":0},
               {"art":"","group":""},
               null]}}}
            """);
        List<Layer> ls = s.menus.get("anvil").layers;
        assertEquals(3, ls.size(), "pinned base + two usable extras; the empty and the null one are dropped");
        assertEquals("a010", ls.get(0).art);
        Layer fixed = ls.get(1);
        assertEquals(Settings.SRC_ART, fixed.src);
        assertEquals("a011", fixed.art);
        assertEquals(20, fixed.w);
        assertTrue(fixed.flip);
        assertFalse(fixed.visible);
        assertEquals(0.3f, fixed.opacity);
        assertEquals(3, fixed.mode);
        Layer grp = ls.get(2);
        assertEquals(Settings.SRC_GROUP, grp.src);
        assertEquals("cats", grp.group);
        assertEquals("a012", grp.art);
        assertArrayEquals(new int[] { 4, 5, 60, 70, 0, 0, 0 }, grp.spots.get("a012"));
        assertNull(s.menus.get("anvil").extras);
    }

    @Test
    void legacyPlacementsOfAMenuWithoutOtherSettingsCreateTheMenu() {
        Settings s = parse("{\"placements\":{\"loom\":{\"a001\":[1,2,3,4,5]}}}");
        assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 0, 0 }, only(s, "loom").spots.get("a001"));
    }

    @Test
    void legacyTitleFlipBecomesTheTitleMirror() {
        Settings s = parse("{\"menus\":{\"title\":{\"flips\":{\"a001\":true}}}}");
        assertTrue(s.title.flipArt);
    }

    @Test
    void legacyArtWideMirrorIsDropped() {
        Settings s = parse("{\"arts\":{\"a001\":{\"flipX\":true,\"group\":\"main\"}}}");
        assertNull(s.arts.get("a001").flipX);
    }

    @Test
    void legacyConversionIsIdempotent() {
        Settings once = parse("{\"menus\":{\"m\":{\"pinned\":\"a1\"}},\"placements\":{\"m\":{\"a1\":[1,2,3,4,5]}}}");
        Settings twice = parse(once.toJson());
        assertEquals(once.menuJson("m"), twice.menuJson("m"));
        assertEquals(1, twice.menus.get("m").layers.size());
    }

    @Test
    void legacyWithMalformedPieces() {
        Settings s = parse("""
            {"menus":{"m":{"flips":{"a":null,"b":true},"pinned":""}, "n":null},
             "placements":{"m":{"a":null,"b":[1,2],"c":[1,2,3,4]},"z":null,"y":{}}}
            """);
        Layer l = only(s, "m");
        assertNull(l.spots.get("a"));
        assertArrayEquals(new int[] { 0, 0, 0, 0, 0, 1, 0 }, l.spots.get("b"));   // too short to be a placement; the flip survives
        assertArrayEquals(new int[] { 1, 2, 3, 4, 0, 0, 0 }, l.spots.get("c"));
        assertFalse(s.menus.containsKey("n"));
        assertFalse(s.menus.containsKey("z"));
    }

    // ------------------------------------------------------------------ sanitize

    @Test
    void clampsOutOfRangeNumbers() {
        Settings s = parse("""
            {"opacity":7,"mode":99,"gearFx":-5,"gearFy":99999,
             "title":{"mode":-3,"rainMax":0,"rainRate":1000,"rainSpeed":0,"rainSize":99,"rainOpacity":-1,
                      "bgType":42,"bgA":-1,"bgB":2147483647,"logoShift":500,"mascotDx":-5000,"mascotDy":5000}}
            """);
        assertEquals(1f, s.opacity);
        assertEquals(3, s.mode);
        assertEquals(0, s.gearFx);
        assertEquals(1000, s.gearFy);
        assertEquals(0, s.title.mode);
        assertEquals(1, s.title.rainMax);
        assertEquals(200f, s.title.rainRate);
        assertEquals(0.2f, s.title.rainSpeed);
        assertEquals(10f, s.title.rainSize);
        assertEquals(0.1f, s.title.rainOpacity);
        assertEquals(2, s.title.bgType);
        assertEquals(0xFFFFFF, s.title.bgA);
        assertEquals(0xFFFFFF, s.title.bgB);
        assertEquals(140, s.title.logoShift);
        assertEquals(-999, s.title.mascotDx);
        assertEquals(999, s.title.mascotDy);
    }

    @Test
    void nanAndInfinityFallBackToDefaults() {
        Settings s = parse("""
            {"opacity":NaN,"title":{"rainRate":Infinity,"rainSpeed":-Infinity,"rainSize":NaN,"rainOpacity":NaN},
             "menus":{"m":{"opacity":NaN,"layers":[{"id":"a","opacity":NaN}]}}}
            """);
        assertEquals(0.5f, s.opacity);
        assertEquals(2f, s.title.rainRate);
        assertEquals(1f, s.title.rainSpeed);
        assertEquals(1f, s.title.rainSize);
        assertEquals(0.9f, s.title.rainOpacity);
        assertEquals(-1f, s.menus.get("m").opacity);
        assertEquals(-1f, s.menus.get("m").layers.get(0).opacity);
    }

    @Test
    void infiniteOpacitiesStillSerialize() {
        // -Infinity is a typo someone can make by hand; the studio snapshots with toJson() and Gson refuses non-finite floats
        Settings s = parse("{\"menus\":{\"m\":{\"opacity\":-Infinity,\"layers\":[{\"id\":\"a\",\"opacity\":-Infinity}]}}}");
        assertDoesNotThrow(s::toJson);
        assertDoesNotThrow(() -> s.menuJson("m"));
    }

    @Test
    void layerValuesAreMadeSafe() {
        Settings s = parse("""
            {"menus":{"m":{"mode":77,"opacity":5,"layers":[
              {"id":"a","src":"bogus","mode":9,"opacity":3,"rot":9999,"x":1,"y":2,"w":-4,"h":10},
              {"id":"b","src":"art","art":""},
              {"id":"c","src":"art","art":"a1","x":100000,"y":0,"w":50,"h":50},
              {"id":"d","spots":{"ok":[1,2,3,4,500,7,0],"short":[1,2],"neg":[0,0,-1,5,0,0,0],"huge":[99999,0,5,5,0,0,0],
                                 "five":[1,2,3,4,5],"nul":null,"long":[1,2,3,4,5,1,1,9,9]}}]}}}
            """);
        MenuCfg m = s.menus.get("m");
        assertEquals(-1, m.mode);
        assertEquals(1f, m.opacity);
        Layer a = m.layers.get(0);
        assertEquals(Settings.SRC_POOL, a.src);
        assertEquals(-1, a.mode);
        assertEquals(1f, a.opacity);
        assertEquals(180, a.rot);
        assertEquals(0, a.w);
        assertEquals(0, a.h);
        assertEquals(Settings.SRC_POOL, m.layers.get(1).src, "a fixed layer without an art is a random one");
        Layer c = m.layers.get(2);
        assertEquals(0, c.w);
        assertEquals(0, c.x);
        Layer d = m.layers.get(3);
        assertArrayEquals(new int[] { 1, 2, 3, 4, 180, 1, 0 }, d.spots.get("ok"));
        assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 0, 0 }, d.spots.get("five"));
        assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 1, 1 }, d.spots.get("long"));
        assertFalse(d.spots.containsKey("short"));
        assertFalse(d.spots.containsKey("neg"));
        assertFalse(d.spots.containsKey("huge"));
        assertFalse(d.spots.containsKey("nul"));
        for (int[] spot : d.spots.values()) assertEquals(7, spot.length);
    }

    @Test
    void unknownGroupsGoToUnsorted() {
        Settings s = parse("{\"groups\":{\"main\":true},\"arts\":{\"a\":{\"group\":\"gone\"},\"b\":{\"group\":\"\"},\"c\":{}}}");
        assertEquals("unsorted", s.arts.get("a").group);
        assertEquals("main", s.arts.get("b").group);
        assertEquals("main", s.arts.get("c").group);
        assertFalse(s.inRotation("a"));
        assertTrue(s.inRotation("b"));
    }

    @Test
    void layerIdsAreUnique() {
        Settings s = parse("{\"menus\":{\"m\":{\"layers\":[{\"id\":\"x\"},{\"id\":\"x\"},{\"id\":\"\"},{},{\"id\":\"x\"}]}}}");
        Set<String> ids = new HashSet<>();
        for (Layer l : s.menus.get("m").layers) {
            assertFalse(l.id.isEmpty());
            assertTrue(ids.add(l.id), "duplicate id " + l.id);
        }
        assertEquals(5, ids.size());
        assertEquals("x", s.menus.get("m").layers.get(0).id, "the first keeps its id");
    }

    @Test
    void layerCountIsCapped() {
        StringBuilder sb = new StringBuilder("{\"menus\":{\"m\":{\"layers\":[");
        for (int i = 0; i < 40; i++) sb.append(i == 0 ? "" : ",").append("{\"id\":\"l").append(i).append("\"}");
        Settings s = parse(sb.append("]}}}").toString());
        List<Layer> ls = s.menus.get("m").layers;
        assertEquals(Settings.MAX_LAYERS, ls.size());
        assertEquals("l0", ls.get(0).id);
        assertEquals("l" + (Settings.MAX_LAYERS - 1), ls.get(ls.size() - 1).id, "the top layers are the ones dropped");
    }

    @Test
    void nullEntriesAreRemoved() {
        Settings s = parse("{\"menus\":{\"m\":{\"layers\":[null,{\"id\":\"a\"},null]},\"n\":null},\"arts\":{\"x\":null},\"groups\":{\"g\":null}}");
        assertEquals(1, s.menus.get("m").layers.size());
        assertFalse(s.menus.containsKey("n"));
        assertFalse(s.arts.containsKey("x"));
        assertEquals(Boolean.TRUE, s.groups.get("g"));
    }

    @Test
    void newIdIsSixHexDigits() {
        for (int i = 0; i < 200; i++) assertTrue(Settings.newId().matches("[0-9a-f]{6}"));
    }

    // ------------------------------------------------------------------ garbage input never throws out of parse()

    @Test
    void unreadableInputNeverThrows() {
        String[] bad = { "", "   ", "null", "garbage", "{", "{\"menus\":", "{\"menus\":{\"m\":{\"layers\":[{\"id\":", "[]", "[1,2]", "42", "\"text\"",
                "{\"opacity\":\"high\"}", "{\"menus\":[]}", "{\"menus\":{\"m\":5}}", "{\"menus\":{\"m\":{\"layers\":5}}}",
                "{\"menus\":{\"m\":{\"layers\":[1,2]}}}", "{\"title\":5}", "{\"arts\":{\"a\":[]}}", "\u0000\u0001\u0002" };
        for (String b : bad) assertDoesNotThrow(() -> Settings.parse(b), b);
    }

    @Test
    void nullLikeInputsGiveNullOrAUsableObject() {
        assertNull(Settings.parse(null));
        assertNull(Settings.parse(""));
        assertNull(Settings.parse("null"));
        assertNull(Settings.parse("garbage"));
        assertNull(Settings.parse("{\"menus\":{\"m\":{\"layers\":[{\"id\":"), "truncated json");
        Settings s = Settings.parse("{\"menus\":null,\"groups\":null,\"arts\":null,\"title\":null}");
        assertNotNull(s);
        assertNotNull(s.menus);
        assertNotNull(s.title);
    }

    @Test
    void truncatedValidFilesNeverThrow() {
        String full = parse("{\"menus\":{\"m\":{\"pinned\":\"a\",\"layers\":[{\"id\":\"q\",\"spots\":{\"a\":[1,2,3,4,5,6,7]}}]}}}").toJson();
        for (int cut = 0; cut < full.length(); cut += 3) {
            String part = full.substring(0, cut);
            assertDoesNotThrow(() -> Settings.parse(part), part);
        }
    }

    // ------------------------------------------------------------------ Layer, spots

    @Test
    void layerCopyIsDeepAndKeepsTheLink() {
        Layer l = new Layer();
        l.id = "x"; l.link = "inv"; l.group = "g"; l.art = "a"; l.src = Settings.SRC_GROUP;
        l.visible = false; l.locked = true; l.opacity = 0.2f; l.mode = 3;
        l.x = 1; l.y = 2; l.w = 3; l.h = 4; l.rot = 5; l.flip = true; l.flipY = true;
        l.spots.put("a", new int[] { 1, 2, 3, 4, 5, 1, 0 });
        Layer c = l.copy();
        assertEquals("inv", c.link);
        assertEquals("x", c.id);
        assertEquals("g", c.group);
        assertEquals("a", c.art);
        assertEquals(Settings.SRC_GROUP, c.src);
        assertFalse(c.visible);
        assertTrue(c.locked);
        assertEquals(0.2f, c.opacity);
        assertEquals(3, c.mode);
        assertEquals(4, c.h);
        assertEquals(5, c.rot);
        assertTrue(c.flip && c.flipY);
        assertNotSame(l.spots, c.spots);
        assertNotSame(l.spots.get("a"), c.spots.get("a"));
        c.spots.get("a")[0] = 99;
        assertEquals(1, l.spots.get("a")[0]);
        c.spots.clear();
        assertEquals(1, l.spots.size());
    }

    @Test
    void layerCopyOfANullLinkIsEmpty() {
        Layer l = new Layer();
        l.link = null;
        assertEquals("", l.copy().link);
    }

    @Test
    void spotRoundTripWithSevenInts() {
        int[] spot = { 10, 20, 30, 40, -45, 1, 1 };
        Placement p = Settings.fromSpot(spot);
        assertEquals(10, p.x);
        assertEquals(20, p.y);
        assertEquals(30, p.w);
        assertEquals(40, p.h);
        assertEquals(-45, p.rot);
        assertTrue(p.flip);
        assertTrue(p.flipY);
        assertArrayEquals(spot, Settings.toSpot(p));
    }

    @Test
    void sixIntLegacySpotHasNoVerticalFlip() {
        Placement p = Settings.fromSpot(new int[] { 1, 2, 3, 4, 5, 1 });
        assertTrue(p.flip);
        assertFalse(p.flipY);
        assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 1, 0 }, Settings.toSpot(p));
    }

    @Test
    void layerSpotLookup() {
        Layer l = new Layer();
        l.spots.put("auto", new int[] { 0, 0, 0, 0, 0, 1, 0 });
        l.spots.put("saved", new int[] { 1, 2, 30, 40, 0, 0, 0 });
        assertNull(l.spot("none"));
        assertFalse(l.hasSavedSpot("none"));
        assertFalse(l.hasSavedSpot("auto"));
        assertTrue(l.spot("auto").flip, "w = 0 means automatic but flipped");
        assertTrue(l.hasSavedSpot("saved"));
    }

    // ------------------------------------------------------------------ snapshots

    @Test
    void menuSnapshotRoundTrip() {
        Settings s = parse("{\"menus\":{\"m\":{\"opacity\":0.3,\"layers\":[{\"id\":\"a\",\"link\":\"inv\",\"spots\":{\"x\":[1,2,3,4,5,0,1]}}]}}}");
        String snap = s.menuJson("m");
        s.menus.get("m").layers.clear();
        s.menus.get("m").opacity = 0.9f;
        s.restoreMenu("m", snap);
        MenuCfg m = s.menus.get("m");
        assertEquals(0.3f, m.opacity);
        assertEquals(1, m.layers.size());
        assertEquals("inv", m.layers.get(0).link);
        assertArrayEquals(new int[] { 1, 2, 3, 4, 5, 0, 1 }, m.layers.get(0).spots.get("x"));
    }

    @Test
    void restoreMenuIgnoresNullAndRestoresAMissingMenu() {
        Settings s = parse("{}");
        s.restoreMenu("m", "null");
        assertFalse(s.menus.containsKey("m"));
        s.restoreMenu("m", "{\"layers\":[{\"id\":\"z\"}]}");
        assertEquals("z", s.menus.get("m").layers.get(0).id);
    }

    @Test
    void libSnapshotRoundTrip() {
        Settings s = parse("{\"groups\":{\"main\":true,\"g\":false},\"arts\":{\"a\":{\"group\":\"g\",\"rotation\":false}}}");
        String snap = s.libJson();
        s.groups.put("g", true);
        s.arts.clear();
        s.restoreLib(snap);
        assertEquals(Boolean.FALSE, s.groups.get("g"));
        assertEquals("g", s.arts.get("a").group);
        assertFalse(s.arts.get("a").rotation);
    }

    @Test
    void restoreLibIgnoresIncompleteSnapshots() {
        Settings s = parse("{\"groups\":{\"main\":true,\"g\":false}}");
        s.restoreLib("null");
        s.restoreLib("{\"groups\":{}}");
        assertEquals(Boolean.FALSE, s.groups.get("g"));
    }

    @Test
    void titleSnapshotRoundTrip() {
        Settings s = parse("{\"title\":{\"mode\":2,\"flipArt\":true}}");
        String snap = s.titleJson();
        s.title.mode = 0;
        s.title.flipArt = false;
        s.restoreTitle(snap);
        assertEquals(2, s.title.mode);
        assertTrue(s.title.flipArt);
    }

    @Test
    void wholeSetupSnapshotRoundTrip() {
        Settings s = parse("{\"opacity\":0.2,\"menus\":{\"m\":{\"layers\":[{\"id\":\"a\",\"src\":\"art\",\"art\":\"q\",\"w\":5,\"h\":6}]}}}");
        Settings again = parse(s.toJson());
        assertEquals(0.2f, again.opacity);
        assertEquals(s.menuJson("m"), again.menuJson("m"));
    }

    @Test
    void layersOfReadsASnapshotAndSurvivesGarbage() {
        Settings s = parse("{\"menus\":{\"m\":{\"layers\":[{\"id\":\"a\"},{\"id\":\"a\"}]}}}");
        List<Layer> ls = Settings.layersOf(s.menuJson("m"));
        assertEquals(2, ls.size());
        assertNotEquals(ls.get(0).id, ls.get(1).id);
        assertTrue(Settings.layersOf("not json").isEmpty());
        assertTrue(Settings.layersOf("null").isEmpty());
    }

    // ------------------------------------------------------------------ merge / replace / forget

    @Test
    void mergeNeverOverridesExistingData() {
        Settings mine = parse("""
            {"groups":{"main":true,"g":false},"arts":{"a1":{"group":"g","rotation":false}},
             "menus":{"m":{"opacity":0.1,"layers":[{"id":"mine","spots":{"s1":[1,1,10,10,0,0,0]}}]},
                      "empty":{"opacity":0.2}}}
            """);
        Settings other = parse("""
            {"groups":{"main":false,"g":true,"h":true},
             "arts":{"a1":{"group":"main","rotation":true},"a2":{"group":"h"}},
             "menus":{"m":{"opacity":0.9,"layers":[{"id":"theirs","spots":{"s1":[9,9,99,99,0,0,0],"s2":[2,2,20,20,0,0,0]}}]},
                      "empty":{"layers":[{"id":"t","src":"art","art":"q","w":5,"h":5}]},
                      "new":{"mode":1}}}
            """);
        mine.mergeFrom(other);
        assertEquals(Boolean.TRUE, mine.groups.get("main"));
        assertEquals(Boolean.FALSE, mine.groups.get("g"));
        assertEquals(Boolean.TRUE, mine.groups.get("h"));
        assertEquals("g", mine.arts.get("a1").group);
        assertFalse(mine.arts.get("a1").rotation);
        assertEquals("h", mine.arts.get("a2").group);

        MenuCfg m = mine.menus.get("m");
        assertEquals(0.1f, m.opacity);
        assertEquals(1, m.layers.size());
        assertEquals("mine", m.layers.get(0).id);
        assertArrayEquals(new int[] { 1, 1, 10, 10, 0, 0, 0 }, m.layers.get(0).spots.get("s1"));
        assertArrayEquals(new int[] { 2, 2, 20, 20, 0, 0, 0 }, m.layers.get(0).spots.get("s2"));

        MenuCfg e = mine.menus.get("empty");
        assertEquals(0.2f, e.opacity, "own menu settings stay");
        assertEquals(1, e.layers.size(), "a menu without layers takes the layers of the other");
        assertEquals(1, mine.menus.get("new").mode);
    }

    @Test
    void mergeLeavesNonRandomLayersAlone() {
        Settings mine = parse("{\"menus\":{\"m\":{\"layers\":[{\"id\":\"fx\",\"src\":\"art\",\"art\":\"q\",\"w\":5,\"h\":6}]}}}");
        Settings other = parse("{\"menus\":{\"m\":{\"layers\":[{\"id\":\"r\",\"spots\":{\"s\":[1,2,3,4,0,0,0]}}]}}}");
        mine.mergeFrom(other);
        Layer l = only(mine, "m");
        assertEquals("fx", l.id);
        assertTrue(l.spots.isEmpty());
    }

    @Test
    void replaceTakesEverythingFromTheOther() {
        Settings mine = parse("{\"enabled\":true,\"opacity\":0.1,\"menus\":{\"old\":{\"mode\":1}},\"groups\":{\"x\":true}}");
        Settings other = parse("""
            {"enabled":false,"widgetSkin":false,"showGear":false,"gearFx":1,"gearFy":2,"opacity":0.8,"mode":3,
             "title":{"mode":1},"menus":{"new":{"mode":2}},"groups":{"y":false},"arts":{"a":{"group":"y"}}}
            """);
        mine.replaceWith(other);
        assertFalse(mine.enabled);
        assertFalse(mine.showGear);
        assertEquals(1, mine.gearFx);
        assertEquals(2, mine.gearFy);
        assertEquals(0.8f, mine.opacity);
        assertEquals(3, mine.mode);
        assertEquals(1, mine.title.mode);
        assertFalse(mine.menus.containsKey("old"));
        assertEquals(2, mine.menus.get("new").mode);
        assertFalse(mine.groups.containsKey("x"));
        assertEquals(Boolean.FALSE, mine.groups.get("y"));
        assertEquals("y", mine.arts.get("a").group);
    }

    @Test
    void forgetArtRemovesEveryReference() {
        Settings s = parse("""
            {"arts":{"gone":{"group":"main"},"kept":{"group":"main","rotation":false}},
             "menus":{"m":{"layers":[
               {"id":"fixedGone","src":"art","art":"gone","w":5,"h":5},
               {"id":"fixedKept","src":"art","art":"kept","w":5,"h":5},
               {"id":"rnd","art":"gone","spots":{"gone":[1,2,3,4,0,0,0],"kept":[1,2,3,4,0,0,0]}},
               {"id":"rnd2","art":"kept"}]}}}
            """);
        s.forgetArt("gone");
        assertFalse(s.arts.containsKey("gone"));
        assertTrue(s.arts.containsKey("kept"));
        List<Layer> ls = s.menus.get("m").layers;
        assertEquals(3, ls.size());
        assertEquals("fixedKept", ls.get(0).id);
        Layer rnd = ls.get(1);
        assertEquals("", rnd.art);
        assertFalse(rnd.spots.containsKey("gone"));
        assertTrue(rnd.spots.containsKey("kept"));
        assertEquals("kept", ls.get(2).art);
    }

    // ------------------------------------------------------------------ accessors, prune

    @Test
    void viewsDoNotCreateEntriesButEditAccessorsDo() {
        Settings s = parse("{}");
        assertSame(s.menuView("nope"), s.menuView("other"));
        assertFalse(s.menus.containsKey("nope"));
        assertEquals(1, s.layersView("nope").size());
        assertEquals(Settings.MAIN_ID, s.layersView("nope").get(0).id);
        assertThrows(IllegalStateException.class, () -> Settings.guardShared(s.layersView("nope").get(0)));
        List<Layer> edit = s.layersEdit("nope");
        assertEquals(1, edit.size());
        assertNotSame(edit.get(0), s.layersView("other").get(0));
        assertDoesNotThrow(() -> Settings.guardShared(edit.get(0)));
        assertTrue(s.menus.containsKey("nope"));
    }

    @Test
    void opacityAndModeFollowTheMenuAndTheGlobalValue() {
        Settings s = parse("{\"opacity\":0.6,\"mode\":1}");
        MenuCfg m = new MenuCfg();
        Layer l = new Layer();
        assertEquals(0.6f, s.opacityOf(m, l));
        assertEquals(1, s.modeOf(m, l));
        m.opacity = 0.3f; m.mode = 2;
        assertEquals(0.3f, s.opacityOf(m, l));
        assertEquals(2, s.modeOf(m, l));
        l.opacity = 0.9f; l.mode = 0;
        assertEquals(0.9f, s.opacityOf(m, l));
        assertEquals(0, s.modeOf(m, l));
    }

    @Test
    void pruneDropsDefaultsOnly() throws Exception {
        Settings s = parse("""
            {"arts":{"plain":{"group":"main","rotation":true},"off":{"group":"main","rotation":false}},
             "menus":{"onlyDefault":{"layers":[{"id":"main"}]},
                      "calibrated":{"layers":[{"id":"main","spots":{"a":[1,2,3,4,0,0,0]}}]},
                      "linked":{"layers":[{"id":"main","link":"inv"}]},
                      "disabled":{"disabled":true},
                      "twoLayers":{"layers":[{"id":"a"},{"id":"b"}]}}}
            """);
        Method prune = Settings.class.getDeclaredMethod("prune");
        prune.setAccessible(true);
        prune.invoke(s);
        assertFalse(s.arts.containsKey("plain"));
        assertTrue(s.arts.containsKey("off"));
        assertFalse(s.menus.containsKey("onlyDefault"));
        assertTrue(s.menus.containsKey("calibrated"));
        assertTrue(s.menus.containsKey("linked"));
        assertTrue(s.menus.containsKey("disabled"));
        assertEquals(2, s.menus.get("twoLayers").layers.size());
    }
}
