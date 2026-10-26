package cn.yumis.fight.mixin;
import cn.yumis.fight.client.ClientCombat;
import net.minecraft.client.*;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw,float pitch,float roll);
    @Inject(method="update",at=@At("RETURN"))
    private void ydnf$storm(DeltaTracker tracker,CallbackInfo ci){
        var pose=cn.yumis.fight.client.Effects.VFX.camera();
        if(pose==null)return;
        setPosition(pose.position());
        setRotation(pose.yaw(),pose.pitch(),0);
    }
}
