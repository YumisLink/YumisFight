package cn.yumis.fight.modules.professions.platform;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
/** 神器级鱼竿：可在岩浆上右击钓鱼，由本人客户端安装结算逻辑。 */
public class GraphiteRod extends FishingRodItem {
    /** 岩浆垂钓的结算回调；未安装时拒绝。 */
    @FunctionalInterface public interface LavaCatch {boolean catchAt(Player player,ItemStack rod);}
    public static LavaCatch HANDLER=(player,rod)->false;
    private static final long COOLDOWN_TICKS=60;
    private static final Map<UUID,Long> LAST=new ConcurrentHashMap<>();
    public static void clearCooldown(){LAST.clear();}
    public GraphiteRod(Properties properties){
        super(properties);
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(player.fishing==null){
            var hit=getPlayerPOVHitResult(level,player,ClipContext.Fluid.SOURCE_ONLY);
            if(hit.getType()==HitResult.Type.BLOCK&&level.getFluidState(hit.getBlockPos()).is(FluidTags.LAVA)){
                if(!level.isClientSide())return InteractionResult.SUCCESS;
                if(player.isShiftKeyDown())return InteractionResult.PASS;
                long now=level.getGameTime();
                Long last=LAST.get(player.getUUID());
                if(last!=null&&now-last<COOLDOWN_TICKS)return InteractionResult.FAIL;
                if(!HANDLER.catchAt(player,player.getItemInHand(hand)))return InteractionResult.FAIL;
                LAST.put(player.getUUID(),now);
                return InteractionResult.SUCCESS;
            }
        }
        return super.use(level,player,hand);
    }
}
