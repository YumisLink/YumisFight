package cn.yumis.fight.modules.equipment.client;

import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.Weapon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class LightsaberLight {
    private LightsaberLight() {}

    private static boolean saber(ItemStack stack) {
        var gear = EquipmentItems.view(stack);
        return gear != null && gear.weapon() == Weapon.LIGHTSABER;
    }

    public static int heldLight() {
        var player = Minecraft.getInstance().player;
        return player != null && (saber(player.getMainHandItem()) || saber(player.getOffhandItem())) ? 4 : 0;
    }

    public static boolean localModel(LivingEntity owner, ItemDisplayContext context) {
        return owner != null && owner == Minecraft.getInstance().player
            && (context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
    }
}
