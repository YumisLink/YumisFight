package cn.yumis.fight.mixin;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeTabAccessor {
    @Invoker("selectTab") void ydnf$tab(CreativeModeTab tab);
}
