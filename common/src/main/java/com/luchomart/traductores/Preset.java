package com.luchomart.traductores;

/**
 * Servicios de IA predefinidos para el panel. Todos hablan el protocolo de OpenAI (/chat/completions).
 * En la nube el modelo queda vacío: los nombres cambian cada pocos meses, así que ModelPicker elige
 * el recomendado de la lista real del servicio (al probar la conexión o al empezar a traducir).
 */
public enum Preset {
    OLLAMA("Ollama (en tu PC)", "http://localhost:11434/v1/chat/completions", "gemma3:12b", false, 1, "https://ollama.com/download"),
    LM_STUDIO("LM Studio (en tu PC)", "http://localhost:1234/v1/chat/completions", "", false, 1, "https://lmstudio.ai"),
    GEMINI("Google Gemini (nube)", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "", true, 2,
            "https://aistudio.google.com/apikey"),
    OPENROUTER("OpenRouter (nube)", "https://openrouter.ai/api/v1/chat/completions", "", true, 4,
            "https://openrouter.ai/settings/keys"),
    GROQ("Groq (nube)", "https://api.groq.com/openai/v1/chat/completions", "", true, 2,
            "https://console.groq.com/keys"),
    CUSTOM("Personalizado", null, null, false, 0, null);

    public final String label, url, model;
    /** Página donde se descarga el programa (local) o se crea la clave (nube). */
    public final String helpUrl;
    public final boolean needsKey;
    /** Peticiones simultáneas recomendadas (0 = no cambiar). */
    public final int parallel;

    Preset(String label, String url, String model, boolean needsKey, int parallel, String helpUrl) {
        this.label = label;
        this.helpUrl = helpUrl;
        this.url = url;
        this.model = model;
        this.needsKey = needsKey;
        this.parallel = parallel;
    }

    /** El servicio que corresponde a una URL, o CUSTOM si no coincide con ninguno. */
    public static Preset fromUrl(String url) {
        String u = url == null ? "" : url.trim();
        for (Preset p : values()) if (p.url != null && p.url.equalsIgnoreCase(u)) return p;
        return CUSTOM;
    }

    /** Siguiente servicio predefinido ("Personalizado" no entra en el ciclo: aparece al escribir una URL propia). */
    public Preset next() {
        return step(1);
    }

    public Preset previous() {
        return step(-1);
    }

    private Preset step(int dir) {
        int n = values().length - 1; // sin CUSTOM, que es el último
        int i = this == CUSTOM ? (dir > 0 ? -1 : 0) : ordinal();
        return values()[Math.floorMod(i + dir, n)];
    }

    public boolean isCloud() {
        return needsKey;
    }

    /** Servicio que corre en la PC del jugador (Ollama, LM Studio). */
    public boolean isLocal() {
        return this == OLLAMA || this == LM_STUDIO;
    }
}
