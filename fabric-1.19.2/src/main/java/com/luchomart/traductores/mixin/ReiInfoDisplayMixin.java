package com.luchomart.traductores.mixin;

import com.luchomart.traductores.ReiInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Fichas de información de REI (las que un modpack arma con KubeJS tienen el texto fijo en el script). Sus líneas se
 * cambian al pedirlas, en memoria: una traducción nueva se ve sin recargar. REI es opcional: sin él, este mixin no hace nada.
 */
@Pseudo
@Mixin(targets = "me.shedaniel.rei.plugin.common.displays.DefaultInformationDisplay", remap = false)
public abstract class ReiInfoDisplayMixin {
    /** Al agregarle líneas a una ficha, aunque nunca se abra: así se traducen de antemano. El "*" toma todas las variantes. */
    @Inject(method = {"line*", "lines*"}, at = @At("RETURN"), require = 0, remap = false)
    private void traductor_es$capture(CallbackInfoReturnable<?> cir) {
        ReiInfo.capture(this);
    }

    @Inject(method = "getTexts", at = @At("RETURN"), cancellable = true, require = 0, remap = false)
    private void traductor_es$translate(CallbackInfoReturnable<List<Component>> cir) {
        cir.setReturnValue(ReiInfo.translated(cir.getReturnValue()));
    }
}
