package com.luchomart.traductores;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Cuenta, sin traducir nada, cuántos textos le faltan a la variante de destino.
 * Sirve para el asistente ("18.883 textos en 142 mods · ~4 h").
 */
public final class Estimator {
    public record Result(int texts, int mods) {
        /** Tiempo aproximado con el servicio configurado (ver ServiceGuide). */
        public String duration(Config cfg) {
            return texts == 0 ? "nada" : ServiceGuide.estimate(cfg).duration(texts);
        }
    }

    private Estimator() {}

    /** lang: lo que devuelve LangScanner.scan (namespace → idioma → clave → texto). */
    public static Result estimate(Map<String, Map<String, Map<String, String>>> lang, String target, Config cfg) {
        TranslationCache cache = new TranslationCache(target);
        Set<String> uniqueTexts = new HashSet<>();
        int mods = 0;
        for (Map.Entry<String, Map<String, Map<String, String>>> ns : lang.entrySet()) {
            if (cfg.excludedNamespaces.contains(ns.getKey())) continue;
            Map<String, Map<String, String>> langs = ns.getValue();
            Map<String, String> source = langs.getOrDefault("en_us", Map.of());
            Map<String, String> official = langs.getOrDefault(target, Map.of());
            boolean any = false;
            for (Map.Entry<String, String> kv : source.entrySet()) {
                String key = kv.getKey(), text = kv.getValue();
                if (official.containsKey(key) && !Validator.isEnglishCopy(text, official.get(key)) || Validator.isTrivial(text)) continue;
                if (cfg.useSpanishVariants && inVariant(langs, target, key, text)) continue;
                String cached = cache.get(ns.getKey(), key, text);
                if (cached != null && Validator.check(text, cached) == null) continue;
                uniqueTexts.add(text);
                any = true;
            }
            if (any) mods++;
        }
        return new Result(uniqueTexts.size(), mods);
    }

    private static boolean inVariant(Map<String, Map<String, String>> langs, String target, String key, String english) {
        for (String v : SpanishTarget.fallbacks(target)) {
            Map<String, String> m = langs.get(v);
            if (m != null && m.containsKey(key) && !Validator.isEnglishCopy(english, m.get(key))) return true;
        }
        return false;
    }
}
