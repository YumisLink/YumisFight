package cn.yumis.fight.modules.presentation.api.client;

import cn.yumis.fight.modules.presentation.api.EffectParticleTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/** Vanilla particle motion and sprites with a per-frame, opt-in near-camera mask. */
public final class EffectParticles {
    private EffectParticles(){}

    public static void register(IEventBus bus){
        bus.addListener((RegisterParticleProvidersEvent event)->{
            event.registerSpriteSet(EffectParticleTypes.DUST.get(),sprites->(options,level,x,y,z,dx,dy,dz,random)->
                new FadedDust(level,x,y,z,dx,dy,dz,new DustParticleOptions(options.color(),options.scale()),sprites));
            event.registerSpriteSet(EffectParticleTypes.EXPLOSION.get(),sprites->(options,level,x,y,z,size,dy,dz,random)->
                new FadedExplosion(level,x,y,z,size,sprites));
        });
    }

    public static void dust(Vec3 position,int color,float size){
        var level=Minecraft.getInstance().level;
        if(level!=null)level.addAlwaysVisibleParticle(new EffectParticleTypes.Dust(color,size),true,position.x,position.y,position.z,0,0,0);
    }

    /** Same 32-block recipient boundary as the previous server explosion-particle packet. */
    public static void explosion(Vec3 position){
        var mc=Minecraft.getInstance();
        if(mc.level!=null&&mc.player!=null&&mc.player.blockPosition().closerToCenterThan(position,32))
            mc.particleEngine.add(new ExplosionEmitter(mc.level,position));
    }

    private static final class FadedDust extends DustParticle {
        private Layer extractedLayer;
        FadedDust(ClientLevel level,double x,double y,double z,double dx,double dy,double dz,DustParticleOptions options,SpriteSet sprites){
            super(level,x,y,z,dx,dy,dz,options,sprites);
        }
        @Override public Layer getLayer(){return extractedLayer==null?super.getLayer():extractedLayer;}
        @Override public void extract(QuadParticleRenderState state,Camera camera,float partialTick){
            if(!NearEffectRendering.enabled(camera)){super.extract(state,camera,partialTick);return;}
            try{
                extractedLayer=NearEffectRendering.particleLayer(false);super.extract(state,camera,partialTick);
                extractedLayer=NearEffectRendering.particleLayer(true);super.extract(state,camera,partialTick);
            }finally{extractedLayer=null;}
        }
    }

    private static final class FadedExplosion extends HugeExplosionParticle {
        private Layer extractedLayer;
        FadedExplosion(ClientLevel level,double x,double y,double z,double size,SpriteSet sprites){super(level,x,y,z,size,sprites);}
        @Override public Layer getLayer(){return extractedLayer==null?super.getLayer():extractedLayer;}
        @Override public void extract(QuadParticleRenderState state,Camera camera,float partialTick){
            if(!NearEffectRendering.enabled(camera)){super.extract(state,camera,partialTick);return;}
            try{
                extractedLayer=NearEffectRendering.particleLayer(false);super.extract(state,camera,partialTick);
                extractedLayer=NearEffectRendering.particleLayer(true);super.extract(state,camera,partialTick);
            }finally{extractedLayer=null;}
        }
    }

    private static final class ExplosionEmitter extends NoRenderParticle {
        ExplosionEmitter(ClientLevel level,Vec3 p){super(level,p.x,p.y,p.z,0,0,0);lifetime=8;}
        @Override public void tick(){
            for(int i=0;i<6;i++){
                double px=x+(random.nextDouble()-random.nextDouble())*4,py=y+(random.nextDouble()-random.nextDouble())*4,pz=z+(random.nextDouble()-random.nextDouble())*4;
                level.addParticle(EffectParticleTypes.EXPLOSION.get(),px,py,pz,(float)age/lifetime,0,0);
            }
            if(++age==lifetime)remove();
        }
    }
}
