package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
/** 史诗镰刀：一次收五株草药并返种子，档位加成最高，耐久 8000。 */
public final class EpicSickleItem extends Item {
    @FunctionalInterface public interface Harvest {boolean harvest(ServerPlayer player,BlockPos pos,ItemStack tool);}
    public static Harvest HANDLER=(player,pos,tool)->false;
    public static final int DURABILITY=8000;
    public static final int LUCK_BONUS=8;
    public static final int HARVEST_COUNT=5;
    public EpicSickleItem(Properties properties){
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
        if(!HANDLER.harvest(player,pos,context.getItemInHand()))return InteractionResult.FAIL;
        return InteractionResult.SUCCESS;
    }
}
