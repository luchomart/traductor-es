package com.luchomart.traductores.mixin;

import com.luchomart.traductores.Config;
import com.luchomart.traductores.FtbQuests;
import com.luchomart.traductores.SpanishTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Misiones en el formato viejo: los textos están dentro de los capítulos. Cuando las misiones llegan al cliente se
 * cambian en memoria, sin tocar los archivos del modpack. FTB Quests es opcional: sin él, este mixin no hace nada.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.client.ClientQuestFile", remap = false)
public abstract class FtbQuestsContentMixin {
    @Inject(method = "load", at = @At("HEAD"), require = 0, remap = false)
    private void traductor_es$applyTranslation(CallbackInfo ci) {
        String target = SpanishTarget.resolve(Config.get().targetLanguage, Minecraft.getInstance().options.languageCode);
        if (target != null) FtbQuests.applyOld(this, target);
    }
}
