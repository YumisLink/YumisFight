package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.Herder;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Authored, articulated low-poly goblin: club, leather tunic, shoulder guards and throwing stone. */
public final class HerderRenderer extends MobRenderer<Herder,HerderRenderer.State,HerderRenderer.Model> {
    public HerderRenderer(EntityRendererProvider.Context context){super(context,new Model(Model.layer().bakeRoot()),.4f);}
    public static final class State extends LivingEntityRenderState {int action;}
    @Override public State createRenderState(){return new State();}
    @Override public void extractRenderState(Herder entity,State state,float delta){super.extractRenderState(entity,state,delta);state.action=entity.action();}
    @Override public Identifier getTextureLocation(State state){return Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","textures/entity/herder.png");}
    static final class Model extends EntityModel<State> {
        final ModelPart head,rightArm,leftArm,rightLeg,leftLeg,stone;
        Model(ModelPart root){super(root);head=root.getChild("head");rightArm=root.getChild("right_arm");leftArm=root.getChild("left_arm");rightLeg=root.getChild("right_leg");leftLeg=root.getChild("left_leg");stone=leftArm.getChild("stone");}
        static LayerDefinition layer(){
            var mesh=new MeshDefinition();var root=mesh.getRoot();
            var head=root.addOrReplaceChild("head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-6,-3,8,6,6).texOffs(32,0).addBox(-1,-3,-4.5f,2,2,2).texOffs(32,4).addBox(-7,-5,-.5f,3,2,1).addBox(4,-5,-.5f,3,2,1),PartPose.offset(0,10,0));
            head.addOrReplaceChild("eyes",CubeListBuilder.create().texOffs(48,0).addBox(-2.8f,-4,-3.2f,1.8f,1.1f,.4f).addBox(1,-4,-3.2f,1.8f,1.1f,.4f).texOffs(48,4).addBox(-2,-1,-3.5f,1,1,.5f).addBox(1,-1,-3.5f,1,1,.5f),PartPose.ZERO);
            root.addOrReplaceChild("body",CubeListBuilder.create().texOffs(0,16).addBox(-3.5f,0,-2.5f,7,9,5).texOffs(32,48).addBox(-3.7f,6,-2.7f,7.4f,1.5f,5.4f),PartPose.offset(0,10,0));
            var right=root.addOrReplaceChild("right_arm",CubeListBuilder.create().texOffs(32,16).addBox(-3,-1,-1.5f,3,8,3).texOffs(48,24).addBox(-3.5f,-1.5f,-2,4,2.5f,4),PartPose.offset(-3.5f,11,0));
            var left=root.addOrReplaceChild("left_arm",CubeListBuilder.create().texOffs(32,16).mirror().addBox(0,-1,-1.5f,3,8,3).texOffs(48,24).addBox(-.5f,-1.5f,-2,4,2.5f,4),PartPose.offset(3.5f,11,0));
            left.addOrReplaceChild("stone",CubeListBuilder.create().texOffs(48,16).addBox(0,5,-3,3,3,3),PartPose.ZERO);
            right.addOrReplaceChild("club",CubeListBuilder.create().texOffs(32,32).addBox(-2,4,-3,1.5f,8,1.5f).texOffs(48,32).addBox(-3,10,-4,3.5f,5,3.5f),PartPose.ZERO);
            root.addOrReplaceChild("right_leg",CubeListBuilder.create().texOffs(0,36).addBox(-1.5f,0,-2,3,5,4),PartPose.offset(-1.8f,19,0));root.addOrReplaceChild("left_leg",CubeListBuilder.create().texOffs(0,36).mirror().addBox(-1.5f,0,-2,3,5,4),PartPose.offset(1.8f,19,0));
            return LayerDefinition.create(mesh,64,64);
        }
        @Override public void setupAnim(State state){
            super.setupAnim(state);head.yRot=state.yRot*(float)Math.PI/180;head.xRot=state.xRot*(float)Math.PI/180;float walk=(float)Math.cos(state.walkAnimationPos*.6662f)*state.walkAnimationSpeed*1.4f;rightLeg.xRot=walk;leftLeg.xRot=-walk;rightArm.xRot=-walk*.7f;leftArm.xRot=walk*.7f;stone.visible=state.action==2;
            if(state.action==1)rightArm.xRot=-2.4f+(float)Math.sin(state.ageInTicks*.8f)*1.5f;
            if(state.action==2){leftArm.xRot=-2.7f+(float)Math.sin(state.ageInTicks*1.1f)*1.2f;rightArm.xRot=-.5f;}
        }
    }
}
