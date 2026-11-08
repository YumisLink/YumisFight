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
    @FunctionalInterface public interface VoidCatch {boolean catchAt(Player player,ItemStack rod);}
    public static VoidCatch HANDLER=(player,rod)->false;
    public FarerRod(Properties properties){
        super(properties);
    }
    /** 在虚空中右击空气即可垂钓。 */
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(player.getY()>=level.getMinY())return super.use(level,player,hand);
        if(!level.isClientSide())return InteractionResult.SUCCESS;
        return !player.isShiftKeyDown()&&HANDLER.catchAt(player,player.getItemInHand(hand))?InteractionResult.SUCCESS:InteractionResult.FAIL;
    }
}
