package cn.yumis.fight.mixin;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(AbstractContainerMenu.class)
public interface MenuAccessor {
    @Invoker("addSlot") Slot ydnf$addSlot(Slot slot);
}
