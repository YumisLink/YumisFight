package cn.yumis.fight.modules.industry.api;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import java.util.function.Consumer;
import java.util.function.ObjLongConsumer;

/** A registered mana crystal can be consumed through the owner's progression transaction. */
public final class EnergyCrystalItem extends Item {
    private static ObjLongConsumer<InteractionHand> consume=(hand,experience)->{};
    private final long experience;

    public EnergyCrystalItem(int tier,Properties properties){
        super(properties);
        experience=EnergyMath.crystal(tier)/20;
    }

    public static void consuming(ObjLongConsumer<InteractionHand> action){consume=action;}

    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(!player.isShiftKeyDown())return InteractionResult.PASS;
        if(level.isClientSide())consume.accept(hand,experience);
        return InteractionResult.SUCCESS;
    }

    @Override public void appendHoverText(ItemStack stack,TooltipContext context,TooltipDisplay display,
                                          Consumer<Component> tooltip,TooltipFlag flag){
        tooltip.accept(Component.literal("Shift＋右键消耗1枚，获得"+experience+"点基础战斗经验"));
    }
}
