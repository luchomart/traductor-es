package com.luchomart.traductores;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * "Original con Alt": con Alt apretado, la descripción de un objeto se muestra en inglés. Cada línea se rearma con
 * el texto en inglés de su clave, conservando colores y valores (%s). Solo se ofrece en objetos que tradujo este mod.
 */
public final class OriginalText {
    /** Clave que nunca existe: así Minecraft usa el texto en inglés como "fallback" y le aplica los argumentos. */
    private static final String MISSING = "traductor_es.original.";

    /** clave → texto en inglés, o null mientras se carga. */
    private static volatile Map<String, String> english;
    /** Claves que da nuestro pack (las traducidas por el mod). */
    private static volatile Set<String> ours = Set.of();
    /** El idioma cargado para el que se armaron los datos: cambia con cada recarga de recursos (F3+T, otro idioma). */
    private static volatile Language builtFor;
    private static volatile boolean loading;

    private OriginalText() {}

    /** Desde el evento de descripción de objetos de cada plataforma. lines es la lista que se va a mostrar. */
    public static void onTooltip(List<Component> lines) {
        if (lines.isEmpty() || !Config.get().originalOnAlt) return;
        ensureLoaded();
        Map<String, String> en = english;
        if (en == null) return;
        Set<String> mine = ours;
        boolean translatedByUs = false;
        for (Component line : lines) {
            if (usesKey(line, mine)) { translatedByUs = true; break; }
        }
        if (!translatedByUs) return;
        if (Screen.hasAltDown()) {
            lines.replaceAll(line -> toEnglish(line, en));
            lines.add(Component.literal("Original en inglés").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            lines.add(Component.literal("Alt: ver original").withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** La línea con el inglés de cada clave traducible (y lo demás igual: colores, valores, texto fijo). */
    static Component toEnglish(Component c, Map<String, String> en) {
        MutableComponent out;
        if (c.getContents() instanceof TranslatableContents tc) {
            Object[] args = tc.getArgs().clone();
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component arg) args[i] = toEnglish(arg, en);
            }
            String text = en.get(tc.getKey());
            out = text != null ? Component.translatableWithFallback(MISSING + tc.getKey(), text, args)
                    : Component.translatable(tc.getKey(), args);
        } else {
            out = MutableComponent.create(c.getContents());
        }
        out.setStyle(c.getStyle());
        for (Component sibling : c.getSiblings()) out.append(toEnglish(sibling, en));
        return out;
    }

    private static boolean usesKey(Component c, Set<String> keys) {
        if (c.getContents() instanceof TranslatableContents tc) {
            if (keys.contains(tc.getKey())) return true;
            for (Object arg : tc.getArgs()) if (arg instanceof Component a && usesKey(a, keys)) return true;
        }
        for (Component s : c.getSiblings()) if (usesKey(s, keys)) return true;
        return false;
    }

    /** Arma los datos en otro hilo la primera vez (y después de cada recarga de recursos). */
    private static void ensureLoaded() {
        Language current = Language.getInstance();
        if (current == builtFor || loading) return;
        loading = true;
        builtFor = current;
        english = null;
        Minecraft mc = Minecraft.getInstance();
        ResourceManager rm = mc.getResourceManager();
        String target = SpanishTarget.resolve(Config.get().targetLanguage, mc.options.languageCode);
        Thread t = new Thread(() -> {
            try {
                ours = target == null ? Set.of() : packKeys(target);
                english = readEnglish(rm);
            } catch (Exception e) {
                // Por ejemplo, si justo empezó una recarga: se reintenta en la próxima descripción.
                TraductorES.LOG.debug("[Traductor ES] No se pudo cargar el inglés original: {}", e.toString());
                builtFor = null;
            } finally {
                loading = false;
            }
        }, "Traductor ES - original");
        t.setDaemon(true);
        t.start();
    }

    /** en_us de todos los mods y de Minecraft (los packs de más arriba pisan a los de más abajo). */
    private static Map<String, String> readEnglish(ResourceManager rm) {
        Map<String, String> out = new HashMap<>();
        rm.listResourceStacks("lang", rl -> rl.getPath().equals("lang/en_us.json")).values()
                .forEach(stack -> stack.forEach(res -> {
                    try (Reader r = res.openAsReader()) {
                        JsonElement el = JsonParser.parseReader(r);
                        if (!el.isJsonObject()) return;
                        el.getAsJsonObject().entrySet().forEach(kv -> {
                            if (kv.getValue().isJsonPrimitive()) out.put(kv.getKey(), kv.getValue().getAsString());
                        });
                    } catch (Exception ignored) {
                        // Un archivo roto de otro mod no impide el resto.
                    }
                }));
        return out;
    }

    /** Las claves de nuestro pack para la variante de destino (assets/*&#47;lang/&lt;variante&gt;.json). */
    private static Set<String> packKeys(String target) throws IOException {
        Set<String> keys = new HashSet<>();
        Path assets = PackWriter.DIR.resolve("assets");
        if (!Files.isDirectory(assets)) return keys;
        try (Stream<Path> namespaces = Files.list(assets)) {
            for (Path ns : namespaces.toList()) {
                Path f = ns.resolve("lang/" + target + ".json");
                if (!Files.isRegularFile(f)) continue;
                JsonElement el = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8));
                if (el.isJsonObject()) keys.addAll(el.getAsJsonObject().keySet());
            }
        }
        return keys;
    }
}
