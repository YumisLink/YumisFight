package cn.yumis.fight.modules.dungeons.client;

import cn.yumis.fight.modules.dungeons.api.TrainingSandbag;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/** Original cube-built canvas sandbag and weighted timber stand; uses vanilla material textures. */
public final class TrainingSandbagRenderer extends EntityRenderer<TrainingSandbag,TrainingSandbagRenderer.State> {
    private final Model stand=new Model(0),canvas=new Model(1),seams=new Model(2),target=new Model(3);
    public static final class State extends EntityRenderState {float yaw,wobble;}
    public TrainingSandbagRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=.4f;}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(TrainingSandbag bag,State state,float partial){
        super.extractRenderState(bag,state,partial);state.yaw=bag.getYRot();
        float hurt=Math.max(0,bag.hurtTime-partial);state.wobble=(float)(Math.sin(hurt*1.5)*hurt*.35);
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180-state.yaw));pose.scale(1,-1,1);
        draw(stand,"dark_oak_planks",state,pose,collector,-1);
        pose.translate(0,-.35,0);pose.mulPose(Axis.ZP.rotationDegrees(state.wobble));pose.translate(0,.35,0);
        draw(canvas,"white_wool",state,pose,collector,0xffc69b69);
        draw(seams,"brown_wool",state,pose,collector,-1);
        draw(target,"red_wool",state,pose,collector,-1);
        pose.popPose();super.submit(state,pose,collector,camera);
    }
    private static void draw(Model model,String material,State state,PoseStack pose,SubmitNodeCollector collector,int color){
        collector.submitModel(model,state,pose,model.renderType(Identifier.withDefaultNamespace("textures/block/"+material+".png")),state.lightCoords,OverlayTexture.NO_OVERLAY,color,null);
    }
    private static final class Model extends EntityModel<State> {
        Model(int part){super(layer(part).bakeRoot());}
        private static LayerDefinition layer(int part){
            var mesh=new MeshDefinition();var cubes=CubeListBuilder.create().texOffs(0,0);
            switch(part){
                case 0->cubes.addBox(-6,-2,-6,12,2,12).addBox(-1.5f,-30,-1.5f,3,28,3);
                case 1->cubes.addBox(-4.5f,-29,-4.5f,9,21,9).addBox(-3.5f,-31,-3.5f,7,2,7).addBox(-3.5f,-8,-3.5f,7,2,7);
                case 2->{
                    cubes.addBox(-4.65f,-27,-4.65f,9.3f,1.2f,9.3f).addBox(-4.65f,-11,-4.65f,9.3f,1.2f,9.3f);
                    for(int y=-24;y<=-14;y+=2)cubes.addBox(-4.7f,y,-.5f,.25f,1,.9f).addBox(4.45f,y,-.5f,.25f,1,.9f);
                }
                case 3->{
                    cubes.addBox(-3,-21.5f,-4.7f,6,6,.25f).addBox(-3,-21.5f,4.45f,6,6,.25f);
                    cubes.addBox(-4.7f,-21.5f,-3,.25f,6,6).addBox(4.45f,-21.5f,-3,.25f,6,6);
                }
            }
            mesh.getRoot().addOrReplaceChild("mesh",cubes,PartPose.ZERO);return LayerDefinition.create(mesh,16,16);
        }
    }
}
