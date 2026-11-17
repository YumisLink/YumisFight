package cn.yumis.fight.modules.professions.platform;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
/** 药剂：像原版药水一样按住右键饮用，喝完才生效并消耗。 */
public final class AlchemyPotionItem extends Item {
    /** 与原生药水一致的饮用时长（32 刻）。 */
    private static final int DRINK_TICKS=32;
    private final String potion;
    public AlchemyPotionItem(Properties properties,String potion){
        super(properties);
        this.potion=potion;
    }
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(!AlchemyPotions.isRestore(potion)&&!AlchemyPotions.isBuff(potion))return InteractionResult.PASS;
        if(player.getCooldowns().isOnCooldown(player.getItemInHand(hand)))return InteractionResult.FAIL;
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }
    @Override public int getUseDuration(ItemStack stack,LivingEntity entity){
        return DRINK_TICKS;
    }
    @Override public ItemStack finishUsingItem(ItemStack stack,Level level,LivingEntity entity){
        if(entity instanceof ServerPlayer server&&!level.isClientSide()){
            if(AlchemyPotions.isRestore(potion)){
                if(AlchemyPotions.isPercentRestore(potion)){
                    double pct=AlchemyPotions.percentRestore(potion);
                    if(AlchemyPotions.isMana(potion))AlchemyMana.restorePercent(server,pct);
                    else server.heal((float)(server.getMaxHealth()*pct));
                }else{
                    double amount=AlchemyPotions.restoreAmount(potion);
                    if(AlchemyPotions.isMana(potion))AlchemyMana.restore(server,amount);
                    else server.heal((float)amount);
                }
            }else{
                AlchemyPotions.applyBuff(server,potion,System.currentTimeMillis());
            }
            server.getCooldowns().addCooldown(stack,AlchemyPotions.COOLDOWN_TICKS);
            level.playSound(null,server.blockPosition(),SoundEvents.GENERIC_DRINK.value(),SoundSource.PLAYERS,.5f,.9f+server.getRandom().nextFloat()*.1f);
            if(!server.getAbilities().instabuild)stack.shrink(1);
        }
        return stack;
    }
}
