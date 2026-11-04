package com.luchomart.traductores.mixin;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.luchomart.traductores.PackWriter;
import com.luchomart.traductores.TraductorES;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Patchouli lee los libros que tienen el contenido en data/ directamente del .jar del mod: un pack de recursos no los
 * reemplaza. Si nuestro pack tiene la traducción del archivo que pide, se la entrega; si no, sigue como siempre.
 * Patchouli es opcional: sin él, este mixin no hace nada (@Pseudo).
 */
@Pseudo
@Mixin(targets = "vazkii.patchouli.client.book.BookContentClasspathLoader", remap = false)
public abstract class PatchouliContentMixin {
    @Inject(method = "loadJson", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void traductor_es$loadTranslation(@Coerce Object book, ResourceLocation file, ResourceLocation fallback,
                                              CallbackInfoReturnable<JsonElement> cir) {
        Path translated = PackWriter.DIR.resolve("assets").resolve(file.getNamespace()).resolve(file.getPath());
        if (!Files.isRegularFile(translated)) return;
        try (Reader r = Files.newBufferedReader(translated, StandardCharsets.UTF_8)) {
            cir.setReturnValue(JsonParser.parseReader(r));
        } catch (Exception e) {
            TraductorES.LOG.warn("[Traductor ES] No se pudo leer la traducción de {}: {}", file, e.toString());
        }
    }
}
