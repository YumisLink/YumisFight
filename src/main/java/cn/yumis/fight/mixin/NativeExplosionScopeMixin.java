package cn.yumis.fight.mixin;

import cn.yumis.fight.server.NativeEffects;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerExplosion.class)
public abstract class NativeExplosionScopeMixin {
    @Shadow @Final private Map<Player,Vec3> hitPlayers;
    @Unique private static final ThreadLocal<ArrayDeque<NativeEffects.Batch>> ydnf$batches=ThreadLocal.withInitial(ArrayDeque::new);
    @WrapMethod(method="hurtEntities(Ljava/util/List;)V")
    private void ydnf$explosionScope(List<BlockPos> blocks,Operation<Void> original){
        var batch=new NativeEffects.Batch();var stack=ydnf$batches.get();stack.push(batch);
        try{original.call(blocks);}finally{batch.close();batch.deferred.forEach(hitPlayers::remove);stack.pop();if(stack.isEmpty())ydnf$batches.remove();}
    }
    @WrapOperation(method="hurtEntities(Ljava/util/List;)V",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;ignoreExplosion(Lnet/minecraft/world/level/Explosion;)Z"))
    private boolean ydnf$victimScope(Entity victim,Explosion explosion,Operation<Boolean> original){
        var batch=ydnf$batches.get().peek();
        if(batch!=null){batch.close();batch.victim=victim;batch.scope=NativeEffects.open(victim,((ServerExplosion)(Object)this).getDamageSource().getEntity());}
        return original.call(victim,explosion);
    }
}
