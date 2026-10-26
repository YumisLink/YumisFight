package cn.yumis.fight.mixin;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Slot.class)
public interface SlotPositionAccessor {
    @Mutable @Accessor("x") void ydnf$x(int x);
    @Mutable @Accessor("y") void ydnf$y(int y);
}
