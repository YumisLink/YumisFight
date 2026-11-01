package cn.yumis.fight.modules.dungeons.api;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerPlayer;
public final class TreasureMapItem extends Item {
    public TreasureMapItem(Properties properties){super(properties.stacksTo(1));}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){if(player instanceof ServerPlayer p)TreasureActions.use(p,hand);return InteractionResult.SUCCESS;}
}
