package cn.yumis.fight.modules.dungeons.client;
import cn.yumis.fight.modules.dungeons.api.DungeonEnemy;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
/** A crowned golden statue king; native geometry and vanilla gold material. */
public final class GoldenKingRenderer extends MobRenderer<DungeonEnemy,LivingEntityRenderState,GoldenKingRenderer.Model> {
    public GoldenKingRenderer(EntityRendererProvider.Context context){super(context,new Model(layer().bakeRoot()),.8f);}
    public LivingEntityRenderState createRenderState(){return new LivingEntityRenderState();}
    public Identifier getTextureLocation(LivingEntityRenderState state){return Identifier.withDefaultNamespace("textures/block/gold_block.png");}
    private static LayerDefinition layer(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,0).addBox(-6,-1,-3,12,13,6),PartPose.offset(0,7,0));
        root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8).addBox(-5,-10,-5,10,2,10).addBox(-5,-13,-5,2,3,2).addBox(3,-13,-5,2,3,2).addBox(-5,-13,3,2,3,2).addBox(3,-13,3,2,3,2),PartPose.offset(0,7,0));
        for(int side:new int[]{-1,1}){root.addOrReplaceChild("arm"+side,CubeListBuilder.create().texOffs(0,0).addBox(-2,-2,-2,4,14,4).addBox(-3,-3,-3,6,4,6),PartPose.offset(side*8,9,0));root.addOrReplaceChild("leg"+side,CubeListBuilder.create().texOffs(0,0).addBox(-2,0,-2,4,6,4),PartPose.offset(side*3,18,0));}
        root.addOrReplaceChild("cape",CubeListBuilder.create().texOffs(0,0).addBox(-7,0,0,14,18,1),PartPose.offset(0,5,3));return LayerDefinition.create(mesh,16,16);
    }
    static final class Model extends EntityModel<LivingEntityRenderState>{
        private final ModelPart head;
        Model(ModelPart root){super(root);head=root.getChild("head");}
        public void setupAnim(LivingEntityRenderState state){super.setupAnim(state);head.yRot=state.yRot*(float)Math.PI/180;head.xRot=state.xRot*(float)Math.PI/180;}
    }
}
