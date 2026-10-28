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
        if(quads.isEmpty()||event.getLevelRenderState().cameraRenderState==null)return;
        Vec3 camera=event.getLevelRenderState().cameraRenderState.pos;
        event.getSubmitNodeCollector().submitCustomGeometry(event.getPoseStack(),RenderTypes.entityTranslucentEmissive(Identifier.withDefaultNamespace("textures/block/white_concrete.png")),(matrix,vertices)->{
            for(Quad q:quads){Vec3[] points={q.a(),q.b(),q.c(),q.d()};
                for(int i=0;i<4;i++)vertices.addVertex(matrix,(float)(points[i].x-camera.x),(float)(points[i].y-camera.y),(float)(points[i].z-camera.z)).setColor((q.rgb()>>16)&255,(q.rgb()>>8)&255,q.rgb()&255,q.alpha()).setUv(i==0||i==3?0:1,i<2?0:1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(matrix,0,1,0);
            }
        });
    }
}
