package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
/** 神话镰刀：一次收六株草药并返种子，耐久 16000。 */
public final class MythicSickleItem extends Item {
    @FunctionalInterface public interface Harvest {boolean harvest(ServerPlayer player,BlockPos pos);}
    public static Harvest HANDLER=(player,pos)->false;
    public static final int DURABILITY=16000;
    public static final int LUCK_BONUS=10;
    public static final int HARVEST_COUNT=6;
    public MythicSickleItem(Properties properties){
        super(properties);
    }
    @Override public InteractionResult useOn(UseOnContext context){
        Level level=context.getLevel();
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.PASS;
        BlockPos pos=context.getClickedPos();
        BlockState state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof HerbBushBlock))return InteractionResult.PASS;
        if(player.isShiftKeyDown())return InteractionResult.PASS;
        if(!HANDLER.harvest(player,pos))return InteractionResult.FAIL;
        ItemStack stack=context.getItemInHand();
        int next=stack.getDamageValue()+1;
        if(next>=stack.getMaxDamage())stack.shrink(1);
        else stack.setDamageValue(next);
        return InteractionResult.SUCCESS;
    }
}
