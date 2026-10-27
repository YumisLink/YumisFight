package cn.yumis.fight.mixin;

import cn.yumis.fight.server.NativeEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.warden.SonicBoom;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.warden.Warden;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SonicBoom.class)
public abstract class NativeSonicBoomMixin {
    @WrapMethod(method="tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/monster/warden/Warden;J)V")
    private void ydnf$sonicScope(ServerLevel level,Warden warden,long timestamp,Operation<Void> original){
        try(var scope=NativeEffects.open(warden.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null),warden)){original.call(level,warden,timestamp);}
    }
}
