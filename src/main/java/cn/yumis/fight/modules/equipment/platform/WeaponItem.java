package cn.yumis.fight.modules.equipment.platform;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps creative block breaking consistent with vanilla swords without adding tool attributes. */
public final class WeaponItem extends Item {
    public WeaponItem(Properties properties){super(properties);}
    @Override public boolean canDestroyBlock(ItemStack stack,BlockState state,Level level,BlockPos position,LivingEntity user){
        return !(user instanceof Player player&&player.isCreative())&&super.canDestroyBlock(stack,state,level,position,user);
    }
}
