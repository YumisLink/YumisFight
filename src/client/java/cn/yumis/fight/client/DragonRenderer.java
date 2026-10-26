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
public final class DragonRenderer extends MobRenderer<DungeonEnemy,DragonRenderer.State,DragonRenderer.Model> {
    public DragonRenderer(EntityRendererProvider.Context context,int role){
        super(context,new Model(Model.layer(role).bakeRoot()),.5f);
    }
    private static int role(DungeonEnemy entity){
        String name=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        return java.util.List.of("dragonman","blue_dragonman","wyvern","blue_wyvern","lucas").indexOf(name);
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
            "dragonman","blue_dragonman","wyvern","blue_wyvern","lucas","lucas"
        }
        [state.role]+".png");
    }
    @Override protected void scale(State state,PoseStack pose){
        float scale=state.role>=4?1.35f:state.role==2||state.role==3?.85f:1.15f;
        pose.scale(scale,scale,scale);
    }
    public static final class Model extends EntityModel<State> {
        final ModelPart head,body,rightArm,leftArm,rightLeg,leftLeg,tail,leftWing,rightWing,spear,crown;
        Model(ModelPart root){
            super(root);
            head=root.getChild("head");
            body=root.getChild("body");
            rightArm=root.getChild("right_arm");
            leftArm=root.getChild("left_arm");
            rightLeg=root.getChild("right_leg");
            leftLeg=root.getChild("left_leg");
            tail=root.getChild("tail");
            leftWing=root.getChild("left_wing");
            rightWing=root.getChild("right_wing");
            spear=rightArm.getChild("spear");
            crown=head.getChild("crown");
        }
        static LayerDefinition layer(int role){
            MeshDefinition mesh=new MeshDefinition();
            var root=mesh.getRoot();
            var head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-3.5f,-7,-3,7,7,6).texOffs(28,0).addBox(-2.5f,-4,-7,5,3,5).texOffs(48,0).addBox(-4,-10,0,2,5,2).addBox(2,-10,0,2,5,2),PartPose.offset(0,7,0));
            head.addOrReplaceChild("crown",CubeListBuilder.create().texOffs(32,48).addBox(-4,-8,-3.5f,8,2,7).texOffs(48,0).addBox(-1,-11,-3,2,4,2),PartPose.ZERO);
            boolean wing=role==2||role==3;
            float torso=wing?6:role==1?9:8;
            root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,16).addBox(-torso/2,0,-2.5f,torso,10,5).texOffs(32,38).addBox(-4.5f,0,-3,9,role==1?6:4,2),PartPose.offset(0,7,0));
            var arm=root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(32,16).addBox(-3,-1,-2,3,10,4).texOffs(44,18).addBox(-3.5f,-2,-2.5f,4,3,5),PartPose.offset(-4,8,0));
            root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(32,16).mirror().addBox(0,-1,-2,3,10,4).texOffs(44,18).addBox(-.5f,-2,-2.5f,4,3,5),PartPose.offset(4,8,0));
            arm.addOrReplaceChild("spear",CubeListBuilder.create().texOffs(32,32).addBox(-2,-8,-4,1,24,1).texOffs(48,32).addBox(-3,-12,-4.5f,3,5,2),PartPose.ZERO);
            root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,34).addBox(-2,0,-2,4,7,4).texOffs(0,46).addBox(-2,5,-4,4,2,6),PartPose.offset(-2.3f,17,0));
            root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,34).mirror().addBox(-2,0,-2,4,7,4).texOffs(0,46).addBox(-2,5,-4,4,2,6),PartPose.offset(2.3f,17,0));
            root.addOrReplaceChild("tail",CubeListBuilder.create().texOffs(20,40).addBox(-1.5f,0,0,3,3,12).texOffs(44,46).addBox(-1,-1,9,2,3,6),PartPose.offsetAndRotation(0,16,2,-.25f,0,0));
            root.addOrReplaceChild("left_wing",CubeListBuilder.create().texOffs(0,50).addBox(0,-3,0,15,10,1).texOffs(0,58).addBox(0,-4,-.5f,17,2,2),PartPose.offsetAndRotation(3,8,2,0,-.4f,-.2f));
            root.addOrReplaceChild("right_wing",CubeListBuilder.create().texOffs(0,50).mirror().addBox(-15,-3,0,15,10,1).texOffs(0,58).addBox(-17,-4,-.5f,17,2,2),PartPose.offsetAndRotation(-3,8,2,0,.4f,.2f));
            return LayerDefinition.create(mesh,64,64);
        }
        @Override public void setupAnim(State s){
            super.setupAnim(s);
            head.yRot=s.yRot*(float)Math.PI/180;
            head.xRot=s.xRot*(float)Math.PI/180;
            float walk=(float)Math.cos(s.walkAnimationPos*.6662f)*s.walkAnimationSpeed;
            rightLeg.xRot=walk;
            leftLeg.xRot=-walk;
            rightArm.xRot=-walk*.5f;
            leftArm.xRot=walk*.5f;
            tail.yRot=(float)Math.sin(s.ageInTicks*.1)*.15f;
            boolean wing=s.role==2||s.role==3;
            leftWing.visible=rightWing.visible=wing;
            spear.visible=!wing;
            crown.visible=s.role>=4;
            if(wing){
                leftWing.zRot=.25f+(float)Math.sin(s.ageInTicks*.35)*.5f;
                rightWing.zRot=-leftWing.zRot;
                body.xRot=.4f;
                head.xRot-=.2f;
            }
            if(s.casting){
                rightArm.xRot=-1.5f;
                leftArm.xRot=-.8f;
            }
        }
    }
}
