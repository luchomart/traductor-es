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
 * Sirve para el asistente ("18.883 textos en 142 mods · ~4 h"). Lo que ya tradujo la comunidad no cuenta como faltante:
 * el asistente lo baja antes (con barra); las demás pantallas usan lo que ya esté bajado, sin ir a internet.
 */
public final class Estimator {
    /** texts = lo que tendría que hacer la IA; community = lo que ya está en las traducciones de la comunidad. */
    public record Result(int texts, int mods, int community) {
        /** Tiempo aproximado con el servicio configurado (ver ServiceGuide). */
        public String duration(Config cfg) {
            return texts == 0 ? "nada" : ServiceGuide.estimate(cfg).duration(texts);
        }
    }

    private Estimator() {}

    private record Scan(Map<String, Map<String, Map<String, String>>> lang, List<Books.Book> books) {}

    /** Lo que ya existe, por namespace y texto en inglés: el caché propio y, si se usan, las compartidas. */
    private record Known(TranslationCache cache, SharedTranslations shared, Set<String> fromCommunity) {
        /**
         * true si la IA no tiene que traducirlo: ya está en el caché o en la comunidad (con marcadores bien).
         * useShared false para misiones y fichas de REI: la traducción no usa la comunidad para esos.
         */
        boolean has(String ns, String key, String text, boolean useShared) {
            String cached = cache.get(ns, key, text);
            if (cached != null && Validator.check(text, cached) == null) return true;
            SharedTranslations.Hit hit = shared == null || !useShared ? null : shared.get(ns, text);
            if (hit == null || Validator.check(text, hit.text()) != null) return false;
            fromCommunity.add(text);
            return true;
        }
    }

    /**
     * Lee los recursos en el hilo del juego y cuenta en el hilo que llama (no llamar desde el hilo del juego). Usa lo
     * de la comunidad que ya esté bajado, sin ir a internet.
     */
    public static Result estimateModpack(Minecraft mc, String target, Config cfg) {
        return estimateModpack(mc, target, cfg, null);
    }

    /**
     * Como el anterior, pero antes baja lo de la comunidad (lo que falte o cambió), avisando el avance. Para el asistente.
     * @param progress avance de la descarga; null = no bajar nada
     */
    public static Result estimateModpack(Minecraft mc, String target, Config cfg, SharedTranslations.Progress progress) {
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
        List<Books.Book> books = new java.util.ArrayList<>(s.books());
        if (cfg.translateBooks && Platform.readsDataBooks()) {
            books.addAll(Books.scanDataBooks(Books.readJarBooks(mc.gameDirectory.toPath().resolve("mods")), target, variants));
        }
        Known known = new Known(new TranslationCache(target), shared(s.lang().keySet(), books, target, variants, cfg, progress), new HashSet<>());
        Set<String> uniqueTexts = new HashSet<>();
        int mods = countLang(s.lang(), target, cfg, known, uniqueTexts);
        if (cfg.translateBooks) {
            countBooks(books, known, uniqueTexts);
            countQuests(target, variants, known, uniqueTexts);
            InfoTexts.known().forEach((key, text) -> addIfMissing(known, InfoTexts.NS, key, text, uniqueTexts, false));
        }
        // Un texto que la IA igual tiene que hacer en otro lado no cuenta como "de la comunidad".
        known.fromCommunity().removeAll(uniqueTexts);
        return new Result(uniqueTexts.size(), mods, known.fromCommunity().size());
    }

    /** Lo de la comunidad; con progress, antes baja lo de los mods y libros instalados, como al traducir. Nunca falla. */
    private static SharedTranslations shared(Set<String> namespaces, List<Books.Book> books, String target, List<String> variants,
                                             Config cfg, SharedTranslations.Progress progress) {
        if (!cfg.useSharedTranslations) return null;
        SharedTranslations shared = new SharedTranslations(Config.DIR.resolve("compartidas"), target, variants);
        if (progress == null) return shared;
        Set<String> wanted = new HashSet<>(namespaces);
        wanted.removeAll(cfg.excludedNamespaces);
        for (Books.Book b : books) wanted.add(b.pseudoNamespace());
        SharedTranslations.Update u = shared.update(wanted, SharedTranslations.BASE_URL, SharedTranslations.HTTP, progress);
        if (u.error() != null) TraductorES.LOG.warn("[Traductor ES] Traducciones compartidas (asistente): {}", u.error());
        return shared;
    }

    private static void countBooks(List<Books.Book> books, Known cache, Set<String> uniqueTexts) {
        for (Books.Book book : books) {
            book.english.forEach((file, json) -> Books.extract(book.format, file, json,
                    (pointer, text) -> addIfMissing(cache, book.pseudoNamespace(), file + "#" + pointer, text, uniqueTexts, true)));
        }
    }

    private static void countQuests(String target, List<String> variants, Known cache, Set<String> uniqueTexts) {
        try {
            FtbQuests.Quests quests = FtbQuests.scan(target, variants);
            if (quests != null) {
                FtbQuests.extract(quests, (key, text) -> addIfMissing(cache, FtbQuests.NS, key, text, uniqueTexts, false));
            } else if (Platform.readsOldQuests()) {
                Map<String, String> lines = FtbQuests.scanOld();
                if (lines != null) lines.forEach((key, line) -> addIfMissing(cache, FtbQuests.NS, key, FtbQuests.translatable(line), uniqueTexts, false));
            }
        } catch (IOException | RuntimeException e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudieron contar las misiones: {}", e.toString());
        }
    }

    private static void addIfMissing(Known known, String ns, String key, String text, Set<String> uniqueTexts, boolean useShared) {
        if (Validator.isTrivial(text)) return;
        if (!known.has(ns, key, text, useShared)) uniqueTexts.add(text);
    }

    /** Solo los textos de los mods. Devuelve la cantidad de mods con algo por traducir. */
    private static int countLang(Map<String, Map<String, Map<String, String>>> lang, String target, Config cfg,
                                 Known known, Set<String> uniqueTexts) {
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
                if (known.has(ns.getKey(), key, text, true)) continue;
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
