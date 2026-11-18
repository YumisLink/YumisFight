package cn.yumis.fight.modules.dungeons.api;

import cn.yumis.fight.platform.ItemData;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public final class TreasureMapItem extends Item {
    public static final String DATA = "treasure_map";
    public static final int DIG_RADIUS = 10;
    private static final Pattern COORDINATE_NAME = Pattern.compile("已开启的哥布林王国藏宝图 · X:-?\\d+ Z:-?\\d+");

    public TreasureMapItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) TreasureActions.use(serverPlayer, hand);
        return InteractionResult.SUCCESS;
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = super.getName(stack);
        return ItemData.string(stack, DATA).isEmpty() ? name
                : Component.translatable("item.yumis_dungeon_and_fight.goblin_treasure_map.opened", name);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName != null && COORDINATE_NAME.matcher(customName.getString()).matches()
                && !ItemData.string(stack, DATA).isEmpty()) {
            stack.remove(DataComponents.CUSTOM_NAME);
        }
    }
}
