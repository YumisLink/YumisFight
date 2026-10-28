package cn.yumis.fight.modules.equipment.api;
import cn.yumis.fight.modules.character.api.Progress;
import net.minecraft.world.entity.player.Player;
import java.util.function.Function;
/** Player progression projection used by equipment platform adapters on both sides. */
public final class EquipmentEligibility {
    public static Function<Player,Progress> CLIENT_PROGRESS=p->new Progress();
    public static Function<Player,Progress> PROGRESS=p->CLIENT_PROGRESS.apply(p);
    public static java.util.function.ToIntFunction<Player> CLIENT_ALLOWANCE=p->0;
    public static java.util.function.ToIntFunction<Player> LEVEL_ALLOWANCE=p->CLIENT_ALLOWANCE.applyAsInt(p);
    public static int allowance(Player player){return LEVEL_ALLOWANCE.applyAsInt(player);}
    private EquipmentEligibility(){}
}
