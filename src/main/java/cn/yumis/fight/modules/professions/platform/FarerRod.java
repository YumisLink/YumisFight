package cn.yumis.fight.modules.professions.platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
/** 神话级鱼竿"远行者鱼友竿"：继承冥河竿的岩浆垂钓，额外支持虚空垂钓。 */
public class FarerRod extends StyxRod {
    /** 虚空垂钓的结算回调；未安装时拒绝。 */
    @FunctionalInterface public interface VoidCatch {boolean catchAt(ServerPlayer player,ItemStack rod);}
    public static VoidCatch HANDLER=(player,rod)->false;
    public FarerRod(Properties properties){
        super(properties);
    }
    /** 在虚空中右击空气即可垂钓。 */
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server))return InteractionResult.PASS;
        ItemStack rod=player.getItemInHand(hand);
        if(player.getY()>=level.getMinY())return super.use(level,player,hand);
        if(!server.isShiftKeyDown()&&HANDLER.catchAt(server,rod)){
            int next=rod.getDamageValue()+1;
            if(next>=rod.getMaxDamage())rod.shrink(1);
            else rod.setDamageValue(next);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }
}
