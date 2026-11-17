package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
/** 神器级鱼竿：可在岩浆上右击钓鱼，由 ProfessionEvents 安装实际结算逻辑。 */
public class GraphiteRod extends FishingRodItem {
    /** 岩浆垂钓的结算回调；未安装时拒绝。 */
    @FunctionalInterface public interface LavaCatch {boolean catchAt(ServerPlayer player,ItemStack rod);}
    public static LavaCatch HANDLER=(player,rod)->false;
    private static final long COOLDOWN_TICKS=60;
    private static final Map<UUID,Long> LAST=new ConcurrentHashMap<>();
    public GraphiteRod(Properties properties){
        super(properties);
    }
    @Override public InteractionResult useOn(UseOnContext context){
        Level level=context.getLevel();
        if(level.isClientSide())return super.useOn(context);
        BlockPos pos=context.getClickedPos();
        if(!level.getBlockState(pos).is(Blocks.LAVA))return super.useOn(context);
        if(!(context.getPlayer() instanceof ServerPlayer player))return InteractionResult.PASS;
        long now=player.level().getGameTime();
        Long last=LAST.get(player.getUUID());
        if(last!=null&&now-last<COOLDOWN_TICKS)return InteractionResult.FAIL;
        ItemStack rod=context.getItemInHand();
        if(!HANDLER.catchAt(player,rod))return InteractionResult.FAIL;
        LAST.put(player.getUUID(),now);
        int next=rod.getDamageValue()+1;
        if(next>=rod.getMaxDamage())rod.shrink(1);
        else rod.setDamageValue(next);
        return InteractionResult.SUCCESS;
    }
}
