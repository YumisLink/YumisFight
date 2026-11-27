package cn.yumis.fight.mixin;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.ClientUi;

import net.minecraft.client.*;
import net.minecraft.world.phys.*;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow public abstract Vec3 position();
    @Shadow public abstract float yRot();
    @Shadow public abstract float xRot();
    @Shadow public abstract float getRoll();
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw,float pitch,float roll);
    @Inject(method="update",at=@At("RETURN"))
    private void ydnf$storm(DeltaTracker tracker,CallbackInfo ci){
        var pose=cn.yumis.fight.modules.skills.client.Effects.VFX.camera();
        if(pose!=null){
            setPosition(pose.position());
            setRotation(pose.yaw(),pose.pitch(),0);
        }
        var mc=Minecraft.getInstance();
        if(mc.isPaused())return;
        var shake=CameraShake.sample(position(),ClientUi.now());
        if(shake.active()){
            setRotation(yRot()+shake.yaw(),Math.clamp(xRot()+shake.pitch(),-90f,90f),getRoll()+shake.roll());
        }
    }
}
