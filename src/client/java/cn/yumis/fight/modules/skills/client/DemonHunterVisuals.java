package cn.yumis.fight.modules.skills.client;

import cn.yumis.fight.modules.skills.api.*;
import cn.yumis.fight.modules.presentation.api.client.WorldQuads.Quad;
import cn.yumis.fight.modules.presentation.api.client.EffectParticles;
import net.minecraft.client.*;
import net.minecraft.world.phys.*;
import java.util.*;
import static cn.yumis.fight.modules.skills.client.SkillVfx.vector;

/** Fel ribbons and translucent demon silhouettes. Geometry never determines combat results. */
final class DemonHunterVisuals {
    private static final int GREEN=0x87ec39,PALE=0xd6ff90;
    private DemonHunterVisuals(){}
    static void install(){for(String id:DemonHunterSkills.ALL)SkillVisualRegistry.register(id,new Handler(id));}
    private static final class Handler implements SkillVisualRegistry.Handler {
        private final String id;Handler(String id){this.id=id;}
        public int defaultColor(){return GREEN;}
        public boolean cinematic(){return id.equals(DemonHunterSkills.DANCE);}
        public boolean restoreCamera(){return cinematic();}
        public boolean blocksTurning(){return cinematic();}
        public boolean blocksHotbar(){return cinematic()||id.equals(DemonHunterSkills.META)||id.equals(DemonHunterSkills.HUNT);}
        public CameraType cameraType(SkillVfx.Track t,long time){return CameraType.THIRD_PERSON_BACK;}
        public void animate(SkillVfx.Track t,net.minecraft.world.entity.player.Player player,net.minecraft.client.renderer.entity.state.EntityRenderState raw,long time){if(!(raw instanceof net.minecraft.client.renderer.entity.state.AvatarRenderState s)||!id.equals(DemonHunterSkills.DANCE))return;s.walkAnimationSpeed=0;s.attackTime=(float)((time-t.start.startedAt())%350/350d);s.rightArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;s.leftArmPose=net.minecraft.client.model.HumanoidModel.ArmPose.ITEM;}
        public void play(SkillVisualEvent e){if(e.phase()==SkillVisualEvent.Phase.FINISH&&(id.equals(DemonHunterSkills.DANCE)||id.equals(DemonHunterSkills.META))){Vec3 p=vector(e.position());for(int i=0;i<28;i++){double angle=i*Math.PI/14;EffectParticles.dust(p.add(Math.cos(angle)*(1+i%3),.1+(i%4)*.1,Math.sin(angle)*(1+i%3)),0xa9a18b,.8f);}}}
        public void render(SkillVfx.LevelRenderContext context,SkillVfx.Track t,long age,long time,List<Quad> q,Vec3 camera){
            var mc=Minecraft.getInstance();var actor=mc.level==null?null:mc.level.getPlayerByUUID(t.start.caster());Vec3 p=vector(t.last.position()),d=vector(t.last.direction()).normalize();
            if(id.equals(DemonHunterSkills.BEAM)){if(actor==null||age>5000)return;Vec3 from=actor.getEyePosition(),dir=actor.getLookAngle().normalize(),end=from.add(dir.scale(16));var hit=mc.level.clip(new net.minecraft.world.level.ClipContext(from,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,actor));if(hit.getType()!=HitResult.Type.MISS)end=hit.getLocation();beam(q,from,end,.5,GREEN,90);beam(q,from,end,.18,PALE,230);return;}
            if(id.equals(DemonHunterSkills.GLAIVE)){Vec3 right=new Vec3(-d.z,0,d.x);double a=age/70d;Vec3 u=right.scale(Math.cos(a)).add(0,Math.sin(a),0),v=right.scale(-Math.sin(a)).add(0,Math.cos(a),0);crescent(q,p,u,v,.65,.12,GREEN,230);crescent(q,p,u.scale(-1),v,.65,.12,PALE,230);return;}
            if(id.equals(DemonHunterSkills.META)&&actor!=null&&!t.released){Vec3 center=PlayerVisualAnchor.position(actor);for(int i=0;i<6;i++){double a=time/200d+i*Math.PI/3;Vec3 mote=center.add(Math.cos(a)*.7,.3+(i*.3+time/700d)%1.7,Math.sin(a)*.7);beam(q,mote,mote.add(0,.18,0),.05,PALE,190);}}
            if(id.equals(DemonHunterSkills.DANCE)){Vec3 center=vector(t.start.origin());ring(q,center.add(0,.04,0),3,.08,GREEN,170);for(int i=0;i<12;i++){double a=i*Math.PI/6;beam(q,center.add(Math.cos(a)*2.75,.05,Math.sin(a)*2.75),center.add(Math.cos(a)*3,.05,Math.sin(a)*3),.025,PALE,180);}}
            if(id.equals(DemonHunterSkills.RUSH)||id.equals(DemonHunterSkills.HUNT)){if(actor!=null){Vec3 here=actor.position().add(0,.8,0);beam(q,here,here.subtract(actor.getLookAngle().scale(2.5)),.22,GREEN,100);}}
            for(var flash:t.flashes){long n=time-flash.at();if(n<0||n>450||flash.phase()==SkillVisualEvent.Phase.START)continue;double fade=1-n/450d;Vec3 c=vector(flash.position()),forward=vector(flash.direction()).normalize(),side=new Vec3(-forward.z,0,forward.x);if(side.lengthSqr()<.01)side=new Vec3(1,0,0);
                if(flash.phase()==SkillVisualEvent.Phase.FINISH){ring(q,c.add(0,.08,0),Math.min(id.equals(DemonHunterSkills.DANCE)?3:4,.3+n/65d),.2,GREEN,(int)(220*fade));}
                else crescent(q,c.add(0,1,0),side,new Vec3(0,1,0),id.equals(DemonHunterSkills.DANCE)?3:1.6,.15,GREEN,(int)(220*fade));
            }
        }
    }
    static void actors(List<Quad> q,long now){
        var mc=Minecraft.getInstance();if(mc.level==null)return;for(var state:CareerClient.INSTANCE.view.actors){var p=mc.level.getPlayerByUUID(UUID.fromString(state.owner));if(p==null)continue;Vec3 center=PlayerVisualAnchor.position(p);
            if(state.auraUntil>now){ring(q,center.add(0,.06,0),4,.07,GREEN,100);for(int i=0;i<18;i++){double a=i*Math.PI/9+now/1400d,r=3.4+.3*Math.sin(now/270d+i);Vec3 from=center.add(Math.cos(a)*r,.03,Math.sin(a)*r);beam(q,from,from.add(Math.sin(a)*.15,.45+.25*Math.sin(i+now/120d),Math.cos(a)*.15),.055,GREEN,150);}}
            if(state.metamorphosisUntil<=now)continue;double a=Math.toRadians(p.yBodyRot);Vec3 side=new Vec3(Math.cos(a),0,Math.sin(a)),back=new Vec3(Math.sin(a),0,-Math.cos(a));Vec3 root=center.add(0,1.4,0).add(back.scale(.15));
            for(int sign:new int[]{-1,1}){Vec3 tip=root.add(side.scale(sign*(2+.12*Math.sin(now/230d)))).add(0,.9,0).add(back.scale(.65)),low=root.add(side.scale(sign*1.3)).add(0,-.75,0).add(back.scale(.55)),mid=root.add(side.scale(sign*.9)).add(0,.5,0);q.add(new Quad(root,mid,tip,low,0x315a2c,100));q.add(new Quad(low,tip,mid,root,0x315a2c,100));beam(q,root,tip,.035,GREEN,190);beam(q,tip,low,.025,PALE,150);Vec3 horn=center.add(side.scale(sign*.21)).add(0,1.85,0),bend=horn.add(side.scale(sign*.22)).add(0,.35,0).add(back.scale(.1)),end=bend.add(side.scale(-sign*.06)).add(0,.3,0);beam(q,horn,bend,.09,0x253c2b,230);beam(q,bend,end,.045,GREEN,230);}
            for(int i=0;i<5;i++){double v=now/450d+i*Math.PI*2/5;Vec3 here=center.add(Math.cos(v)*.65,.3+(now/500d+i*.4)%1.7,Math.sin(v)*.65);beam(q,here,here.add(0,.15,0),.035,PALE,170);}
        }
    }
    private static void beam(List<Quad> q,Vec3 a,Vec3 b,double w,int color,int alpha){Vec3 d=b.subtract(a),side=new Vec3(-d.z,0,d.x);if(side.lengthSqr()<1e-6)side=new Vec3(1,0,0);side=side.normalize().scale(w);q.add(new Quad(a.add(side),b.add(side),b.subtract(side),a.subtract(side),color,alpha));q.add(new Quad(a.add(0,w,0),b.add(0,w,0),b.add(0,-w,0),a.add(0,-w,0),color,alpha));}
    private static void ring(List<Quad> q,Vec3 p,double r,double w,int color,int alpha){for(int i=0;i<48;i++){double a=i*Math.PI/24,b=(i+1)*Math.PI/24;Vec3 u=new Vec3(Math.cos(a),0,Math.sin(a)),v=new Vec3(Math.cos(b),0,Math.sin(b));q.add(new Quad(p.add(u.scale(r)),p.add(v.scale(r)),p.add(v.scale(r-w)),p.add(u.scale(r-w)),color,alpha));}}
    private static void crescent(List<Quad> q,Vec3 p,Vec3 u,Vec3 v,double r,double w,int color,int alpha){for(int i=0;i<24;i++){double a=-2.5+i*5d/24,b=a+5d/24;Vec3 x=u.scale(Math.cos(a)).add(v.scale(Math.sin(a))),y=u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));q.add(new Quad(p.add(x.scale(r)),p.add(y.scale(r)),p.add(y.scale(r-w)),p.add(x.scale(r-w)),color,alpha));}}
}
