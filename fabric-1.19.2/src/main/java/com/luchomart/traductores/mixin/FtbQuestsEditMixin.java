package com.luchomart.traductores.mixin;

import com.luchomart.traductores.FtbQuestsEdit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Al editar una misión, FTB Quests le manda al servidor el objeto del cliente, que tiene los textos en español
 * (ver FtbQuestsContentMixin). Lo que siga traducido vuelve al original, para no guardar el español en el modpack.
 */
@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbquests.net.EditObjectMessage", remap = false)
public abstract class FtbQuestsEditMixin {
    @Shadow(remap = false)
    @Final
    private CompoundTag nbt;

    @Inject(method = "write", at = @At("HEAD"), require = 0, remap = false)
    private void traductor_es$restoreOriginals(FriendlyByteBuf buf, CallbackInfo ci) {
        FtbQuestsEdit.restoreOriginals(nbt);
    }
}
