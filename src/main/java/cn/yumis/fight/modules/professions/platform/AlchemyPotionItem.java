package cn.yumis.fight.modules.professions.platform;
import cn.yumis.fight.modules.professions.api.AlchemyPotions;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
/** 药剂：像原版药水一样按住右键饮用，喝完才生效并消耗。 */
public final class AlchemyPotionItem extends Item {
    public static java.util.function.BiConsumer<Player,InteractionHand> CLIENT_USE=(player,hand)->{};
    /** 与原生药水一致的饮用时长（32 刻）。 */
    private static final int DRINK_TICKS=32;
    private final String potion;
    public AlchemyPotionItem(Properties properties,String potion){
        super(properties);
        this.potion=potion;
    }
    public String potion(){return potion;}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(!AlchemyPotions.isRestore(potion)&&!AlchemyPotions.isBuff(potion))return InteractionResult.PASS;
        if(player.getCooldowns().isOnCooldown(player.getItemInHand(hand)))return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){
        return DRINK_TICKS;
    }
    @Override public net.minecraft.world.item.ItemUseAnimation getUseAnimation(ItemStack stack){return net.minecraft.world.item.ItemUseAnimation.DRINK;}
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        if(level.isClientSide()&&entity instanceof Player player)CLIENT_USE.accept(player,entity.getUsedItemHand());
        return stack;
    }
}
