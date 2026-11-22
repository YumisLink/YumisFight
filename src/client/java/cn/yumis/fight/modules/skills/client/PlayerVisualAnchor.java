package cn.yumis.fight.modules.skills.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** Attached local effects sample the same predicted entity and render fraction as its model. */
final class PlayerVisualAnchor {
    private PlayerVisualAnchor(){}
    private static float fraction(){return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);}
    static Vec3 position(Player player){return player==Minecraft.getInstance().player?player.getPosition(fraction()):player.position();}
    static Vec3 look(Player player){return player==Minecraft.getInstance().player?player.getViewVector(fraction()):player.getLookAngle();}
    static Vec3 forward(Player player){
        float yaw=player==Minecraft.getInstance().player?Mth.rotLerp(fraction(),player.yRotO,player.getYRot()):player.getYRot();
        return Vec3.directionFromRotation(0,yaw);
    }
    /** Held charges keep aiming along the caster's live crosshair; the launch heading is only the fallback. */
    static Vec3 chargeAim(Player player,Vec3 fallback){
        Vec3 look=player==null?null:look(player);
        Vec3 flat=look==null?null:new Vec3(look.x,0,look.z);
        return flat==null||flat.lengthSqr()<.001?fallback:flat.normalize();
    }
}
