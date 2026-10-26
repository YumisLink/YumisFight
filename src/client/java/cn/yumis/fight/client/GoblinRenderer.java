// Ported from the user-owned YDNF visual reference, as requested on 2026-10-26.
package cn.yumis.fight.client;
import cn.yumis.fight.game.DungeonEnemy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
public final class GoblinRenderer extends MobRenderer<DungeonEnemy,GoblinRenderer.State,GoblinRenderer.Model> {
    public GoblinRenderer(EntityRendererProvider.Context context){
        super(context,new Model(Model.layer().bakeRoot()),.4f);
    }
    private static int role(DungeonEnemy entity){
        String name=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return java.util.List.of("goblin","goblin_thrower","goblin_captain","goblin_shaman").indexOf(name);
    }
    public static final class State extends LivingEntityRenderState {
        int role;
        boolean casting;
    }
    @Override public State createRenderState(){
        return new State();
    }
    @Override public void extractRenderState(DungeonEnemy entity,State state,float delta){
        super.extractRenderState(entity,state,delta);
        state.role=role(entity);
        state.casting=entity.isAggressive();
    }
    @Override public Identifier getTextureLocation(State state){
        return ClientBootstrap.id("textures/entity/"+new String[]{
            "goblin","goblin_thrower","goblin_captain","goblin_shaman"
        }
        [state.role]+".png");
    }
    @Override protected void scale(State state,PoseStack pose){
        float scale=state.role==3?1.2f:state.role==2?1.1f:1;
        pose.scale(scale,scale,scale);
    }
    static final class Model extends EntityModel<State> {
        final ModelPart head,rightArm,leftArm,rightLeg,leftLeg,club,stone,staff,helmet,crown;
        Model(ModelPart root){
            super(root);
            head=root.getChild("head");
            rightArm=root.getChild("right_arm");
            leftArm=root.getChild("left_arm");
            rightLeg=root.getChild("right_leg");
            leftLeg=root.getChild("left_leg");
            club=rightArm.getChild("club");
            staff=rightArm.getChild("staff");
            stone=leftArm.getChild("stone");
            helmet=head.getChild("helmet");
            crown=head.getChild("crown");
        }
        static LayerDefinition layer(){
            MeshDefinition mesh=new MeshDefinition();
            PartDefinition root=mesh.getRoot();
            PartDefinition head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-6,-3,8,6,6).texOffs(32,0).addBox(-1,-3,-5,2,2,2).texOffs(32,4).addBox(-7,-5,-.5f,3,2,1).addBox(4,-5,-.5f,3,2,1),PartPose.offset(0,10,0));
            head.addOrReplaceChild("helmet",CubeListBuilder.create().texOffs(32,48).addBox(-4.5f,-7,-3.5f,9,2,7).texOffs(48,0).addBox(-1,-9,-1,2,2,2),PartPose.ZERO);
            head.addOrReplaceChild("crown",CubeListBuilder.create().texOffs(32,48).addBox(-4.5f,-7,-3.5f,9,1,7).texOffs(48,0).addBox(-1.5f,-9,-1.5f,3,3,3),PartPose.ZERO);
            root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,16).addBox(-3,0,-2,6,9,4),PartPose.offset(0,10,0));
            PartDefinition right=root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(32,16).addBox(-3,-1,-1.5f,3,8,3),PartPose.offset(-3,11,0));
            root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(32,16).mirror().addBox(0,-1,-1.5f,3,8,3),PartPose.offset(3,11,0)).addOrReplaceChild("stone",CubeListBuilder.create().texOffs(48,16).addBox(0,5,-3,3,3,3),PartPose.ZERO);
            right.addOrReplaceChild("club",CubeListBuilder.create().texOffs(32,32).addBox(-2,4,-3,1,8,1).texOffs(48,32).addBox(-3,10,-4,3,4,3),PartPose.ZERO);
            right.addOrReplaceChild("staff",CubeListBuilder.create().texOffs(32,32).addBox(-2,-6,-3,1,17,1).texOffs(48,0).addBox(-3,-9,-4,3,4,3),PartPose.ZERO);
            root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,36).addBox(-1.5f,0,-2,3,5,4),PartPose.offset(-1.75f,19,0));
            root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,36).mirror().addBox(-1.5f,0,-2,3,5,4),PartPose.offset(1.75f,19,0));
            return LayerDefinition.create(mesh,64,64);
        }
        @Override public void setupAnim(State state){
            super.setupAnim(state);
            head.yRot=state.yRot*(float)Math.PI/180;
            head.xRot=state.xRot*(float)Math.PI/180;
            float swing=(float)Math.cos(state.walkAnimationPos*.6662f)*state.walkAnimationSpeed*1.4f;
            rightLeg.xRot=swing;
            leftLeg.xRot=-swing;
            rightArm.xRot=-swing*.7f;
            leftArm.xRot=swing*.7f;
            club.visible=state.role==0||state.role==2;
            stone.visible=state.role==1;
            staff.visible=state.role==3;
            helmet.visible=state.role==2;
            crown.visible=state.role==3;
            if(state.casting){
                rightArm.xRot=-2.2f;
                if(state.role==3)leftArm.xRot=-1.8f;
                else leftArm.xRot=-1.3f;
            }
        }
    }
}
