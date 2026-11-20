package cn.yumis.fight.modules.dungeons.api;

import com.google.gson.JsonElement;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Narrow reward composition; dungeon code does not own economy internals. */
public interface WaystonePorts {
    Map<String,JsonElement> drop(ServerPlayer player,ItemStack stack,ReturnPoint point,String instance);
    void publish(Collection<String> committedKeys);
}
