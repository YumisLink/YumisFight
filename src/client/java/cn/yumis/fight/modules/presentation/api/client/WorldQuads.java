package cn.yumis.fight.modules.presentation.api.client;

import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.List;

/** Shared NeoForge drawing adapter. Modules own the geometry and lifetime of their effects. */
public final class WorldQuads {
    public record Quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,int rgb,int alpha){}
    private WorldQuads(){}
    public static void submit(SubmitCustomGeometryEvent event,List<Quad> quads){
        submit(event,quads,false);
    }
    /** nearFade is captured by the caller during this frame's camera extraction. */
    public static void submit(SubmitCustomGeometryEvent event,List<Quad> quads,boolean nearFade){
        if(quads.isEmpty()||event.getLevelRenderState().cameraRenderState==null)return;
        Vec3 camera=event.getLevelRenderState().cameraRenderState.pos;
        var type=nearFade?NearEffectRendering.quads():RenderTypes.entityTranslucentEmissive(Identifier.withDefaultNamespace("textures/block/white_concrete.png"));
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),type,(matrix,vertices)->{
            for(Quad q:quads){Vec3[] points={q.a(),q.b(),q.c(),q.d()};
                for(int i=0;i<4;i++)vertices.addVertex(matrix,(float)(points[i].x-camera.x),(float)(points[i].y-camera.y),(float)(points[i].z-camera.z)).setColor((q.rgb()>>16)&255,(q.rgb()>>8)&255,q.rgb()&255,q.alpha()).setUv(i==0||i==3?0:1,i<2?0:1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(matrix,0,1,0);
            }
        });
    }
    /** Hollow two-layer beam, a bright core plus a wider faint glow, shared by industrial links and loot drops. */
    public static void beam(List<Quad> out,Vec3 from,Vec3 to,int coreColor,double coreRadius,int coreAlpha,int glowColor,double glowRadius,int glowAlpha){
        Vec3 direction=to.subtract(from);
        if(direction.lengthSqr()<.001)return;
        direction=direction.normalize();
        Vec3 u=direction.cross(Math.abs(direction.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),v=direction.cross(u).normalize();
        for(int layer=0;layer<2;layer++){
            double radius=layer==0?coreRadius:glowRadius;int color=layer==0?coreColor:glowColor,alpha=layer==0?coreAlpha:glowAlpha;
            for(int i=0;i<8;i++){double a=i*Math.PI/4,b=(i+1)*Math.PI/4;
                Vec3 left=u.scale(Math.cos(a)*radius).add(v.scale(Math.sin(a)*radius)),right=u.scale(Math.cos(b)*radius).add(v.scale(Math.sin(b)*radius));
                out.add(new Quad(from.add(left),to.add(left),to.add(right),from.add(right),color,alpha));
            }
        }
    }
}
