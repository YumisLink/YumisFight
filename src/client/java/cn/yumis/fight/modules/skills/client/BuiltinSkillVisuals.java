package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.StormChoreography;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent;

import cn.yumis.fight.modules.equipment.api.EquipmentItems;
import cn.yumis.fight.modules.equipment.api.EquipmentVisualItems;
import cn.yumis.fight.modules.equipment.api.Weapon;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites;
import cn.yumis.fight.modules.presentation.api.client.EffectSprites.SpriteQuad;
import cn.yumis.fight.modules.presentation.api.client.WorldEffects;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.*;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

import java.util.*;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.api.client.BasicAttackVisuals;
import cn.yumis.fight.modules.skills.client.SkillVfx.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.vector;
/** Existing skill presentation content. New handlers register without editing track dispatch or rendering transport. */
final class BuiltinSkillVisuals {
    private BuiltinSkillVisuals(){}
    static void install(){
        for(String id:List.of("ATTACK","MASTERY_WAVE",SkillCatalog.GHOST.id,SkillCatalog.TRIPLE.id,SkillCatalog.WAVE.id,SkillCatalog.MASTERY.id,SkillCatalog.OVERDRIVE.id,SkillCatalog.RISING.id,SkillCatalog.DRAW.id,SkillCatalog.DRAGON.id,SkillCatalog.DANCE.id,SkillCatalog.STORM.id,SkillCatalog.METEOR_RISE.id,SkillCatalog.METEOR_LEAP.id,SkillCatalog.METEOR_PIERCE.id,cn.yumis.fight.modules.skills.api.SkillVisualRules.SM_DRAW_ARC,cn.yumis.fight.modules.skills.api.SkillVisualRules.SM_DRAGON_WHIRLWIND)){
            SkillVisualRegistry.register(id,new SkillVisualRegistry.Handler(){
                public boolean immediateEnd(){return id.equals(SkillCatalog.STORM.id)||id.equals(cn.yumis.fight.modules.skills.api.SkillVisualRules.SM_DRAGON_WHIRLWIND);}
                public boolean persistentEnd(){return id.equals(SkillCatalog.OVERDRIVE.id);}
                public boolean refreshable(){return id.equals(cn.yumis.fight.modules.skills.api.SkillVisualRules.SM_DRAGON_WHIRLWIND);}
                public boolean cinematic(){return id.equals(SkillCatalog.STORM.id);}
                public boolean cinematicFromFirstPerson(){return cinematic();}
                public boolean restoreCamera(){return cinematic();}
                public long finishedUntil(Track track,SkillVisualEvent event){return cinematic()?track.start.startedAt()+Math.max(track.start.duration(),event.duration()):event.at()+500;}
                public long defaultDuration(){return id.equals(SkillCatalog.STORM.id)?8000:id.equals(SkillCatalog.GHOST.id)?3800:id.equals(SkillCatalog.OVERDRIVE.id)?30000:id.equals(cn.yumis.fight.modules.skills.api.SkillVisualRules.SM_DRAGON_WHIRLWIND)?4000:id.equals(cn.yumis.fight.modules.skills.api.SkillVisualRules.SM_DRAW_ARC)?900:2500;}
                public int defaultColor(){return id.equals(SkillCatalog.GHOST.id)?SkillVisualEvent.DARK:SkillVisualEvent.CYAN;}
                public void tick(Track track,long time){SwordmasterVisuals.tick(track,time);}
                public void render(LevelRenderContext context,Track track,long age,long time,List<Quad> quads,Vec3 camera){SwordmasterVisuals.render(context,track,age,time,quads,camera);}
                public void play(Track track,SkillVisualEvent event){SwordmasterVisuals.play(event);}
                public void animate(Track track,net.minecraft.world.entity.LivingEntity caster,EntityRenderState state,long time){BuiltinSkillVisuals.animate(track,caster,state,time);}
                public void decorateAvatar(Track track,Player player,AvatarRenderState state,long time){BuiltinSkillVisuals.animate(track,player,state,time);}
                public CameraType cameraType(Track track,long time){return CameraType.THIRD_PERSON_BACK;}
                public CameraPose camera(Track track,long time){return cinematic()?BuiltinSkillVisuals.camera(track):null;}
            });
        }
        SkillVisualRegistry.register(SkillCatalog.BLINK.id,new SkillVisualRegistry.Handler(){
            @Override public void play(Track track,SkillVisualEvent event){
                if(event.phase()==SkillVisualEvent.Phase.START){
                    SkillSound.playAt(event.position(),SoundEvents.ENDERMAN_TELEPORT,.65f,1.30f);
                    var mc=Minecraft.getInstance();
                    var actor=mc.level==null?null:mc.level.getPlayerByUUID(event.caster());
                    if(actor!=null)PlayerAfterimages.captureAt(actor,vector(event.origin()),vector(event.direction()),0x89d6ff,180,380,true);
                    WorldEffects.playShockwave(vector(event.origin()).add(0,.08,0),.25,1.5,0x89d6ff,190,260);
                    WorldEffects.playGlow(vector(event.origin()).add(0,1.0,0),.85,0xbff0ff,210,220);
                }
            }
        });
        // 太刀普攻按单周期管理；开启狂战双刀等多段普攻时每段独立发射特效。
        BasicAttackVisuals.register("KATANA",true,false,(visual,slash,interval)->{
            visual.addProperty("sequence",slash);
        });
    }
    private static CameraPose camera(Track t){
        var mc=Minecraft.getInstance();
        if(t==null||mc.level==null)return null;
        Vec3 origin=vector(t.start.origin()),direction=vector(t.start.direction()),side=side(direction),target=origin.add(direction.scale(3)).add(0,2,0);
        Vec3 position=origin.subtract(direction.scale(10)).add(side.scale(5)).add(0,7,0);
        var wall=mc.level.clip(new ClipContext(target,position,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
        if(wall.getType()!=HitResult.Type.MISS)position=wall.getLocation().add(target.subtract(position).normalize().scale(.25));
        Vec3 look=target.subtract(position);
        return new CameraPose(position,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
    }
    private static void animate(Track t,net.minecraft.world.entity.LivingEntity caster,EntityRenderState raw,long time){
        if(!(raw instanceof AvatarRenderState state))return;
        if(t.start.skill().equals("yumis_dungeon_and_fight:storm")&&t.start.caster().equals(caster.getUUID())){
            long age=StormChoreography.presentationTime(time-t.start.startedAt());
            if(age<0||age>=7200)return;
            Vec3 p=local(t.start,StormChoreography.actor(t.start.seed(),age));
            state.x=p.x;
            state.y=p.y;
            state.z=p.z;
            state.shadowRadius=0;
            state.shadowPieces.clear();
            state.nameTag=null;
            state.displayFireAnimation=false;
            Vec3 heading=vector(t.start.direction());
            state.xRot=0;
            if(age>=1000&&age<7000){
                int phase=(int)((age-1000)/250);
                int[] sequence=StormChoreography.order(t.start.seed());
                Vec3 from=local(t.start,StormChoreography.sword(sequence[phase])),to=phase==23?vector(t.start.origin()):local(t.start,StormChoreography.sword(sequence[phase+1]));
                heading=to.subtract(from);
                state.xRot=(float)-Math.toDegrees(Math.atan2(heading.y,heading.horizontalDistance()));
            }
            state.bodyRot=(float)Math.toDegrees(Math.atan2(-heading.x,heading.z));
            state.yRot=0;
            state.walkAnimationSpeed=0;
            state.attackTime=age>=1000&&age<7000?(age%250)/250f:0;
            state.isCrouching=false;
            state.isFallFlying=false;
            ensureSwords(t);
            int index=StormChoreography.order(t.start.seed())[(int)Math.clamp((age-1000)/250,0,23)];
            Minecraft.getInstance().getItemModelResolver().updateForLiving(state.getMainHandItemState(),t.swords[index],caster.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,caster);
            return;
        }
    }
    private static void ensureSwords(Track t){
        if(t.swords!=null)return;
        t.swords=GhostSwordsmanWeaponVisuals.choose(t.start.seed());
        t.models=new ItemStackRenderState[24];
        t.inclinations=GhostSwordsmanWeaponVisuals.inclinations(t.start.seed());
    }
    record BasicSlashSample(double progress,double alphaScale){}
    enum CondensedBlade{BLOOD_SWORD,LIGHTSABER}
    record OffhandSlash(CondensedBlade blade,float swingProgress,float equipOffset,double visibility,int color){}
    private static final Identifier BLOOD_SWORD_MODEL=Identifier.fromNamespaceAndPath("yumis_dungeon_and_fight","effect/blood_sword");
    /** Fast draw-in to full arc followed by slower alpha fade on the completed crescent. */
    static BasicSlashSample basicSlashSample(long age,long duration){
        if(duration<=0||age<0||age>=duration)return null;
        double draw=Math.clamp(duration*.22,50d,85d);
        if(age<draw){
            double d=age/draw;
            return new BasicSlashSample(0.16+0.30*Math.pow(d,0.78),1.0);
        }
        double f=Math.clamp((age-draw)/Math.max(1d,duration-draw),0d,1d);
        return new BasicSlashSample(Math.clamp(0.46+0.18*f,0d,0.999),Math.pow(Math.max(0d,1d-f),1.35));
    }
    /** True when a dual-wield basic attack event is an off-hand (2nd / odd-index) strike. */
    static boolean isDualOffhandStrike(SkillVisualEvent event){
        return event!=null&&"ATTACK".equals(event.skill())&&event.phase()==SkillVisualEvent.Phase.START&&event.dual()&&!event.sweep()&&(event.sequence()&1)==1;
    }
    /** Samples the condensed left-hand blade during off-hand strikes only (Berserker blood sword / Swordmaster lightsaber). */
    static OffhandSlash sampleOffhandSlash(SkillVisualEvent event,long age,boolean continuous){
        if(!isDualOffhandStrike(event))return null;
        CondensedBlade blade=BerserkerVisuals.isFrenzyBasic(event)?CondensedBlade.BLOOD_SWORD
            :"LIGHTSABER".equals(event.weaponType())?CondensedBlade.LIGHTSABER:null;
        if(blade==null)return null;
        long baseDur=SwordmasterVisuals.basicSingleDuration(event);
        if(age<0||age>=baseDur)return null;
        long swingDur=Math.min(240L,Math.max(150L,baseDur-60L));
        long fadeStart=Math.max(swingDur,baseDur-95L);
        double vanish=continuous||age<=fadeStart?1.0:Math.clamp((baseDur-age)/(double)Math.max(1L,baseDur-fadeStart),0.0,1.0);
        float swing=age<swingDur?(float)Math.clamp(age/(double)swingDur,0.0,1.0):0f;
        float equip=(float)((1.0-vanish)*0.72);
        return new OffhandSlash(blade,swing,equip,vanish,event.color());
    }
    static ItemStack condensedBladeStack(Player player,CondensedBlade blade){
        if(blade==CondensedBlade.BLOOD_SWORD){
            var stack=new ItemStack(Items.NETHERITE_SWORD);
            stack.set(DataComponents.ITEM_MODEL,BLOOD_SWORD_MODEL);
            return stack;
        }
        if(player!=null){
            var main=player.getMainHandItem();
            var gear=EquipmentItems.view(main);
            if(gear!=null&&gear.weapon()==Weapon.LIGHTSABER)return main.copy();
        }
        return EquipmentVisualItems.weaponModel("common_lightsaber");
    }
    static void decorateOffhandBlade(Player player,AvatarRenderState state,SkillVfx.OffhandBlade blade){
        boolean left=player.getMainArm()!=HumanoidArm.LEFT;
        ItemStack stack=blade.item();
        Minecraft.getInstance().getItemModelResolver().updateForLiving(
            left?state.leftHandItemState:state.rightHandItemState,stack,
            left?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,player);
        if(left){
            state.leftHandItemStack=stack;
            state.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        }else{
            state.rightHandItemStack=stack;
            state.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;
        }
        state.attackArm=left?HumanoidArm.LEFT:HumanoidArm.RIGHT;
        state.attackTime=blade.attack();
    }
    static void warglaiveBasicSlash(List<SpriteQuad> sprites,Vec3 p,Vec3 d,long age,long duration,boolean leftMainArm,int color){
        if(duration<=0||age<0)return;
        double half=duration/2d;
        long slashDur=BasicAttackVisuals.slashDuration(duration,2,false);
        Vec3 center=p.add(0,1.15,0).add(d.scale(1.1));
        for(int slash=0;slash<2;slash++){
            long slashAge=Math.round(age-slash*half);
            var sample=basicSlashSample(slashAge,slashDur);
            if(sample==null)continue;
            int alpha=(int)Math.round(235*sample.alphaScale());
            if(alpha<=4)continue;
            boolean leftHand=((slash&1)==1)^leftMainArm;
            double roll=leftHand?-138d:-42d;
            double yOff=leftHand?-.04:.04;
            EffectSprites.slashArc(sprites,center.add(0,yOff,0),d,roll,2.2,sample.progress(),color,alpha);
        }
    }
    private static Vec3 side(Vec3 direction){
        return new Vec3(direction.z,0,-direction.x);
    }
    private static Vec3 local(SkillVisualEvent e,StormChoreography.Point point){
        return vector(e.origin()).add(side(vector(e.direction())).scale(point.x())).add(0,point.y(),0).add(vector(e.direction()).scale(point.z()));
    }
    private static Vec3 pixel(Vec3 p){
        return new Vec3(Math.rint(p.x*32)/32,Math.rint(p.y*32)/32,Math.rint(p.z*32)/32);
    }
    static void swordWave(List<Quad> out,Vec3 tip,Vec3 direction,double slashAngle,double radius,double thickness,int fill,int edge,int alpha){
        if(direction.lengthSqr()<1e-6)return;
        Vec3 right=side(direction).normalize();
        if(right.lengthSqr()==0)right=new Vec3(1,0,0);
        Vec3 up=direction.cross(right).normalize();
        double angle=Math.toRadians(slashAngle);
        Vec3 span=up.scale(Math.cos(angle)).add(right.scale(Math.sin(angle)));
        Vec3 forward=direction.scale(.65);
        crescent(out,tip.subtract(forward.scale(radius)),forward,span.scale(.72),radius,thickness,fill,edge,alpha);
    }
    static void bladeLoop(List<Quad> out,Vec3 center,Vec3 u,Vec3 v,double radius,double thickness,int fill,int edge,int alpha){
        Vec3 depth=u.cross(v).normalize().scale(.09);
        for(int i=0;i<24;i++){
            double a=i*Math.TAU/24,b=(i+1)%24*Math.TAU/24;
            Vec3 from=u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));
            Vec3 to=u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));
            bladeStrip(out,center.add(from.scale(radius)),center.add(to.scale(radius)),
                center.add(from.scale(radius-thickness)),center.add(to.scale(radius-thickness)),depth,depth,fill,edge,alpha);
        }
    }
    private static void crescent(List<Quad> out,Vec3 p,Vec3 u,Vec3 v,double radius,double thickness,int fill,int edge,int alpha){
        Vec3 depth=u.cross(v).normalize().scale(.09);
        for(int i=0;i<24;i++){
            double a=-Math.PI/2+i*Math.PI/24,b=a+Math.PI/24;
            Vec3 aa=p.add(u.scale(Math.cos(a)*radius)).add(v.scale(Math.sin(a)*radius)),bb=p.add(u.scale(Math.cos(b)*radius)).add(v.scale(Math.sin(b)*radius));
            Vec3 ia=aa.subtract(u.scale(thickness*Math.cos(a))),ib=bb.subtract(u.scale(thickness*Math.cos(b)));
            Vec3 da=depth.scale(Math.cos(a)),db=depth.scale(Math.cos(b));
            bladeStrip(out,aa,bb,ia,ib,da,db,fill,edge,alpha);
        }
    }
    private static void bladeStrip(List<Quad> out,Vec3 aa,Vec3 bb,Vec3 ia,Vec3 ib,Vec3 da,Vec3 db,int fill,int edge,int alpha){
        int opacity=Math.clamp(alpha,0,255),rim=Math.clamp(alpha+20,0,255);
        out.add(new Quad(pixel(aa.add(da)),pixel(bb.add(db)),pixel(ib.add(db)),pixel(ia.add(da)),fill,opacity));
        out.add(new Quad(pixel(ia.subtract(da)),pixel(ib.subtract(db)),pixel(bb.subtract(db)),pixel(aa.subtract(da)),fill,opacity));
        out.add(new Quad(pixel(aa.subtract(da)),pixel(bb.subtract(db)),pixel(bb.add(db)),pixel(aa.add(da)),edge,rim));
        out.add(new Quad(pixel(ia.add(da)),pixel(ib.add(db)),pixel(ib.subtract(db)),pixel(ia.subtract(da)),edge,opacity));
    }
}
