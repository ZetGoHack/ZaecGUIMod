package com.mahiru.gui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The English text is the translation key; ru_ru.json must cover every literal the code passes to T.t / T.f. */
class I18nTest {
    private static final Path SRC = TestPaths.project("src/main/java");
    private static final Path LANG = TestPaths.project("src/main/resources/assets/zaecguimod/lang");
    private static final String STR = "\"((?:[^\"\\\\\\n]|\\\\.)*)\"";
    /** T.t("a") / T.f("a", ...) */
    private static final Pattern DIRECT = Pattern.compile("\\bT\\.[tf]\\(\\s*" + STR);
    /** T.t(cond ? "a" : "b") */
    private static final Pattern TERNARY = Pattern.compile("\\bT\\.[tf]\\(\\s*[^\"();]*\\?\\s*" + STR + "\\s*:\\s*" + STR);
    private static final Pattern FORMAT = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[a-zA-Z%]");

    private static String unescape(String lit) {
        return new Gson().fromJson("\"" + lit + "\"", String.class);
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> s = Files.walk(SRC)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** every literal used as a translation key, with the file that uses it */
    private static Map<String, String> usedKeys() throws IOException {
        Map<String, String> keys = new LinkedHashMap<>();
        for (Path p : javaFiles()) {
            String text = Files.readString(p, StandardCharsets.UTF_8);
            for (Pattern pat : new Pattern[] { DIRECT, TERNARY }) {
                Matcher m = pat.matcher(text);
                while (m.find())
                    for (int g = 1; g <= m.groupCount(); g++) keys.putIfAbsent(unescape(m.group(g)), p.getFileName().toString());
            }
        }
        for (String s : Settings.MODE_NAMES) keys.putIfAbsent(s, "Settings.MODE_NAMES");
        for (String s : Settings.TITLE_MODES) keys.putIfAbsent(s, "Settings.TITLE_MODES");
        for (String s : Settings.BG_TYPES) keys.putIfAbsent(s, "Settings.BG_TYPES");
        for (MenuInfo.Entry e : MenuInfo.ENTRIES) keys.putIfAbsent(e.title(), "MenuInfo.ENTRIES");
        return keys;
    }

    /** reads a flat json object; fails on duplicate keys and non-string values */
    private static Map<String, String> readLang(String file) throws IOException {
        String text = Files.readString(LANG.resolve(file), StandardCharsets.UTF_8);
        Map<String, String> out = new LinkedHashMap<>();
        try (JsonReader r = new JsonReader(new StringReader(text))) {
            r.beginObject();
            while (r.peek() != JsonToken.END_OBJECT) {
                String k = r.nextName();
                assertEquals(JsonToken.STRING, r.peek(), file + ": value of '" + k + "' is not a string");
                assertNull(out.put(k, r.nextString()), file + ": duplicate key '" + k + "'");
            }
            r.endObject();
            assertEquals(JsonToken.END_DOCUMENT, r.peek(), file + ": trailing data");
        }
        return out;
    }

    private static List<String> specs(String s) {
        List<String> l = new ArrayList<>();
        Matcher m = FORMAT.matcher(s);
        while (m.find()) if (!m.group().equals("%%")) l.add(m.group().replaceFirst("^%\\d+\\$", "%"));
        java.util.Collections.sort(l);   // a translation may reorder arguments (%2$s ... %1$s)
        return l;
    }

    @Test
    void sourceScanFindsALotOfKeys() throws IOException {
        // guards the scanner itself: a broken regex would make every other test pass vacuously
        assertTrue(usedKeys().size() > 100, "only " + usedKeys().size() + " keys found");
    }

    @Test
    void ruLangParsesWithoutDuplicatesOrEmptyValues() throws IOException {
        Map<String, String> ru = readLang("ru_ru.json");
        assertFalse(ru.isEmpty());
        ru.forEach((k, v) -> {
            assertFalse(k.isBlank(), "blank key");
            assertFalse(v.isBlank(), "empty value for '" + k + "'");
        });
    }

    @Test
    void everyKeyUsedInCodeIsTranslatedToRussian() throws IOException {
        Map<String, String> ru = readLang("ru_ru.json");
        List<String> missing = new ArrayList<>();
        usedKeys().forEach((k, where) -> { if (!ru.containsKey(k)) missing.add("\"" + k + "\"  (" + where + ")"); });
        assertTrue(missing.isEmpty(), missing.size() + " untranslated keys:\n" + String.join("\n", missing));
    }

    @Test
    void formatSpecifiersMatchBetweenKeyAndRussianValue() throws IOException {
        List<String> bad = new ArrayList<>();
        readLang("ru_ru.json").forEach((k, v) -> {
            if (!specs(k).equals(specs(v))) bad.add("\"" + k + "\" -> \"" + v + "\"");
        });
        assertTrue(bad.isEmpty(), "format specifiers differ:\n" + String.join("\n", bad));
    }

    @Test
    void usedFormatKeysAreFormattableWithTheirArguments() throws IOException {
        // a Russian value must not use more arguments than the English key (it would throw at runtime)
        readLang("ru_ru.json").forEach((k, v) -> {
            Object[] args = new Object[specs(k).size()];
            java.util.Arrays.fill(args, "x");
            assertDoesNotThrow(() -> String.format(v, args), k);
        });
    }

    @Test
    void enLangHasTheKeyBindingNames() throws IOException {
        Map<String, String> en = readLang("en_us.json");
        String hooks = Files.readString(SRC.resolve("com/mahiru/gui/KeyHooks.java"), StandardCharsets.UTF_8);
        Set<String> wanted = new LinkedHashSet<>();
        Matcher m = Pattern.compile("\"(key\\.[a-z_.]+)\"").matcher(hooks);
        while (m.find()) wanted.add(m.group(1));
        assertTrue(wanted.size() >= 3, "expected the category and two key names in KeyHooks, got " + wanted);
        for (String k : wanted) {
            assertTrue(en.containsKey(k), "en_us.json lacks " + k);
            assertFalse(en.get(k).isBlank(), k);
        }
    }

    @Test
    void reportRussianKeysNoSourceLiteralUses() throws IOException {
        Set<String> used = usedKeys().keySet();
        List<String> unused = new ArrayList<>();
        for (String k : readLang("ru_ru.json").keySet()) if (!used.contains(k)) unused.add(k);
        // informational only: keys may be built at runtime (for example Imports error messages)
        Files.createDirectories(TestPaths.project("build"));
        Files.write(TestPaths.project("build/i18n-unused-ru-keys.txt"), unused, StandardCharsets.UTF_8);
        System.out.println("ru_ru.json keys without a source literal (" + unused.size() + "):");
        unused.forEach(k -> System.out.println("  " + k));
    }
}
