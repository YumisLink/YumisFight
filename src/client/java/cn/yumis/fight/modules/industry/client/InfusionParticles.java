package cn.yumis.fight.modules.industry.client;

import cn.yumis.fight.modules.industry.platform.IndustrialContent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.DustParticleOptions;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** A two-pixel falling drop, with short, local splashes on the display surface. */
public final class InfusionParticles {
    private InfusionParticles(){}
    public static void register(IEventBus bus){bus.addListener((RegisterParticleProvidersEvent event)->IndustrialContent.INFUSION_DROPS.forEach((id,type)->{
        int color=switch(id){case "concentrated_mana_fluid"->0x3659e8;case "shimmer"->0xac93ef;default->0x58baff;};
        event.registerSpriteSet(type.get(),sprites->(options,level,x,y,z,dx,dy,dz,random)->new Drop(level,x,y,z,color,sprites));
    }));}
    private static final class Drop extends DustParticle {
        private final SpriteSet sprites;private final int color;private final double landing;
        Drop(ClientLevel level,double x,double y,double z,int color,SpriteSet sprites){
            super(level,x,y,z,0,-.07,0,new DustParticleOptions(color,1),sprites);this.color=color;this.sprites=sprites;landing=y-.43;
            quadSize=.0625f;gravity=.025f;xd=zd=0;yd=-.07;lifetime=15;hasPhysics=false;
        }
        @Override public void tick(){super.tick();if(!removed&&y<=landing){for(int i=0;i<5;i++)net.minecraft.client.Minecraft.getInstance().particleEngine.add(new Splash(level,x,landing+.015,z,color,sprites));remove();}}
    }
    private static final class Splash extends DustParticle {
        Splash(ClientLevel level,double x,double y,double z,int color,SpriteSet sprites){
            super(level,x,y,z,0,0,0,new DustParticleOptions(color,1),sprites);quadSize=.018f;lifetime=8;gravity=.08f;
            xd=(random.nextDouble()-.5)*.1;zd=(random.nextDouble()-.5)*.1;yd=.035;hasPhysics=false;
        }
    }
}
