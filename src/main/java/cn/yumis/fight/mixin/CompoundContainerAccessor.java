package cn.yumis.fight.mixin;
import net.minecraft.world.Container;
import net.minecraft.world.CompoundContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(CompoundContainer.class)
public interface CompoundContainerAccessor {
    @Accessor("container1") Container ydnf$first();
    @Accessor("container2") Container ydnf$second();
}
