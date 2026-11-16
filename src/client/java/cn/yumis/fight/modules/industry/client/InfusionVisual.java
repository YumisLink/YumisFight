package cn.yumis.fight.modules.industry.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;

/** A short telescoping nozzle and the fluid actually being processed below it. */
final class InfusionVisual {
    private static final Identifier METAL=Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","textures/block/arcane/turbine_generator_2.png");
    private InfusionVisual(){}

    static final class Motion {
        private double last=Double.NaN;
        private float position;
        float sample(double time,boolean active,float progress){
            float target=active?smooth(progress/.18f)*(1-smooth((progress-.8f)/.2f)):0;
            double delta=Double.isNaN(last)?0:Math.clamp(time-last,0,4);last=time;
            position+=(target-position)*(float)Math.min(1,delta/2);
            return position<.001f?0:position;
        }
        private static float smooth(float x){x=Math.clamp(x,0,1);return x*x*(3-2*x);}
    }

    static void submit(PoseStack pose,SubmitNodeCollector nodes,float stroke,float bob,TextureAtlasSprite fluid,int tint,int light,int fluidLight){
        float travel=.1f*stroke;
        if(stroke>0){
            // The existing fixed nozzle remains the anchor for this sliding sleeve.
            draw(nodes,pose,RenderTypes.entityCutout(METAL),box(.455f,-travel,.455f,.545f,.005f,.545f,.51f,.02f,.62f,.15f),-1,light);
            draw(nodes,pose,RenderTypes.entityCutout(METAL),box(.425f,-travel-.04f,.425f,.575f,-travel+.005f,.575f,.51f,.02f,.68f,.15f),-1,light);
        }
        if(fluid==null)return;
        float top=-travel-.04f,bottom=-.32f+bob;
        if(bottom>=top)return;
        float radius=.03125f*(.65f+.35f*stroke),u0=fluid.getU0(),u1=fluid.getU(radius*2),v0=fluid.getV0(),v1=fluid.getV(top-bottom);
        draw(nodes,pose,RenderTypes.entityTranslucent(fluid.atlasLocation()),box(.5f-radius,bottom,.5f-radius,.5f+radius,top,.5f+radius,u0,v0,u1,v1),0xd8000000|(tint&0xffffff),fluidLight);
    }

    private static void draw(SubmitNodeCollector nodes,PoseStack pose,RenderType type,float[][] faces,int color,int light){
        nodes.submitCustomGeometry(pose,type,(matrix,vertices)->{
            for(float[] f:faces)for(int i=0;i<4;i++){int at=i*5;vertices.addVertex(matrix,f[at],f[at+1],f[at+2]).setColor(color).setUv(f[at+3],f[at+4]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(matrix,f[20],f[21],f[22]);}
        });
    }

    private static float[][] box(float x,float y,float z,float X,float Y,float Z,float u,float v,float U,float V){
        return new float[][]{
            {x,y,z,U,V, x,Y,z,U,v, X,Y,z,u,v, X,y,z,u,V, 0,0,-1},
            {X,y,Z,U,V, X,Y,Z,U,v, x,Y,Z,u,v, x,y,Z,u,V, 0,0,1},
            {x,y,Z,U,V, x,Y,Z,U,v, x,Y,z,u,v, x,y,z,u,V, -1,0,0},
            {X,y,z,U,V, X,Y,z,U,v, X,Y,Z,u,v, X,y,Z,u,V, 1,0,0},
            {x,Y,z,u,v, x,Y,Z,u,V, X,Y,Z,U,V, X,Y,z,U,v, 0,1,0},
            {x,y,Z,u,V, x,y,z,u,v, X,y,z,U,v, X,y,Z,U,V, 0,-1,0}
        };
    }
}
