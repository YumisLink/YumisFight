package cn.yumis.fight.mixin;

import cn.yumis.fight.modules.equipment.api.client.MythicStyleAccess;
import cn.yumis.fight.modules.equipment.api.client.MythicTextMarker;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Style.class)
public abstract class MythicStyleMixin implements MythicStyleAccess {
    @Shadow @Final private String insertion;
    @Override public String ydnf$rawInsertion(){return insertion;}
    @Inject(method="getInsertion",at=@At("HEAD"),cancellable=true)
    private void ydnf$preserveInsertion(CallbackInfoReturnable<String> result){
        if(MythicTextMarker.marked(insertion))result.setReturnValue(MythicTextMarker.original(insertion));
    }
}
