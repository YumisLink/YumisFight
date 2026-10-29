package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.StormChoreography;
import cn.yumis.fight.modules.skills.api.SkillVisualEvent;

import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import net.minecraft.client.*;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.client.renderer.item.ItemStackRenderState;

import net.minecraft.sounds.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

import java.util.*;
import cn.yumis.fight.modules.skills.api.SkillCatalog;
import cn.yumis.fight.modules.skills.client.SkillVfx.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.vector;
/** Existing skill presentation content. New handlers register without editing track dispatch or rendering transport. */
final class BuiltinSkillVisuals {
    private static final Vec3 UP=new Vec3(0,1,0);
    private BuiltinSkillVisuals(){}
    static void install(){
        for(String id:List.of("ATTACK","MASTERY_WAVE",SkillCatalog.GHOST.id,SkillCatalog.TRIPLE.id,SkillCatalog.WAVE.id,SkillCatalog.MASTERY.id,SkillCatalog.OVERDRIVE.id,SkillCatalog.RISING.id,SkillCatalog.DRAW.id,SkillCatalog.DRAGON.id,SkillCatalog.DANCE.id,SkillCatalog.STORM.id)){
            SkillVisualRegistry.register(id,new SkillVisualRegistry.Handler(){
                public boolean immediateEnd(){return id.equals(SkillCatalog.STORM.id);}
                public boolean persistentEnd(){return id.equals(SkillCatalog.OVERDRIVE.id);}
                public boolean cinematic(){return id.equals(SkillCatalog.STORM.id);}
                public long finishedUntil(Track track,SkillVisualEvent event){return cinematic()?track.start.startedAt()+Math.max(track.start.duration(),event.duration()):event.at()+500;}
                public long defaultDuration(){return id.equals(SkillCatalog.STORM.id)?8000:id.equals(SkillCatalog.GHOST.id)?3800:id.equals(SkillCatalog.OVERDRIVE.id)?30000:2500;}
                public int defaultColor(){return id.equals(SkillCatalog.GHOST.id)?SkillVisualEvent.DARK:SkillVisualEvent.CYAN;}
                public void tick(Track track,long time){BuiltinSkillVisuals.tick(track,time);}
                public void render(LevelRenderContext context,Track track,long age,long time,List<Quad> quads,Vec3 camera){BuiltinSkillVisuals.render(context,track,age,time,quads,camera);}
                public void play(SkillVisualEvent event){BuiltinSkillVisuals.play(event);}
                public void animate(Track track,Player player,EntityRenderState state,long time){BuiltinSkillVisuals.animate(track,player,state,time);}
                public CameraType cameraType(Track track,long time){return StormChoreography.presentationTime(time-track.start.startedAt())<StormChoreography.FINISH?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON;}
                public CameraPose camera(Track track,long time){return cinematic()?BuiltinSkillVisuals.camera(track,time):null;}
            });
        }
    }
    private static void tick(Track t,long time){
            if(t.start.skill().equals("yumis_dungeon_and_fight:storm")){
                long age=StormChoreography.presentationTime(time-t.start.startedAt());
                int phase=(int)Math.clamp((age-1000)/250,0,23);
                if(age>=1000&&age<7000&&phase>t.soundPhase){
                    t.soundPhase=phase;
                    sound(StormChoreography.actor(t.start.seed(),age),t.start,SoundEvents.PLAYER_ATTACK_SWEEP,.55f,1.1f+(phase%3)*.14f);
                }
            }
    }
    private static void render(LevelRenderContext context,Track t,long age,long time,List<Quad> quads,Vec3 camera){
        SkillVisualEvent event=t.start;
        if(event.skill().equals("ATTACK")&&t.motion!=null){double fade=t.last.phase()==SkillVisualEvent.Phase.FINISH?Math.max(0,1-(time-t.last.at())/180d):1;Vec3 p=vector(t.last.position());orb(quads,p,.16*fade,event.color(),(int)(220*fade));ring(quads,p.add(0,-.1,0),.2*fade,event.color(),(int)(110*fade));return;}
            if(event.skill().equals("yumis_dungeon_and_fight:storm")){
                storm(context,t,age,quads,camera);
                return;
            }
            if(event.skill().equals("yumis_dungeon_and_fight:overdrive")){
                Player actor=Minecraft.getInstance().level.getPlayerByUUID(event.caster());
                if(actor!=null){
                    Vec3 p=actor.position().add(actor.getLookAngle().multiply(1,0,1).scale(.55)).add(0,1,0);
                    orb(quads,p,.10,SkillVisualEvent.GOLD,100);
                    if(age<550)ring(quads,actor.position().add(0,.08,0),Math.min(1.8,age/250.0),SkillVisualEvent.GOLD,(int)(160*(1-age/550.0)));
                }
                return;
            }
            if(event.skill().equals("yumis_dungeon_and_fight:ghost")&&!t.released){
                Player actor=Minecraft.getInstance().level.getPlayerByUUID(event.caster());
                Vec3 p=(actor==null?vector(event.origin()):actor.position()).add(vector(event.direction()).scale(.8)).add(0,1.2,0);
                orb(quads,p,.15+.18*Math.min(1,age/1000.0),event.color(),180);
                ringVertical(quads,p,.35+.3*Math.min(1,age/1000.0),vector(event.direction()),event.color(),130);
            }
            if(event.skill().equals("yumis_dungeon_and_fight:draw")&&!t.released&&age<400)ring(quads,vector(event.origin()).add(0,.1,0),1.2*(1-age/500.0),event.color(),160);
            if((event.skill().equals("yumis_dungeon_and_fight:wave")||(event.skill().equals("yumis_dungeon_and_fight:dance")||event.skill().equals("MASTERY_WAVE"))&&t.released)&&t.last.phase()==SkillVisualEvent.Phase.TRAIL&&time-t.last.at()<180){
                double radius=event.skill().equals("yumis_dungeon_and_fight:wave")?1:1.5;
                Vec3 direction=vector(t.last.direction());
                Vec3 p=vector(t.last.position()).subtract(direction.scale(radius*.65)).add(0,event.skill().equals("yumis_dungeon_and_fight:wave")?.8:1,0);
                crescent(quads,p,direction.scale(.65),UP.scale(.72),radius,.35,event.color(),event.color(),200);
                ring(quads,vector(t.last.position()).add(0,.08,0),.4,event.color(),75);
            }
            for(SkillVisualEvent flash:t.flashes){
                long elapsed=time-flash.at();
                if(elapsed<0||elapsed>flashDuration(flash))continue;
                slash(quads,flash,elapsed);
            }
            if(t.last.phase()==SkillVisualEvent.Phase.TRAIL&&time-t.last.at()<150)slash(quads,t.last,time-t.last.at());
    }
    private static CameraPose camera(Track t,long time){
        var mc=Minecraft.getInstance();
        if(t==null||StormChoreography.presentationTime(time-t.start.startedAt())>=StormChoreography.FINISH||mc.level==null)return null;
        Vec3 origin=vector(t.start.origin()),direction=vector(t.start.direction()),side=side(direction),target=origin.add(direction.scale(3)).add(0,2,0);
        Vec3 position=origin.subtract(direction.scale(10)).add(side.scale(5)).add(0,7,0);
        var wall=mc.level.clip(new ClipContext(target,position,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));
        if(wall.getType()!=HitResult.Type.MISS)position=wall.getLocation().add(target.subtract(position).normalize().scale(.25));
        Vec3 look=target.subtract(position);
        return new CameraPose(position,(float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())));
    }
    private static void animate(Track t,Player player,EntityRenderState raw,long time){
        if(!(raw instanceof AvatarRenderState state))return;
        if(t.start.skill().equals("yumis_dungeon_and_fight:storm")&&t.start.caster().equals(player.getUUID())){
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
            Minecraft.getInstance().getItemModelResolver().updateForLiving(state.getMainHandItemState(),t.swords[index],player.getMainArm()==net.minecraft.world.entity.HumanoidArm.LEFT?ItemDisplayContext.THIRD_PERSON_LEFT_HAND:ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,player);
            return;
        }
    }
    private static void ensureSwords(Track t){
        if(t.swords!=null)return;
        t.swords=GhostSwordsmanWeaponVisuals.choose(t.start.seed());
        t.models=new ItemStackRenderState[24];
        t.inclinations=GhostSwordsmanWeaponVisuals.inclinations(t.start.seed());
    }
    private static void slash(List<Quad> out,SkillVisualEvent e,long age){
        Vec3 direction=vector(e.direction()),right=side(direction),p=vector(e.position()).add(direction.scale(e.forwardOffset()));
        double fade=Math.max(0,1-age/(double)flashDuration(e));
        if(e.skill().equals("ATTACK"))fade=Math.sqrt(fade);
        int alpha=(int)(210*fade),color=e.color();
        int first=out.size();
        switch(e.skill()){
            case "ATTACK"->{
                if(e.phase()==SkillVisualEvent.Phase.START){if(e.sweep())crescent(out,p.add(direction.scale(3)).add(0,1.1,0),direction,right,3,.25,color,color,alpha);else crescent(out,p.add(direction.scale(.35)).add(0,1.1,0),direction,attackSpan(e,right),.8,.20,color,color,alpha);}
            }
            case "yumis_dungeon_and_fight:mastery"->{
                orb(out,p.add(direction.scale(.6)).add(0,1,0),.18,color,alpha);
            }
            case "yumis_dungeon_and_fight:ghost"->{
                if(e.phase()==SkillVisualEvent.Phase.RELEASE)crescent(out,p.add(direction.scale(2.3)).add(0,1.3-age/2500.0,0),direction.scale(.65),UP.scale(.5),1.8,.35+.22*e.charge(),0x171022,color,alpha);
            }
            case "yumis_dungeon_and_fight:triple","yumis_dungeon_and_fight:dragon","yumis_dungeon_and_fight:rising"->{
                if(e.phase()==SkillVisualEvent.Phase.SEGMENT)crescent(out,p.add(direction.scale(1.5)).add(0,1.25-age/3000.0,0),direction.scale(.7),UP.scale(.5),e.skill().equals("yumis_dungeon_and_fight:dragon")?1.4:1.1,.28,color,color,alpha);
                if(e.phase()==SkillVisualEvent.Phase.TRAIL&&age<180){
                    crescent(out,p.add(0,.9,0),direction,UP.scale(.7),.8,.18,color,color,alpha/2);
                    if(e.skill().equals("yumis_dungeon_and_fight:dragon"))dragon(out,p,direction,color,alpha);
                }
                if(e.phase()==SkillVisualEvent.Phase.RELEASE)crescent(out,p.add(direction.scale(.5)).add(0,.7+age/650.0,0),direction,UP.scale(.75),1.3,.4,color,color,alpha);
            }
            case "yumis_dungeon_and_fight:draw"->{
                if(e.phase()==SkillVisualEvent.Phase.RELEASE)crescent(out,p.add(0,1,0),direction,right,5,.45,color,0xF5FFFF,alpha);
            }
            case "yumis_dungeon_and_fight:dance"->{
                if(e.phase()==SkillVisualEvent.Phase.HIT)crescent(out,p.add(direction.scale(.65)).add(0,1.2,0),direction,danceSpan(e,right),1.45,.25,color,color,alpha);
            }
            default->{
            }
        }
        if(e.horizontalScale()!=1)for(int i=first;i<out.size();i++){Quad q=out.get(i);out.set(i,new Quad(horizontalScale(q.a(),p,e.horizontalScale()),horizontalScale(q.b(),p,e.horizontalScale()),horizontalScale(q.c(),p,e.horizontalScale()),horizontalScale(q.d(),p,e.horizontalScale()),q.rgb(),q.alpha()));}
    }
    private static long flashDuration(SkillVisualEvent event){return event.skill().equals("ATTACK")?650:event.skill().equals("yumis_dungeon_and_fight:dance")?225:450;}
    private static Vec3 horizontalScale(Vec3 point,Vec3 origin,double scale){Vec3 delta=point.subtract(origin);return origin.add(delta.x*scale,delta.y,delta.z*scale);}
    private static Vec3 attackSpan(SkillVisualEvent e,Vec3 right){
        double tilt=new SplittableRandom(e.seed()).nextDouble(Math.PI*2);
        return UP.scale(Math.cos(tilt)*.8).add(right.scale(Math.sin(tilt)*.8));
    }
    private static Vec3 danceSpan(SkillVisualEvent e,Vec3 right){
        double angle=new SplittableRandom(e.seed()^((long)e.sequence()*0x9E3779B97F4A7C15L)).nextDouble(Math.PI*2);
        return UP.scale(Math.cos(angle)*.75).add(right.scale(Math.sin(angle)*.75));
    }
    private static void storm(LevelRenderContext context,Track t,long age,List<Quad> out,Vec3 camera){
        age=StormChoreography.presentationTime(age);
        SkillVisualEvent e=t.start;
        Vec3 center=vector(e.origin()).add(vector(e.direction()).scale(3));
        int color=e.color();
        double deploy=Math.clamp((age-200)/800.0,0,1);
        if(age<1000)orb(out,center.add(0,2,0),age<200?.3+age/600.0:.25,color,210);
        ring(out,center.add(0,.06,0),5*deploy,color,age>=7200?(int)(110*(8000-age)/800.0):110);
        if(age<7200){
            ensureSwords(t);
            int[] order=StormChoreography.order(e.seed());
            boolean[] used=new boolean[24];
            for(int i=0;i<StormChoreography.completed(age);i++)used[order[i]]=true;
            for(int i=0;i<24;i++)if(!used[i]){
                var point=StormChoreography.sword(i);
                double spin=(1-deploy)*Math.PI*2,xx=point.x()*Math.cos(spin)-(point.z()-3)*Math.sin(spin),zz=point.x()*Math.sin(spin)+(point.z()-3)*Math.cos(spin);
                Vec3 p=local(e,new StormChoreography.Point(xx*deploy,2+(point.y()-2)*deploy,zz*deploy+3));
                if(age<200){
                    orb(out,p.add(0,.6,0),.12,color,140);
                    continue;
                }
                var model=t.models[i];
                if(model==null){
                    model=new ItemStackRenderState();
                    Minecraft.getInstance().getItemModelResolver().updateForLiving(model,t.swords[i],ItemDisplayContext.NONE,Minecraft.getInstance().player);
                    t.models[i]=model;
                }
                context.swords().add(new Sword(model,p,i*30,t.inclinations.get(i)));
                ring(out,p.add(0,.04,0),.23,color,95);
            }
        }
        if(age>=1000&&age<7000){
            int phase=(int)((age-1000)/250);
            var order=StormChoreography.order(e.seed());
            Vec3 from=local(e,StormChoreography.sword(order[phase])),to=phase==23?vector(e.origin()):local(e,StormChoreography.sword(order[phase+1]));
            ribbon(out,from.add(0,1,0),to.add(0,1,0),.15,color,140);
            Vec3 actor=local(e,StormChoreography.actor(e.seed(),age)),travel=to.subtract(from).normalize(),span=UP.subtract(travel.scale(travel.dot(UP))).normalize();
            crescent(out,actor.add(0,1,0),travel,span,1.2,.35,color,color,180);
        }
        if(age>=7200){
            double fade=Math.max(0,1-(age-7200)/800.0);
            crescent(out,center.add(0,1.7,0),vector(e.direction()),side(vector(e.direction())),5,.8,color,0xF8FFFF,(int)(230*fade));
            crescent(out,center.add(0,1.8,0),vector(e.direction()),UP.scale(.6),3,.5,color,color,(int)(190*fade));
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
    private static void crescent(List<Quad> out,Vec3 p,Vec3 u,Vec3 v,double radius,double thickness,int fill,int edge,int alpha){
        Vec3 depth=u.cross(v).normalize().scale(.09);
        for(int i=0;i<24;i++){
            double a=-Math.PI/2+i*Math.PI/24,b=a+Math.PI/24;
            Vec3 aa=p.add(u.scale(Math.cos(a)*radius)).add(v.scale(Math.sin(a)*radius)),bb=p.add(u.scale(Math.cos(b)*radius)).add(v.scale(Math.sin(b)*radius));
            Vec3 ia=aa.subtract(u.scale(thickness*Math.cos(a))),ib=bb.subtract(u.scale(thickness*Math.cos(b)));
            Vec3 da=depth.scale(Math.cos(a)),db=depth.scale(Math.cos(b));
            int opacity=Math.clamp(alpha,0,255),rim=Math.clamp(alpha+20,0,255);
            out.add(new Quad(pixel(aa.add(da)),pixel(bb.add(db)),pixel(ib.add(db)),pixel(ia.add(da)),fill,opacity));
            out.add(new Quad(pixel(ia.subtract(da)),pixel(ib.subtract(db)),pixel(bb.subtract(db)),pixel(aa.subtract(da)),fill,opacity));
            out.add(new Quad(pixel(aa.subtract(da)),pixel(bb.subtract(db)),pixel(bb.add(db)),pixel(aa.add(da)),edge,rim));
            out.add(new Quad(pixel(ia.add(da)),pixel(ib.add(db)),pixel(ib.subtract(db)),pixel(ia.subtract(da)),edge,opacity));
        }
    }
    private static void ring(List<Quad> out,Vec3 p,double radius,int color,int alpha){
        for(int i=0;i<48;i++){
            double a=i*Math.PI/24,b=(i+1)*Math.PI/24;
            Vec3 aa=p.add(Math.cos(a)*radius,0,Math.sin(a)*radius),bb=p.add(Math.cos(b)*radius,0,Math.sin(b)*radius);
            out.add(new Quad(aa,bb,p.add(Math.cos(b)*(radius-.045),0,Math.sin(b)*(radius-.045)),p.add(Math.cos(a)*(radius-.045),0,Math.sin(a)*(radius-.045)),color,Math.clamp(alpha,0,255)));
        }
    }
    private static void ringVertical(List<Quad> out,Vec3 p,double radius,Vec3 direction,int color,int alpha){
        crescent(out,p,side(direction),UP,radius,.035,color,color,alpha);
        crescent(out,p,side(direction).scale(-1),UP,radius,.035,color,color,alpha);
    }
    private static void orb(List<Quad> out,Vec3 p,double radius,int color,int alpha){
        for(int i=0;i<3;i++){
            Vec3 u=i==0?UP:new Vec3(1,0,0),v=i==2?UP:new Vec3(0,0,1);
            out.add(new Quad(p.add(u.scale(radius)),p.add(v.scale(radius)),p.subtract(u.scale(radius)),p.subtract(v.scale(radius)),color,Math.clamp(alpha,0,255)));
        }
    }
    private static void ribbon(List<Quad> out,Vec3 a,Vec3 b,double width,int color,int alpha){
        Vec3 offset=UP.scale(width);
        out.add(new Quad(a.add(offset),b.add(offset),b.subtract(offset),a.subtract(offset),color,alpha));
    }
    private static void dragon(List<Quad> out,Vec3 p,Vec3 direction,int color,int alpha){
        Vec3 right=side(direction);
        for(int i=0;i<5;i++){
            Vec3 a=p.subtract(direction.scale(i*.45)).add(right.scale(Math.sin(i*1.7)*.3)).add(0,1-i*.08,0);
            orb(out,a,.20-i*.025,color,alpha/2);
        }
        ribbon(out,p.add(right.scale(.35)).add(0,1,0),p.add(right.scale(.55)).add(direction.scale(.4)).add(0,1.5,0),.04,color,alpha);
        ribbon(out,p.subtract(right.scale(.35)).add(0,1,0),p.subtract(right.scale(.55)).add(direction.scale(.4)).add(0,1.5,0),.04,color,alpha);
    }
    private static void play(SkillVisualEvent e){
        if(e.phase()==SkillVisualEvent.Phase.TRAIL)return;
        SoundEvent sound=SoundEvents.PLAYER_ATTACK_SWEEP;
        float volume=.55f,pitch=1.1f;
        if(e.phase()==SkillVisualEvent.Phase.START){
            if(e.skill().equals("yumis_dungeon_and_fight:ghost")||e.skill().equals("yumis_dungeon_and_fight:draw")){
                sound=SoundEvents.ENCHANTMENT_TABLE_USE;
                volume=.35f;
                pitch=1.6f;
            }
            else if(e.skill().equals("yumis_dungeon_and_fight:overdrive")){
                sound=SoundEvents.BEACON_ACTIVATE;
                volume=.6f;
                pitch=1.5f;
            }
            else if(e.skill().equals("yumis_dungeon_and_fight:storm")){
                sound=SoundEvents.BEACON_ACTIVATE;
                volume=.8f;
                pitch=.9f;
            }
            else if(!e.skill().equals("ATTACK")&&!e.skill().equals("yumis_dungeon_and_fight:wave")&&!e.skill().equals("yumis_dungeon_and_fight:mastery"))return;
        }
        if(e.phase()==SkillVisualEvent.Phase.FINISH){
            sound=SoundEvents.PLAYER_ATTACK_STRONG;
            volume=1;
            pitch=.65f;
        }
        var mc=Minecraft.getInstance();
        Vec3 p=vector(e.position());
        if(mc.level!=null)mc.level.playLocalSound(p.x,p.y,p.z,sound,SoundSource.PLAYERS,volume,pitch,false);
    }
    private static void sound(StormChoreography.Point relative,SkillVisualEvent e,SoundEvent sound,float volume,float pitch){
        var mc=Minecraft.getInstance();
        Vec3 p=local(e,relative);
        if(mc.level!=null)mc.level.playLocalSound(p.x,p.y,p.z,sound,SoundSource.PLAYERS,volume,pitch,false);
    }
}
