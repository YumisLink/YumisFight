package cn.yumis.fight.modules.economy.api;

import net.minecraft.world.item.Item;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;

/** One registered item class; stable definition IDs select data-pack rewards. */
public final class TreasureChestItem extends Item {
    private static BiConsumer<Player,InteractionHand> opening=(p,h)->{};
    public static void opening(BiConsumer<Player,InteractionHand> action){opening=action;}
    public TreasureChestItem(Properties p){super(p.stacksTo(1));}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide())opening.accept(player,hand);
        // The inventory transaction can replace the consumed chest with a reward in this hand.
        return InteractionResult.SUCCESS.heldItemTransformedTo(player.getItemInHand(hand));
    }
}
