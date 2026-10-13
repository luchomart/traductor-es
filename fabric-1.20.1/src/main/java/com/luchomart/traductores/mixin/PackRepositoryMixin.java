package com.luchomart.traductores.mixin;

import com.luchomart.traductores.PackRegistration;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Fabric no tiene un evento para sumar packs desde una carpeta propia: se agrega la fuente del pack
 * al repositorio de recursos del cliente (el único que incluye ClientPackSource).
 */
@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void traductor_es$addSource(RepositorySource[] given, CallbackInfo ci) {
        if (sources.stream().noneMatch(s -> s instanceof ClientPackSource)) return;
        Set<RepositorySource> all = new LinkedHashSet<>(sources);
        all.add(PackRegistration.SOURCE);
        sources = all;
    }
}
