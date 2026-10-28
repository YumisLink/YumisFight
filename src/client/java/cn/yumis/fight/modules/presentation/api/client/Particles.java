package cn.yumis.fight.modules.presentation.api.client;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
public final class Particles {
    private Particles(){}
    public static void dust(Vec3 position,int color,float size){var level=Minecraft.getInstance().level;if(level!=null)level.addAlwaysVisibleParticle(new DustParticleOptions(color,size),true,position.x,position.y,position.z,0,0,0);}
}
