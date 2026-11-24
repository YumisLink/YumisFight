package cn.yumis.fight.modules.professions.platform;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
/** 稀有镰刀：一次收两株草药并额外返种子，耐久更高。 */
public final class RareSickleItem extends Item {
    @FunctionalInterface public interface Harvest {boolean harvest(ServerPlayer player,BlockPos pos,ItemStack tool);}
    public static Harvest HANDLER=(player,pos,tool)->false;
    public static final int DURABILITY=750;
    /** 抽取档位时的等效草药等级加成。 */
    public static final int LUCK_BONUS=2;
    public RareSickleItem(Properties properties){
        super(properties);
    }
    @Override public InteractionResult useOn(UseOnContext context){
        return SickleItem.useSickle(context,HANDLER::harvest);
    }
}
