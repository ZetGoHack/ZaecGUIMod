package com.mahiru.gui;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Only the validation in {@link SetupZip#inspect}; zips without imported arts never touch the game's config folder. */
class SetupZipTest {
    @TempDir Path tmp;

    private static final String MANIFEST = "{\"format\":1,\"mod\":\"zaecguimod\",\"created\":\"2026-01-02T03:04:05\"}";
    private static final String LAYOUT = "{\"format\":2,\"opacity\":0.25,\"menus\":{\"furnace\":{\"mode\":2}}}";

    private Path zip(Map<String, byte[]> entries) throws IOException {
        Path p = tmp.resolve("t" + System.nanoTime() + ".zip");
        try (OutputStream os = Files.newOutputStream(p); ZipOutputStream z = new ZipOutputStream(os)) {
            for (var e : entries.entrySet()) {
                z.putNextEntry(new ZipEntry(e.getKey()));
                z.write(e.getValue());
                z.closeEntry();
            }
        }
        return p;
    }

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    private static Map<String, byte[]> valid() {
        Map<String, byte[]> m = new LinkedHashMap<>();
        m.put("manifest.json", b(MANIFEST));
        m.put("layout.json", b(LAYOUT));
        return m;
    }

    /** the localisation key of the rejection, or null when the zip was accepted */
    private String rejection(Map<String, byte[]> entries) throws IOException {
        try {
            SetupZip.inspect(zip(entries));
            return null;
        } catch (Exception e) {
            return SetupZip.errorKey(e);
        }
    }

    @Test
    void validMinimalZipIsAccepted() throws Exception {
        SetupZip.Preview p = SetupZip.inspect(zip(valid()));
        assertTrue(p.hasSettings);
        assertEquals("2026-01-02T03:04:05", p.created);
        assertEquals(0.25f, p.settings.opacity, 1e-6f);
        assertEquals(2, p.settings.menus.get("furnace").mode);
        assertTrue(p.artIds.isEmpty());
    }

    @Test
    void zipSlipEntriesAreRejected() throws IOException {
        for (String evil : new String[] { "../evil.txt", "imports/../../evil.png", "/etc/passwd", "..\\evil.json", "imports/u_0123456789ab_b.png/../../x" }) {
            Map<String, byte[]> m = valid();
            m.put(evil, b("x"));
            assertEquals("the file contains unexpected entries (not a setup made by this mod)", rejection(m), evil);
        }
    }

    @Test
    void unknownAndMalformedArtNamesAreRejected() throws IOException {
        for (String name : new String[] { "readme.txt", "imports/u_XYZ_b.png", "imports/u_0123456789ab_x.png", "imports/u_0123456789ab_b.png.exe" }) {
            Map<String, byte[]> m = valid();
            m.put(name, b("x"));
            assertNotNull(rejection(m), name);
        }
    }

    @Test
    void oversizeEntryIsRejected() throws IOException {
        Map<String, byte[]> m = valid();
        byte[] big = new byte[41 * 1024 * 1024];   // zeros: compresses to a few KB, but unpacks beyond the 40 MB cap
        java.util.Arrays.fill(big, (byte) ' ');
        m.put("layout.json", big);
        assertEquals("the file contains an entry that is too large", rejection(m));
    }

    @Test
    void tooManyEntriesAreRejected() throws IOException {
        Map<String, byte[]> m = valid();
        for (int i = 0; i < 2001; i++) m.put("imports/u_" + String.format("%012x", i) + "_s.png", new byte[0]);
        assertEquals("the file has too many entries", rejection(m));
    }

    @Test
    void missingManifestIsRejected() throws IOException {
        Map<String, byte[]> m = valid();
        m.remove("manifest.json");
        assertEquals("this is not a setup zip (no manifest)", rejection(m));
    }

    @Test
    void newerFormatIsRejected() throws IOException {
        Map<String, byte[]> m = valid();
        m.put("manifest.json", b("{\"format\":99}"));
        assertEquals("the setup was made by a newer version of the mod", rejection(m));
    }

    @Test
    void damagedLayoutIsRejected() throws IOException {
        Map<String, byte[]> m = valid();
        m.put("layout.json", b("{\"menus\": {"));
        assertEquals("layout.json inside the zip is damaged", rejection(m));
    }

    @Test
    void zipWithNothingToImportIsRejected() throws IOException {
        Map<String, byte[]> m = valid();
        m.remove("layout.json");
        assertEquals("the zip contains nothing to import", rejection(m));
    }

    @Test
    void garbageFileGivesTheGenericError() throws IOException {
        Path p = tmp.resolve("garbage.zip");
        Files.writeString(p, "this is not a zip");
        Exception e = assertThrows(Exception.class, () -> SetupZip.inspect(p));
        assertEquals("could not read the zip file", SetupZip.errorKey(e));
    }

    @Test
    void manifestThatIsNotAnObjectIsRejectedCleanly() throws IOException {
        Map<String, byte[]> m = valid();
        m.put("manifest.json", b("[1,2,3]"));
        assertEquals("could not read the zip file", rejection(m));
    }
}
