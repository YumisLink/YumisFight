package cn.yumis.fight.mixin;
import cn.yumis.fight.client.UiOpenType;
import com.mojang.blaze3d.font.GlyphProvider;
import net.minecraft.client.gui.font.providers.TrueTypeGlyphProviderDefinition;
import net.minecraft.server.packs.resources.ResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.io.IOException;
/** Extend only our imported fonts; do not change other packs' TTF validation. */
@Mixin(TrueTypeGlyphProviderDefinition.class)
public abstract class UiOpenTypeMixin {
    @Inject(method="load",at=@At("HEAD"),cancellable=true)
    private void ydnf$openType(ResourceManager manager,CallbackInfoReturnable<GlyphProvider> callback) throws IOException {
        var definition=(TrueTypeGlyphProviderDefinition)(Object)this;
        if(definition.location().getNamespace().equals("yumis_dungeon_and_fight")&&(definition.location().getPath().startsWith("imported/")||java.util.Set.of("inter-frozen-medium.otf","source-han-sans-cn-medium.otf").contains(definition.location().getPath()))){
            try(var input=manager.open(definition.location().withPrefix("font/"))){
                callback.setReturnValue(UiOpenType.load(input,definition));
            }
            catch(IOException|RuntimeException failure){
                UiOpenType.failed(definition.location().getPath());
                throw failure;
            }
        }
    }
}
