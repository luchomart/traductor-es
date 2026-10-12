package com.luchomart.traductores;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lee los archivos de idioma (assets/<ns>/lang/<idioma>.json) de todos los packs cargados. */
public final class LangScanner {
    private static final Pattern LANG_FILE = Pattern.compile("lang/([a-z0-9_]+)\\.json");

    private LangScanner() {}

    /** namespace → idioma → clave → texto. Solo carga los idiomas que hacen falta. */
    public static Map<String, Map<String, Map<String, String>>> scan(ResourceManager rm) {
        Map<ResourceLocation, List<Resource>> stacks = rm.listResourceStacks("lang", rl -> LANG_FILE.matcher(rl.getPath()).matches());

        // Primero se agrupan los archivos por namespace sin leerlos.
        Map<String, Map<String, List<Resource>>> files = new TreeMap<>();
        for (Map.Entry<ResourceLocation, List<Resource>> e : stacks.entrySet()) {
            Matcher m = LANG_FILE.matcher(e.getKey().getPath());
            if (!m.matches()) continue;
            files.computeIfAbsent(e.getKey().getNamespace(), k -> new TreeMap<>()).put(m.group(1), e.getValue());
        }

        Map<String, Map<String, Map<String, String>>> out = new TreeMap<>();
        for (Map.Entry<String, Map<String, List<Resource>>> nsEntry : files.entrySet()) {
            Map<String, List<Resource>> langs = nsEntry.getValue();
            boolean hasEnglish = langs.containsKey("en_us");
            Map<String, Map<String, String>> nsOut = new LinkedHashMap<>();
            for (Map.Entry<String, List<Resource>> langEntry : langs.entrySet()) {
                String lang = langEntry.getKey();
                // Sin inglés hace falta cualquier otro idioma como origen; con inglés, solo inglés y español.
                if (hasEnglish && !lang.equals("en_us") && !lang.startsWith("es_")) continue;
                Map<String, String> merged = read(langEntry.getValue());
                if (!merged.isEmpty()) nsOut.put(lang, merged);
            }
            if (!nsOut.isEmpty()) out.put(nsEntry.getKey(), nsOut);
        }
        return out;
    }

    /** Combina un archivo de varios packs: los de más arriba pisan a los de más abajo. Ignora nuestro propio pack. */
    private static Map<String, String> read(List<Resource> stack) {
        Map<String, String> merged = new HashMap<>();
        for (Resource res : stack) {
            if (PackWriter.PACK_ID.equals(res.sourcePackId())) continue;
            try (Reader r = res.openAsReader()) {
                JsonElement el = JsonParser.parseReader(r);
                if (!el.isJsonObject()) continue;
                JsonObject obj = el.getAsJsonObject();
                for (Map.Entry<String, JsonElement> kv : obj.entrySet()) {
                    JsonElement v = kv.getValue();
                    if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString()) merged.put(kv.getKey(), v.getAsString());
                }
            } catch (Exception ex) {
                TraductorES.LOG.debug("[Traductor ES] No se pudo leer un archivo de idioma de {}: {}", res.sourcePackId(), ex.toString());
            }
        }
        return merged;
    }
}
