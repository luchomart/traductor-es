package com.luchomart.traductores;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Elige un modelo para traducir entre los que ofrece el servicio. Los nombres de la nube cambian
 * cada pocos meses, así que en vez de fijarlos se buscan por patrón en la lista real.
 */
public final class ModelPicker {
    /** Modelos que no sirven para traducir texto: voz, filtros de seguridad, imágenes, embeddings… */
    private static final Pattern NOT_CHAT = Pattern.compile(
            "guard|safeguard|orpheus|tts|whisper|embed|image|imagen|audio|speech|moderation|rerank|veo|aqa|learnlm|live|transcribe|vision-preview|computer-use");
    private static final Pattern VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)");
    /** Gemini sugiere el reemplazo en el error: "Please update your code to use models/gemini-x-flash". */
    private static final Pattern SUGGESTION = Pattern.compile("use (?:models/)?([A-Za-z0-9._:/-]+[A-Za-z0-9])");

    private ModelPicker() {}

    /** Solo los modelos de chat, en el orden en que llegaron. */
    public static List<String> chatModels(List<String> all) {
        List<String> out = new ArrayList<>();
        for (String m : all) if (!NOT_CHAT.matcher(m.toLowerCase(Locale.ROOT)).find()) out.add(m);
        return out;
    }

    /** El modelo recomendado para el servicio, o null si no hay ninguno de chat. */
    public static String recommend(Preset preset, List<String> available) {
        List<String> chat = chatModels(available);
        if (chat.isEmpty()) return null;
        String pick = switch (preset) {
            // El "flash" más nuevo: rápido, barato y con plan gratis. Sin las variantes lite/preview/thinking.
            case GEMINI -> newest(chat, "gemini-[\\d.]+-flash$");
            case OPENROUTER -> firstNonNull(newest(chat, "^google/gemini-[\\d.]+-flash$"), newest(chat, ":free$"));
            // Modelos grandes de chat de uso general.
            case GROQ -> firstNonNull(newest(chat, "^openai/gpt-oss-120b$"), newest(chat, "llama-[\\d.]+-70b"),
                    newest(chat, "^openai/gpt-oss"), newest(chat, "llama"));
            default -> null;
        };
        return pick != null ? pick : chat.get(0);
    }

    /** Modelos de chat con el recomendado primero (para mostrarlos en el panel). */
    public static List<String> ranked(Preset preset, List<String> available) {
        List<String> chat = chatModels(available);
        String best = recommend(preset, available);
        if (best != null) {
            chat.remove(best);
            chat.add(0, best);
        }
        return chat;
    }

    /** El modelo que el servidor sugiere en su mensaje de error, si lo hay. */
    public static String suggestedReplacement(String errorMessage) {
        if (errorMessage == null) return null;
        Matcher m = SUGGESTION.matcher(errorMessage);
        return m.find() ? m.group(1) : null;
    }

    /** El de versión más alta entre los que cumplen el patrón. */
    static String newest(List<String> models, String regex) {
        Pattern p = Pattern.compile(regex);
        return models.stream()
                .filter(m -> p.matcher(m.toLowerCase(Locale.ROOT)).find())
                .max(Comparator.comparing(ModelPicker::version, ModelPicker::compareVersions))
                .orElse(null);
    }

    private static int[] version(String model) {
        Matcher m = VERSION.matcher(model);
        if (!m.find()) return new int[0];
        String[] parts = m.group(1).split("\\.");
        int[] v = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                v[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                v[i] = 0;
            }
        }
        return v;
    }

    private static int compareVersions(int[] a, int[] b) {
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0, y = i < b.length ? b[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T v : values) if (v != null) return v;
        return null;
    }
}
