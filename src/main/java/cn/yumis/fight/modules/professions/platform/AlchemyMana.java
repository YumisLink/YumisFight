package cn.yumis.fight.modules.professions.platform;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;
/** 魔力恢复的接线点：由技能/资源模块安装；未安装时按 0 处理。 */
public final class AlchemyMana {
    private AlchemyMana(){}
    public static BiConsumer<ServerPlayer,Double> RESTORE=(player,amount)->{};
    public static BiConsumer<ServerPlayer,Double> RESTORE_PERCENT=(player,fraction)->{};
    public static void restore(ServerPlayer player,double amount){RESTORE.accept(player,amount);}
    public static void restorePercent(ServerPlayer player,double fraction){RESTORE_PERCENT.accept(player,fraction);}
}
