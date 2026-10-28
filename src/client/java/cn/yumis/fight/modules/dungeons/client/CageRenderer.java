// Geometry ported from the user-owned YDNF cage renderer.
package cn.yumis.fight.modules.dungeons.client;
import cn.yumis.fight.modules.dungeons.api.CageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
public final class CageRenderer extends EntityRenderer<CageEntity,CageRenderer.State> {
    private final Model model=new Model();
    public CageRenderer(EntityRendererProvider.Context context){
        super(context);
    }
    public static final class State extends EntityRenderState {
        int side;
    }
    @Override public State createRenderState(){
        return new State();
    }
    @Override public void extractRenderState(CageEntity cage,State state,float delta){
        super.extractRenderState(cage,state,delta);
        state.side=cage.side();
    }
    @Override public void submit(State state,PoseStack pose,SubmitNodeCollector collector,CameraRenderState camera){
        if(state.side!=0)return;
        pose.pushPose();
        pose.scale(1,-1,1);
        collector.submitModel(model,state,pose,model.renderType(Identifier.withDefaultNamespace("textures/block/iron_block.png")),state.lightCoords,OverlayTexture.NO_OVERLAY,-1,null);
        pose.popPose();
    }
    private static final class Model extends EntityModel<State> {
        Model(){
            super(layer().bakeRoot());
        }
        static LayerDefinition layer(){
            var mesh=new MeshDefinition();
            var cubes=CubeListBuilder.create();
            for(int x:new int[]{
                -24,0,24
            }
            )for(int z:new int[]{
                -24,0,24
            }
            )if(x!=0||z!=0)cubes.texOffs(0,0).addBox(x-1,-40,z-1,2,40,2);
            for(int y:new int[]{
                -40,-22,-2
            }
            ){
                cubes.addBox(-25,y,-25,50,2,2).addBox(-25,y,23,50,2,2).addBox(-25,y,-23,2,2,46).addBox(23,y,-23,2,2,46);
            }
            mesh.getRoot().addOrReplaceChild("bars",cubes,PartPose.ZERO);
            return LayerDefinition.create(mesh,16,16);
        }
    }
}
