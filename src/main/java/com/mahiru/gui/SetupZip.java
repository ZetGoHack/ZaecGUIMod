package com.mahiru.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.Util;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/** Export / import of the whole setup (settings + imported arts) as one zip. */
public final class SetupZip {
    public static final int FORMAT = 1;
    private static final long MAX_ENTRY = 40L * 1024 * 1024, MAX_TOTAL = 500L * 1024 * 1024;
    private static final int MAX_ENTRIES = 2000;
    private static final Pattern ART_FILE = Pattern.compile("imports/(u_[0-9a-f]{12})_([bs])\\.png");

    /** What a zip contains, checked but not yet applied. */
    public static final class Preview {
        public Path zip;
        public boolean hasSettings;
        public String created = "";
        public final List<String> artIds = new ArrayList<>();
        public int newArts;
        public Settings settings;
        public Map<String, Imports.Entry> library = new LinkedHashMap<>();
    }

    private static final class Bad extends Exception {
        Bad(String m) { super(m); }
    }

    public static Path exportDir() { return Settings.configDir().resolve("exports"); }

    /** Writes a new zip into config/zaecguimod/exports and returns its path. */
    public static Path export() throws Exception {
        Settings.save();
        Files.createDirectories(exportDir());
        Path out = exportDir().resolve("zaecguimod-setup-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip");
        try (OutputStream os = Files.newOutputStream(out); ZipOutputStream z = new ZipOutputStream(os)) {
            JsonObject man = new JsonObject();
            man.addProperty("format", FORMAT);
            man.addProperty("mod", "zaecguimod");
            man.addProperty("created", LocalDateTime.now().toString());
            put(z, "manifest.json", man.toString().getBytes(StandardCharsets.UTF_8));
            Path layout = Settings.configDir().resolve("layout.json");
            if (Files.exists(layout)) put(z, "layout.json", Files.readAllBytes(layout));
            List<Imports.Entry> lib = Imports.load();
            if (!lib.isEmpty()) {
                put(z, "imports/library.json", Files.readAllBytes(Imports.dir().resolve("library.json")));
                for (Imports.Entry e : lib) {
                    put(z, "imports/" + e.id + "_b.png", Files.readAllBytes(Imports.bigFile(e.id)));
                    put(z, "imports/" + e.id + "_s.png", Files.readAllBytes(Imports.smallFile(e.id)));
                }
            }
        }
        return out;
    }

    private static void put(ZipOutputStream z, String name, byte[] data) throws Exception {
        z.putNextEntry(new ZipEntry(name));
        z.write(data);
        z.closeEntry();
    }

    private static byte[] read(ZipFile z, ZipEntry e) throws Exception {
        try (InputStream in = z.getInputStream(e)) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            long n = 0;
            int r;
            while ((r = in.read(buf)) > 0) {
                n += r;
                if (n > MAX_ENTRY) throw new Bad("the file contains an entry that is too large");
                bo.write(buf, 0, r);
            }
            return bo.toByteArray();
        }
    }

    /** Validates a zip without touching anything on disk. Runs on a worker thread. */
    public static Preview inspect(Path zip) throws Exception {
        Preview p = new Preview();
        p.zip = zip;
        try (ZipFile z = new ZipFile(zip.toFile())) {
            if (z.size() > MAX_ENTRIES) throw new Bad("the file has too many entries");
            long total = 0;
            for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements(); ) {
                ZipEntry e = en.nextElement();
                if (e.isDirectory()) continue;
                String n = e.getName();
                boolean ok = n.equals("manifest.json") || n.equals("layout.json") || n.equals("imports/library.json") || ART_FILE.matcher(n).matches();
                if (!ok) throw new Bad("the file contains unexpected entries (not a setup made by this mod)");
                total += Math.max(0, e.getSize());
                if (total > MAX_TOTAL) throw new Bad("the file is too large when unpacked");
            }
            ZipEntry man = z.getEntry("manifest.json");
            if (man == null) throw new Bad("this is not a setup zip (no manifest)");
            JsonObject mo = JsonParser.parseString(new String(read(z, man), StandardCharsets.UTF_8)).getAsJsonObject();
            if (!mo.has("format") || mo.get("format").getAsInt() > FORMAT) throw new Bad("the setup was made by a newer version of the mod");
            p.created = mo.has("created") ? mo.get("created").getAsString() : "";
            ZipEntry lay = z.getEntry("layout.json");
            if (lay != null) {
                p.settings = Settings.parse(new String(read(z, lay), StandardCharsets.UTF_8));
                if (p.settings == null) throw new Bad("layout.json inside the zip is damaged");
                p.hasSettings = true;
            }
            ZipEntry lib = z.getEntry("imports/library.json");
            if (lib != null) {
                var arr = com.google.gson.JsonParser.parseString(new String(read(z, lib), StandardCharsets.UTF_8)).getAsJsonArray();
                var gson = new com.google.gson.Gson();
                List<String> have = new ArrayList<>();
                for (Imports.Entry e : Imports.load()) have.add(e.id);
                for (var el : arr) {
                    Imports.Entry e = gson.fromJson(el, Imports.Entry.class);
                    if (e == null || e.id == null || !e.id.matches("u_[0-9a-f]{12}") || e.w <= 0 || e.h <= 0 || e.sw <= 0 || e.sh <= 0 || e.w > 8192 || e.h > 8192 || e.sw > 8192 || e.sh > 8192) continue;
                    if (z.getEntry("imports/" + e.id + "_b.png") == null || z.getEntry("imports/" + e.id + "_s.png") == null) continue;
                    p.library.put(e.id, e);
                    p.artIds.add(e.id);
                    if (!have.contains(e.id)) p.newArts++;
                }
            }
            if (!p.hasSettings && p.artIds.isEmpty()) throw new Bad("the zip contains nothing to import");
        } catch (Bad b) {
            throw b;
        } catch (Exception e) {
            throw new Bad("could not read the zip file");
        }
        return p;
    }

    public static String errorKey(Throwable t) {
        return t instanceof Bad ? t.getMessage() : "could not read the zip file";
    }

    /**
     * Applies a checked zip. Merge only adds what is missing locally; replace first copies the current setup to
     * config/zaecguimod/backups/replace-TIMESTAMP. File work runs on a worker, the settings swap on the game thread.
     */
    public static void apply(Preview p, boolean settings, boolean arts, boolean replace, Consumer<String> done) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        // the backup copies layout.json: write the live settings now, on the game thread, before the worker starts
        if (replace) Settings.save();
        Thread t = new Thread(() -> {
            String err = null;
            List<String> dropped = new ArrayList<>();
            try {
                if (replace) backup();
                if (arts && !p.library.isEmpty()) copyArts(p, replace, dropped);
            } catch (Throwable ex) {
                Guard.LOG.error("[ZaecGuiMod] setup import failed", ex);
                err = "could not write the imported files";
            }
            String fe = err;
            mc.execute(() -> {
                if (fe == null) {
                    Guard.run("setup-apply", () -> {
                        for (String id : dropped) Arts.releaseTextures(id);   // frees the GPU copies of replaced imports
                        Arts.invalidate();
                        if (settings && p.settings != null) {
                            if (replace) Settings.get().replaceWith(p.settings);
                            else Settings.get().mergeFrom(p.settings);
                        }
                        Settings.save();
                    });
                }
                done.accept(fe);
            });
        }, "Zaec-SetupImport");
        t.setDaemon(true);
        t.start();
    }

    private static void backup() throws Exception {
        Path dir = Settings.configDir().resolve("backups").resolve("replace-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        Files.createDirectories(dir);
        Path layout = Settings.configDir().resolve("layout.json");
        if (Files.exists(layout)) Files.copy(layout, dir.resolve("layout.json"));
        if (Files.isDirectory(Imports.dir())) {
            Path id = dir.resolve("imports");
            Files.createDirectories(id);
            try (var s = Files.list(Imports.dir())) {
                for (Path f : (Iterable<Path>) s::iterator) if (Files.isRegularFile(f)) Files.copy(f, id.resolve(f.getFileName().toString()));
            }
        }
    }

    private static void copyArts(Preview p, boolean replace, List<String> dropped) throws Exception {
        Files.createDirectories(Imports.dir());
        if (replace) {
            for (Imports.Entry e : Imports.load()) {
                dropped.add(e.id);
                Files.deleteIfExists(Imports.bigFile(e.id));
                Files.deleteIfExists(Imports.smallFile(e.id));
            }
        }
        List<Imports.Entry> lib = replace ? new ArrayList<>() : Imports.load();
        List<String> have = new ArrayList<>();
        for (Imports.Entry e : lib) have.add(e.id);
        try (ZipFile z = new ZipFile(p.zip.toFile())) {
            for (Imports.Entry e : p.library.values()) {
                if (have.contains(e.id)) continue;
                for (String suffix : new String[] { "_b", "_s" }) {
                    ZipEntry ze = z.getEntry("imports/" + e.id + suffix + ".png");
                    Path dst = Imports.dir().resolve(e.id + suffix + ".png");
                    byte[] data = read(z, ze);
                    Path tmp = Imports.dir().resolve(e.id + suffix + ".png.tmp");
                    Files.write(tmp, data);
                    Files.move(tmp, dst, StandardCopyOption.REPLACE_EXISTING);
                }
                lib.add(e);
            }
        }
        Imports.storeAll(lib);
    }

    public static void openFolder(Path dir) {
        try { Util.getPlatform().openFile(dir.toFile()); } catch (Throwable ignored) { }
    }

    private SetupZip() {}
}
