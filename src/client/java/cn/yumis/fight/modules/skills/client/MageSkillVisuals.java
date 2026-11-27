package cn.yumis.fight.modules.skills.client;
import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.CameraShake;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.RibbonTrail;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import java.util.*;

import static cn.yumis.fight.modules.skills.api.SkillVisualEvent.Phase.*;
import static java.util.Map.entry;

/** Native three-dimensional meshes; projectiles use the synchronized source trajectory. */
final class MageSkillVisuals {
    private static final Map<String,List<SkillSound>> SOUNDS=Map.ofEntries(
        entry(MageSkills.STAR,List.of(
            new SkillSound(START,SoundEvents.FIREWORK_ROCKET_LAUNCH,.48f,1.50f),
            new SkillSound(FINISH,SoundEvents.AMETHYST_BLOCK_CHIME,.50f,1.35f))),
        entry(MageSkills.FANG,List.of(new SkillSound(RELEASE,SoundEvents.SPEAR_ATTACK.value(),.62f,1.20f))),
        entry(MageSkills.JACK,List.of(
            new SkillSound(START,SoundEvents.BLAZE_SHOOT,.56f,1.18f),
            new SkillSound(FINISH,SoundEvents.GENERIC_EXPLODE.value(),.74f,1.22f))),
        entry(MageSkills.HERDER,List.of(new SkillSound(START,SoundEvents.ENCHANTMENT_TABLE_USE,.55f,1.22f))),
        entry(SummonerSkills.SPIRITS,List.of(
            new SkillSound(START,SoundEvents.AMETHYST_BLOCK_CHIME,.52f,1.08f),
            new SkillSound(START,SoundEvents.BEACON_POWER_SELECT,.33f,1.32f))),
        entry(SummonerSkills.LOUISE,List.of(new SkillSound(START,SoundEvents.AMETHYST_BLOCK_RESONATE,.65f,1.36f))),
        entry(SummonerSkills.SANDOR,List.of(new SkillSound(START,SoundEvents.ANVIL_PLACE,.58f,.82f))),
        entry(SummonerSkills.KING,List.of(new SkillSound(START,SoundEvents.BEACON_ACTIVATE,.65f,1.12f))),
        entry(SummonerSkills.FRENZY,List.of(new SkillSound(START,SoundEvents.RESPAWN_ANCHOR_CHARGE,.60f,.85f))),
        entry(SummonerSkills.KASIJAS,List.of(new SkillSound(START,SoundEvents.RAVAGER_ROAR,.68f,.88f)))
    );

    private MageSkillVisuals(){}
    static void install(){for(String id:java.util.stream.Stream.concat(List.of(MageSkills.STAR,MageSkills.FANG,MageSkills.JACK,MageSkills.HERDER).stream(),SummonerSkills.ALL.stream()).toList())SkillVisualRegistry.register(id,new SkillVisualRegistry.Handler(){
        @Override public long defaultDuration(){return 1500;}
        @Override public long finishedUntil(SkillVfx.Track track,SkillVisualEvent event){return id.equals(MageSkills.JACK)?event.at()+560L:SkillVisualRegistry.Handler.super.finishedUntil(track,event);}
        @Override public void play(SkillVfx.Track track,SkillVisualEvent event){
            if(track.summonMove.isEmpty()){
                if(id.equals(MageSkills.STAR)&&WitchVisuals.isImprovedStar(track.start.cast())){WitchVisuals.playImprovedStar(event);return;}
                SkillSound.play(event,SOUNDS.getOrDefault(id,List.of()));
                if(id.equals(MageSkills.JACK)&&event.phase()==FINISH)CameraShake.impulse(SkillVfx.vector(event.position()),16,.34f,85,175);
                else if(id.equals(SummonerSkills.KASIJAS)&&event.phase()==START)CameraShake.impulse(SkillVfx.vector(event.position()),16,.34f,120,220);
            }
        }
        @Override public boolean motion(SkillVfx.Track track,com.google.gson.JsonObject motion){
            if(id.equals(MageSkills.FANG)){BattleMageVisuals.recordMotion(track.start.cast(),motion,track.last.phase()==HIT?track.last.at():0L);return true;}
            if(id.equals(MageSkills.STAR)){WitchVisuals.recordImprovedStar(track.start.cast(),motion);return false;}
            if(id.equals(MageSkills.JACK)){ElementalistVisuals.recordJackMotion(track.start.cast(),motion);return false;}
            return false;
        }
        // 宠物招式轨道（带 summonMove）不回放施法者姿态；只有玩家自己的召唤施法播姿态文档。
        @Override public String poseAnimation(SkillVfx.Track track,long time){return track.summonMove.isEmpty()?track.start.skill():null;}
        @Override public void render(SkillVfx.LevelRenderContext context,SkillVfx.Track track,long age,long time,List<Quad> quads,Vec3 camera){draw(id,track,age,time,quads,context.sprites(),camera);}
        @Override public void animate(SkillVfx.Track track,net.minecraft.world.entity.LivingEntity caster,net.minecraft.client.renderer.entity.state.EntityRenderState state,long time){if(!id.equals(MageSkills.HERDER)&&!SummonerSkills.contains(id)&&state instanceof net.minecraft.client.renderer.entity.state.HumanoidRenderState humanoid)humanoid.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;}
    });}
    private static void draw(String id,SkillVfx.Track t,long age,long time,List<Quad> out,List<SpriteQuad> sprites,Vec3 camera){
        if(SummonMoveVisuals.draw(t,time,out,sprites,camera))return;
        Vec3 p=SkillVfx.vector(t.last.position()),direction=SkillVfx.vector(t.last.direction()).normalize();boolean finished=t.last.phase()==SkillVisualEvent.Phase.FINISH;
        if(id.equals(MageSkills.STAR)){
            if(WitchVisuals.isImprovedStar(t.start.cast())){
                WitchVisuals.renderImprovedStar(t,age,time,sprites,camera);
                return;
            }
            if(!finished){
                star(out,p,direction,.28,0xa7eaff,Math.PI/4,Math.max(0,time-t.start.startedAt())*Math.PI/250d);
                EffectSprites.glow(sprites,p,camera,.52,0xa7eaff,230);
                RibbonTrail.push("star:"+t.start.cast(),p,.16,0x7ce2ff,215,240,time);
            }else{
                cube(out,p,.15,0xe3ffff,150);
                double u=Math.clamp((time-t.last.at())/320d,0,0.999);
                EffectSprites.impactBurst(sprites,p,camera,1.15,u,0xa7eaff,225,false);
            }
        }
        else if(id.equals(MageSkills.JACK)){
            ElementalistVisuals.renderJackBomb(t,age,time,sprites,camera);
        }else if(id.equals(MageSkills.HERDER)){if(!finished){cube(out,p,.22,0x979b99,255);cube(out,p.add(.045,.06,-.02),.15,0xc0c0b4,255);RibbonTrail.push("herder:"+t.start.cast(),p,.12,0xdadcd6,170,180,time);}}
        else if(SummonerSkills.contains(id)){
            if(age<900){
                double u=age/900d;
                EffectSprites.sigil(sprites,SkillVfx.vector(t.start.origin()).add(0,.05,0),id.equals(SummonerSkills.KASIJAS)?3.6:2.0,u*2.8,id.equals(SummonerSkills.KASIJAS)?0xffe07d:0xb68eff,(int)(210*(1-u*u)),true);
            }
            return;
        }
        else {
            BattleMageVisuals.renderDragonFang(t,age,time,sprites,camera);
        }
    }
    private static void star(List<Quad> out,Vec3 center,Vec3 direction,double radius,int color){
        star(out,center,direction,radius,color,0,0);
    }
    private static void star(List<Quad> out,Vec3 center,Vec3 direction,double radius,int color,double tilt,double rotation){
        Vec3 right=direction.cross(new Vec3(0,1,0));if(right.lengthSqr()<.01)right=new Vec3(1,0,0);right=right.normalize();Vec3 normal=direction.scale(Math.cos(tilt)).add(right.cross(direction).normalize().scale(Math.sin(tilt))),up=right.cross(normal).normalize(),depth=normal.scale(.075);var points=new Vec3[10];for(int i=0;i<10;i++){double angle=Math.PI/2+i*Math.PI/5+rotation,r=i%2==0?radius:radius*.42;points[i]=center.add(right.scale(Math.cos(angle)*r)).add(up.scale(Math.sin(angle)*r));}
        for(int i=0;i<10;i++){Vec3 a=points[i],b=points[(i+1)%10];out.add(new Quad(center.add(depth),a.add(depth),b.add(depth),center.add(depth),0xe4fbff,255));out.add(new Quad(center.subtract(depth),b.subtract(depth),a.subtract(depth),center.subtract(depth),color,255));out.add(new Quad(a.add(depth),a.subtract(depth),b.subtract(depth),b.add(depth),color,255));}
    }
    private static void cube(List<Quad> out,Vec3 p,double size,int color,int alpha){if(alpha<=0)return;double r=size/2;Vec3[] v={p.add(-r,-r,-r),p.add(r,-r,-r),p.add(r,r,-r),p.add(-r,r,-r),p.add(-r,-r,r),p.add(r,-r,r),p.add(r,r,r),p.add(-r,r,r)};for(int[] f:new int[][]{{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}})out.add(new Quad(v[f[0]],v[f[1]],v[f[2]],v[f[3]],color,alpha));}
}
