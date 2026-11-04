package com.luchomart.traductores;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * Antes de mandarle al servidor una misión editada: lo que sigue traducido vuelve al original, para no guardar el
 * español en los archivos del modpack. Lo que se escribió a mano queda como está.
 */
public final class FtbQuestsEdit {
    private FtbQuestsEdit() {}

    public static void restoreOriginals(CompoundTag data) {
        for (String key : List.of("title", "subtitle", "description")) restore(data, key);
        if (data.get("images") instanceof ListTag images) {
            for (Tag image : images) {
                if (image instanceof CompoundTag c) restore(c, "hover");
            }
        }
    }

    private static void restore(CompoundTag data, String key) {
        Tag tag = data.get(key);
        if (tag instanceof StringTag) {
            String original = FtbQuests.originalOf(tag.getAsString());
            if (original != null) data.putString(key, original);
        } else if (tag instanceof ListTag list) {
            for (int i = 0; i < list.size(); i++) {
                if (!(list.get(i) instanceof StringTag line)) continue;
                String original = FtbQuests.originalOf(line.getAsString());
                if (original != null) list.set(i, StringTag.valueOf(original));
            }
        }
    }
}
