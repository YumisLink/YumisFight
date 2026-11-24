package cn.yumis.fight.modules.npc.api;

import cn.yumis.fight.modules.presentation.api.ModText;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/**
 * 「伊蕾娜的信物」：击败封印塔第50层伊蕾娜必得。
 * 主世界对方块右键可将世界唯一的伊蕾娜 NPC 迁移/召唤至目标落点（不消耗，冷却 2 秒）。
 */
public final class ElainaTokenItem extends Item {
    public static final String ID = "elaina_token";
    private static final int COOLDOWN_TICKS = 40;

    public ElainaTokenItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        if (level.dimension() != Level.OVERWORLD) {
            serverPlayer.sendSystemMessage(ModText.of("仅可在主世界召唤伊蕾娜"));
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (serverPlayer.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.FAIL;
        }
        Vec3 targetPos = Vec3.atBottomCenterOf(context.getClickedPos().above());
        float yaw = (serverPlayer.getYRot() + 180.0f) % 360.0f;
        try {
            NpcSpawns.relocateUnique(level, "elaina", targetPos, yaw);
        } catch (IllegalArgumentException invalid) {
            serverPlayer.sendSystemMessage(ModText.of("目标位置需要平坦实心地面与两格净空"));
            return InteractionResult.FAIL;
        }
        serverPlayer.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
        serverPlayer.sendSystemMessage(ModText.of("已将伊蕾娜召唤至此处"));
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable("item.yumis_dungeon_and_fight.elaina_token.hint")
            .withStyle(style -> style.withColor(0xD8B4FF).withItalic(false)));
    }
}
