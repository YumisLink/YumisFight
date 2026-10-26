package cn.yumis.fight.mixin;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Minecraft.class)
public abstract class VerificationWindowMixin {
    @Redirect(method="<init>",at=@At(value="INVOKE",target="Lorg/lwjgl/glfw/GLFW;glfwShowWindow(J)V"))
    private void ydnf$backgroundVerification(long handle){
        if(!Boolean.getBoolean("ydnf.verifyHidden"))GLFW.glfwShowWindow(handle);
    }
}
