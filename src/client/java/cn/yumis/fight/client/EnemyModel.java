package cn.yumis.fight.client;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
/** Fresh geometry for the supplied skins; no previous project renderer is used. */
public final class EnemyModel extends HumanoidModel<EnemyRenderer.State> {
    private final boolean winged;
    public EnemyModel(ModelPart root,boolean winged){
        super(root);
        this.winged=winged;
    }
    public static LayerDefinition layer(String kind){
        MeshDefinition mesh=HumanoidModel.createMesh(new CubeDeformation(0),0);
        PartDefinition root=mesh.getRoot();
        if(kind.equals("goblin")){
            PartDefinition head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-7,-4,8,7,8).texOffs(32,0).addBox(-6,-4,-1,2,3,1).addBox(4,-4,-1,2,3,1),PartPose.offset(0,9,0));
            head.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
            root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(16,16).addBox(-3,0,-2,6,9,4),PartPose.offset(0,9,0));
            root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(40,16).addBox(-3,-1,-2,3,9,4),PartPose.offset(-3,10,0));
            root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(40,16).mirror().addBox(0,-1,-2,3,9,4),PartPose.offset(3,10,0));
            root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,16).addBox(-1.5f,0,-2,3,6,4),PartPose.offset(-1.5f,18,0));
            root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,16).mirror().addBox(-1.5f,0,-2,3,6,4),PartPose.offset(1.5f,18,0));
        }
        else if(kind.equals("wyvern")){
            PartDefinition head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-3,-5,-5,6,5,7).texOffs(0,20).addBox(-2,-2,-8,4,2,4),PartPose.offset(0,14,0));
            head.addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
            root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(16,16).addBox(-3,0,-2,6,7,5).texOffs(0,28).addBox(-1,3,3,2,2,7),PartPose.offset(0,14,0));
            root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(32,32).addBox(-13,0,-1,13,1,8),PartPose.offset(-3,15,0));
            root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(32,32).mirror().addBox(0,0,-1,13,1,8),PartPose.offset(3,15,0));
            root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,16).addBox(-1,0,-2,2,3,4),PartPose.offset(-2,21,0));
            root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,16).mirror().addBox(-1,0,-2,2,3,4),PartPose.offset(2,21,0));
        }
        else{
            PartDefinition head=root.getChild("head");
            head.addOrReplaceChild("horns",CubeListBuilder.create().texOffs(32,0).addBox(-4,-11,-1,2,4,2).addBox(2,-11,-1,2,4,2),PartPose.ZERO);
        }
        return LayerDefinition.create(mesh,64,64);
    }
    @Override public void setupAnim(EnemyRenderer.State state){
        super.setupAnim(state);
        if(winged){
            rightArm.xRot=leftArm.xRot=0;
            rightArm.zRot=(float)(-.3-Math.sin(state.ageInTicks*.5)*.5);
            leftArm.zRot=-rightArm.zRot;
        }
        else if(state.preparing){
            rightArm.xRot=-1.8f;
            leftArm.xRot=-1.3f;
        }
    }
}
