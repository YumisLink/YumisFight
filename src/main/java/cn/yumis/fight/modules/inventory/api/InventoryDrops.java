package cn.yumis.fight.modules.inventory.api;

import com.google.gson.JsonElement;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** A prepared item drop joins the inventory transaction before its world projection is published. */
public interface InventoryDrops {
    record Prepared(UUID id,Vec3 position,String journalKey,JsonElement state){}
    Prepared prepare(ServerPlayer player,net.minecraft.world.entity.item.ItemEntity projection);
    void publishPrepared(Collection<Prepared> drops);
}
