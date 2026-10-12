package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Términos que deben traducirse siempre igual. Por prioridad:
 * los del usuario (glosario.json), los oficiales de Minecraft, los nombres del mismo mod y los de otros mods.
 */
public class Glossary {
    public static final Path USER_FILE = Config.DIR.resolve("glosario.json");
    /** Claves de nombres: item.mod.cosa, block.mod.cosa, entity.mod.cosa… */
    public static final Pattern NAME_KEY = Pattern.compile(
            "^(block|item|entity|fluid|fluid_type|effect|enchantment|biome)\\.[a-z0-9_]+\\.[a-z0-9_]+$");
    private static final int MAX_TERMS_PER_BATCH = 30;

    private enum Source { USER, VANILLA, MOD }

    private record Term(String source, String lower, String target, Source kind, String ns) {}

    private final List<Term> fixed = new ArrayList<>();
    private final List<Term> mods = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    public static Glossary create() {
        Glossary g = new Glossary();
        for (Map.Entry<String, String> user : loadUser().entrySet()) g.add(user.getKey(), user.getValue(), Source.USER, "");
        return g;
    }

    /** Agrega los nombres oficiales de Minecraft (en_us → idioma de destino). */
    public void addVanilla(Map<String, String> en, Map<String, String> es) {
        for (Map.Entry<String, String> e : en.entrySet()) {
            String t = es.get(e.getKey());
            if (t != null && NAME_KEY.matcher(e.getKey()).matches()) add(e.getValue(), t, Source.VANILLA, "minecraft");
        }
        sort();
    }

    /** Agrega un nombre de un mod (de su español oficial, de una variante o de una traducción ya hecha). */
    public void addModName(String ns, String key, String en, String es) {
        if (NAME_KEY.matcher(key).matches()) add(en, es, Source.MOD, ns);
    }

    private synchronized void add(String en, String es, Source kind, String ns) {
        if (en == null || es == null || en.length() < 3 || en.contains("%") || en.contains("§") || en.equals(es)) return;
        String lower = en.toLowerCase(Locale.ROOT);
        if (!seen.add(lower)) return;
        Term t = new Term(en, lower, es, kind, ns);
        (kind == Source.MOD ? mods : fixed).add(t);
    }

    /** Ordena: largos primero ("Ender Chest" antes que "Chest"). Llamar después de cargas masivas. */
    public synchronized void sort() {
        Comparator<Term> c = Comparator.comparing((Term t) -> t.kind).thenComparing(t -> -t.source.length());
        fixed.sort(c);
        mods.sort(Comparator.comparing((Term t) -> -t.source.length()));
    }

    public synchronized int size() { return fixed.size() + mods.size(); }

    /** Términos que aparecen (como palabra completa) en los textos del lote; primero los del mismo mod. */
    public synchronized Map<String, String> relevant(Collection<String> texts, String ns) {
        String all = String.join("\n", texts).toLowerCase(Locale.ROOT);
        Map<String, String> out = new LinkedHashMap<>();
        collect(fixed, all, out, null);
        collect(mods, all, out, ns);
        collect(mods, all, out, null);
        return out;
    }

    private static void collect(List<Term> terms, String all, Map<String, String> out, String onlyNs) {
        for (Term t : terms) {
            if (out.size() >= MAX_TERMS_PER_BATCH) return;
            if (onlyNs != null && !onlyNs.equals(t.ns)) continue;
            if (out.containsKey(t.source)) continue;
            int idx = all.indexOf(t.lower);
            while (idx >= 0) {
                if (isBoundary(all, idx - 1) && isBoundary(all, idx + t.lower.length())) {
                    out.put(t.source, t.target);
                    break;
                }
                idx = all.indexOf(t.lower, idx + 1);
            }
        }
    }

    private static boolean isBoundary(String s, int i) {
        return i < 0 || i >= s.length() || !Character.isLetterOrDigit(s.charAt(i));
    }

    private static Map<String, String> loadUser() {
        try {
            if (!Files.exists(USER_FILE)) {
                Files.createDirectories(Config.DIR);
                Map<String, String> example = new LinkedHashMap<>();
                example.put("_comentario", "Agrega aquí términos propios: \"Original\": \"Traducción\". Las claves que empiezan con _ se ignoran.");
                Files.writeString(USER_FILE, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(example), StandardCharsets.UTF_8);
                return Map.of();
            }
            Map<String, String> m = new Gson().fromJson(Files.readString(USER_FILE, StandardCharsets.UTF_8),
                    new TypeToken<LinkedHashMap<String, String>>() {}.getType());
            if (m == null) return Map.of();
            m.keySet().removeIf(k -> k.startsWith("_"));
            return m;
        } catch (Exception e) {
            TraductorES.LOG.error("[Traductor ES] glosario.json inválido, se ignora", e);
            return Map.of();
        }
    }
}
