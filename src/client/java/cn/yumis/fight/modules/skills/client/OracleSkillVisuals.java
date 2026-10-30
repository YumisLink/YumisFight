package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.*;

/** Gold Oracle strokes and a content-owned third-person camera. No damage is derived from these meshes. */
final class OracleSkillVisuals {
    private OracleSkillVisuals(){}
    static void install(){for(String id:OracleSkills.ACTIONS)SkillVisualRegistry.register(id,new Handler(false));SkillVisualRegistry.register(OracleSkills.REPLICA,new Handler(true));SkillVisualRegistry.register(OracleSkills.WILL,new Handler(false));}
    private static final class Handler implements SkillVisualRegistry.Handler {
        private final boolean replica;Handler(boolean replica){this.replica=replica;}
        public boolean cinematic(){return replica;}
        public boolean refreshable(){return replica;}
        public boolean restoreCamera(){return replica;}
        public boolean blocksTurning(){return replica;}
        public boolean blocksHotbar(){return replica;}
        public long finishedUntil(Track track,SkillVisualEvent event){return replica?track.start.startedAt()+track.start.duration()+5500:event.at()+500;}
        public CameraType cameraType(Track track,long time){return CameraType.THIRD_PERSON_BACK;}
        public int defaultColor(){return 0xf2cb72;}
        public void animate(Track track,net.minecraft.world.entity.player.Player player,net.minecraft.client.renderer.entity.state.EntityRenderState raw,long time){
            if(!(raw instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState state))return;
            var event=track.last;Vec3 heading=vector(event.direction());state.bodyRot=(float)Math.toDegrees(Math.atan2(-heading.x,heading.z));state.yRot=0;state.walkAnimationSpeed=0;
            long stage=track.flashes.stream().filter(e->e.phase()==SkillVisualEvent.Phase.SEGMENT).mapToLong(SkillVisualEvent::at).max().orElse(track.start.startedAt());state.attackTime=(float)Math.clamp((time-stage)*track.speed/600,0,1);
            if(replica&&event.sweep()){state.attackTime=.1f;state.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.THROW_TRIDENT;state.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.THROW_TRIDENT;}
            if(replica&&OracleSkills.WEAPONS.contains(event.weaponType())){
                var hand=cn.yumis.fight.modules.equipment.api.EquipmentItems.view(player.getMainHandItem());
                if(hand==null||!hand.weapon().name().equals(event.weaponType())){
                    var item=cn.yumis.fight.modules.equipment.api.EquipmentVisualItems.weaponModel("common_"+event.weaponType().toLowerCase(java.util.Locale.ROOT));
                    Minecraft.getInstance().getItemModelResolver().updateForLiving(state.getMainHandItemState(),item,player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT?net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_LEFT_HAND:net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,player);
                }
            }
        }
        public void play(SkillVisualEvent event){if(event.phase()!=SkillVisualEvent.Phase.HIT&&event.phase()!=SkillVisualEvent.Phase.FINISH&&event.phase()!=SkillVisualEvent.Phase.RELEASE)return;var level=Minecraft.getInstance().level;if(level!=null){Vec3 p=vector(event.position());level.playLocalSound(p.x,p.y,p.z,net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,net.minecraft.sounds.SoundSource.PLAYERS,.65f,event.phase()==SkillVisualEvent.Phase.FINISH?.7f:1.2f,false);}}
        public void render(LevelRenderContext context,Track track,long age,long time,List<Quad> out,Vec3 camera){
            var event=track.last;Vec3 p=vector(event.position()),direction=vector(event.direction()),right=new Vec3(-direction.z,0,direction.x);int color=event.color();
            if(!replica&&event.skill().equals(OracleSkills.SWEEP)&&!track.released)ring(out,p.add(0,.1,0),1.2,color,120);
            for(var flash:track.flashes){long elapsed=time-flash.at();if(elapsed<0||elapsed>350||flash.phase()==SkillVisualEvent.Phase.START)continue;
                Vec3 center=vector(flash.position()).add(0,1,0),forward=vector(flash.direction()),side=new Vec3(-forward.z,0,forward.x);int alpha=(int)(220*(1-elapsed/350d));
                boolean down=flash.skill().equals(OracleSkills.DOWN)||flash.weaponType().equals("BLUNT"),sweep=flash.skill().equals(OracleSkills.SWEEP)||flash.weaponType().equals("GREATSWORD");
                crescent(out,center.add(forward.scale(1.2)),forward.scale(.7),down?new Vec3(0,1,0):side,sweep?3:2,.25,color,0xfff5cd,alpha);
                if(flash.phase()==SkillVisualEvent.Phase.FINISH){ring(out,center,4,color,alpha);crescent(out,center,forward,side,4,.45,color,0xffffff,alpha);}
            }
            if(event.phase()==SkillVisualEvent.Phase.TRAIL)crescent(out,p.add(0,1,0),direction.scale(.5),right,.8,.15,color,color,100);
        }
        public CameraPose camera(Track track,long time){
            if(!replica)return null;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null)return null;
            Vec3 look=vector(track.start.direction()),origin=vector(track.start.origin()),side=new Vec3(-look.z,0,look.x),target=origin.add(look.scale(3)).add(0,2,0),point=origin.subtract(look.scale(10)).add(side.scale(5)).add(0,7,0);
            var wall=mc.level.clip(new ClipContext(target,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));if(wall.getType()!=HitResult.Type.MISS)point=wall.getLocation().add(look.scale(.2));
            Vec3 delta=target.subtract(point);float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=(float)-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));return new CameraPose(point,yaw,pitch);
        }
    }
    private static void crescent(List<Quad> out,Vec3 point,Vec3 u,Vec3 v,double radius,double thickness,int fill,int edge,int alpha){
        for(int i=0;i<24;i++){double a=-Math.PI*.8+i*Math.PI*1.6/24,b=-Math.PI*.8+(i+1)*Math.PI*1.6/24;
            Vec3 outerA=u.scale(Math.cos(a)*radius).add(v.scale(Math.sin(a)*radius)),outerB=u.scale(Math.cos(b)*radius).add(v.scale(Math.sin(b)*radius));
            Vec3 innerA=outerA.scale(Math.max(0,1-thickness)),innerB=outerB.scale(Math.max(0,1-thickness));out.add(new Quad(point.add(outerA),point.add(outerB),point.add(innerB),point.add(innerA),i%3==0?edge:fill,alpha));
        }
    }
    private static void ring(List<Quad> out,Vec3 point,double radius,int color,int alpha){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24;Vec3 u=new Vec3(Math.cos(a),0,Math.sin(a)),v=new Vec3(Math.cos(b),0,Math.sin(b));out.add(new Quad(point.add(u.scale(radius)),point.add(v.scale(radius)),point.add(v.scale(radius-.12)),point.add(u.scale(radius-.12)),color,alpha));}}
}
