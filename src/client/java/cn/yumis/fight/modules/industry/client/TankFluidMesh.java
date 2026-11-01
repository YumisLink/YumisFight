package cn.yumis.fight.modules.industry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** The real fluid texture occupies the transparent tank interior at its stored volume. */
final class TankFluidMesh {
    private TankFluidMesh(){}
    static void submit(PoseStack pose,SubmitNodeCollector nodes,TextureAtlasSprite sprite,int tint,float fill,int light){
        float lo=3.15f/16,hi=12.85f/16,bottom=3.1f/16,top=bottom+9.8f/16*fill;
        float u0=sprite.getU0(),u1=sprite.getU((hi-lo)),v0=sprite.getV0(),v1=sprite.getV(top-bottom),vt=sprite.getV(hi-lo);
        // Outward winding lets the visible surface hide the rear liquid faces.
        float[][] faces={
            {lo,bottom,lo,u1,v1, lo,top,lo,u1,v0, hi,top,lo,u0,v0, hi,bottom,lo,u0,v1, 0,0,-1},
            {hi,bottom,hi,u1,v1, hi,top,hi,u1,v0, lo,top,hi,u0,v0, lo,bottom,hi,u0,v1, 0,0,1},
            {lo,bottom,hi,u1,v1, lo,top,hi,u1,v0, lo,top,lo,u0,v0, lo,bottom,lo,u0,v1, -1,0,0},
            {hi,bottom,lo,u1,v1, hi,top,lo,u1,v0, hi,top,hi,u0,v0, hi,bottom,hi,u0,v1, 1,0,0},
            {lo,top,lo,u0,v0, lo,top,hi,u0,vt, hi,top,hi,u1,vt, hi,top,lo,u1,v0, 0,1,0}
        };
        nodes.submitCustomGeometry(pose,RenderTypes.entityTranslucent(sprite.atlasLocation()),(matrix,vertices)->{
            for(float[] f:faces)for(int i=0;i<4;i++){int at=i*5;vertices.addVertex(matrix,f[at],f[at+1],f[at+2]).setColor(tint).setUv(f[at+3],f[at+4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,f[20],f[21],f[22]);}
        });
    }
}
