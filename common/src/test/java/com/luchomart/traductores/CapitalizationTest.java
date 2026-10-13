package com.luchomart.traductores;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CapitalizationTest {
    @Test
    void nombresComoElOficial() {
        assertEquals("Medidor básico de redstone", Capitalization.fix("Basic Redstone Gauge", "Medidor Básico de Redstone"));
        assertEquals("Estación de investigación cuántica", Capitalization.fix("Quantum Research Station", "Estación de Investigación Cuántica"));
        assertEquals("Fragua atómica", Capitalization.fix("Atomic Forge", "Fragua Atómica"));
        assertEquals("Bloque de almacenamiento 16k", Capitalization.fix("16k Storage Block", "Bloque de Almacenamiento 16k"));
        assertEquals("Bloque de Adamant", Capitalization.fix("Block of Adamant", "Bloque de Adamant"));
    }

    @Test
    void respetaNombresPropiosSiglasYRomanos() {
        assertEquals("Ojo de ender", Capitalization.fix("Eye of Ender", "Ojo de Ender"));        // como el oficial
        assertEquals("Ladrillos de roca del Nether rotados", Capitalization.fix("Rotated Netherrack Bricks", "Ladrillos de Roca del Nether Rotados"));
        assertEquals("Estrella del End", Capitalization.fix("Ender Star", "Estrella del End"));
        assertEquals("Mejora de velocidad II", Capitalization.fix("Speed Upgrade II", "Mejora de Velocidad II"));
        assertEquals("Celda de energía RF", Capitalization.fix("RF Energy Cell", "Celda de Energía RF"));
        assertEquals("Martillo de Thor", Capitalization.fix("Thor's Hammer", "Martillo de Thor"));
        assertEquals("Modo: Automático", Capitalization.fix("Mode: Automatic", "Modo: Automático"));
    }

    @Test
    void noTocaOracionesNiFormato() {
        assertEquals("Haz Clic para abrir.", Capitalization.fix("Click to open.", "Haz Clic para abrir."));
        assertEquals("Pulsa Mayús para Más", Capitalization.fix("Hold Shift for more", "Pulsa Mayús para Más"));
        assertEquals("§aBloque Verde", Capitalization.fix("Green Block", "§aBloque Verde"));
        assertEquals("Energía: %s FE", Capitalization.fix("Energy: %s FE", "Energía: %s FE"));
        assertEquals("Cobre", Capitalization.fix("Copper", "Cobre"));
    }

    @Test
    void aprendeDelOficial() {
        Capitalization c = Capitalization.learn(java.util.Map.of(
                "block.minecraft.x", "Ladrillos de Prismarina", "item.minecraft.y", "Fragmento de prismarina",
                "block.minecraft.z", "Piedra de Overworld"));
        assertEquals("Losa de prismarina", c.apply("Prismarine Slab", "Losa de Prismarina"));
        assertEquals("Roca del Overworld", c.apply("Overworld Rock", "Roca del Overworld"));
    }
}
