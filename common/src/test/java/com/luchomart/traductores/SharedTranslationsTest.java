package com.luchomart.traductores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SharedTranslationsTest {
    private static final String BASE = "https://repo.falso/";

    @TempDir
    Path dir;

    /** Un repo falso en memoria: URL → contenido. Anota qué se pidió. */
    private static final class FakeRepo implements SharedTranslations.Downloader {
        final Map<String, String> files = new HashMap<>();
        final List<String> requested = java.util.Collections.synchronizedList(new ArrayList<>());
        boolean down;

        void put(String rel, String json) { files.put(rel, json); }

        /** Arma index.json con el hash de cada archivo, como validar.py. */
        void index() {
            StringBuilder sb = new StringBuilder("{\"formato\": 1, \"archivos\": {");
            boolean first = true;
            for (Map.Entry<String, String> e : files.entrySet()) {
                if (e.getKey().equals("index.json")) continue;
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(e.getKey()).append("\": {\"hash\": \"")
                        .append(SharedTranslations.hash(e.getValue().getBytes(StandardCharsets.UTF_8))).append("\"}");
            }
            files.put("index.json", sb.append("}}").toString());
        }

        @Override
        public byte[] get(String url) throws IOException {
            requested.add(url.substring(BASE.length()));
            if (down) throw new IOException("sin internet");
            String body = files.get(url.substring(BASE.length()));
            if (body == null) throw new IOException("HTTP 404");
            return body.getBytes(StandardCharsets.UTF_8);
        }
    }

    private FakeRepo repo() {
        FakeRepo r = new FakeRepo();
        r.put("es_ar/ae2.json", """
                {"revisadas": {"ME Drive": "Unidad ME"},
                 "ia": {"Portable Fuel Interface": "Interfaz portátil de combustible", "Charged Quartz Ore": "Mena de cuarzo cargado"}}""");
        r.put("es_es/ae2.json", """
                {"revisadas": {"Crafting CPU": "CPU de fabricación"},
                 "ia": {"Click to open the terminal and see what is stored inside.": "Haz clic para abrir la terminal."}}""");
        r.put("es_ar/@patchouli__ae2__guide.json", "{\"revisadas\": {}, \"ia\": {\"Getting Started\": \"Primeros pasos\"}}");
        r.put("es_ar/otro_mod.json", "{\"revisadas\": {}, \"ia\": {\"Thing\": \"Cosa\"}}");
        r.index();
        return r;
    }

    @Test
    void bajaSoloLosModsInstaladosYUsaLaVarianteDeDestino() {
        FakeRepo r = repo();
        SharedTranslations s = new SharedTranslations(dir, "es_ar", List.of());
        SharedTranslations.Update u = s.update(Set.of("ae2", "@patchouli/ae2/guide"), BASE, r);
        assertNull(u.error());
        assertEquals(2, u.downloaded());
        assertFalse(r.requested.contains("es_ar/otro_mod.json"));
        assertFalse(r.requested.contains("es_es/ae2.json"), "sin otras variantes no baja es_es");

        assertEquals(new SharedTranslations.Hit("Unidad ME", true, true), s.get("ae2", "ME Drive"));
        assertEquals(new SharedTranslations.Hit("Interfaz portátil de combustible", false, true), s.get("ae2", "Portable Fuel Interface"));
        assertEquals("Primeros pasos", s.get("@patchouli/ae2/guide", "Getting Started").text());
        assertNull(s.get("ae2", "Crafting CPU"));
        assertNull(s.get("otro_mod", "Thing"));
    }

    @Test
    void laSegundaVezNoVuelveABajarLoQueNoCambio() {
        FakeRepo r = repo();
        new SharedTranslations(dir, "es_ar", List.of()).update(Set.of("ae2"), BASE, r);
        r.requested.clear();
        SharedTranslations.Update u = new SharedTranslations(dir, "es_ar", List.of()).update(Set.of("ae2"), BASE, r);
        assertEquals(List.of("index.json"), r.requested);
        assertEquals(0, u.downloaded());
        assertEquals(1, u.upToDate());

        // Cambió en el repo: se baja de nuevo.
        r.put("es_ar/ae2.json", "{\"revisadas\": {\"ME Drive\": \"Unidad de ME\"}, \"ia\": {}}");
        r.index();
        SharedTranslations s = new SharedTranslations(dir, "es_ar", List.of());
        assertEquals(1, s.update(Set.of("ae2"), BASE, r).downloaded());
        assertEquals("Unidad de ME", s.get("ae2", "ME Drive").text());
    }

    @Test
    void deOtraVarianteSoloTextosCortos() {
        FakeRepo r = repo();
        SharedTranslations s = new SharedTranslations(dir, "es_ar", SpanishTarget.fallbacks("es_ar"));
        s.update(Set.of("ae2"), BASE, r);
        assertEquals(new SharedTranslations.Hit("CPU de fabricación", true, false), s.get("ae2", "Crafting CPU"));
        assertNull(s.get("ae2", "Click to open the terminal and see what is stored inside."), "texto largo: va a la IA (voseo)");
    }

    @Test
    void avisaElAvanceHastaElTotal() {
        FakeRepo r = repo();
        List<String> steps = java.util.Collections.synchronizedList(new ArrayList<>());
        new SharedTranslations(dir, "es_ar", List.of()).update(Set.of("ae2", "@patchouli/ae2/guide", "otro_mod"), BASE, r,
                (done, total) -> steps.add(done + "/" + total));
        assertEquals("0/3", steps.get(0));
        assertTrue(steps.contains("3/3"));
        assertEquals(4, steps.size());
    }

    @Test
    void textoCorto() {
        assertTrue(SharedTranslations.isShort("Charged Quartz Ore"));
        assertFalse(SharedTranslations.isShort("Stores items."));
        assertFalse(SharedTranslations.isShort("Line one\nLine two"));
        assertFalse(SharedTranslations.isShort("A very long name that goes beyond forty characters"));
    }

    @Test
    void sinInternetSeUsaLoQueYaEstaba() {
        FakeRepo r = repo();
        new SharedTranslations(dir, "es_ar", List.of()).update(Set.of("ae2"), BASE, r);
        r.down = true;
        SharedTranslations s = new SharedTranslations(dir, "es_ar", List.of());
        SharedTranslations.Update u = s.update(Set.of("ae2"), BASE, r);
        assertNotNull(u.error());
        assertEquals("Unidad ME", s.get("ae2", "ME Drive").text());
    }

    @Test
    void unArchivoRotoEnElRepoNoPisaElBueno() throws IOException {
        FakeRepo r = repo();
        new SharedTranslations(dir, "es_ar", List.of()).update(Set.of("ae2"), BASE, r);
        r.put("es_ar/ae2.json", "esto no es json");
        r.index();
        SharedTranslations s = new SharedTranslations(dir, "es_ar", List.of());
        SharedTranslations.Update u = s.update(Set.of("ae2"), BASE, r);
        assertNotNull(u.error());
        assertEquals(0, u.downloaded());
        assertEquals("Unidad ME", s.get("ae2", "ME Drive").text());
        assertFalse(Files.exists(dir.resolve("es_ar/ae2.json.tmp")));
    }

    @Test
    void sinNadaBajadoNoHayTraducciones() {
        assertNull(new SharedTranslations(dir, "es_ar", List.of()).get("ae2", "ME Drive"));
    }
}
