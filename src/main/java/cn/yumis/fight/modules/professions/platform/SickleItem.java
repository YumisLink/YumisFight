package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
/** 镰刀：右击野生草药丛收割，产出草药与种子；破坏方块则走掉落事件。 */
public final class SickleItem extends Item {
    /** 收割回调；返回 true 表示已产出草药。 */
    @FunctionalInterface public interface Harvest {boolean harvest(ServerPlayer player,BlockPos pos,ItemStack tool);}
    public static Harvest HANDLER=(player,pos,tool)->false;
    public static final int DURABILITY=250;
    public static final long HARVEST_DEDUPE_MILLIS=1500L;
    private static final java.util.Map<String,Long> recentClientHarvest=new java.util.concurrent.ConcurrentHashMap<>();
    public SickleItem(Properties properties){
        super(properties);
    }
    public static InteractionResult useSickle(UseOnContext context,Harvest handler){
        Level level=context.getLevel();
        var actor=context.getPlayer();
        BlockPos pos=context.getClickedPos();
        BlockState state=level.getBlockState(pos);
        if(!(state.getBlock() instanceof HerbBushBlock)||actor==null||actor.isShiftKeyDown())return InteractionResult.PASS;
        if(level.isClientSide()){
            long now=System.currentTimeMillis();
            String key=actor.getUUID()+"/"+level.dimension().identifier()+"/"+pos.asLong();
            Long until=recentClientHarvest.get(key);
            if(until!=null&&now<until)return InteractionResult.CONSUME;
            if(recentClientHarvest.size()>512)recentClientHarvest.values().removeIf(t->now>=t);
            recentClientHarvest.put(key,now+HARVEST_DEDUPE_MILLIS);
            return InteractionResult.SUCCESS;
        }
        if(!(actor instanceof ServerPlayer player))return InteractionResult.PASS;
        if(!handler.harvest(player,pos,context.getItemInHand()))return InteractionResult.FAIL;
        return InteractionResult.SUCCESS;
    }
    @Override public InteractionResult useOn(UseOnContext context){
        return useSickle(context,HANDLER);
    }
}
