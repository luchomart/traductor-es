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
            "guard|safeguard|orpheus|tts|whisper|embed|image|imagen|audio|speech|moderation|rerank|veo|aqa|learnlm|live|transcribe|vision-preview|computer-use"
            + "|deep-research|antigravity|robotics|banana");
    /** Versiones de prueba: se pueden elegir, pero se listan al final. */
    private static final Pattern EXPERIMENTAL = Pattern.compile("preview|exp|beta|latest");
    /** Modelos entrenados sobre todo para otro idioma (ALLaM: árabe; Jais: árabe; Swallow: japonés…). */
    private static final Pattern OTHER_LANGUAGE = Pattern.compile("allam|jais|swallow|sailor|typhoon|sea-?lion|sabia|bielik");
    /** Modelos para programar o para matemática: traducen peor que uno de uso general del mismo tamaño. */
    private static final Pattern CODE = Pattern.compile("coder|codestral|starcoder|codellama|devstral|-math");
    private static final Pattern SIZE = Pattern.compile("[-_:](\\d{1,4})b(?![a-z])");
    private static final Pattern VERSION = Pattern.compile("(\\d+(?:\\.\\d+)*)");
    /** Gemini sugiere el reemplazo en el error: "Please update your code to use models/gemini-x-flash". */
    private static final Pattern SUGGESTION = Pattern.compile("use (?:models/)?([A-Za-z0-9._:/-]+[A-Za-z0-9])");

    private ModelPicker() {}

    /** Tipos de modelo para ordenar la lista. El orden del enum es el de la lista. */
    public enum Kind {
        FAST("Rápidos", "Responden rápido y suelen tener más cupo gratis. Para traducir alcanzan."),
        BALANCED("Equilibrados", "Un poco mejores que los rápidos, con menos cupo gratis."),
        QUALITY("Mejor calidad", "Los más capaces: menos cupo gratis o de pago."),
        PREVIEW("De prueba", "Versiones de prueba: pueden cambiar o desaparecer sin aviso."),
        WEAK("No sirven para español", "Hechos para otro idioma, para programar o demasiado chicos.");

        public final String label, help;

        Kind(String label, String help) {
            this.label = label;
            this.help = help;
        }
    }

    private static final Pattern FAST_NAME = Pattern.compile("lite|instant|(?<![a-z])mini|nano|small|haiku|tiny|flash-8b");
    private static final Pattern QUALITY_NAME = Pattern.compile("(?<![a-z])pro(?![a-z])|large|opus|ultra|sonnet|(?<![a-z])max(?![a-z])");

    /** De qué tipo es el modelo, por su nombre (lite/instant: rápido; pro/120b: calidad…). */
    public static Kind kind(String model) {
        String m = model.toLowerCase(Locale.ROOT);
        if (weakReason(model) != null) return Kind.WEAK;
        if (EXPERIMENTAL.matcher(m).find()) return Kind.PREVIEW;
        int s = size(m);
        if (FAST_NAME.matcher(m).find() || (s > 0 && s <= 20)) return Kind.FAST;
        if (QUALITY_NAME.matcher(m).find() || s >= 60) return Kind.QUALITY;
        return Kind.BALANCED;
    }

    /** Solo los modelos de chat, en el orden en que llegaron. */
    public static List<String> chatModels(List<String> all) {
        List<String> out = new ArrayList<>();
        for (String m : all) if (!NOT_CHAT.matcher(m.toLowerCase(Locale.ROOT)).find()) out.add(m);
        return out;
    }

    /**
     * Por qué un modelo no conviene para traducir al español, o null si sirve. Se puede elegir igual, pero
     * nunca se elige solo y en la lista va al final.
     */
    public static String weakReason(String model) {
        String m = model.toLowerCase(Locale.ROOT);
        if (OTHER_LANGUAGE.matcher(m).find()) return "hecho para otro idioma";
        if (CODE.matcher(m).find()) return "pensado para programar";
        int s = size(m);
        if (s > 0 && s < 4) return "muy chico, traduce mal";
        return null;
    }

    /** Los modelos de chat que sirven para traducir; si ninguno sirve, todos los de chat. */
    private static List<String> usable(List<String> available) {
        List<String> chat = chatModels(available);
        List<String> good = chat.stream().filter(m -> weakReason(m) == null).toList();
        return good.isEmpty() ? chat : new ArrayList<>(good);
    }

    /** El modelo recomendado para el servicio, o null si no hay ninguno de chat. */
    public static String recommend(Preset preset, List<String> available) {
        List<String> chat = usable(available);
        if (chat.isEmpty()) return null;
        String pick = switch (preset) {
            // El "flash-lite" más nuevo: en el plan gratis es el que más cupo tiene (el "flash" a veces da 20 por día).
            // Sin las variantes preview/thinking.
            case GEMINI -> firstNonNull(newest(chat, "gemini-[\\d.]+-flash-lite$"), newest(chat, "gemini-[\\d.]+-flash$"));
            case OPENROUTER -> firstNonNull(newest(chat, "^google/gemini-[\\d.]+-flash$"), newest(chat, ":free$"));
            // Modelos grandes de chat de uso general.
            // En la PC: TranslateGemma (Gemma 3 entrenado para traducir) y si no, Gemma 3. Medido con 300 textos reales
            // de OceanBlock: translategemma:12b 98 % bien y menos textos sin traducir que gemma3:12b (96 %), misma velocidad.
            case OLLAMA, LM_STUDIO -> firstNonNull(newest(chat, "translategemma"), newest(chat, "^gemma3:12b"), newest(chat, "gemma-?3"),
                    newest(chat, "qwen3"));
            case GROQ -> firstNonNull(newest(chat, "^openai/gpt-oss-120b$"), newest(chat, "llama-[\\d.]+-70b"),
                    newest(chat, "^openai/gpt-oss"), newest(chat, "llama"));
            default -> null;
        };
        return pick != null ? pick : chat.get(0);
    }

    /** El modelo gratis recomendado de OpenRouter (los que terminan en ":free"), o null si no hay. */
    public static String recommendFree(List<String> available) {
        List<String> free = usable(available).stream().filter(m -> m.endsWith(":free")).toList();
        if (free.isEmpty()) return null;
        // Los que mejor traducen al español entre los gratis, y si no, cualquiera.
        return firstNonNull(newest(free, "^google/gemma"), newest(free, "^google/gemini"), newest(free, "^meta-llama/llama-[\\d.]+-70b"),
                newest(free, "^deepseek/"), newest(free, "^meta-llama/"), free.get(0));
    }

    /** Modelos de chat con el recomendado primero (para mostrarlos en el panel). */
    public static List<String> ranked(Preset preset, List<String> available) {
        List<String> chat = chatModels(available);
        // Por tipo: rápidos, equilibrados, calidad, de prueba y al final los que no sirven para traducir.
        chat.sort(Comparator.comparing(ModelPicker::kind));
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
                // A igual versión, el más grande (27b antes que 12b): traduce mejor.
                .max(Comparator.comparing(ModelPicker::version, ModelPicker::compareVersions).thenComparingInt(ModelPicker::size))
                .orElse(null);
    }

    /** Tamaño en miles de millones de parámetros ("gemma-3-27b-it" → 27), o 0 si no figura. */
    private static int size(String model) {
        Matcher m = SIZE.matcher(model.toLowerCase(Locale.ROOT));
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
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
