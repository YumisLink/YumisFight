package cn.yumis.fight.modules.economy.api;

import cn.yumis.fight.platform.ItemData;
import cn.yumis.fight.platform.ItemRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.function.BiConsumer;

/** Stacks identify a pool, not an individual draw; each consumption gets its own transaction ID. */
public final class LootJarItem extends Item {
    public static final String ID="loot_jar";
    private static BiConsumer<Player,InteractionHand> opening=(p,h)->{};
    public LootJarItem(Properties properties){super(properties.stacksTo(64));}
    public static void opening(BiConsumer<Player,InteractionHand> action){opening=action;}
    public static String definition(ItemStack stack){return ItemData.own(stack)&&ItemData.id(stack).equals(ID)?ItemData.string(stack,ID):"";}
    public static ItemStack create(String definition,int count){
        if(count<1||count>64)throw new IllegalArgumentException("罐子数量必须为1～64");
        JarDefinitions.current().require(definition);var stack=ItemRegistry.item(ID,count);
        ItemData.string(stack,ID,definition);return stack;
    }
    @Override public Component getName(ItemStack stack){var rule=JarDefinitions.current().jars.get(definition(stack));return rule==null?super.getName(stack):Component.literal(rule.name());}
    @Override public InteractionResult use(Level level,Player player,InteractionHand hand){
        if(level.isClientSide())opening.accept(player,hand);
        return InteractionResult.SUCCESS.heldItemTransformedTo(player.getItemInHand(hand));
    }
}
