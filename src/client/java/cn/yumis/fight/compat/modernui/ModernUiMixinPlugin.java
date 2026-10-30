package cn.yumis.fight.compat.modernui;

import java.util.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.*;
import org.spongepowered.asm.service.MixinService;

/** Optional bytecode capability check. Does not load Minecraft or Modern UI classes on a server. */
public final class ModernUiMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String packageName){}
    @Override public String getRefMapperConfig(){return null;}
    @Override public boolean shouldApplyMixin(String targetClassName,String mixinClassName){
        var service=MixinService.getService();if(!"CLIENT".equalsIgnoreCase(service.getSideName()))return false;
        try{
            var target=service.getBytecodeProvider().getClassNode(targetClassName);
            return target.fields.stream().anyMatch(f->f.name.equals("mUseSpectrum")&&f.desc.equals("Z"))&&target.fields.stream().anyMatch(f->f.name.equals("sStrokeColor")&&f.desc.equals("[I"))&&target.methods.stream().anyMatch(m->m.name.equals("chooseBorderColor")&&m.desc.equals("(I)I"))&&target.methods.stream().anyMatch(m->m.name.equals("drawTooltip")&&m.desc.startsWith("(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/client/gui/GuiGraphicsExtractor;"))&&target.methods.stream().anyMatch(m->m.name.equals("drawRoundedBackground"));
        }catch(Exception unavailable){return false;}
    }
    @Override public void acceptTargets(Set<String> mine,Set<String> others){}
    @Override public List<String> getMixins(){return null;}
    @Override public void preApply(String target,ClassNode node,String mixin,IMixinInfo info){}
    @Override public void postApply(String target,ClassNode node,String mixin,IMixinInfo info){}
}
