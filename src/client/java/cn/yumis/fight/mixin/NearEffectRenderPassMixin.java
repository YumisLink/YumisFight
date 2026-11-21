package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.presentation.api.client.NearEffectRendering;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderPass.class)
public abstract class NearEffectRenderPassMixin {
    @Inject(method="setPipeline",at=@At("TAIL"))
    private void yumisFight$bindNearEffectSettings(RenderPipeline pipeline,CallbackInfo ci){
        NearEffectRendering.bindSettings((RenderPass)(Object)this,pipeline);
    }
}
