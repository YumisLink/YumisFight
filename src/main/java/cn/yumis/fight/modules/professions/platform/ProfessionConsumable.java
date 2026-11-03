package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.professions.api.ProfessionBonuses;
import net.minecraft.core.Holder;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.function.ToIntFunction;
/** 菜品与药剂的即时使用：右键施加效果并消耗 1 个，不走原版食物组件。 */
public final class ProfessionConsumable extends Item {
    /** 由 ProfessionEvents 在服务端安装，用来读取饮用者的药师等级；默认 0 级。 */
    public static ToIntFunction<Player> LEVEL_SOURCE=player->0;
    private final Holder<MobEffect> effect;
    private final int duration;
    private final int amplifier;
    public ProfessionConsumable(Properties properties,Holder<MobEffect> effect,int duration,int amplifier){
        super(properties);
        this.effect=effect;
        this.duration=duration;
        this.amplifier=amplifier;
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide())return InteractionResult.SUCCESS;
        ItemStack stack=player.getItemInHand(hand);
        int extra=(int)Math.round(duration*ProfessionBonuses.potionDuration(LEVEL_SOURCE.applyAsInt(player)));
        player.addEffect(new MobEffectInstance(effect,duration+extra,amplifier));
        stack.shrink(1);
        return InteractionResult.SUCCESS;
    }
}
