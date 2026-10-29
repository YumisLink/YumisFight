package cn.yumis.fight.modules.character.api;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
/** Supplied equipment and reward ports join the character transaction. */
public interface InitialEquipment {
    ItemStack weapon(String baseJob);
    void drop(ServerPlayer player,ItemStack stack)throws Exception;
    void publish();
    void abort();
}
