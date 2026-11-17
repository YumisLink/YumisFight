package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
/** 神话级鱼竿"远行者鱼友竿"：继承冥河竿的岩浆垂钓，额外支持末地虚空垂钓。 */
public class FarerRod extends StyxRod {
    /** 虚空垂钓的结算回调；未安装时拒绝。 */
    @FunctionalInterface public interface VoidCatch {boolean catchAt(ServerPlayer player,ItemStack rod);}
    public static VoidCatch HANDLER=(player,rod)->false;
    public FarerRod(Properties properties){
        super(properties);
    }
    /** 末地空岛边缘朝虚空右击即可垂钓；已经掉进虚空时同样生效。 */
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide())return InteractionResult.SUCCESS;
        if(!(player instanceof ServerPlayer server))return InteractionResult.PASS;
        ItemStack rod=player.getItemInHand(hand);
        boolean endVoid=level.dimension().equals(Level.END)&&(player.getY()<level.getMinY()||aimingIntoVoid(player));
        if(!endVoid)return super.use(level,player,hand);
        if(!server.isShiftKeyDown()&&HANDLER.catchAt(server,rod)){
            int next=rod.getDamageValue()+1;
            if(next>=rod.getMaxDamage())rod.shrink(1);
            else rod.setDamageValue(next);
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }
    /** 视线 6 格内没有方块，且落脚点向下 64 格全是空气，视为对着虚空。 */
    private static boolean aimingIntoVoid(Player player){
        if(player.pick(6,1f,false).getType()!=HitResult.Type.MISS)return false;
        var eye=player.getEyePosition();
        var dir=player.getLookAngle();
        BlockPos base=BlockPos.containing(eye.add(dir.scale(5)));
        for(int dy=0;dy>=-64;dy--){
            if(!player.level().getBlockState(base.offset(0,dy,0)).isAir())return false;
        }
        return true;
    }
}
