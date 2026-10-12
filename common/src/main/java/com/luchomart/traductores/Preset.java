package com.luchomart.traductores;

/**
 * Servicios de IA predefinidos para el panel. Todos hablan el protocolo de OpenAI (/chat/completions).
 * Los nombres de modelo de la nube son una sugerencia: "Probar conexión" muestra los que hay de verdad.
 */
public enum Preset {
    OLLAMA("Ollama (en tu PC)", "http://localhost:11434/v1/chat/completions", "gemma3:12b", false, 1),
    LM_STUDIO("LM Studio (en tu PC)", "http://localhost:1234/v1/chat/completions", "", false, 1),
    GEMINI("Google Gemini (nube)", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.5-flash", true, 2),
    OPENROUTER("OpenRouter (nube)", "https://openrouter.ai/api/v1/chat/completions", "google/gemini-2.5-flash", true, 4),
    GROQ("Groq (nube)", "https://api.groq.com/openai/v1/chat/completions", "llama-3.3-70b-versatile", true, 2),
    CUSTOM("Personalizado", null, null, false, 0);

    public final String label, url, model;
    public final boolean needsKey;
    /** Peticiones simultáneas recomendadas (0 = no cambiar). */
    public final int parallel;

    Preset(String label, String url, String model, boolean needsKey, int parallel) {
        this.label = label;
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

    public Preset next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public boolean isCloud() {
        return needsKey;
    }
}
