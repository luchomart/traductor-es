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
    /** Solo para servicios en la nube; Ollama y LM Studio no la necesitan. Es la del servicio elegido. */
    public String apiKey = "";
    /** Clave de cada servicio en la nube, para no perderla al cambiar de uno a otro. */
    public java.util.Map<String, String> apiKeys = new java.util.HashMap<>();
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
    /** Ya se completó el asistente de primera vez. */
    public boolean setupDone = false;
    /** Mostrar el asistente cuando falta configurar algo (el jugador puede desactivarlo). */
    public boolean showWizard = true;
    /** Traducir solo al llegar a la pantalla de título si hay textos nuevos. */
    public boolean autoStart = true;
    /** Recargar los recursos al terminar si se está en el menú principal. */
    public boolean autoReload = true;
    /**
     * Traducir mientras se juega: "auto" (sigue si la IA está en la nube; pausa si está en esta PC,
     * porque compartiría la placa de video con el juego), "si" o "no".
     */
    public String whilePlaying = "auto";
    /** Traducir también los libros de Patchouli (manuales dentro del juego). */
    public boolean translateBooks = true;
    /** Con Alt apretado, la descripción de los objetos traducidos se ve en inglés. */
    public boolean originalOnAlt = true;
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

    /**
     * Copia independiente: la usa cada traducción, para que cambiar el servicio en el panel no la afecte a mitad
     * de camino (sigue con lo que tenía al empezar; lo nuevo se usa en la próxima).
     */
    public Config snapshot() {
        return GSON.fromJson(GSON.toJson(this), Config.class);
    }

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
        // La clave escrita queda guardada para su servicio.
        Preset p = Preset.fromUrl(apiUrl);
        if (p.isCloud()) apiKeys.put(p.name(), apiKey);
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TraductorES.LOG.error("[Traductor ES] No se pudo guardar config.json", e);
        }
    }

    private void sanitize() {
        if (apiUrl == null || apiUrl.isBlank()) apiUrl = Preset.OLLAMA.url;
        if (model == null) model = "";
        if (apiKeys == null) apiKeys = new java.util.HashMap<>();
        if (apiKey == null) apiKey = "";
        if (!"si".equals(whilePlaying) && !"no".equals(whilePlaying)) whilePlaying = "auto";
        // Una IA en la PC no usa clave: si quedó una de otro servicio, no se muestra ni se manda.
        if (Preset.fromUrl(apiUrl).isLocal()) apiKey = "";
        if (batchSize < 1) batchSize = 1;
        if (parallelRequests < 1) parallelRequests = 1;
        if (parallelRequests > 16) parallelRequests = 16;
        if (batchMaxChars < 200) batchMaxChars = 200;
        if (timeoutSeconds < 10) timeoutSeconds = 10;
        if (targetLanguage == null || targetLanguage.isBlank()) targetLanguage = "auto";
        if (excludedNamespaces == null) excludedNamespaces = new ArrayList<>();
        if (systemPrompt == null) systemPrompt = "";
    }

    /** Cambia de servicio: URL, modelo y peticiones a la vez; trae la clave guardada de ese servicio. */
    public void applyPreset(Preset p) {
        save(); // guarda la clave del servicio actual antes de cambiar
        apiUrl = p.url;
        model = p.model;
        if (p.parallel > 0) parallelRequests = p.parallel;
        apiKey = p.isCloud() ? apiKeys.getOrDefault(p.name(), "") : "";
        save();
    }

    /** La IA corre en esta misma PC (comparte la placa de video con el juego). */
    public boolean isLocalAi() {
        String host;
        try {
            host = java.net.URI.create(LlmClient.normalizeUrl(apiUrl)).getHost();
        } catch (IllegalArgumentException e) {
            return false; // URL a medio escribir en el panel
        }
        if (host == null) return false;
        host = host.toLowerCase(java.util.Locale.ROOT);
        return host.equals("localhost") || host.startsWith("127.") || host.equals("::1") || host.equals("[::1]") || host.equals("0.0.0.0");
    }

    /** Si la traducción sigue mientras se juega, según la opción y dónde corre la IA. */
    public boolean translateWhilePlaying() {
        return switch (whilePlaying) {
            case "si" -> true;
            case "no" -> false;
            default -> !isLocalAi();
        };
    }

    public String effectivePrompt(String target) {
        return systemPrompt.isBlank() ? Prompts.system(target) : systemPrompt;
    }
}
