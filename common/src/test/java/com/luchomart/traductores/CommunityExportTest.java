package com.luchomart.traductores;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class CommunityExportTest {
    @TempDir
    Path dir;

    private Path cache() { return dir.resolve("cache"); }
    private Path shared() { return dir.resolve("compartidas"); }

    private void write(Path f, String json) throws IOException {
        Files.createDirectories(f.getParent());
        Files.writeString(f, json, StandardCharsets.UTF_8);
    }

    @Test
    void juntaLoDelRepoConLoNuevoYSoloDevuelveArchivosConNovedades() throws IOException {
        write(cache().resolve("es_ar/ae2.json"), """
                {"block.ae2.drive": {"o": "ME Drive", "t": "Unidad de ME", "m": "x"},
                 "item.ae2.cpu": {"o": "Crafting CPU", "t": "CPU de crafteo"}}""");
        write(cache().resolve("es_ar/viejo.json"), "{\"a\": {\"o\": \"Thing\", \"t\": \"Cosa\"}}");
        write(shared().resolve("es_ar/ae2.json"), "{\"revisadas\": {\"ME Drive\": \"Unidad ME\"}, \"ia\": {}}");
        write(shared().resolve("es_ar/viejo.json"), "{\"revisadas\": {}, \"ia\": {\"Thing\": \"Objeto\"}}");

        CommunityExport.Result r = CommunityExport.build(cache(), shared(), Map.of(), "es_ar");
        assertEquals(1, r.newAi());
        assertEquals(0, r.newRevised());
        assertEquals(java.util.Set.of("es_ar/ae2.json"), r.files().keySet(), "viejo.json no trae nada nuevo");
        String ae2 = r.files().get("es_ar/ae2.json");
        assertTrue(ae2.contains("\"ME Drive\": \"Unidad ME\""), "la revisada del repo se respeta");
        assertTrue(ae2.contains("\"Crafting CPU\": \"CPU de crafteo\""));
    }

    @Test
    void lasCorreccionesVanComoRevisadasSoloEnLaVarianteDeDestino() throws IOException {
        write(cache().resolve("es_ar/ae2.json"), "{\"k\": {\"o\": \"Charged Quartz Ore\", \"t\": \"Mena de cuarzo\"}}");
        write(cache().resolve("es_es/ae2.json"), "{\"k\": {\"o\": \"Charged Quartz Ore\", \"t\": \"Mena de cuarzo\"}}");
        write(shared().resolve("es_ar/ae2.json"), "{\"revisadas\": {}, \"ia\": {\"Charged Quartz Ore\": \"Mena de cuarzo\"}}");
        Map<String, Map<String, String>> corr = Map.of("ae2", Map.of("k", "Mineral de cuarzo cargado"));

        CommunityExport.Result r = CommunityExport.build(cache(), shared(), corr, "es_ar");
        assertEquals(1, r.newRevised());
        String ar = r.files().get("es_ar/ae2.json");
        assertTrue(ar.contains("\"revisadas\": {\n  \"Charged Quartz Ore\": \"Mineral de cuarzo cargado\""), ar);
        assertFalse(ar.contains("\"Mena de cuarzo\""), "la de la IA se reemplaza por la revisada");
        assertTrue(r.files().get("es_es/ae2.json").contains("\"Mena de cuarzo\""), "en es_es no se aplica la corrección");
    }

    @Test
    void misionesYReiNoVan() throws IOException {
        write(cache().resolve("es_ar/@ftbquests.json"), "{\"q\": {\"o\": \"Quest\", \"t\": \"Misión\"}}");
        write(cache().resolve("es_ar/@rei.json"), "{\"q\": {\"o\": \"Vein\", \"t\": \"Veta\"}}");
        write(cache().resolve("es_ar/@patchouli__ae2__guide.json"), "{\"q\": {\"o\": \"Getting Started\", \"t\": \"Primeros pasos\"}}");
        CommunityExport.Result r = CommunityExport.build(cache(), shared(), Map.of(), "es_ar");
        assertEquals(java.util.Set.of("es_ar/@patchouli__ae2__guide.json"), r.files().keySet());
    }

    @Test
    void loYaEnviadoNoSeVuelveAMandar() throws IOException {
        write(cache().resolve("es_ar/ae2.json"), "{\"k\": {\"o\": \"ME Drive\", \"t\": \"Unidad ME\"}}");
        write(cache().resolve("es_ar/create.json"), "{\"k\": {\"o\": \"Cogwheel\", \"t\": \"Engranaje\"}}");
        CommunityExport.Result r = CommunityExport.build(cache(), shared(), Map.of(), "es_ar");
        String sent = CommunityExport.hash(r.files().get("es_ar/ae2.json"));
        CommunityExport.Result rest = r.withoutSent(java.util.Set.of(sent));
        assertEquals(java.util.Set.of("es_ar/create.json"), rest.files().keySet());
        assertEquals(1, rest.total());
    }

    @Test
    void tituloFijoConLosModsQueMasTraen() {
        Map<String, int[]> added = new TreeMap<>();
        added.put("es_ar/ae2.json", new int[]{10, 2});
        added.put("es_ar/create.json", new int[]{30, 0});
        added.put("es_ar/@patchouli__ae2__guide.json", new int[]{5, 0});
        added.put("es_ar/mekanism.json", new int[]{1, 0});
        Map<String, String> files = new TreeMap<>();
        added.keySet().forEach(k -> files.put(k, "{}"));
        assertEquals("Traducciones es_ar: 48 nuevas (create, ae2 y 2 más)",
                CommunityExport.title(new CommunityExport.Result(files, added)));

        Map<String, int[]> one = Map.of("es_ar/@patchouli__ae2__guide.json", new int[]{0, 1});
        assertEquals("Traducciones es_ar: 1 nueva (libro ae2)",
                CommunityExport.title(new CommunityExport.Result(Map.of("es_ar/@patchouli__ae2__guide.json", "{}"), one)));
    }

    @Test
    void sinCacheNoHayNada() throws IOException {
        assertTrue(CommunityExport.build(cache(), shared(), Map.of(), "es_ar").isEmpty());
    }

    /** Mismo formato que json.dumps(ensure_ascii=False, indent=1) de Python (tools/compartir.py y validar.py). */
    @Test
    void formatoIgualAlDePython() {
        Map<String, String> ia = new TreeMap<>();
        ia.put("b \"q\"", "línea\nnueva\ttab \\ ñ");
        ia.put("a", "x");
        assertEquals("{\n \"revisadas\": {},\n \"ia\": {\n  \"a\": \"x\",\n  \"b \\\"q\\\"\": \"línea\\nnueva\\ttab \\\\ ñ\"\n }\n}\n",
                CommunityExport.toJson(Map.of(), ia));
    }
}
