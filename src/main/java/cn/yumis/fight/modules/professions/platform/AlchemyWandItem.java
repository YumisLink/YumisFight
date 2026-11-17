package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
/** 点金棒：只有它能点化炼金锅里已经煮透的草药，把同档两株炼成药剂。 */
public final class AlchemyWandItem extends Item {
    public static final int DURABILITY=128;
    public AlchemyWandItem(Properties properties){
        super(properties);
    }
    @Override public InteractionResult useOn(UseOnContext context){
        Level level=context.getLevel();
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.PASS;
        BlockPos pos=context.getClickedPos();
        BlockState state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof AlchemyCauldronBlock))return InteractionResult.PASS;
        if(!(level.getBlockEntity(pos) instanceof AlchemyCauldronBlockEntity cauldron))return InteractionResult.PASS;
        if(!cauldron.transform(player))return InteractionResult.PASS;
        ItemStack wand=context.getItemInHand();
        int next=wand.getDamageValue()+1;
        if(next>=wand.getMaxDamage())wand.shrink(1);
        else wand.setDamageValue(next);
        return InteractionResult.SUCCESS;
    }
}
