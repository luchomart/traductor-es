package com.luchomart.traductores;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Configuración del mod, guardada en config/traductor_es/config.json. */
public class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final Path DIR = Platform.configDir().resolve(TraductorES.MODID);
    public static final Path FILE = DIR.resolve("config.json");

    public String apiUrl = "http://localhost:11434/v1/chat/completions";
    /** Solo para servicios en la nube; Ollama y LM Studio no la necesitan. */
    public String apiKey = "";
    public String model = "gemma3:12b";
    /** Máximo de textos por petición. */
    public int batchSize = 20;
    /** Peticiones a la IA al mismo tiempo. 1 para una IA local; más para servicios en la nube. */
    public int parallelRequests = 1;
    /** Máximo de caracteres de origen por petición (protege la ventana de contexto). */
    public int batchMaxChars = 2500;
    public int timeoutSeconds = 180;
    public double temperature = 0.2;
    /** Pedir al servidor respuesta en modo JSON (Ollama y LM Studio lo soportan). */
    public boolean jsonMode = true;
    /** Traducir solo al llegar a la pantalla de título si hay textos nuevos. */
    public boolean autoStart = true;
    /** Recargar los recursos al terminar si se está en el menú principal. */
    public boolean autoReload = true;
    /** Pausar la traducción al entrar a un mundo (libera la GPU) y reanudarla al volver al menú. */
    public boolean pauseInWorld = true;
    /** Traducir también los libros de Patchouli (manuales dentro del juego). */
    public boolean translateBooks = true;
    /** Si la IA devuelve sin cambios un texto de varias palabras, pedirle una vez más que lo traduzca. */
    public boolean retryUnchanged = true;
    /** Español de destino: "auto" usa el idioma del juego; también es_es, es_mx, es_ar, es_cl, es_uy, es_ve, es_ec. */
    public String targetLanguage = "auto";
    /** Usar otras variantes del español (es_mx, es_ar…) cuando un mod no trae la de destino. */
    public boolean useSpanishVariants = true;
    /** Namespaces (ids de mods) que no se traducen. */
    public List<String> excludedNamespaces = new ArrayList<>();
    /** Prompt de sistema. Vacío = usar el prompt incorporado. */
    public String systemPrompt = "";

    private static Config instance;

    public static Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    public static Config load() {
        Config cfg = null;
        if (Files.exists(FILE)) {
            try {
                cfg = GSON.fromJson(Files.readString(FILE, StandardCharsets.UTF_8), Config.class);
            } catch (Exception e) {
                TraductorES.LOG.error("[Traductor ES] config.json inválido, se usan valores por defecto", e);
            }
        }
        if (cfg == null) cfg = new Config();
        cfg.sanitize();
        instance = cfg;
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TraductorES.LOG.error("[Traductor ES] No se pudo guardar config.json", e);
        }
    }

    private void sanitize() {
        if (batchSize < 1) batchSize = 1;
        if (parallelRequests < 1) parallelRequests = 1;
        if (parallelRequests > 16) parallelRequests = 16;
        if (batchMaxChars < 200) batchMaxChars = 200;
        if (timeoutSeconds < 10) timeoutSeconds = 10;
        if (targetLanguage == null || targetLanguage.isBlank()) targetLanguage = "auto";
        if (excludedNamespaces == null) excludedNamespaces = new ArrayList<>();
        if (systemPrompt == null) systemPrompt = "";
    }

    public String effectivePrompt(String target) {
        return systemPrompt.isBlank() ? Prompts.system(target) : systemPrompt;
    }
}
