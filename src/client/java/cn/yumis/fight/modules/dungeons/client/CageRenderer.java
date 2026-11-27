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
        collector.submitModel(model,state,pose,model.renderType(Identifier.withDefaultNamespace("textures/block/amethyst_block.png")),0x00F000F0,OverlayTexture.NO_OVERLAY,0xFFC8E8FF,null);
        pose.popPose();
    }
    private static final class Model extends EntityModel<State> {
        Model(){
            super(layer().bakeRoot());
        }
        static LayerDefinition layer(){
            var mesh=new MeshDefinition();
            var cubes=CubeListBuilder.create();
            // 四角雷光龙枪柱与底部锚环，与 LucasBossVisuals 电弧栏杆无缝衔接
            for(int x:new int[]{-26,26})for(int z:new int[]{-26,26}){
                cubes.texOffs(0,0).addBox(x-1.5f,-46,z-1.5f,3,46,3);
                cubes.texOffs(0,0).addBox(x-2.5f,-48,z-2.5f,5,4,5);
            }
            for(int y:new int[]{-42,-2}){
                cubes.addBox(-27,y,-27,54,1.5f,1.5f).addBox(-27,y,25.5f,54,1.5f,1.5f).addBox(-27,y,-25.5f,1.5f,1.5f,51).addBox(25.5f,y,-25.5f,1.5f,1.5f,51);
            }
            mesh.getRoot().addOrReplaceChild("bars",cubes,PartPose.ZERO);
            return LayerDefinition.create(mesh,16,16);
        }
    }
}
