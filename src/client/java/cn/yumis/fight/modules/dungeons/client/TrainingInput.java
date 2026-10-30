package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.presentation.api.client.ClientUi;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.context.UseOnContext;
import org.lwjgl.glfw.GLFW;

/** Capture the actual modifier state at the time of the item-use gesture. */
public final class TrainingInput {
    private TrainingInput(){}
    public static void use(UseOnContext context){
        var ui=ClientUi.current();var mc=Minecraft.getInstance();if(!ui.ready()||mc.player==null||mc.level==null)return;
        boolean alt=InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_LEFT_ALT)||InputConstants.isKeyDown(mc.getWindow(),GLFW.GLFW_KEY_RIGHT_ALT);
        var pos=context.getClickedPos();JsonObject request=new JsonObject();request.addProperty("session",ui.session());request.addProperty("dimension",mc.level.dimension().identifier().toString());request.addProperty("x",pos.getX());request.addProperty("y",pos.getY());request.addProperty("z",pos.getZ());request.addProperty("face",context.getClickedFace().name());request.addProperty("hand",context.getHand().name());request.addProperty("seconds",alt?60:20);ui.send("trainingStart",request);
    }
}
