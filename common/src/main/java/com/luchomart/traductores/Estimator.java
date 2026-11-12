package com.luchomart.traductores;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Cuenta, sin traducir nada, cuántos textos le faltan a la variante de destino: los de los mods, los libros y las misiones.
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

    private record Scan(Map<String, Map<String, Map<String, String>>> lang, List<Books.Book> books) {}

    /** Lee los recursos en el hilo del juego y cuenta en el hilo que llama (no llamar desde el hilo del juego). */
    public static Result estimateModpack(Minecraft mc, String target, Config cfg) {
        List<String> variants = cfg.useSpanishVariants ? List.copyOf(SpanishTarget.fallbacks(target)) : List.of();
        CompletableFuture<Scan> scan = new CompletableFuture<>();
        mc.execute(() -> {
            try {
                ResourceManager rm = mc.getResourceManager();
                scan.complete(new Scan(LangScanner.scan(rm), cfg.translateBooks ? Books.scan(rm, target, variants) : List.of()));
            } catch (Throwable e) {
                scan.completeExceptionally(e);
            }
        });
        Scan s = scan.join();
        TranslationCache cache = new TranslationCache(target);
        Set<String> uniqueTexts = new HashSet<>();
        int mods = countLang(s.lang(), target, cfg, cache, uniqueTexts);
        if (cfg.translateBooks) {
            countBooks(s.books(), cache, uniqueTexts);
            if (Platform.readsDataBooks()) {
                countBooks(Books.scanDataBooks(Books.readJarBooks(mc.gameDirectory.toPath().resolve("mods")), target, variants), cache, uniqueTexts);
            }
            countQuests(target, variants, cache, uniqueTexts);
            InfoTexts.known().forEach((key, text) -> addIfMissing(cache, InfoTexts.NS, key, text, uniqueTexts));
        }
        return new Result(uniqueTexts.size(), mods);
    }

    private static void countBooks(List<Books.Book> books, TranslationCache cache, Set<String> uniqueTexts) {
        for (Books.Book book : books) {
            book.english.forEach((file, json) -> Books.extract(book.format, file, json,
                    (pointer, text) -> addIfMissing(cache, book.pseudoNamespace(), file + "#" + pointer, text, uniqueTexts)));
        }
    }

    private static void countQuests(String target, List<String> variants, TranslationCache cache, Set<String> uniqueTexts) {
        try {
            FtbQuests.Quests quests = FtbQuests.scan(target, variants);
            if (quests != null) {
                FtbQuests.extract(quests, (key, text) -> addIfMissing(cache, FtbQuests.NS, key, text, uniqueTexts));
            } else if (Platform.readsOldQuests()) {
                Map<String, String> lines = FtbQuests.scanOld();
                if (lines != null) lines.forEach((key, line) -> addIfMissing(cache, FtbQuests.NS, key, FtbQuests.translatable(line), uniqueTexts));
            }
        } catch (IOException | RuntimeException e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudieron contar las misiones: {}", e.toString());
        }
    }

    private static void addIfMissing(TranslationCache cache, String ns, String key, String text, Set<String> uniqueTexts) {
        if (Validator.isTrivial(text)) return;
        String cached = cache.get(ns, key, text);
        if (cached == null || Validator.check(text, cached) != null) uniqueTexts.add(text);
    }

    /** Solo los textos de los mods. Devuelve la cantidad de mods con algo por traducir. */
    private static int countLang(Map<String, Map<String, Map<String, String>>> lang, String target, Config cfg,
                                 TranslationCache cache, Set<String> uniqueTexts) {
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
        return mods;
    }

    private static boolean inVariant(Map<String, Map<String, String>> langs, String target, String key, String english) {
        for (String v : SpanishTarget.fallbacks(target)) {
            Map<String, String> m = langs.get(v);
            if (m != null && m.containsKey(key) && !Validator.isEnglishCopy(english, m.get(key))) return true;
        }
        return false;
    }
}
