package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.modules.equipment.api.Rarity;
import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Narrow reward composition; dungeon code does not own economy internals. */
public interface WaystonePorts {
    double dropRate(ServerPlayer player);
    Rarity rarity(double dropRate);
    Map<String,JsonElement> drop(UUID player,ItemStack stack,ReturnPoint point,String instance);
    ItemStack chest(int tier);
    void publish();
}
